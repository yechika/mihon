## ADDED Requirements

### Requirement: Mihon-detection dialog quick actions are deep-linkable

Each of the three quick actions in the first-launch dialog SHALL navigate the user to a working destination, not a placeholder.

The actions are:

- **Open Mihon** — launches `app.mihon` via `Intent.ACTION_MAIN` + `CATEGORY_LAUNCHER`
- **Restore from backup file** — opens the existing restore-backup screen (the same screen reachable from Settings → Data and storage → Restore backup)
- **Sign in to cloud sync** — opens the existing `AccountSignInScreen`

#### Scenario: Open Mihon launches the official app

- **WHEN** the user taps "Open Mihon" in the migration dialog
- **THEN** the system SHALL launch the official Mihon app's main launcher activity
- **AND** the migration dialog SHALL be dismissed

#### Scenario: Restore from backup file opens the existing restore screen

- **WHEN** the user taps "Restore from backup file" in the migration dialog
- **THEN** the system SHALL navigate to the same screen the user would reach via Settings → Data and storage → Restore backup
- **AND** the migration dialog SHALL be dismissed
- **AND** no data SHALL be imported until the user picks a `.tachibk` file and confirms

#### Scenario: Sign in to cloud sync opens the account screen

- **WHEN** the user taps "Sign in to cloud sync" in the migration dialog
- **THEN** the system SHALL navigate to `AccountSignInScreen`
- **AND** the migration dialog SHALL be dismissed
- **AND** no Firestore call SHALL be made until the user actually submits a sign-in or sign-up form

### Requirement: User can dismiss the dialog without performing any migration

The migration dialog SHALL allow the user to close it without taking any of the quick actions, and the persistent "shown" flag SHALL be set so the dialog does not reappear.

#### Scenario: User dismisses with the close button

- **WHEN** the user taps a "Close" or back-press dismisses the dialog
- **THEN** the dialog SHALL close
- **AND** the persistent "mihonmod_onboarding_shown" preference SHALL be set to `true`
- **AND** subsequent launches of the same install SHALL NOT show the dialog again

#### Scenario: Reinstalling app.mihonmod resets the flag

- **WHEN** the user uninstalls and reinstalls `app.mihonmod`
- **THEN** the persistent flag SHALL be cleared (because preferences are wiped on uninstall)
- **AND** the dialog SHALL appear again on the next first launch if Mihon is still installed

### Requirement: No background data access to the official Mihon

The mihonmod flavor SHALL NOT, at any point, attempt to read files inside `/data/data/app.mihon/` or call any non-public API to access another app's storage. The only interaction with `app.mihon` permitted by this change is the call to `PackageManager.getPackageInfo("app.mihon", 0)` for presence detection, and the `Intent.ACTION_MAIN` launch from the "Open Mihon" button.

#### Scenario: Static analysis confirms no app.mihon data access

- **WHEN** a code reviewer searches for references to `/data/data/app.mihon` or string `app.mihon` across the mihonmod flavor source set
- **THEN** the only matches SHALL be (a) the `PackageManager.getPackageInfo` call, (b) the `Intent` package target string, and (c) localised UI copy
- **AND** there SHALL be no file I/O, content provider access, or runtime reflection touching the official Mihon's data directory
