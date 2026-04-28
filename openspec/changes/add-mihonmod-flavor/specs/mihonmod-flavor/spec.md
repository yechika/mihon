## ADDED Requirements

### Requirement: Side-by-side install with official Mihon

The build SHALL produce a `mihonmod` APK whose `applicationId` is distinct from `app.mihon` and from any debug-suffixed variant of it. The two APKs SHALL be installable on the same Android device without one replacing the other.

The applicationIds are:

- `mihonRelease` → `app.mihon`
- `mihonDebug` → `app.mihon.dev`
- `mihonmodRelease` → `app.mihonmod`
- `mihonmodDebug` → `app.mihonmod.dev`

#### Scenario: Both APKs install on the same device

- **WHEN** a device already has `app.mihon` (official Mihon) installed and the user installs the `mihonmod` release APK
- **THEN** Android Package Manager SHALL install `app.mihonmod` as a new package
- **AND** `app.mihon` SHALL remain installed and usable
- **AND** the home-screen launcher SHALL show two separate icons

#### Scenario: Distinct visible name and icon

- **WHEN** the user views the app drawer with both APKs installed
- **THEN** the `mihon` APK SHALL display the label "Mihon" with its standard icon
- **AND** the `mihonmod` APK SHALL display the label "MihonMod" with a visibly different icon

### Requirement: Independent local data per flavor

Each flavor SHALL maintain its own SQLite database, preferences, and chapter cache under its own `/data/data/<applicationId>/` directory. No flavor SHALL read or write data belonging to the other flavor's directory.

#### Scenario: Library changes in one flavor do not affect the other

- **WHEN** a user adds a manga to the library in `app.mihon`
- **THEN** the manga SHALL NOT appear in the `app.mihonmod` library by automatic propagation
- **AND** the only paths that can move data between the two SHALL be (a) backup file export-import or (b) shared cloud sync account

#### Scenario: Uninstalling one flavor does not delete the other's data

- **WHEN** a user uninstalls `app.mihonmod`
- **THEN** `app.mihon`'s database, preferences, and downloaded chapters SHALL remain intact

### Requirement: Cloud sync recognises the new packages

`CloudSyncProductionGuard.ALLOWED_PACKAGES` SHALL include `app.mihonmod` and `app.mihonmod.dev` so that Firebase initialisation succeeds for the mihonmod flavor and the cloud-sync feature is reachable from its Settings screen.

#### Scenario: mihonmod build initialises Firebase

- **WHEN** the user installs `app.mihonmod` and the build's signing certificate is in `ALLOWED_CERTIFICATE_FINGERPRINTS`
- **THEN** `CloudSyncProductionGuard.isAllowed(context)` SHALL return `true`
- **AND** `CloudSyncBindings` SHALL bind to the Firebase implementations, not the no-op fallback
- **AND** the Cloud sync section SHALL appear in Settings → Data and storage

#### Scenario: Fork build with unknown package falls back silently

- **WHEN** an APK with `applicationId = app.somefork` is built and run
- **THEN** `CloudSyncProductionGuard.isAllowed(context)` SHALL return `false`
- **AND** `AccountManager.state` SHALL emit `AccountState.Unavailable`
- **AND** the Cloud sync section SHALL be hidden from Settings

### Requirement: First-launch migration prompt when official Mihon is detected

On the first launch of the `mihonmod` flavor, the app SHALL detect whether `app.mihon` is installed via `PackageManager.getPackageInfo(...)`. If detected, the app SHALL show a one-time non-blocking dialog explaining the available migration paths (backup file export-import, or cloud sync with a shared account) and offering quick-action buttons.

The dialog MUST NOT silently access or attempt to import data from `app.mihon`. All migration steps require explicit user action.

#### Scenario: Dialog appears once on first launch with Mihon installed

- **WHEN** a user installs and opens `app.mihonmod` for the first time on a device that has `app.mihon` installed
- **THEN** a dialog titled "Mihon detected" SHALL appear within 2 seconds of the home screen rendering
- **AND** the dialog SHALL offer at least three actions: "Open Mihon", "Restore from backup file", and "Sign in to cloud sync"
- **AND** a checkbox or button "Don't show again" SHALL persist a flag so the dialog does not reappear on subsequent launches

#### Scenario: Dialog does not appear when Mihon is absent

- **WHEN** a user installs and opens `app.mihonmod` on a device that does NOT have `app.mihon` installed
- **THEN** the dialog SHALL NOT appear
- **AND** the persistent "shown" flag SHALL remain false so a future install of Mihon does not re-trigger it (the flag tracks "we have shown this once", not "Mihon is currently installed")

#### Scenario: Dialog does not appear in the official `mihon` flavor

- **WHEN** the running flavor is `mihon` (not `mihonmod`)
- **THEN** the migration prompt SHALL NEVER appear regardless of any package installed on the device

### Requirement: Manifest declares package visibility for the official Mihon

The mihonmod flavor's `AndroidManifest.xml` SHALL declare a `<queries>` entry for `app.mihon` so that `PackageManager.getPackageInfo("app.mihon", 0)` works on Android 11+ where package visibility is restricted by default.

#### Scenario: Package visibility query returns the official Mihon

- **WHEN** the device runs Android 11 or newer and has both `app.mihon` and `app.mihonmod` installed
- **THEN** `app.mihonmod`'s `PackageManager.getPackageInfo("app.mihon", 0)` SHALL succeed and return the package metadata
- **AND** the failure-mode `PackageManager.NameNotFoundException` SHALL NOT be thrown when the package is in fact present
