## Context

Fork ini sudah punya beberapa build type (`debug`, `release`, `foss`, `preview`, `benchmark`) dan switching source-set lewat `Config.includeTelemetry`, tapi belum memiliki Android **product flavor**. Semua build berbagi `applicationId = app.mihon` (debug menambahkan suffix `.dev`). Kalau pengguna memasang APK fork di HP yang sudah punya Mihon resmi, dua APK ini dianggap aplikasi yang sama oleh Android Package Manager — APK fork akan menggantikan Mihon resmi.

Dalam Android, identitas aplikasi ditentukan oleh `applicationId`. Untuk install berdampingan, kita harus memberikan `applicationId` yang berbeda. Mekanisme paling umum di Android Gradle Plugin: **product flavor** dengan dimensi tunggal yang memutar `applicationId` per flavor, lalu kombinasi flavor × buildType menghasilkan variant (mis. `mihonRelease`, `mihonmodRelease`, `mihonmodDebug`).

Permintaan turunan: "boleh tidak DB-nya share dengan Mihon resmi?" Konteks Android sandbox:

- Tiap aplikasi memiliki direktori private `/data/data/<applicationId>/` yang **hanya dapat** diakses oleh aplikasi tersebut (atau root). SQLite database `tachiyomi.db` Mihon ada di sana.
- Aplikasi lain bisa membaca data sebuah app **hanya jika** app tersebut mengekspos `ContentProvider` yang sengaja dipublikasikan, atau menyalin data ke external storage / SAF tree yang dipilih user.
- Mihon resmi tidak mengekspos `ContentProvider` untuk database-nya, dan modifikasi Mihon resmi di luar lingkup change ini.
- Workaround "share via external storage" rapuh: scoped storage Android 11+ membatasi akses, dan database SQLite tidak aman dibuka oleh dua process berbeda secara bersamaan (file lock + journal mode butuh koordinasi).

Kesimpulan: shared DB satu-arah / dua-arah real-time = tidak feasible tanpa modifikasi Mihon resmi. Yang feasible:

1. Sinkronisasi via cloud sync (sudah dibangun oleh change `add-firebase-cloud-sync`).
2. Migrasi sekali-jalan via backup file `.tachibk` (sudah didukung `BackupCreator` + `BackupRestorer`).

## Goals / Non-Goals

**Goals:**
- Hasilkan APK `mihonmod` yang dapat dipasang berdampingan dengan Mihon resmi pada device yang sama.
- APK `mihonmod` harus jelas dibedakan oleh user (nama + icon berbeda).
- Cloud sync tetap berfungsi pada `mihonmod` — tidak silent-disable karena mismatch package allow-list.
- Onboarding pengalaman pertama: kalau Mihon resmi terdeteksi terpasang, beri instruksi singkat tentang opsi migrasi (backup file atau cloud sync) tanpa memaksa user mengikuti salah satu.

**Non-Goals:**
- Akses database `app.mihon` secara langsung dari `app.mihonmod` (Android sandbox blok ini tanpa root).
- Modifikasi APK Mihon resmi.
- Sinkronisasi otomatis dua arah real-time tanpa cloud akun (impossible without shared backend or root).
- Auto-import data Mihon resmi tanpa keterlibatan user (menyentuh data app lain = pelanggaran sandbox).
- Membuat fork ini menjadi "drop-in replacement" yang seamless — selalu ada langkah eksplisit migrasi.

## Decisions

### Pakai product flavor, bukan terpisah git branch / repo

Alternatif: maintain dua branch yang masing-masing menetapkan `applicationId` berbeda. Ditolak karena double maintenance, semua perbaikan harus di-cherry-pick ke kedua branch, dan CI workflow dobel.

Product flavor menyatu dalam satu codebase, dengan satu `applicationId` per flavor di Gradle:

