# 🔐 passenD

**passenD** adalah ekosistem manajemen kredensial dan kata sandi multi-platform yang dirancang untuk memudahkan penyimpanan dan pengisian otomatis (*autofill*) akun Anda secara aman di perangkat mobile maupun peramban web (*browser*).

[![Android CI - Build Debug APK](https://github.com)](https://github.com)

---

## 📂 Struktur Repositori

Repositori ini merupakan *monorepo* yang terdiri dari beberapa komponen utama:

```text
passenD/
├── passend/               # Aplikasi Android utama (Gradle / Kotlin / Java)
├── passenD-extension/     # Ekstensi peramban berbasis Chromium (Chrome, Edge, Brave)
├── passend-firefox/       # Ekstensi peramban untuk Mozilla Firefox
└── .github/workflows/     # Konfigurasi CI/CD GitHub Actions untuk build APK otomatis
```

---

## ✨ Fitur Utama

- **Aplikasi Mobile Android (`passend`):** Kelola, simpan, dan buat kata sandi kuat langsung dari perangkat Android Anda.
- **Ekstensi Chromium (`passenD-extension`):** Integrasi langsung untuk peramban Google Chrome, Microsoft Edge, dan Brave.
- **Ekstensi Firefox (`passend-firefox`):** Dukungan penuh untuk pengguna Mozilla Firefox.
- **Build Otomatis (CI/CD):** Pembuatan file Debug APK secara otomatis setiap kali ada pembaruan kode melalui GitHub Actions.

---

## 🚀 Panduan Instalasi & Pengembangan

### 1. Aplikasi Android (`passend`)

#### Prasyarat
- **JDK 17** atau lebih baru
- **Android Studio** (disarankan versi terbaru) atau Android SDK

#### Langkah Build Manual
1. Kloning repositori ini:
   ```bash
   git clone https://github.com/baguzzz/passenD
   cd passenD/passend
   ```
2. Berikan izin eksekusi pada Gradle wrapper (Linux/macOS):
   ```bash
   chmod +x gradlew
   ```
3. Jalankan perintah build Debug APK:
   ```bash
   ./gradlew assembleDebug
   ```
4. File APK hasil build dapat ditemukan di:
   `passend/app/build/outputs/apk/debug/`

> **Catatan:** Anda juga dapat mengunduh file **passenD-debug-apk** langsung melalui tab **Actions** pada repositori ini setelah proses build selesai.

---

### 2. Ekstensi Chromium (`passenD-extension`)

1. Buka peramban berbasis Chromium (Chrome / Edge / Brave).
2. Akses halaman ekstensi melalui `chrome://extensions/` (atau `edge://extensions/`).
3. Aktifkan **Developer mode** (Mode pengembang) di pojok kanan atas.
4. Klik **Load unpacked** dan pilih folder `passenD-extension/` dari repositori ini.

---

### 3. Ekstensi Mozilla Firefox (`passend-firefox`)

1. Buka peramban Mozilla Firefox dan akses `about:debugging#/runtime/this-firefox`.
2. Klik tombol **Load Temporary Add-on...**.
3. Arahkan ke folder `passend-firefox/` dan pilih file `manifest.json`.

---

## 🤝 Kontribusi

Kontribusi selalu diterima! Silakan buat *Fork* dari repositori ini, buat *branch* fitur baru (`git checkout -b fitur-baru`), lakukan *commit* perubahan Anda, dan ajukan **Pull Request**.

---

## 📄 Lisensi

Proyek ini dikembangkan oleh [@baguzzz](https://github.com/baguzzz). Silakan tambahkan informasi lisensi (misalnya MIT License) sesuai kebutuhan distribusi proyek Anda.
