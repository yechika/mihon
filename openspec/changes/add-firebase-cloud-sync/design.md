## Context

Mihon is a native Android app written in Kotlin + Jetpack Compose, built with AGP 9.2.0 and Gradle 9.4.1 ([gradle/libs.versions.toml](gradle/libs.versions.toml)). User state today lives entirely on-device:

- Library, categories, chapter progress, tracking state are persisted by SQLDelight (`data/` module).
- The only off-device representation is a serialised `Backup` protobuf written to a user-chosen file URI ([BackupCreator.kt](app/src/main/java/eu/kanade/tachiyomi/data/backup/create/BackupCreator.kt)) and a parallel CSV/JSON export ([LibraryExporter.kt](app/src/main/java/eu/kanade/tachiyomi/data/export/LibraryExporter.kt)).
- Restore is symmetric: a file URI is fed to `BackupRestorer` ([BackupRestorer.kt](app/src/main/java/eu/kanade/tachiyomi/data/backup/restore/BackupRestorer.kt)) and the protobuf is decoded and re-applied to the database.

Firebase is already integrated, but only for telemetry (Analytics + Crashlytics) and only on the `standard` flavor — see [TelemetryConfig.kt](telemetry/src/firebase/kotlin/mihon/telemetry/TelemetryConfig.kt). The flavor-isolation pattern there is the model: Firebase code lives in a flavor-specific source set so the `dev` flavor and forks without `google-services.json` build cleanly.

The author of this change has limited Kotlin experience and has not been able to get Compose `@Preview` or full Android Studio working locally (AGP 9.x is too new for the installed Studio version, and gradle cache corruption has blocked CLI builds on Windows). The design therefore deliberately favours small, CLI-verifiable units and avoids any approach that depends on UI preview to validate.

## Goals / Non-Goals

**Goals:**

- Let a user opt in to cloud sync with a username + password account, with email as an *optional* recovery channel.
- On sign-in from a new device, the user's library, categories, chapter progress, and tracking state are restored automatically.
- Reuse the existing `Backup` protobuf as the canonical sync payload — do not invent a parallel data model.
- Keep all Firebase code inside the `standard` build flavor; the `dev` flavor stays Firebase-free and a fork without a Firebase project still compiles.
- Each implementation step is verifiable from the command line: `./gradlew :app:compileStandardDebugKotlin` for compile, `./gradlew :<module>:testDebugUnitTest` for unit tests, no device required for the majority of work.
- Pure-Kotlin units (mapping, conflict resolution, validation) are unit-testable without the Firebase SDK by depending on small interfaces, not Firebase classes directly.

**Non-Goals:**

- Real-time, multi-device live sync. v1 syncs on demand and on sign-in only.
- Conflict resolution beyond last-write-wins by `updatedAt` timestamp.
- End-to-end encryption of synced data — Firestore at-rest encryption is relied on, plus security rules. Documented as a known limitation.
- Syncing downloaded chapter image files. Only metadata syncs; downloads remain device-local.
- Sharing libraries between accounts.
- Migrating existing local data automatically on first sign-in if the cloud also has data — v1 prompts the user to choose which side wins.

## Decisions

### Data model: reuse `Backup` protobuf, do not invent a Firestore-native shape

The existing `Backup` model in [Backup.kt](app/src/main/java/eu/kanade/tachiyomi/data/backup/models/Backup.kt) already represents everything we want to sync (manga, categories, sources, source preferences, extension repos, app preferences). Reusing it means:

- Restore from cloud goes through the *same* `BackupRestorer` already battle-tested for local file restore.
- We get backup-format version tolerance for free.
- One serialisation surface to maintain, not two.

Cloud sync stores the protobuf bytes in a single Firestore document field `users/{uid}/library/snapshot.payload` (binary blob). A small set of denormalised fields (`updatedAt`, `schemaVersion`, `mangaCount`) sit alongside the blob for cheap querying without parsing the protobuf.

**Alternative considered:** a fully decomposed Firestore shape with one document per manga, one per category, etc. Rejected for v1 because (a) it doubles the serialisation work, (b) it duplicates the restore code path, and (c) the dev's stated knowledge limit makes a smaller surface area materially safer. A follow-up change can decompose into per-manga progress documents once the v1 round-trip is proven.

### Per-manga progress sub-documents — but only for chapter read state

Rewriting the entire library snapshot every time a user reads one chapter is wasteful (large write amplification, Firestore cost). To avoid this, **chapter read state only** is also written to `users/{uid}/progress/{mangaId}` documents in a tiny shape:

```
{
  mangaId: "<source>:<url-hash>",
  lastReadChapterId: "...",
  lastReadAt: 1714000000,
  read: [chapterId1, chapterId2, ...]   // small array of read chapter ids
}
```

