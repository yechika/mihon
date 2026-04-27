# Firebase config for cloud sync

This directory contains the Firebase configuration shipped with the cloud-sync feature.
The app reads `google-services.json` (placed by you at `app/src/standard/google-services.json`); this directory holds the artifacts that live on the Firebase **server** side: security rules and indexes.

## Files

- `firestore.rules` - access control for the Firestore documents the app reads and writes. Source of truth for the `usernames/` and `users/{uid}/...` document trees. Production deployments **must** install these rules; the default Firestore rules are wide open and would expose every user's library to every other signed-in user.

## One-time setup for forks

If you are running a fork of Mihon and want cloud sync to work for your fork's users, you need your own Firebase project. The project this repo points at is locked to the official Mihon signing certificate; a fork build will silently disable the feature against it.

1. Create a Firebase project at <https://console.firebase.google.com/>.
2. Add an Android app with the package name `app.mihon` (or your fork's package name) and the SHA-1 of your release signing key.
3. In the Firebase console:
   - **Authentication** -> Sign-in method -> enable **Email/Password**.
   - **Firestore Database** -> Create database in **production** mode.
4. Download the new project's `google-services.json` and place it at `app/src/standard/google-services.json`.
5. Update the `MIHON_CERTIFICATE_FINGERPRINT` constant referenced by the cloud-sync production-app guard (mirrors the existing telemetry pattern in [`telemetry/src/firebase/kotlin/mihon/telemetry/TelemetryConfig.kt`](../telemetry/src/firebase/kotlin/mihon/telemetry/TelemetryConfig.kt)) so it matches your release signing certificate.

## Deploying the rules

Install the Firebase CLI: <https://firebase.google.com/docs/cli>.

```bash
firebase login
firebase use --add        # pick your project, alias it as "default"
firebase deploy --only firestore:rules --project default
```

A minimal `firebase.json` for that command:

```json
{
  "firestore": {
    "rules": "firebase/firestore.rules"
  }
}
```

## What the rules guarantee

- A user can only read or write their own `users/{uid}/...` subtree.
- A username reservation can only be created by the signed-in user who claims it, and is immutable thereafter.
- Per-manga progress documents must keep their `mangaId`, `readChapterIds` (list), and `lastReadAt` (number) fields well-formed; malformed writes are rejected.
- Every other path is denied.

## Known limitations (v1)

- The username reservation is created by the client at sign-up time. If the auth-user creation succeeds but the reservation write fails, the client recovers by deleting its own auth user and surfacing an error. There is still a narrow window where a stuck reservation could persist for an unowned uid; this is documented in the design and is acceptable for v1.
- Account deletion clears `users/{uid}/...` from the client. The `usernames/{username}` doc is **not** deletable from the client because it is immutable under these rules. Forks that need to recycle usernames should add a Cloud Function that runs on user deletion to clear the reservation.
