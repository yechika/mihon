## ADDED Requirements

### Requirement: Cloud sync feature opt-in

The system SHALL gate all cloud-sync behaviour behind a user-controlled, off-by-default opt-in toggle exposed in Settings → Data and storage. While the toggle is off, the app MUST NOT initialise Firestore, MUST NOT attempt to sign the user in, and MUST NOT make any network call to Firebase.

#### Scenario: Toggle is off by default after fresh install

- **WHEN** a user installs the app for the first time and opens Settings → Data and storage
- **THEN** the "Cloud sync" toggle SHALL be visible and OFF
- **AND** no Firestore SDK initialisation SHALL have occurred

#### Scenario: User enables the toggle without an account

- **WHEN** a user toggles "Cloud sync" on while not signed in
- **THEN** the system SHALL navigate the user to the sign-in / sign-up screen
- **AND** the toggle SHALL only stay on after a successful sign-in

#### Scenario: User disables the toggle while signed in

- **WHEN** a user toggles "Cloud sync" off while signed in
- **THEN** the system SHALL stop all in-flight sync work
- **AND** the system SHALL NOT sign the user out (the account stays available for re-enable)
- **AND** local data SHALL remain untouched on disk

### Requirement: Snapshot upload and restore use the existing Backup protobuf

The system SHALL serialise the synced library as a `Backup` protobuf identical in shape to the local-file backup defined in `eu.kanade.tachiyomi.data.backup.models.Backup`. Restore from cloud SHALL reuse the existing `BackupRestorer` code path. No parallel data model SHALL be introduced for v1.

#### Scenario: Upload writes a Backup protobuf as the snapshot payload

- **WHEN** the sync engine performs a snapshot upload
- **THEN** the bytes written to the Firestore `users/{uid}/library/snapshot.payload` field SHALL be a valid `Backup` protobuf decodable by `BackupDecoder`

#### Scenario: Restore from cloud delegates to BackupRestorer

- **WHEN** the sync engine receives a cloud snapshot during a restore
- **THEN** the engine SHALL pass the decoded `Backup` to `BackupRestorer` rather than apply the data through any new code path
- **AND** all options supported by local-file restore (categories, tracking, history, app preferences, source preferences, extension repos) SHALL be applied identically

### Requirement: Snapshot fields exposed alongside payload

The Firestore document at `users/{uid}/library/snapshot` SHALL expose denormalised metadata fields alongside the protobuf payload so the client can inspect freshness without parsing the blob.

The fields are:

- `payload`: bytes — the `Backup` protobuf
- `updatedAt`: timestamp — server timestamp of the last write
- `schemaVersion`: integer — proto schema version, matched against `BackupCreator.BACKUP_VERSION` or equivalent
- `mangaCount`: integer — number of manga in the payload at write time
- `deviceLabel`: string — short identifier of the device that wrote the snapshot (model + first 4 chars of install id), for diagnostic display only

#### Scenario: Client reads metadata without decoding payload

- **WHEN** the client checks whether a remote snapshot is newer than the local one at sign-in
- **THEN** the client SHALL fetch only `updatedAt` and `schemaVersion` from the snapshot document
- **AND** the binary `payload` field SHALL NOT be downloaded unless a restore is actually triggered

### Requirement: Per-manga progress sub-documents

The system SHALL maintain a per-manga progress document at `users/{uid}/progress/{mangaId}` for high-frequency chapter-read events. The document MUST hold at minimum: `mangaId`, `lastReadChapterId`, `lastReadAt`, and a set of `read` chapter ids. Writes to this document MUST merge the `read` set as a union, never an overwrite.

#### Scenario: Reading a chapter writes to the progress document

- **WHEN** a user finishes a chapter while signed in and cloud sync is enabled
- **THEN** the progress document for that manga SHALL be updated with the chapter id added to the `read` set and `lastReadAt` set to the server timestamp
- **AND** the snapshot document SHALL NOT be rewritten as a side effect of a single chapter read

#### Scenario: Reading the same chapter on two devices

- **WHEN** device A and device B each mark chapter `c1` as read while offline, then both come online
- **THEN** the progress document `read` set SHALL contain `c1` exactly once after both writes resolve
- **AND** neither device's other read chapters SHALL be lost

### Requirement: Sync triggers

The system SHALL perform a snapshot sync (a) when the user signs in, (b) when the user taps a "Sync now" button in the cloud-sync settings screen, and (c) after structural library changes (manga added, manga removed, category created, category renamed, category deleted, manga moved between categories). Snapshot writes from (c) SHALL be debounced with a 30-second window so a burst of edits coalesces into a single write.

#### Scenario: Sync now button uploads a snapshot

- **WHEN** the user taps "Sync now" while signed in
- **THEN** the system SHALL upload a fresh `Backup` snapshot to Firestore within the same user-visible action
- **AND** the system SHALL display the result (success / error) before the action UI dismisses

#### Scenario: Burst of category edits coalesces into one snapshot write

- **WHEN** the user creates three categories in five seconds
- **THEN** at most one snapshot write SHALL be issued, no earlier than 30 seconds after the last edit

#### Scenario: Chapter reads do not trigger snapshot writes

- **WHEN** a user reads ten chapters in a row
- **THEN** zero snapshot writes SHALL be issued by the chapter-read code path
- **AND** ten progress-document writes SHALL be issued (one per chapter)

