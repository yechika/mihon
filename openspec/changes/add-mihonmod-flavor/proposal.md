## Why

Saat ini fork ini hanya menghasilkan APK dengan `applicationId = app.mihon` (dan suffix `.dev` untuk debug build). Kalau pengguna sudah pasang aplikasi Mihon resmi di HP-nya dan kemudian memasang APK dari fork ini, Android akan menganggap kedua APK identitas sama: **fork APK akan meng-update / mengganti Mihon resmi**, bukan terpasang berdampingan. Ini berbahaya:

- Pengguna yang ingin mencoba fitur fork (cloud sync, dll.) tidak bisa balik dengan mudah ke build resmi.
- Library lokal Mihon resmi bisa terpengaruh oleh skema database / flag versi yang fork tambahkan.
- Sulit untuk QA dan A/B testing antara dua versi pada device yang sama.

Solusi: tambahkan **product flavor "mihonmod"** dengan `applicationId = app.mihonmod` (+ suffix `.dev` untuk debug) dan label / icon yang berbeda. APK fork dan APK resmi terpasang berdampingan tanpa menabrak satu sama lain.

Pertanyaan turunan dari pengguna: *"bisakah mihonmod pakai database lokal yang sama dengan Mihon resmi?"* Jawaban singkat: tidak bisa langsung tanpa root. Android sandbox mengisolasi `/data/data/<package>/databases/` per-package; satu app tidak bisa membaca database app lain kecuali aplikasi lain mengekspos `ContentProvider`. Mihon resmi tidak punya `ContentProvider` semacam itu, dan menambahkan satu di Mihon resmi (yang fork ini tidak rilis) di luar lingkup. Yang **bisa** dilakukan dan akan dipakai sebagai pengganti "shared DB":

1. **Cloud sync co-tenancy.** Pengguna sign-in ke akun cloud sync yang sama dari mihonmod dan dari Mihon resmi (kalau Mihon resmi dimodifikasi untuk pakai cloud sync — di luar lingkup) atau dari mihonmod di HP A dan mihonmod di HP B; library, kategori, dan progress tersinkronisasi via Firestore yang sudah ada.
2. **Backup file migrasi sekali jalan.** Ekspor `.tachibk` dari Mihon resmi (Settings → Create backup), lalu di mihonmod pakai Restore backup. Ini membuat snapshot satu kali; setelah itu kedua app berjalan independen kecuali dipasangkan via cloud sync.

Change ini fokus pada item #1 (terpisah-tapi-berdampingan). Item migrasi backup sudah didukung oleh `BackupRestorer` yang ada — cukup didokumentasikan di README.

## What Changes

- Tambah Android product flavor `mihonmod` di [app/build.gradle.kts](app/build.gradle.kts) dengan `applicationId = app.mihonmod`, debug suffix `.dev` (jadi `app.mihonmod.dev`).
- Pertahankan flavor default `mihon` apa adanya supaya CI build resmi (kalau fork user pernah ingin sinkronisasi ke upstream) tetap berfungsi.
- Tambah resource override per-flavor (`app/src/mihonmod/res/`) untuk:
  - `app_name` di `strings.xml` → "MihonMod"
  - Adaptive launcher icon yang dapat dibedakan secara visual dari Mihon resmi (warna / tinta berbeda).
- Update Cloud sync `CloudSyncProductionGuard.ALLOWED_PACKAGES` untuk menerima `app.mihonmod` + `app.mihonmod.dev`. Tanpa ini fork build akan jatuh ke noop bindings dan cloud sync silent-disabled.
- Update [`google-services.json`](app/google-services.json) (gitignored, lokal saja) untuk mendaftarkan dua paket Android baru di Firebase Console: `app.mihonmod` dan `app.mihonmod.dev`.
- Update workflow CI ([.github/workflows/build.yml](.github/workflows/build.yml)) untuk membangun varian `mihonmodRelease` selain `mihonRelease`. APK yang diunggah sebagai artifact diberi nama eksplisit per flavor.
- Update README + CONTRIBUTING.md menjelaskan kapan pakai mihon vs mihonmod, peringatan bahwa keduanya tidak share DB, dan langkah migrasi backup jika perlu.
- Tambah migration runtime sekali jalan: pada first launch mihonmod, deteksi kalau mihon resmi terpasang (lewat `PackageManager.getPackageInfo("app.mihon")`). Jika ada, tampilkan in-app prompt "Import library from Mihon? Open Mihon → Settings → Create backup → return here to restore." Ini panduan UX, bukan transfer otomatis.

Non-goals untuk change ini:
- Akses langsung ke database `app.mihon` (tidak mungkin tanpa root).
- Menambahkan `ContentProvider` di Mihon resmi (out of fork's scope).
- Auto-import via shared external storage (rapuh, tergantung permission model SAF dan Android 11+ scoped storage).

## Capabilities

### New Capabilities

- `mihonmod-flavor`: Build variant "mihonmod" dengan applicationId, app name, dan icon terpisah; terpasang berdampingan dengan Mihon resmi. Production-app guard cloud sync menerima paket varian baru.
- `mihonmod-onboarding`: Dialog one-time pada first launch mihonmod yang mendeteksi keberadaan Mihon resmi dan menjelaskan opsi migrasi (backup file atau cloud sync dengan akun yang sama).

### Modified Capabilities

- `cloud-sync`: `CloudSyncProductionGuard.ALLOWED_PACKAGES` diperluas. Spec mendukung `app.mihonmod` dan `app.mihonmod.dev` sebagai paket yang sah untuk Firebase init.

## Impact

- **Build / config**: dua APK output per release (mihon + mihonmod). Ukuran release artifact dua kali lipat di GitHub Actions; mitigasi: split workflow atau hanya build mihonmod untuk distribusi internal fork. CI runtime naik ~2× untuk build phase.
- **Code**: tidak ada perubahan logika besar. Hanya: Gradle flavor block, `CloudSyncProductionGuard`, satu MigrationCompletedListener atau onboarding dialog di [MainActivity.kt](app/src/main/java/eu/kanade/tachiyomi/ui/main/MainActivity.kt).
- **Dependencies**: tidak ada dependency baru.
- **APIs**: tidak ada perubahan publik. `applicationId` pada Firebase project harus didaftarkan ulang (langkah manual oleh fork maintainer).
- **Privacy / data**: tidak ada perubahan data yang dikumpulkan. Mihon resmi tetap independen; mihonmod punya database sendiri.
- **Backwards compatibility**: pengguna fork yang sudah memasang APK lama (applicationId `app.mihon`) akan ter-update kalau mereka pasang APK varian default — *tapi* mihonmod adalah APK terpisah dengan applicationId baru, jadi tidak menabrak instalasi sebelumnya, dan tidak otomatis memigrasikan data dari `app.mihon`. Pengguna fork yang ingin pindah ke mihonmod harus ekspor backup lama → restore di mihonmod, atau pakai cloud sync.
