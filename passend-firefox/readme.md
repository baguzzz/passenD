# PassenD Client — Firefox Extension

Client browser untuk **PassenD** password manager (Android). Extension ini
menghubungkan Firefox ke aplikasi PassenD di HP via WebSocket lokal (LAN),
menggunakan AES-GCM + HKDF untuk enkripsi end-to-end.

**Repository:** (URL Git repo Anda, opsional)
**Lisensi:** MIT (untuk kode extension sendiri)
**Author:** Few <few@example.com>

---

## 1. Build Requirements

Extension ini **tidak memiliki build step** yang memerlukan kompilasi,
transpilasi, bundling, atau minifikasi kode sumber sendiri. Semua file `.js`,
`.html`, `.css` dimuat apa adanya oleh Firefox. Satu-satunya langkah "build"
adalah **packaging** (zip direktori menjadi file `.xpi`).

Untuk mereproduksi file `.xpi` dari source code, diperlukan:

| Software | Versi Minimal | Keterangan |
|---|---|---|
| **Windows 10/11** | 21H2+ | OS utama (build.ps1) |
| **PowerShell** | 5.1 | Built-in Windows; atau PowerShell 7.4+ |
| **`Compress-Archive` cmdlet** | Bundled dengan PS 5.1 | Tidak perlu install tambahan |
| **Atau Linux/macOS** | - | Alternatif (build.sh) |
| **bash** | 5.0+ | Built-in Linux/macOS |
| **`zip`** | 3.0+ | `sudo apt install zip` (Ubuntu) / `brew install zip` (macOS) |
| **Firefox** | **140.0+** | Untuk test extension; sesuai `strict_min_version` di manifest |
| **Node.js** *(opsional)* | 18.0.0 LTS+ | Hanya untuk `web-ext lint` validation |
| **npm** *(opsional)* | 9.0.0+ | Bundled dengan Node 18+ |

**Catatan**: Node.js dan npm **TIDAK diperlukan** untuk build. Keduanya hanya
opsional untuk menjalankan `web-ext lint` (validasi AMO). Build `.xpi` bisa
dilakukan 100% dengan PowerShell atau bash bawaan OS.

---

## 2. Installation of Build Tools

### Windows (recommended)

PowerShell 5.1 sudah ada di Windows 10/11. Tidak perlu install apapun.

Untuk menggunakan `web-ext lint` (opsional):

1. Install **Node.js 20 LTS** dari https://nodejs.org/en/download/
   - Pilih Windows Installer (.msi) 64-bit
   - Verifikasi: `node --version` → harus `v20.x.x` atau lebih baru
   - Verifikasi: `npm --version` → harus `10.x.x` atau lebih baru

2. Install **web-ext** global:
   ```powershell
   npm install -g web-ext@8