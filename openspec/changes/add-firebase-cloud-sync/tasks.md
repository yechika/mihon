## 1. Firebase project + build wiring

- [x] 1.1 Create a Firebase project (or reuse an existing dev-only one), enable the Email/Password provider in Authentication, enable Firestore in production mode. (User supplied own Firebase project `mobileprogrammingaol-e55be`.)
- [x] 1.2 Download the project's `google-services.json` and place it at `app/google-services.json` (canonical location used by Mihon's telemetry — there is no `standard` flavor; the file lives at the app module root). Adjusted package_name entries to `app.mihon` and `app.mihon.debug`.
- [x] 1.3 Add `firebase-auth` and `firebase-firestore` to `gradle/libs.versions.toml` mirroring how `firebase-analytics` / `firebase-crashlytics` are declared (BOM-versioned, no version on the individual library entries).
- [x] 1.4 In [app/build.gradle.kts](app/build.gradle.kts), add `firebase-auth` and `firebase-firestore` inside the existing `if (Config.includeTelemetry)` block alongside the BOM. (Project uses `Config.includeTelemetry` source-set switching, not Android product flavors; cloud sync piggybacks on the same switch.)
- [x] 1.5 Run `./gradlew :app:compileDebugKotlin` from the CLI to confirm the build still passes after deps + source-set switch changes. Build successful in 5m 39s.

## 2. Pure-Kotlin domain model and interfaces

- [x] 2.1 Create package `eu.kanade.tachiyomi.data.cloudsync` under `app/src/main/java/`.
- [x] 2.2 Add `data class SnapshotEnvelope(val payload: ByteArray, val updatedAt: Long, val schemaVersion: Int, val mangaCount: Int, val deviceLabel: String)`.
- [x] 2.3 Add `data class MangaProgress(val mangaId: String, val lastReadChapterId: String?, val lastReadAt: Long, val readChapterIds: Set<String>)`.
- [x] 2.4 Add `interface CloudSyncStorage` with the methods listed in design.md (`fetchSnapshotMetadata`, `fetchSnapshot`, `writeSnapshot`, `fetchProgress`, `writeProgress`, `deleteAll`).
- [x] 2.5 Add `sealed interface AccountState { object SignedOut; data class SignedIn(uid, username, hasRecoveryEmail); object Unavailable }`.
- [x] 2.6 Add `interface AccountManager` exposing `state: StateFlow<AccountState>`, `signUp(...)`, `signIn(...)`, `signOut()`, `requestPasswordReset(usernameOrEmail)`, `addRecoveryEmail(email)`, `deleteAccount(password)`.
- [x] 2.7 Add a small `UsernameValidator` object (pure functions) that enforces the regex and length rules in the spec. Cover with unit tests in `app/src/test/.../cloudsync/UsernameValidatorTest.kt`.
- [x] 2.8 Add `SyntheticEmail.from(username)` helper that produces the deterministic `<sanitised>@accounts.mihon-cloud.invalid` form. Cover with unit tests.

## 3. Pure-Kotlin sync engine

- [x] 3.1 Implement `CloudSyncEngine(storage, accountManager, backupCreator, backupRestorer, clock)` with methods: `pushSnapshotIfDirty()`, `pullAndRestoreIfNewer()`, `recordChapterRead(mangaId, chapterId)`, `decideOnSignUp(localSummary): SignUpDecision` (returns `UploadLocal`, `StartFresh`, or `AskUser(localSummary)` based on whether local library is non-empty), `decideOnSignIn(localSummary, cloudMetadata): SignInDecision` (returns `RestoreCloud`, `UploadLocal`, `AskUser(local, cloud)`, or `Cancel`).
- [x] 3.2 Implement a 30-second debouncer for snapshot writes (`SnapshotDebouncer` — pure Kotlin, takes a `CoroutineScope` and a `Clock`).
- [x] 3.3 Wire `pushSnapshotIfDirty()` to call `BackupCreator.createBackup` to produce protobuf bytes and pass them to `storage.writeSnapshot`. (Pure-Kotlin `SnapshotProducer` interface introduced; concrete `BackupCreator` adapter belongs to §6 wiring.)
- [x] 3.4 Wire `pullAndRestoreIfNewer()` to fetch metadata first, compare timestamps, fetch payload only if newer, and pass the decoded `Backup` to `BackupRestorer`. (`SnapshotConsumer` interface introduced; concrete `BackupRestorer` adapter belongs to §6 wiring.)
- [x] 3.5 Unit-test `CloudSyncEngine` against an in-memory `FakeCloudSyncStorage` and a fake `Clock`. Cover: snapshot upload happens, snapshot upload skipped when not dirty, debounce coalesces three writes into one, restore-on-newer is automatic, restore-on-tie prompts.
- [x] 3.6 Unit-test progress write set-union: two writes of `read = {c1}` and `read = {c2}` produce `read = {c1, c2}`.
- [x] 3.7 Unit-test `decideOnSignUp`: empty-library returns `StartFresh`; non-empty-library returns `AskUser` with a populated summary.
- [x] 3.8 Unit-test `decideOnSignIn` four-way matrix: empty-local + empty-cloud → no-op, empty-local + non-empty-cloud → `RestoreCloud`, non-empty-local + empty-cloud → `UploadLocal`, non-empty-local + non-empty-cloud → `AskUser`.

