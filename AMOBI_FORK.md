# Amobi Kotlin/KMP client fork

- Upstream: <https://github.com/powersync-ja/powersync-kotlin>
- Baseline: `v1.15.2`, commit `364c30b70bb179ed68385ad3087f7f9a2b42af16`
- Source archive SHA-256: `67ffd62ed880bd0013527b464d7967fa325be07bfa2257b82b76e5ed8a4aec69`
- Upstream license: MIT, declared in the tag's Gradle POM metadata. The source
  archive has no standalone LICENSE file.
- Amobi coordinates: `com.amobi.sync`, version `1.15.2-amobi.1`.
- Gradle/Kotlin plugin versions are aligned with Period's root build so the
  composite build uses one Android Gradle Plugin and one Kotlin compiler.
- API packages and sync implementation remain upstream-compatible.

Amobi adds optional `CrudTransaction.completeWith` for retiring a local upload
journal in the same SQLite transaction as the SDK CRUD acknowledgement. Existing
`complete` calls keep their previous behavior. Three actual SQLite JVM tests
cover callback rollback, a frozen pending record across reopen, and a later
identical DELETE with an independent key. Their remote receipt/peer state is
modeled; this is not a live server or full Period app acceptance claim.
- Period resolves the `core` and SQLDelight integration source projects through
  Gradle composite-build substitution; see the project `settings.gradle.kts`.

The SDK build downloads prebuilt SQLite core binaries from the pinned upstream
`powersync-sqlite-core` 0.5.3 release. The source fork is recorded at
[`../sqlite_core/AMOBI_FORK.md`](../sqlite_core/AMOBI_FORK.md), but Amobi has not
published equivalent platform binaries yet. Do not describe the native runtime
binary as an Amobi build until that artifact cutover is completed.

The upstream Gradle publication metadata still names the upstream repository.
Do not publish this fork until an Amobi repository URL and release process are
configured.

Period verification on 2026-10-28:

- `./gradlew :androidApp:compileDevDebugKotlin` passed with this source tree
  included as a Gradle composite build.
- 15 Android instrumented tests passed on each of SM-G998B (API 35, ARM64) and
  SM-T517P (API 30, ARMv7): queue guard, account deletion, insert identity,
  migration, rollback metadata, synced-profile scope and profile-settings
  observer. Captured output is under
  `/Users/admin/Work/period_tracker/androidApp/build/verification/powersync-client-fork/`.

An eight-phase server-backed offline sync E2E also passed on the same date with
both physical devices (SM-T517P and SM-G998B) and the isolated local preview
server: seed, offline edit, pending-queue rollback refusal, resume/download,
rollback export, account deletion and delete propagation. The harness verified
that its synthetic token/key/database, server process, and temporary app
packages were removed. Logs and `summary.json` are in
`/Users/admin/WorkTemp/period-amobi-two-physical-20261028/evidence/`; no
production sync endpoint or account was used.