```kotlin
android {
    flavorDimensions += "default"
    productFlavors {
        create("mihon") {
            dimension = "default"
            applicationId = "app.mihon"
        }
        create("mihonmod") {
            dimension = "default"
            applicationId = "app.mihonmod"
            // versionNameSuffix dst.
        }
    }
}
```

Build type `debug` tetap menambahkan suffix `.dev`, jadi kombinasinya: `mihonRelease=app.mihon`, `mihonDebug=app.mihon.dev`, `mihonmodRelease=app.mihonmod`, `mihonmodDebug=app.mihonmod.dev`.

### Default flavor = `mihon`

Supaya CI yang membangun `assembleRelease` tetap menghasilkan APK yang sama dengan sebelum change ini (tidak menyebabkan regresi pada workflow existing), `mihon` ditandai sebagai default. `assembleRelease` akan menghasilkan **kedua** APK (mihon + mihonmod) — itu memang yang diinginkan untuk fork rilis.

### Resource override per flavor untuk app name + icon

`app/src/mihonmod/res/values/strings.xml`:
```xml
<resources>
    <string name="app_name" translatable="false">MihonMod</string>
</resources>
```

`app/src/mihonmod/res/mipmap-*/ic_launcher.xml` (adaptive icon dengan tinta warna berbeda — diturunkan dari icon Mihon dengan layer warna primary di-rotate atau diberi badge "M+").

Resource main `app/src/main/res/values/strings.xml` sudah punya `<string name="app_name">Mihon</string>`, jadi flavor override hanya perlu di flavor source set.

### Cloud sync allow-list ditambah, **bukan** dilemahkan

`CloudSyncProductionGuard.ALLOWED_PACKAGES` saat ini = `setOf("app.mihon", "app.mihon.dev")`. Tambah `"app.mihonmod", "app.mihonmod.dev"`. Tidak menggunakan `setOf("app.*")` atau pattern karena guard juga mengecek SHA-1 fingerprint cert — package allow-list kasar adalah salah satu lapisan, bukan satu-satunya.

Cert fingerprint: fork developer harus menambahkan SHA-1 release sign key mereka ke `ALLOWED_CERTIFICATE_FINGERPRINTS` apa pun flavor-nya, satu kali. Karena release sign key sama untuk kedua flavor, satu fingerprint cukup.

### Onboarding dialog mendeteksi Mihon resmi via PackageManager

Pada first launch `mihonmod`, MainActivity (atau `App.onCreate()` callback jalur sekali-jalan) akan:

```kotlin
val originalInstalled = runCatching {
    packageManager.getPackageInfo("app.mihon", 0)
    true
}.getOrDefault(false)
```

Kalau `true` dan flag `mihonmod_onboarding_shown == false`, tampilkan dialog satu kali yang menjelaskan:
- "Mihon (resmi) terdeteksi di HP. Library tidak terbagi otomatis antar dua aplikasi."
- Tiga aksi:
  - **Open Mihon** — `Intent` untuk meluncurkan `app.mihon`, biar user export backup di sana.
  - **Restore from backup file** — pintasan langsung ke layar restore.
  - **Sign in to cloud sync** — pintasan ke `AccountSignInScreen`, supaya user bisa pakai akun yang sama dari device lain (kalau pernah sign-in di device lain).
- Tombol **Don't show again**.

Flag onboarding disimpan di `BasePreferences` atau sebuah `MihonModPreferences` baru. Tidak boleh muncul di flavor `mihon`.

### Tidak coba mengakses DB Mihon resmi

Solusi ini sengaja **tidak** mencoba membaca atau meng-attach `tachiyomi.db` dari `/data/data/app.mihon/`. Alasan:
- Tidak akan bekerja di device non-root (Android sandbox).
- Walau di device root, kedua process (`app.mihon` dan `app.mihonmod`) yang men-share file SQLite secara bersamaan rentan korupsi journal.
- Membuat fitur yang hanya bekerja di rooted device = UX sampah untuk mayoritas user.

