package com.powersync

import com.powersync.db.getLong
import com.powersync.db.getString
import com.powersync.db.PowerSyncDatabaseImpl
import com.powersync.db.schema.Column
import com.powersync.db.schema.Schema
import com.powersync.db.schema.Table
import com.powersync.testutils.databaseTest
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class AmobiAtomicCompletionTest {
    private val items = Schema(Table("items", listOf(Column.text("value"))))

    @Test
    fun callbackFailureRollsBackSdkQueueAndLocalReceipt() = databaseTest {
        database.updateSchema(Schema(Table("items", listOf(Column.text("value")))))
        database.execute("CREATE TABLE local_receipt (id TEXT PRIMARY KEY)")
        database.execute("INSERT INTO items(id,value) VALUES('same','value')")
        database.execute("INSERT INTO local_receipt(id) VALUES('receipt')")
        val transaction = database.getNextCrudTransaction()!!
        val completeWith = checkNotNull(transaction.completeWith)

        runCatching {
            completeWith(null) { tx ->
                tx.executeAsync("DELETE FROM local_receipt WHERE id = 'receipt'")
                error("simulate process failure before COMMIT")
            }
        }.isFailure shouldBe true
        database.getNextCrudTransaction()!!.crud.first().clientId shouldBe transaction.crud.first().clientId
        database.get("SELECT COUNT(*) AS n FROM local_receipt") { it.getLong("n") } shouldBe 1L

        completeWith(null) { tx ->
            tx.executeAsync("DELETE FROM local_receipt WHERE id = 'receipt'") shouldBe 1L
        }
        database.getNextCrudTransaction() shouldBe null
        database.get("SELECT COUNT(*) AS n FROM local_receipt") { it.getLong("n") } shouldBe 0L
    }

    @Test
    fun frozenPendingBodyAndKeySurviveCloseReopenAfterLostAck() = databaseTest {
        database.updateSchema(items)
        createPeriodPendingTable(database)
        database.execute("INSERT INTO items(id,value) VALUES('same','value')")
        val first = database.getNextCrudTransaction()!!
        val key = "period-v1-00000000-0000-4000-8000-000000000001-0"
        val body = """{"entries":[{"op":"PUT","table":"items","id":"same","data":{"value":"value"}}]}"""
        database.execute(
            "INSERT INTO period_upload_pending(singleton,upload_id,body,next_chunk) VALUES(1,?,?,0)",
            listOf(key, body),
        )

        val accepted = mutableMapOf<String, String>()
        accepted[key] = body // The server committed; its HTTP response was lost.
        database.close()
        database = openDatabase(items)

        val frozen = database.get("SELECT upload_id,body FROM period_upload_pending") {
            it.getString("upload_id") to it.getString("body")
        }
        frozen shouldBe (key to body)
        accepted[frozen.first] shouldBe body
        accepted.size shouldBe 1
        database.execute("UPDATE period_upload_pending SET next_chunk=1 WHERE singleton=1")
        val retried = database.getNextCrudTransaction()!!
        retried.crud.first().clientId shouldBe first.crud.first().clientId
        checkNotNull(retried.completeWith)(null) { tx ->
            tx.executeAsync("DELETE FROM period_upload_pending WHERE singleton=1 AND next_chunk=1") shouldBe 1L
        }
        database.getNextCrudTransaction() shouldBe null
        database.get("SELECT COUNT(*) AS n FROM period_upload_pending") { it.getLong("n") } shouldBe 0L
    }

    @Test
    fun identicalLaterDeleteGetsIndependentReceiptAfterPeerPut() = databaseTest {
        database.updateSchema(items)
        createPeriodPendingTable(database)
        database.execute("INSERT INTO items(id,value) VALUES('same','value')")
        database.getNextCrudTransaction()!!.complete(null)
        val remoteRows = mutableMapOf("same" to "value")
        val receipts = mutableSetOf<String>()
        val deleteBody = """{"entries":[{"op":"DELETE","table":"items","id":"same"}]}"""

        suspend fun deleteWithKey(key: String) {
            database.execute("DELETE FROM items WHERE id='same'")
            val pending = database.getNextCrudTransaction()!!
            database.execute(
                "INSERT INTO period_upload_pending(singleton,upload_id,body,next_chunk) VALUES(1,?,?,0)",
                listOf(key, deleteBody),
            )
            if (receipts.add(key)) remoteRows.remove("same")
            database.execute("UPDATE period_upload_pending SET next_chunk=1 WHERE singleton=1")
            checkNotNull(pending.completeWith)(null) { tx ->
                tx.executeAsync("DELETE FROM period_upload_pending WHERE singleton=1 AND next_chunk=1") shouldBe 1L
            }
        }

        val firstKey = "period-v1-00000000-0000-4000-8000-000000000001-0"
        val secondKey = "period-v1-00000000-0000-4000-8000-000000000002-0"
        deleteWithKey(firstKey)
        remoteRows.containsKey("same") shouldBe false
        // A peer puts the row back. The next local DELETE has identical body.
        remoteRows["same"] = "peer value"
        database.execute("INSERT INTO items(id,value) VALUES('same','peer value')")
        database.getNextCrudTransaction()!!.complete(null)
        deleteWithKey(secondKey)
        firstKey.equals(secondKey) shouldBe false
        remoteRows.containsKey("same") shouldBe false
        receipts.size shouldBe 2
        database.getNextCrudTransaction() shouldBe null
    }

    private suspend fun createPeriodPendingTable(database: PowerSyncDatabaseImpl) {
        database.execute("""CREATE TABLE period_upload_pending (
            singleton INTEGER PRIMARY KEY, upload_id TEXT NOT NULL, body TEXT NOT NULL, next_chunk INTEGER NOT NULL)""")
    }
}
