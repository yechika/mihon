## MODIFIED Requirements

### Requirement: Production-app guard

The system SHALL initialise Firebase Authentication and Firestore only when the running build is identified as a Mihon production app, by package name AND signing certificate fingerprint. Forks without a matching certificate fingerprint SHALL silently skip cloud-sync initialisation.

The package allow-list SHALL include both the original Mihon flavor and the MihonMod flavor:

- `app.mihon` and `app.mihon.dev` (mihon flavor, release + debug)
- `app.mihonmod` and `app.mihonmod.dev` (mihonmod flavor, release + debug)

A flavor whose `applicationId` is outside this set SHALL be treated as an unknown fork and the production-app guard SHALL return `false`, regardless of signing certificate.

#### Scenario: Fork build attempts to enable cloud sync

- **WHEN** a build whose signing certificate does not match the configured Mihon production fingerprint is run, even if `google-services.json` is present
- **THEN** the cloud-sync feature toggle SHALL appear disabled with a "not available in this build" hint
- **AND** zero Firebase requests SHALL be made

#### Scenario: MihonMod flavor passes the production-app guard

- **WHEN** an APK with `applicationId = app.mihonmod` (release variant) is run on a device with Google Play Services available, and the build's signing certificate is in the configured allow-list
- **THEN** `CloudSyncProductionGuard.isAllowed(context)` SHALL return `true`
- **AND** the Cloud sync section SHALL be reachable from Settings → Data and storage

#### Scenario: Unknown applicationId fails the guard

- **WHEN** an APK with `applicationId = app.somefork` is run, even with a valid signing certificate fingerprint
- **THEN** the guard SHALL return `false`
- **AND** `CloudSyncBindings` SHALL bind to the no-op implementations
- **AND** `AccountManager.state` SHALL emit `AccountState.Unavailable`
