// PassenD Extension - QR Scanner

const video = document.getElementById('video');
const statusEl = document.getElementById('status');
const fileInput = document.getElementById('qrFile');

let detector = null;
let scanning = false;
let streamRef = null;

function setStatus(text, cls = '') {
    statusEl.textContent = text;
    statusEl.className = 'status ' + cls;
}

async function processQr(rawValue) {
    if (!rawValue || !rawValue.startsWith('passend://pair?')) return;
    setStatus('QR valid terdeteksi! Menghubungkan…', 'ok');
    scanning = false;
    try {
        const resp = await new Promise(resolve => {
            chrome.runtime.sendMessage({ type: 'qrDetected', payload: rawValue }, resolve);
        });
        if (resp && resp.ok) {
            setStatus('Berhasil terhubung! Menutup tab…', 'ok');
            setTimeout(() => window.close(), 800);
        } else {
            setStatus('Gagal pair: ' + ((resp && resp.error) || 'unknown'), 'error');
            scanning = true; scanLoop();
        }
    } catch (e) {
        setStatus('Gagal: ' + e.message, 'error');
        scanning = true; scanLoop();
    }
}

async function scanLoop() {
    if (!scanning || !detector) return;
    try {
        const codes = await detector.detect(video);
        for (const c of codes) {
            if (c.rawValue && c.rawValue.startsWith('passend://pair?')) {
                await processQr(c.rawValue);
                return;
            }
        }
    } catch (e) {}
    if (scanning) requestAnimationFrame(scanLoop);
}

async function start() {
    if (!('BarcodeDetector' in window)) {
        setStatus('Browser tidak mendukung deteksi QR otomatis. Gunakan upload gambar di bawah, atau paste URL di popup.', 'error');
        return;
    }
    try {
        detector = new BarcodeDetector({ formats: ['qr_code'] });
    } catch (e) {
        setStatus('BarcodeDetector gagal: ' + e.message, 'error');
        return;
    }
    try {
        const stream = await navigator.mediaDevices.getUserMedia({
            video: { facingMode: 'environment' }
        });
        streamRef = stream;
        video.srcObject = stream;
        await video.play();
        setStatus('Arahkan QR ke kamera…');
        scanning = true;
        scanLoop();
    } catch (e) {
        setStatus('Kamera tidak bisa diakses: ' + e.message, 'error');
    }
}

// Upload fallback
fileInput.addEventListener('change', async () => {
    const file = fileInput.files && fileInput.files[0];
    if (!file) return;
    if (!('BarcodeDetector' in window)) {
        setStatus('BarcodeDetector tidak tersedia. Paste URL di popup.', 'error');
        return;
    }
    try {
        if (!detector) detector = new BarcodeDetector({ formats: ['qr_code'] });
        const bitmap = await createImageBitmap(file);
        const codes = await detector.detect(bitmap);
        if (codes.length === 0) { setStatus('QR tidak terdeteksi di gambar.', 'error'); return; }
        await processQr(codes[0].rawValue);
    } catch (e) {
        setStatus('Gagal decode gambar: ' + e.message, 'error');
    }
});

window.addEventListener('beforeunload', () => {
    scanning = false;
    if (streamRef) { streamRef.getTracks().forEach(t => t.stop()); streamRef = null; }
});

start();