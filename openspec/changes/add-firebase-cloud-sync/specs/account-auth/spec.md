## ADDED Requirements

### Requirement: Username and password sign-up

The system SHALL allow a user to create a Mihon Cloud account with a username and password. Email SHALL be optional. Username SHALL be unique across the system.

The username MUST satisfy:

- Lowercase ASCII letters, digits, hyphen, and underscore only (`[a-z0-9_-]`).
- Length between 3 and 24 characters inclusive.
- Stored in normalised lowercase form.

The password MUST satisfy:

- Length at least 8 characters.
- Maximum length 64 characters.

#### Scenario: Successful sign-up with username and password only

- **WHEN** a user submits a valid username, a valid password, and leaves the email field blank
- **THEN** the system SHALL create a Firebase Auth account using a synthesised internal email
- **AND** the system SHALL write a `usernames/{username}` reservation document atomically with account creation
- **AND** the user SHALL be returned to the sync screen signed in

#### Scenario: Sign-up rejects an already-taken username

- **WHEN** a user submits a username that is already reserved
- **THEN** the system SHALL display "this username is already taken" inline next to the username field
- **AND** no Firebase Auth account SHALL be created

#### Scenario: Sign-up rejects an invalid username format

- **WHEN** a user submits a username containing characters outside `[a-z0-9_-]`, shorter than 3, or longer than 24
- **THEN** the system SHALL display the format rule next to the field
- **AND** the submit button SHALL remain disabled while the value is invalid

#### Scenario: Sign-up warns the user when no email is provided

- **WHEN** a user submits a sign-up form with the email field blank
- **THEN** the system SHALL display a non-dismissible warning that password recovery will not be possible without an email
- **AND** the user SHALL be required to confirm before the account is created

### Requirement: Username and password sign-in

The system SHALL allow a previously created user to sign in by entering their username and password. Email-based sign-in SHALL also be accepted as an alternative input on the same form for users who provided one at sign-up.

#### Scenario: Successful sign-in with username

- **WHEN** a user submits a valid username and the matching password
- **THEN** the system SHALL resolve the username to its synthesised internal email
- **AND** the system SHALL sign the user in via Firebase Auth Email/Password
- **AND** the user's signed-in state SHALL be observable via `AccountManager`

#### Scenario: Successful sign-in with real email

- **WHEN** a user who provided a real email at sign-up submits that email and their password
- **THEN** the system SHALL sign the user in via Firebase Auth Email/Password directly without a username lookup

#### Scenario: Sign-in rejects an unknown username

- **WHEN** a user submits a username that has no reservation document
- **THEN** the system SHALL display "no account found" without revealing whether the username or password failed

#### Scenario: Sign-in rejects a wrong password

- **WHEN** a user submits a known username with an incorrect password
- **THEN** the system SHALL display the same generic "incorrect username or password" message used for unknown usernames, to avoid disclosing existence

### Requirement: Optional email-based password reset

The system SHALL allow a user who provided a real email at sign-up to request a password reset email. Users without a real email on file SHALL be told that recovery is not available.

#### Scenario: Password reset for a user with an email

- **WHEN** a user taps "Forgot password" and submits a username that has a real email on file
- **THEN** the system SHALL trigger Firebase Auth `sendPasswordResetEmail` to that real email
- **AND** the system SHALL display a generic "if an email is on file, a reset link has been sent" message regardless of whether the username exists, to avoid disclosing account existence

#### Scenario: Password reset for a user without an email

- **WHEN** a user taps "Forgot password" and submits a username that has no real email on file
- **THEN** the system SHALL display the same generic "if an email is on file, a reset link has been sent" message

#### Scenario: User adds a recovery email after the fact

- **WHEN** a signed-in user opens Account settings and adds a real email
- **THEN** the system SHALL persist that email to the user's profile document
- **AND** subsequent password-reset requests for that account SHALL succeed

### Requirement: Sign-out

The system SHALL allow the signed-in user to sign out from the Account settings screen. Signing out SHALL NOT delete any local library data and SHALL NOT delete any cloud data.

#### Scenario: User signs out

- **WHEN** a signed-in user taps "Sign out" and confirms
- **THEN** the system SHALL clear the Firebase Auth session
- **AND** the cloud-sync feature toggle SHALL remain on but the "Sync now" button SHALL be disabled until sign-in again
- **AND** the local library SHALL remain readable and usable

### Requirement: AccountManager exposes a single source of truth for sign-in state

The system SHALL provide a single `AccountManager` (or equivalent name) that exposes the current sign-in state as an observable Kotlin `Flow<AccountState>`. Other features SHALL consume this flow rather than calling Firebase Auth directly.

The states are:

- `SignedOut`
- `SignedIn(uid: String, username: String, hasRecoveryEmail: Boolean)`
- `Unavailable` — used when the build is a fork without a Firebase project, or when Google Play Services is unavailable.

#### Scenario: Cloud sync engine observes sign-in state via AccountManager

- **WHEN** the cloud-sync engine needs the current uid for a Firestore call
- **THEN** the engine SHALL read it from `AccountManager` rather than from `FirebaseAuth.getInstance()`
- **AND** if the state is `SignedOut` or `Unavailable`, the engine SHALL skip the call without throwing

#### Scenario: AccountManager reports Unavailable in fork builds

- **WHEN** the app is running on a build whose signing certificate does not match the configured Mihon production fingerprint
- **THEN** `AccountManager` SHALL emit `AccountState.Unavailable` and never transition to any other state

### Requirement: Account deletion

The system SHALL provide an "Delete account" action under Account settings that removes the user's Firebase Auth account, the `users/{uid}/...` Firestore subtree, and the `usernames/{username}` reservation. After deletion, the user SHALL be signed out and a confirmation SHALL be shown.

#### Scenario: User deletes their account

- **WHEN** a signed-in user taps "Delete account", confirms by re-entering their password, and confirms a final modal
- **THEN** the system SHALL delete the `usernames/{username}` reservation, the `users/{uid}` subtree, and the Firebase Auth account in that order
- **AND** the user SHALL be signed out
- **AND** the local library SHALL remain on the device unless the user separately chose to wipe it