### Requirement: Conflict resolution by last-write-wins on snapshot, set-union on progress

The system SHALL resolve snapshot conflicts by last-write-wins keyed on the `updatedAt` server timestamp. The system SHALL resolve progress-document conflicts by merging the `read` set as a union and taking the maximum of `lastReadAt` per side.

#### Scenario: Cloud snapshot is strictly newer than local at sign-in

- **WHEN** a user signs in and the cloud snapshot `updatedAt` is strictly later than the local last-sync timestamp, and the local library has not been modified since the last sync
- **THEN** the system SHALL automatically restore the cloud snapshot to the local database without prompting

#### Scenario: Both sides have been edited since last sync

- **WHEN** a user signs in and both the cloud snapshot and the local library have been modified since the last successful sync
- **THEN** the system SHALL display a modal showing both timestamps and asking the user to choose which side to keep
- **AND** the non-chosen side SHALL be overwritten only after explicit confirmation

### Requirement: Existing local library at first account creation

The system SHALL never silently destroy a pre-existing local library when a user first creates a cloud account. When a user signs up for a new account on a device that already has a non-empty library, the system SHALL prompt the user to choose whether to upload the existing library to the new account or start fresh, and SHALL default to "upload existing library".

A library is considered non-empty when it contains at least one favourite manga or at least one user-created category beyond the default category.

#### Scenario: Sign-up on a device with an existing library

- **WHEN** a user with three favourite manga and two custom categories completes the sign-up form
- **THEN** before any Firestore write, the system SHALL display a modal: "Your device has an existing library. Upload it to your new account, or start with an empty cloud library?"
- **AND** the modal SHALL default the focus to "Upload"
- **AND** if the user chooses "Upload", the first snapshot push SHALL contain the existing library
- **AND** if the user chooses "Start fresh", the first snapshot push SHALL contain an empty `Backup` and the local library SHALL remain on the device but flagged as "diverged from cloud"

#### Scenario: Sign-up on a device with an empty library

- **WHEN** a user with no favourite manga and no custom categories completes the sign-up form
- **THEN** the system SHALL skip the upload-or-fresh modal
- **AND** the first snapshot push SHALL contain an empty `Backup`

#### Scenario: User chose "Start fresh" but later wants to recover the local library

- **WHEN** a user who chose "Start fresh" returns to Settings → Cloud sync within the same install session and taps "Upload my current library now"
- **THEN** the system SHALL push the current local library as a snapshot and clear the "diverged from cloud" flag

### Requirement: Existing local library at first sign-in to a previously-used account

The system SHALL never silently destroy a pre-existing local library when a user signs in for the first time on a new device to an account that already has a cloud snapshot. The system SHALL display a three-way modal listing the local library summary and the cloud library summary and asking the user to choose: "Use cloud (replace local)", "Use local (replace cloud)", or "Cancel sign-in".

#### Scenario: Sign-in on a device with a local library when cloud also has data

- **WHEN** a user signs in on a device that has 12 favourite manga locally, and the account's cloud snapshot has 30 favourite manga, and there is no prior `lastSyncedAt` for this account on this device
- **THEN** the system SHALL display a modal showing "Local: 12 manga, last edited <timestamp>" and "Cloud: 30 manga, last edited <timestamp>" with the three actions
- **AND** "Cancel sign-in" SHALL leave the user signed out and the local library untouched
- **AND** "Use cloud" SHALL overwrite the local library only after the user confirms a second "Replace local library?" dialog
- **AND** "Use local" SHALL upload the local library as the new snapshot and discard the cloud snapshot only after the user confirms a second "Replace cloud library?" dialog

#### Scenario: Sign-in on an empty device when cloud has data

- **WHEN** a user signs in on a device with an empty library and the account's cloud snapshot has data
- **THEN** the system SHALL skip the three-way modal and restore the cloud snapshot directly

### Requirement: Production-app guard

The system SHALL initialise Firebase Authentication and Firestore only when the running build is identified as a Mihon production app, by package name AND signing certificate fingerprint. Forks without a matching certificate fingerprint SHALL silently skip cloud-sync initialisation.

#### Scenario: Fork build attempts to enable cloud sync

- **WHEN** a build whose signing certificate does not match the configured Mihon production fingerprint is run, even if `google-services.json` is present
- **THEN** the cloud-sync feature toggle SHALL appear disabled with a "not available in this build" hint
- **AND** zero Firebase requests SHALL be made

### Requirement: Builds without `-Pinclude-telemetry` remain Firebase-free

A build invoked without the `-Pinclude-telemetry` gradle property SHALL compile and run without any Firebase dependency on the classpath. The interface bindings used by cloud sync MUST resolve to no-op implementations in those builds. (Mihon does not declare an Android product flavor for telemetry; instead, the `Config.includeTelemetry` flag is driven by a gradle property and switches the active source set, mirroring the existing telemetry module.)

#### Scenario: Build without telemetry flag does not link Firebase

- **WHEN** `./gradlew :app:assembleDebug` is run with no `-Pinclude-telemetry` flag
- **THEN** the resulting APK SHALL NOT contain any `com.google.firebase.*` class
- **AND** the build SHALL succeed even if `app/google-services.json` is missing
