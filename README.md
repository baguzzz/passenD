

# 🔐 passenD

**passenD** adalah proyek manajemen kredensial lintas perangkat yang terdiri dari aplikasi Android dan ekstensi peramban untuk Chromium serta Firefox.

[![Android CI - Build Debug APK](https://github.com/baguzzz/passenD/actions/workflows/android-build.yml/badge.svg)](https://github.com/baguzzz/passenD/actions/workflows/android-build.yml)

> Gunakan rilis hanya dari sumber tepercaya. Lindungi perangkat Anda dengan kata sandi, PIN, atau biometrik.

## Struktur Repositori

```text
passenD/
├── passend/               # Aplikasi Android
├── passenD-extension/     # Ekstensi Chromium
├── passend-firefox/       # Ekstensi Firefox
└── .github/workflows/     # Workflow GitHub Actions
```

## Fitur Utama

- Penyimpanan kredensial lokal.
- Ekstensi Chrome, Edge, Brave, dan Firefox.
- Pembuatan kata sandi dan TOTP.
- Impor, ekspor, dan cadangan vault.
- Build APK debug otomatis melalui GitHub Actions.

## Persyaratan

- JDK 17
- Android SDK
- Android Studio atau Gradle
- Peramban Chromium atau Mozilla Firefox

## Instalasi Aplikasi Android

### Kloning Repository

```bash
git clone https://github.com/baguzzz/passenD.git
cd passenD
```

### Build Menggunakan Gradle Wrapper

Linux atau macOS:

```bash
cd passend
chmod +x gradlew
./gradlew assembleDebug
```

Windows:

```bat
cd passend
gradlew.bat assembleDebug
```

APK debug akan tersedia di:

```text
passend/app/build/outputs/apk/debug/
```

> Pastikan terminal berada dalam direktori `passend` yang berisi file `settings.gradle.kts` dan `gradlew`.

## Instalasi Ekstensi Chromium

1. Buka `chrome://extensions/`, `edge://extensions/`, atau halaman ekstensi peramban Chromium.
2. Aktifkan **Developer mode**.
3. Pilih **Load unpacked**.
4. Pilih folder:

```text
passenD-extension
```

## Instalasi Ekstensi Firefox

1. Buka `about:debugging#/runtime/this-firefox`.
2. Pilih **Load Temporary Add-on**.
3. Masuk ke folder `passend-firefox`.
4. Pilih file `manifest.json`.

## Pengujian Build Android

Linux atau macOS:

```bash
cd passend
./gradlew assembleDebug
```

Windows:

```bat
cd passend
gradlew.bat assembleDebug
```

Hasil build dapat diperiksa di:

```text
passenD/passend/app/build/outputs/apk/debug/
```

## Download APK dari GitHub Actions

1. Buka tab **Actions** repository.
2. Pilih workflow **Android CI - Build Debug APK**.
3. Pilih run dengan status berhasil.
4. Unduh artefak bernama `passenD-debug-apk`.

## Kontribusi

1. Fork repository ini.
2. Buat branch baru:

```bash
git switch -c fitur-baru
```

3. Commit perubahan:

```bash
git add .
git commit -m "Tambahkan fitur baru"
```

4. Push branch:

```bash
git push origin fitur-baru
```

5. Buat Pull Request ke branch `main`.

## Lisensi

Repository saat ini belum memiliki file lisensi. Tambahkan lisensi sebelum mendistribusikan aplikasi atau membuat rilis publik.