The full `Backup` snapshot is rewritten on a coarser cadence (manual sync, app start after sign-in, or after structural changes like adding a manga or a category). This keeps the high-frequency event (chapter read) cheap.

**Alternative considered:** Firestore Bundles or Cloud Functions to merge updates server-side. Rejected — adds operational complexity (a backend the dev would have to maintain) and is not justified at the expected user volume for v1.

### Auth: Firebase Authentication with Email/Password provider, treat "username" as a synthetic email

Firebase Auth does not natively support raw username + password — only Email/Password, phone, and federated providers. The design therefore stores the user's chosen username and synthesises a deterministic email of the form `<sanitised-username>@accounts.mihon-cloud.invalid` to feed into Firebase Auth's Email/Password provider. The `.invalid` TLD is an IANA-reserved name guaranteed to never resolve, so the synthetic address can never actually be used to send mail.

If the user *also* provides a real email at sign-up, that email is stored in the user's Firestore profile document (`users/{uid}/profile`) and used as the password-recovery channel via `sendPasswordResetEmail` (which Firebase will reject for the `.invalid` synthetic address — only the optional real email can recover).

Username uniqueness is enforced by:

1. Lowercasing and stripping characters not in `[a-z0-9_-]`, length 3–24.
2. Writing to a Firestore `usernames/{username}` document inside the same transaction that creates the user. Firestore security rules reject the write if the document already exists.

**Alternative considered:** Firebase Anonymous Auth + a username-only profile in Firestore. Rejected because anonymous accounts cannot recover from device loss without manual link-account flows, defeating the whole purpose of the change.

**Alternative considered:** Custom Auth via a self-hosted token minter. Rejected — requires a backend, out of scope.

### Sync engine: pure-Kotlin core + thin Firebase adapter

`CloudSyncEngine` is split into two layers so the testable logic does not import any Firebase class:

```
interface CloudSyncStorage {            // pure-Kotlin abstraction
    suspend fun fetchSnapshot(): SnapshotEnvelope?
    suspend fun writeSnapshot(envelope: SnapshotEnvelope)
    suspend fun fetchProgress(mangaId: String): MangaProgress?
    suspend fun writeProgress(progress: MangaProgress)
}

class CloudSyncEngine(
    private val storage: CloudSyncStorage,
    private val backupCreator: BackupCreator,
    private val backupRestorer: BackupRestorer,
    private val clock: Clock = Clock.System,
)
```

The Firebase implementation `FirestoreCloudSyncStorage` lives under `app/src/standard/...` (flavor-specific source set), implements `CloudSyncStorage`, and is the only file that imports `com.google.firebase.firestore.*`. Unit tests for `CloudSyncEngine` use an in-memory fake — no Firebase emulator, no device.

This is also the pattern used by [TelemetryConfig.kt](telemetry/src/firebase/kotlin/mihon/telemetry/TelemetryConfig.kt) (flavor-specific Firebase code).

### Conflict resolution: last-write-wins by `updatedAt`, with explicit prompt on first sign-in

On sign-in, the engine compares the cloud `snapshot.updatedAt` with the local last-sync timestamp:

- Cloud strictly newer → restore cloud → local.
- Local strictly newer → push local → cloud.
- Both updated since last sync → show a modal: "Your cloud library was last updated <X>, your device library was last updated <Y>. Which one do you want to keep? The other will be overwritten." Default focus is on the newer one.
- First-ever sign-in on a device with an existing local library → modal: "Upload this device's library to your account, or replace it with your cloud library?"

No automatic three-way merge in v1. The proto model has no per-field timestamps and inventing them is out of scope.

### Build isolation: mirror the telemetry module pattern, not Android flavors

Mihon does **not** declare an `standard` vs `dev` Android product flavor. Instead, the existing telemetry module ([telemetry/build.gradle.kts](telemetry/build.gradle.kts)) toggles a `Config.includeTelemetry` boolean and swaps between two source sets at build time:

- `telemetry/src/firebase/kotlin/...` — real Firebase implementation, only added when telemetry is included.
- `telemetry/src/noop/kotlin/...` — pure stubs, only added when telemetry is excluded.

Cloud sync follows the same pattern. A new `cloudsync` library module is added with the same shape:

- `cloudsync/src/main/kotlin/...` — pure-Kotlin interfaces (`CloudSyncStorage`, `AccountManager`, `AccountState`, `CloudSyncEngine`, validators, decisions, debouncer). No Firebase imports.
- `cloudsync/src/firebase/kotlin/...` — `FirestoreCloudSyncStorage`, `FirebaseAccountManager`, production-app guard. Imports `com.google.firebase.*`. Only added when `Config.includeCloudSync` is true.
- `cloudsync/src/noop/kotlin/...` — `NoopCloudSyncStorage`, `NoopAccountManager`. Always emits `AccountState.Unavailable`. Used when cloud sync is excluded.