Migrasi data via:
1. **Backup file** — user-driven, satu kali.
2. **Cloud sync** — kontinu, lewat akun.

### Build APK file naming yang jelas

CI artifact upload → file APK diberi nama eksplisit per flavor:
- `mihon-arm64-v8a-${{ github.sha }}.apk`
- `mihonmod-arm64-v8a-${{ github.sha }}.apk`

Mencegah kebingungan saat fork user download APK dari Actions.

## Risks / Trade-offs

- **Risk:** Build time CI naik ~2× (dua flavor build).
  → **Mitigasi:** split workflow — debug job hanya build mihonmodDebug, release job build keduanya. Kalau masih mahal, jadikan flavor `mihon` opt-in di CI lewat workflow input.

- **Risk:** Pengguna mengira kedua app sinkron otomatis dan mengedit library di salah satu, kemudian bingung kenapa tidak terbawa.
  → **Mitigasi:** onboarding dialog spesifik soal ini + dokumentasi di README. Kalau cloud sync diaktifkan di kedua app, mereka pun tetap independen kecuali pakai akun yang sama.

- **Risk:** Firebase project hanya mendaftarkan `app.mihon` sehingga build `app.mihonmod` gagal init Firebase.
  → **Mitigasi:** task #X mendaftarkan paket baru di Firebase Console + regenerate `google-services.json`. Tanpa ini, `CloudSyncProductionGuard` akan reject dan cloud sync silent-disabled (graceful), tidak crash.

- **Risk:** Onboarding dialog menebak salah karena `getPackageInfo` blok di Android 11+ tanpa `<queries>` di manifest.
  → **Mitigasi:** tambah `<queries><package android:name="app.mihon"/></queries>` di [AndroidManifest.xml](app/src/main/AndroidManifest.xml) flavor mihonmod.

- **Trade-off:** APK ukuran tidak berubah (kedua flavor identik kode-nya), tapi update channel beda. Pengguna yang ingin ikut update otomatis dari fork harus install APK mihonmod sebagai aplikasi terpisah, bukan menambal Mihon resmi.

## Migration Plan

Untuk fork developer:
1. Daftar `app.mihonmod` dan `app.mihonmod.dev` di Firebase Console (project sama dengan Mihon).
2. Tambah SHA-1 release/debug fingerprint kedua paket.
3. Download `google-services.json` baru → ganti file lokal (gitignored).
4. Update `CloudSyncProductionGuard.ALLOWED_PACKAGES` + `ALLOWED_CERTIFICATE_FINGERPRINTS` (kalau pertama kali set).
5. Push commit; CI akan build `assembleRelease -Pinclude-telemetry` menghasilkan dua APK.

Untuk pengguna existing yang sudah pasang fork lama (applicationId `app.mihon`):
1. Mereka **tidak** otomatis pindah ke mihonmod.
2. APK mihonmod adalah aplikasi terpisah; install side-by-side.
3. Untuk mempertahankan library: ekspor backup di app lama → restore di mihonmod, atau setup cloud sync di kedua app dengan akun yang sama.

Rollback: kalau flavor mihonmod bermasalah, build hanya flavor `mihon` (`./gradlew :app:assembleMihonRelease`). Tidak ada perubahan irreversible di codebase.

## Open Questions

- Apakah membuat icon yang berbeda visual cukup, atau perlu juga splash screen yang berbeda warna untuk lebih clearly differentiate? **Tentative: icon saja untuk v1.**
- Apakah onboarding dialog harus juga muncul kalau user *uninstall* Mihon resmi setelah onboarding ditampilkan? **Tentative: tidak, flag "shown" cukup; deteksi ulang membuat noise.**
- Apakah versi rilis fork sebaiknya diberi suffix string (mis. "0.19.9-mod.1") supaya jelas berbeda dari upstream? **Tentative: ya, ditangani via `versionNameSuffix = "-mod"` di flavor block.**