## 4. Firebase adapter (firebase source set, gated by `Config.includeTelemetry`)

- [x] 4.1 Create `app/src/firebase/kotlin/eu/kanade/tachiyomi/data/cloudsync/firebase/` directory and a sibling `app/src/noop/kotlin/...` for the build path used when telemetry is excluded. Source-set switching wired in [app/build.gradle.kts](app/build.gradle.kts) `sourceSets.main`.
- [x] 4.2 Implement `FirestoreCloudSyncStorage : CloudSyncStorage` using `FirebaseFirestore.getInstance()`. Uses `set(SetOptions.merge())` and `FieldValue.arrayUnion(...)` for progress writes so the `read` array unions correctly without overwriting.
- [x] 4.3 Implement `FirebaseAccountManager : AccountManager` using `FirebaseAuth.getInstance()`. Signup runs auth-create → username reservation → profile-doc write in sequence, and rolls back the auth user if either Firestore write fails so we don't leak orphaned auth records.
- [x] 4.4 Implement the production-app guard `CloudSyncProductionGuard` mirroring [TelemetryConfig.kt](telemetry/src/firebase/kotlin/mihon/telemetry/TelemetryConfig.kt). When the guard fails, `CloudSyncBindings` falls back to the noop bindings so `AccountState` reads `Unavailable` and `FirestoreCloudSyncStorage` is never instantiated.

## 5. No-op bindings (mirrors telemetry's `noop` source set)

