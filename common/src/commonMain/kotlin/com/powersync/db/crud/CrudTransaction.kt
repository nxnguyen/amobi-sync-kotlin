package com.powersync.db.crud

import com.powersync.db.internal.PowerSyncTransaction

/**
 * A transaction of client-side changes.
 */
public data class CrudTransaction(
    /**
     * Unique transaction id.
     *
     * If null, this contains a list of changes recorded without an explicit transaction associated.
     */
    val transactionId: Int?,
    /**
     * List of client-side changes.
     */
    val crud: List<CrudEntry>,
    /**
     * Call to remove the changes from the local queue, once successfully uploaded.
     *
     * [writeCheckpoint] is optional.
     */
    val complete: suspend (writeCheckpoint: String?) -> Unit,
    /** Atomically run local metadata cleanup with SDK queue completion. */
    val completeWith: (suspend (writeCheckpoint: String?, cleanup: suspend (PowerSyncTransaction) -> Unit) -> Unit)? = null,
) {
    override fun toString(): String = "CrudTransaction<$transactionId, $crud>"
}