The `:app` module always depends on `:cloudsync` and never imports Firebase classes directly. This lets a fork without `google-services.json` — or a build that turns off `Config.includeCloudSync` — compile cleanly with the no-op bindings, exactly the same way `Config.includeTelemetry=false` works today.

To minimise churn for the dev who is implementing this without easy access to Android Studio, a transitional shortcut is allowed: the pure-Kotlin pieces (interfaces, engine, validators, tests) may live under `app/src/main/java/eu/kanade/tachiyomi/data/cloudsync/` initially, and later migrate to a dedicated `:cloudsync` module once the Firebase deps are wired. The migration is a pure file-move — the package and class shape are designed to make this trivial.

## Risks / Trade-offs

- **Risk:** Synthetic `.invalid` email address breaks if Firebase tightens its email format validation.
  → **Mitigation:** wrap the synthesis in a single helper, version it, and document a fallback (real email required at signup) that can be flipped behind a remote config / build flag without touching call sites.

- **Risk:** Last-write-wins overwrites recent local progress if the user reads on two devices without syncing in between.
  → **Mitigation:** progress sub-documents (per manga, written on each chapter-read event) are merged set-union, not overwritten — only the *snapshot* uses last-write-wins. Document the trade-off prominently in the Settings screen description.

- **Risk:** Firestore cost spikes from frequent snapshot rewrites.
  → **Mitigation:** snapshot writes are debounced (30 s) and only fire on structural changes; chapter-read events go to the cheap progress documents instead.

- **Risk:** Forks accidentally inherit our Firebase project.
  → **Mitigation:** same protection as telemetry — `isMihonProductionApp()` check in [TelemetryConfig.kt](telemetry/src/firebase/kotlin/mihon/telemetry/TelemetryConfig.kt) gates initialisation by package name and signing certificate. Cloud sync uses an identical guard. Without the Mihon signing certificate, Firebase init returns early and the feature is silently disabled.

- **Risk:** Dev cannot run Compose `@Preview`, so UI mistakes only surface on a real device build.
  → **Mitigation:** keep new UI screens minimal (Settings list rows + a single sign-in form built from existing `presentation-core` components), reuse existing forms patterns from auth providers in `app/src/main/java/eu/kanade/tachiyomi/data/track/`, write screen-model unit tests so the bulk of behaviour is verified without rendering.

- **Risk:** No email = no password recovery, user is locked out forever.
  → **Mitigation:** signup flow shows an explicit, non-dismissible warning when the user leaves email blank. Settings → Account shows "no recovery email set — add one" if the user later changes their mind.

- **Trade-off:** Reusing `Backup` protobuf means cloud snapshots are bigger than they need to be (they include preferences, sources, etc.), but the cost is one-time-per-snapshot and avoids the much bigger cost of a parallel data model.

## Migration Plan

This is a purely additive change for end users — no existing data is modified. Sign-in is opt-in; uninstalling the feature simply means the user goes back to the manual backup workflow.

For developers and forks:

1. Forks **must** replace `app/src/standard/google-services.json` with their own Firebase project credentials. If left unchanged, the production-app guard disables the feature silently and no requests are made.
2. The Firebase project must have:
   - **Authentication** with the Email/Password provider enabled.
   - **Firestore** in production mode with the security rules shipped under `firebase/firestore.rules` (added by the `infra` task in tasks.md).
3. No DB migration is required on the device side. The local SQLDelight schema is untouched.

Rollback: feature can be disabled by flipping a single preference (`pref_cloud_sync_enabled`) and is gated server-side by Firestore security rules — disabling auth at the project level instantly stops all sync traffic without an app update.

## Open Questions

- Should the v1 ship include automatic background sync via WorkManager, or restrict to manual + on-sign-in only? **Tentative answer: manual + on-sign-in for v1**, follow-up change for periodic background sync once cost/usage are observed.
- Should the username uniqueness check happen client-side first (extra read) or only server-side at create time? **Tentative answer: client-side check for UX, server-side enforcement for correctness.**
- Do we want a "Delete my cloud data" button for GDPR / user trust, and where does it live in Settings? **Tentative answer: yes, under Settings → Account → Delete account, calls a Cloud Function or relies on a security-rule-permitted recursive delete.** Marked as a task even though no Cloud Function is in scope yet — the button can be wired to a client-side recursive delete for v1.
