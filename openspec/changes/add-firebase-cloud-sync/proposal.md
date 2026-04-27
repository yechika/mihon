## Why

Mihon currently only persists user library, categories, chapter progress, and tracking state to a local device. The only off-device export is a manual protobuf backup file in [BackupCreator.kt](app/src/main/java/eu/kanade/tachiyomi/data/backup/create/BackupCreator.kt) plus a local CSV/JSON dump in [LibraryExporter.kt](app/src/main/java/eu/kanade/tachiyomi/data/export/LibraryExporter.kt). When a user switches device, reinstalls the app, or loses their phone, they must remember to make a backup file in advance and transfer it manually — there is no automatic, account-bound recovery path.

Adding optional cloud sync via Firebase Firestore solves this: the user signs into a lightweight account once, and their library state is mirrored to the cloud and restored on any device they sign into. The Firebase SDK is already a project dependency for telemetry ([TelemetryConfig.kt](telemetry/src/firebase/kotlin/mihon/telemetry/TelemetryConfig.kt)), so the dependency surface and build cost stay bounded.

## What Changes

- Add a new optional **Cloud Sync** feature, opt-in via Settings → Data and storage. Off by default; the app remains fully usable offline with no Firebase calls when disabled.
- Add a username + password account system backed by Firebase Authentication. Email is **optional** at signup, used only to recover a forgotten password. Accounts without an email can never recover a forgotten password — this trade-off is shown to the user at signup.
- Add a new `CloudSync` engine that reuses the existing `Backup` protobuf model in [Backup.kt](app/src/main/java/eu/kanade/tachiyomi/data/backup/models/Backup.kt) as the canonical document shape, so cloud-restored data flows through the same restore code path as a local backup file.
- Add a Firestore document layout: `users/{uid}/library/snapshot` plus per-manga progress documents under `users/{uid}/manga/{mangaId}` so updates do not require rewriting the entire library on every chapter read.
- Add a sync trigger: manual ("Sync now" button) for v1; periodic background sync (WorkManager) and per-event push (on chapter read, on manga added) are listed as follow-ups.
- Add a sign-in / sign-up / forgot-password screen reachable from Settings.
- **BREAKING for forks**: forks must replace `app/src/standard/google-services.json` with their own Firebase project credentials, otherwise cloud sync is a no-op (same pattern already documented in [CONTRIBUTING.md](CONTRIBUTING.md)).

Non-goals for this change: real-time multi-device conflict resolution beyond last-write-wins, sharing libraries between accounts, end-to-end encryption of synced data, syncing downloaded chapter image files (only metadata is synced).

## Capabilities

### New Capabilities

- `cloud-sync`: Mirror the user's library, categories, chapter read/unread state, and tracking state to a remote Firestore collection bound to the signed-in account. Trigger sync on user action (manual button) and reconcile cloud → local at sign-in time. Reuse the existing `Backup` model as the canonical sync payload to minimise new serialisation code.
- `account-auth`: Username + password account creation, sign-in, sign-out, and optional-email password reset, backed by Firebase Authentication. Account state is exposed app-wide via a single `AccountManager` so other features (and future per-user settings) can observe sign-in state.

### Modified Capabilities

<!-- None — no existing specs in openspec/specs/ to modify. -->

## Impact

- **Code**: New module/package `app/src/main/java/eu/kanade/tachiyomi/data/cloudsync/` containing the sync engine, plus a new `eu.kanade.tachiyomi.data.account` package for the auth wrapper. New Compose screens under `presentation-core` for sign-in / sign-up / cloud sync settings. New entries in [SettingsDataScreen.kt](app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsDataScreen.kt) (or whichever data-and-storage settings screen exists).
- **Dependencies**: Add `firebase-auth-ktx` and `firebase-firestore-ktx` to the `standard` flavor only (telemetry flavor pattern in [telemetry/build.gradle.kts](telemetry/build.gradle.kts) is the model). The `dev` flavor remains Firebase-free.
- **APIs**: New internal Kotlin APIs only — `AccountManager`, `CloudSyncEngine`. No public-facing API. No protocol changes to the `Backup` protobuf (additive only).
- **Build / config**: `app/src/standard/google-services.json` must contain a project that has both Authentication (Email/Password provider enabled) and Firestore enabled. Firestore security rules must restrict reads/writes to `request.auth.uid == userId`.
- **Privacy**: New data leaves the device when the feature is enabled. The opt-in Settings toggle, the sign-up screen copy, and the README must clearly state what is uploaded (library metadata, read progress, categories, tracking IDs) and what is not (downloaded chapter images, source extension secrets, device telemetry — telemetry remains separate).
- **Developer experience**: Author of this change has limited Kotlin / Android Studio access and cannot rely on Compose `@Preview`. Design (next artifact) must split work into small modules each verifiable from CLI (`./gradlew :app:compileStandardDebugKotlin`, `./gradlew :app:testStandardDebugUnitTest`) and prefer pure-Kotlin units that do not need a device or emulator to validate.
