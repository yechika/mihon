## 1. Gradle product flavor

- [x] 1.1 Added `flavorDimensions += "default"` + `productFlavors { mihon, mihonmod }` in [app/build.gradle.kts](app/build.gradle.kts). `mihonmod` sets `applicationId = "app.mihonmod"` and `versionNameSuffix = "-mod"`.
- [x] 1.2 `assembleMihonRelease` and `assembleMihonmodRelease` task names exist (verified via `./gradlew :app:compileMihonDebugKotlin` / `:app:compileMihonmodDebugKotlin` both passing).
- [x] 1.3 Debug build type still applies `.dev` suffix on top of flavor applicationId: `app.mihon.dev` / `app.mihonmod.dev`.

## 2. Flavor-specific resources

- [x] 2.1 Created [app/src/mihonmod/res/values/strings.xml](app/src/mihonmod/res/values/strings.xml) with `<string name="app_name">MihonMod</string>`.
- [ ] 2.2 Provide a distinct adaptive launcher icon at `app/src/mihonmod/res/mipmap-anydpi-v26/ic_launcher.xml` plus foreground/background drawables. **Deferred — design asset; fork maintainer must supply.** Until provided, mihonmod inherits the default Mihon icon which makes the two installs visually identical.
- [ ] 2.3 Verify icon renders correctly on Android 8+ adaptive + legacy fallback. **Deferred — depends on 2.2.**

## 3. Cloud sync allow-list

- [x] 3.1 Updated `ALLOWED_PACKAGES` in [CloudSyncProductionGuard.kt](app/src/firebase/kotlin/eu/kanade/tachiyomi/data/cloudsync/firebase/CloudSyncProductionGuard.kt) to include all four packages.
- [ ] 3.2 Add unit test for package allow-list. **Deferred — guard depends on Firebase classes which the noop unit-test classpath does not include; would need a separate test source set under `app/src/firebaseTest/`. Guard is exercised end-to-end in §8 manual verification.**

## 4. Firebase project registration

- [ ] 4.1–4.4 **USER ACTION required.** Cannot be done from code: register `app.mihonmod` and `app.mihonmod.dev` in the Firebase Console, add SHA-1 fingerprints, re-download `google-services.json`, re-encode + update `GOOGLE_SERVICES_JSON_BASE64` GitHub secret. Until done, mihonmod build will sign-in fail with PERMISSION_DENIED on Firestore.

## 5. Manifest and onboarding

- [x] 5.1 Created [app/src/mihonmod/AndroidManifest.xml](app/src/mihonmod/AndroidManifest.xml) with `<queries><package android:name="app.mihon" /></queries>`.
- [x] 5.2 Use `BuildConfig.FLAVOR == "mihonmod"` check inline (no separate sealed indicator needed for v1).
- [x] 5.3 Created [MihonModPreferences.kt](domain/src/main/java/tachiyomi/domain/mihonmod/service/MihonModPreferences.kt) with `mihonmodOnboardingShown: Preference<Boolean>`. Bound in [PreferenceModule.kt](app/src/main/java/eu/kanade/tachiyomi/di/PreferenceModule.kt).
- [x] 5.4 Created [MihonDetectionDialog.kt](app/src/main/java/eu/kanade/presentation/more/mihonmod/MihonDetectionDialog.kt) with three quick-action buttons + close. Plus `shouldShowMihonModOnboarding(context, onboardingShown)` helper that gates on `BuildConfig.FLAVOR == "mihonmod"` AND `PackageManager.getPackageInfo("app.mihon", 0)` AND not-yet-shown.
- [x] 5.5 Wired in [MainActivity.kt](app/src/main/java/eu/kanade/tachiyomi/ui/main/MainActivity.kt) — `LaunchedEffect(Unit)` calls the helper and flips `showMihonDetectionDialog` state if the conditions match.
- [x] 5.6 Quick-action wiring: "Open Mihon" → `openOfficialMihon(context)` (uses `getLaunchIntentForPackage`); "Restore" → `navigator.push(SettingsDataScreen)` (existing screen owns the file picker); "Sign in" → `navigator.push(AccountSignInScreen())`.
- [x] 5.7 Every action (incl. close + back-press dismiss) sets `mihonmodOnboardingShown = true`, so the dialog is one-shot per install.

## 6. CI workflow

- [x] 6.1 Updated [.github/workflows/build.yml](.github/workflows/build.yml) `Build app` step to call `./gradlew assembleMihonRelease assembleMihonmodRelease ...` so both APKs are produced. The flag-driven Firebase / no-Firebase fallback from the previous change is preserved.
- [x] 6.2 Replaced the single `Upload APK` step with two named steps: `Upload Mihon APK` and `Upload MihonMod APK`, each pointing at the flavor-specific output directory. Mapping artifact upload path generalised to `app/build/outputs/mapping/`.
- [x] 6.3 `Verify Firebase-free path compiles` step now compiles both flavors explicitly: `:app:compileMihonDebugKotlin :app:compileMihonmodDebugKotlin`. Confirms neither flavor depends on Firebase being present.
- [ ] 6.4 Smoke-test the workflow with a dummy push and inspect the Actions log. **Pending the next push.**

## 7. Docs

- [x] 7.1 Updated [README.md](README.md) Features list to mention MihonMod side-by-side install + the data-not-shared caveat + migration paths.
- [x] 7.2 Updated [CONTRIBUTING.md](CONTRIBUTING.md) "Forks" section with a `mihon` vs `mihonmod` flavor table, build commands, and a setup checklist (Firebase, icon, user-facing migration docs).
- [x] 7.3 Updated [firebase/README.md](firebase/README.md) to list all four package entries (`app.mihon`, `app.mihon.dev`, `app.mihonmod`, `app.mihonmod.dev`) that must be registered in the Firebase project.

## 8. Manual verification on device

- [ ] 8.1 Install the official Mihon APK on a clean emulator. Verify it works (browse, library).
- [ ] 8.2 Install `app.mihonmod` APK on top. Confirm both apps appear in the launcher with distinct names and icons.
- [ ] 8.3 First launch of MihonMod: confirm the "Mihon detected" dialog appears within 2 seconds and offers all three quick actions plus close.
- [ ] 8.4 Tap "Open Mihon": confirm Mihon's main activity launches.
- [ ] 8.5 In Mihon, go to Settings → Data and storage → Create backup → save `.tachibk` to a known location.
- [ ] 8.6 Switch to MihonMod, tap "Restore from backup file", pick the saved `.tachibk`. Confirm library is imported.
- [ ] 8.7 Verify both apps now have the same library content but editing one (add/remove manga) does NOT affect the other.
- [ ] 8.8 Close MihonMod, reopen → confirm the migration dialog does NOT reappear.
- [ ] 8.9 Uninstall MihonMod → reinstall → confirm the dialog appears again on first launch (preferences cleared).
- [ ] 8.10 Test cloud sync from MihonMod: sign-up, library push, sign-in on a second mihonmod install on a different device. Confirm data syncs.