- [x] 5.1 Create the no-op implementations in `app/src/main/java/eu/kanade/tachiyomi/data/cloudsync/noop/` (kept inside the app module for now; will move to a dedicated `:cloudsync` module's `src/noop/kotlin/` source set once that module is split out, mirroring [telemetry/build.gradle.kts](telemetry/build.gradle.kts)).
- [x] 5.2 Implement `NoopCloudSyncStorage : CloudSyncStorage` whose methods all throw `UnsupportedOperationException` with a `Cloud sync unavailable in this build` message — consumers always gate on `AccountState.Unavailable` first so this is defence-in-depth.
- [x] 5.3 Implement `NoopAccountManager : AccountManager` that emits `AccountState.Unavailable` permanently and returns `Unavailable` results for every call.
- [x] 5.4 Configure DI ([AppModule.kt](app/src/main/java/eu/kanade/tachiyomi/di/AppModule.kt)) to bind `CloudSyncStorage` and `AccountManager` via `CloudSyncBindings(app)`. The `CloudSyncBindings` class is provided per source set: `app/src/firebase/kotlin/.../CloudSyncBindings.kt` returns Firebase impls (gated by `CloudSyncProductionGuard`), `app/src/noop/kotlin/.../CloudSyncBindings.kt` returns noop impls. The `main` source set never imports any Firebase class.

## 6. Sync triggers

- [x] 6.1 Added `cloudSyncEnabled`, `lastSyncedAt`, `divergedFromCloud` to new [CloudSyncPreferences.kt](domain/src/main/java/tachiyomi/domain/cloudsync/service/CloudSyncPreferences.kt). Bound in [PreferenceModule.kt](app/src/main/java/eu/kanade/tachiyomi/di/PreferenceModule.kt). Engine uses pref via [PreferenceLongStore.kt](app/src/main/java/eu/kanade/tachiyomi/data/cloudsync/PreferenceLongStore.kt).
- [x] 6.2 Sign-in trigger lives in [CloudSyncCoordinator.kt](app/src/main/java/eu/kanade/tachiyomi/data/cloudsync/CloudSyncCoordinator.kt). Started from [App.kt](app/src/main/java/eu/kanade/tachiyomi/App.kt). Observes `AccountManager.state` via `filterIsInstance<AccountState.SignedIn>()`, calls `engine.decideOnSignIn()` and routes to pull / push / no-op.
- [x] 6.3 Library mutation observer in [CloudSyncCoordinator.kt](app/src/main/java/eu/kanade/tachiyomi/data/cloudsync/CloudSyncCoordinator.kt). Combines `GetLibraryManga.subscribe()` + `GetCategories.subscribe()`, distinct-until-changed, drops initial emit, debounces 30 s.
- [x] 6.4 Chapter-read trigger in [ReaderViewModel.kt:560](app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderViewModel.kt#L560) `updateChapterProgressOnComplete`. Calls `cloudSyncEngine.recordChapterRead` only when `cloudSyncEnabled` is on and incognito is off.

## 7. UI: account screens

- [x] 7.1 Added [AccountSignInScreen.kt](app/src/main/java/eu/kanade/presentation/more/settings/screen/cloudsync/AccountSignInScreen.kt) — Voyager screen with Sign in / Sign up tabs. Wired into [SettingsDataScreen.kt](app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsDataScreen.kt) account row.
- [x] 7.2 Sign-up form has username, password, confirm-password, optional email. `Button.enabled = state.canSubmitSignUp` is gated on `UsernameValidator.isValid`, password length 8–64, and matching confirm-password.
- [x] 7.3 `NoEmailWarningDialog` is non-dismissible (`onDismissRequest = {}`) and shown when email blank at submit time. User must explicitly click "Continue without email" or "Add an email".
- [ ] 7.4 The forgot-password screen takes a username-or-email and always shows the generic "if an email is on file, a reset link has been sent" outcome regardless of whether the account exists. **Deferred — wire when account screen lands.**
- [ ] 7.5 Add `AccountScreen` (signed-in state) showing username, masked recovery email status ("set" / "not set — add one"), Sign out, Delete account, and a "Sync now" button that calls `CloudSyncEngine.pushSnapshotIfDirty(force = true)`. **Deferred — Sync now currently lives directly in Settings.**
- [ ] 7.6 Add a `SignUpExistingLibraryModal` Composable that shows the local library summary (manga count, category count) with two actions: "Upload existing library" (default focus) and "Start fresh". Wired to `CloudSyncEngine.decideOnSignUp` and only displayed when that returns `AskUser`. **Deferred — engine returns the decision but UI not yet wired.**
- [ ] 7.7 Add a `SignInConflictModal` Composable that shows the three-way local-vs-cloud comparison and the actions "Use cloud (replace local)", "Use local (replace cloud)", and "Cancel sign-in", each guarded by a second confirmation dialog. Wired to `CloudSyncEngine.decideOnSignIn` and only displayed when that returns `AskUser`. **Deferred — engine returns the decision but UI not yet wired.**

## 7a. UI verification (blind-coding aid via Roborazzi)

- [ ] 7a.1 Add the Roborazzi plugin and dependency to `gradle/libs.versions.toml` and to `app/build.gradle.kts` under `testImplementation` (Robolectric-based, no device or emulator needed). Mirror version pins of any existing screenshot-test setup in the repo if one exists; otherwise pin to the latest stable Roborazzi version.
- [ ] 7a.2 Add Robolectric runtime config so `*ScreenshotTest` classes can render Compose without an emulator (`@RunWith(RobolectricTestRunner::class)`, `@Config(qualifiers = "...")`).
- [ ] 7a.3 Write `AccountSignInScreenshotTest` covering: sign-in tab default state, sign-up tab default state, sign-up tab with all-valid input, sign-up tab with invalid username, sign-up tab with mismatched passwords. Each test captures a PNG via `captureRoboImage()` to `app/src/test/screenshots/`.
- [ ] 7a.4 Write `AccountScreenScreenshotTest` covering: signed-in state with recovery email set, signed-in state without recovery email (the "add one" hint), and the in-flight "Sync now" loading state.
- [ ] 7a.5 Write `CloudSyncSettingsScreenshotTest` covering: section hidden state (Unavailable), toggle-off state, toggle-on signed-out state, and toggle-on signed-in state with last-synced timestamp.
- [ ] 7a.6 Write `SignUpExistingLibraryModalScreenshotTest` and `SignInConflictModalScreenshotTest` for the migration modals (empty/non-empty library combinations).
- [ ] 7a.7 Add a gradle task `./gradlew :app:recordRoborazziDevDebug` (or the standard Roborazzi task name) that produces the PNG snapshots so the developer can review the rendered UI without running an emulator. Add a one-line note to [CONTRIBUTING.md](CONTRIBUTING.md) pointing to `app/src/test/screenshots/` for visual verification.

## 8. UI: settings entry point

- [x] 8.1 Added "Cloud sync" group via `getCloudSyncGroup()` in [SettingsDataScreen.kt](app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsDataScreen.kt). Returns `null` (so the row is hidden entirely) when `AccountState.Unavailable`.
- [x] 8.2 Section contains: master `cloudSyncEnabled` switch, an account row showing username or "Sign in to enable" (clicking surfaces a placeholder toast — full account screen lands in §7), a "Sync now" button enabled only when signed in and the master switch is on, a "Last synced" subtitle with relative time, and an info row with the upload/no-upload disclosure copy.

## 9. Firestore security rules

- [x] 9.1 Create `firebase/firestore.rules` with:
  - `match /usernames/{username}`: read allowed for any signed-in user (for uniqueness probe), write allowed only as part of the matching user creation transaction.
  - `match /users/{uid}/{document=**}`: read/write allowed only when `request.auth.uid == uid`.
- [x] 9.2 Add a short `firebase/README.md` documenting how to deploy the rules with `firebase deploy --only firestore:rules` for forks.

## 10. CI verification on a clean machine

- [x] 10.1 Added a "Verify Firebase-free path compiles" step in [.github/workflows/build.yml](.github/workflows/build.yml) that runs `./gradlew :app:compileDebugKotlin` (no `-Pinclude-telemetry`) to prove the no-op source set compiles cleanly.
- [x] 10.2 The existing `Run unit tests` step (`./gradlew testDebugUnitTest`) already covers the cloud-sync unit tests; no additional job is required because the cloud-sync tests live in `app/src/test/...` and are picked up automatically.

## 11. Docs and copy

- [x] 11.1 Added Settings description strings (`pref_cloud_sync_summary`, `pref_cloud_sync_what_uploaded`, plus screen / modal / form copy) to [i18n/src/commonMain/moko-resources/base/strings.xml](i18n/src/commonMain/moko-resources/base/strings.xml).
- [x] 11.2 Updated [CONTRIBUTING.md](CONTRIBUTING.md) under the "Forks" section with the cloud-sync setup checklist (replace `google-services.json`, register packages + SHA-1, pin guard, deploy Firestore rules, build with `-Pinclude-telemetry`).
- [x] 11.3 Added a one-line note to the [README.md](README.md) "Features" list mentioning optional cloud sync.

## 12. Manual end-to-end verification on device

- [ ] 12.1 Build `installStandardDebug` on a physical device. Sign up a new account with a username only. Confirm sign-up succeeds and the no-email warning was shown.
- [ ] 12.2 Add three manga to the library, create two categories, move one manga between categories. Confirm exactly one snapshot write fires (visible in Firestore console) within roughly 30 seconds.
- [ ] 12.3 Read three chapters of one manga. Confirm three progress-document writes fire and zero snapshot writes fire.
- [ ] 12.4 Uninstall the app, reinstall, sign in with the same username and password. Confirm the library, categories, and read state are restored.
- [ ] 12.5 Sign in on a second device, edit the library on both while offline, bring both online. Confirm the conflict modal appears with both timestamps and that the chosen side wins.
- [ ] 12.6 Sign out, confirm local data is intact and no further Firestore traffic is observed.
- [ ] 12.7 Build `installDevDebug`. Open Settings → Data and storage. Confirm the "Cloud sync" section is hidden entirely.
