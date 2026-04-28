Looking to report an issue/bug or make a feature request? Please refer to the [README file](https://github.com/mihonapp/mihon#issues-feature-requests-and-contributing).

---

Thanks for your interest in contributing to Mihon!


# Code contributions

Pull requests are welcome!

If you're interested in taking on [an open issue](https://github.com/mihonapp/mihon/issues), please comment on it so others are aware.
You do not need to ask for permission nor an assignment.

## Prerequisites

Before you start, please note that the ability to use following technologies is **required** and that existing contributors will not actively teach them to you.

- Basic [Android development](https://developer.android.com/)
- [Kotlin](https://kotlinlang.org/)

### Tools

- [Android Studio](https://developer.android.com/studio)
- Emulator or phone with developer options enabled to test changes.

## Getting help

- Join [the Discord server](https://discord.gg/mihon) for online help and to ask questions while developing.

# Translations

Translations are done externally via Weblate. See [our website](https://mihon.app/docs/contribute#translation) for more details.


# Forks

Forks are allowed so long as they abide by [the project's LICENSE](https://github.com/mihonapp/mihon/blob/main/LICENSE).

When creating a fork, remember to:

- To avoid confusion with the main app:
    - Change the app name
    - Change the app icon
    - Change or disable the [app update checker](https://github.com/mihonapp/mihon/blob/main/app/src/main/java/eu/kanade/tachiyomi/data/updater/AppUpdateChecker.kt)
- To avoid installation conflicts:
    - Change the `applicationId` in [`build.gradle.kts`](https://github.com/mihonapp/mihon/blob/main/app/build.gradle.kts), **or** distribute the `mihonmod` product flavor described below which already uses `app.mihonmod` as a side-by-side install.

## Choosing between the `mihon` and `mihonmod` product flavors

This fork ships two Android product flavors:

| Flavor | applicationId | Use when |
|--------|---------------|----------|
| `mihon` | `app.mihon` | You want a drop-in update for users who already have the official Mihon APK installed. Sideloading replaces it. |
| `mihonmod` | `app.mihonmod` | You want users to keep the official Mihon app and install your fork as a separate, side-by-side app named **MihonMod**. The two installs do not share local data. |

Build commands:

```bash
./gradlew assembleMihonRelease            # produces app/build/outputs/apk/mihon/release/...
./gradlew assembleMihonmodRelease         # produces app/build/outputs/apk/mihonmod/release/...
./gradlew assembleMihonRelease assembleMihonmodRelease  # both at once
```

When choosing `mihonmod`, also remember to:

- Register both `app.mihonmod` and `app.mihonmod.dev` (debug variant) in the Firebase project tied to your fork's `google-services.json` (the file with all four packages: `app.mihon`, `app.mihon.dev`, `app.mihonmod`, `app.mihonmod.dev`).
- Add a distinct launcher icon at `app/src/mihonmod/res/mipmap-*` so users can visually tell the side-by-side install apart from the official Mihon.
- Document for your users that data does NOT migrate automatically between the two installs; they must export a `.tachibk` backup from Mihon and restore in MihonMod, or sign in to the same cloud-sync account from both.
- To avoid having your data polluting the main app's analytics and crash report services:
    - If you want to use Firebase analytics, replace [`google-services.json`](https://github.com/mihonapp/mihon/blob/main/app/google-services.json) with your own
- If you want the optional cloud sync feature to work for your fork's users:
    - Replace `app/google-services.json` with credentials from your own Firebase project (Authentication with Email/Password enabled, plus Firestore in production mode).
    - Register both `app.mihon` (or your fork's package name) and `<package>.dev` Android apps in the project, and add the SHA-1 fingerprints of your debug and release signing keys.
    - Pin those signing-key SHA-1s in `CloudSyncProductionGuard.ALLOWED_CERTIFICATE_FINGERPRINTS` (and the package names in `ALLOWED_PACKAGES`); without that pin, the production-app guard logs a warning and lets any signed build through, which is fine for development but unsafe for shipping.
    - Deploy the Firestore security rules at [`firebase/firestore.rules`](https://github.com/mihonapp/mihon/blob/main/firebase/firestore.rules) to your project (`firebase deploy --only firestore:rules`).
    - Build with `-Pinclude-telemetry` to enable the Firebase source set; otherwise the no-op bindings are used and cloud sync is silently disabled — that is the default for forks that don't supply a Firebase project.
