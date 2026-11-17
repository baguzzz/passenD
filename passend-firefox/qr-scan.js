// PassenD Extension - QR Scanner (Firefox-compatible, dengan jsQR fallback)
// Butuh jsqr.min.js di folder yang sama (expose window.jsQR).

const video = document.getElementById('video');
const statusEl = document.getElementById('status');
const fileInput = document.getElementById('qrFile');

const hasBarcodeDetector = 'BarcodeDetector' in window;
const hasJsQR = (typeof jsQR !== 'undefined');

let detector = null;
let scanning = false;
let streamRef = null;
let canvas = null;
let ctx = null;

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

function captureFrame() {
    if (!video.videoWidth || !video.videoHeight) return null;
    if (!canvas) {
        canvas = document.createElement('canvas');
        ctx = canvas.getContext('2d', { willReadFrequently: true });
    }
    canvas.width = video.videoWidth;
    canvas.height = video.videoHeight;
    ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
    return ctx.getImageData(0, 0, canvas.width, canvas.height);
}

async function detectQrFromVideo() {
    // Prioritas BarcodeDetector (Chrome native, paling cepat)
    if (detector) {
        try {
            const codes = await detector.detect(video);
            const vals = codes.map(c => c.rawValue).filter(Boolean);
            if (vals.length) return vals;
        } catch (e) { /* fallback */ }
    }
    // Fallback jsQR (Firefox, atau Chrome kalau BarcodeDetector gagal)
    if (hasJsQR) {
        const frame = captureFrame();
        if (frame) {
            const code = jsQR(frame.data, frame.width, frame.height, {
                inversionAttempts: 'dontInvert'
            });
            if (code && code.data) return [code.data];
        }
    }
    return [];
}

async function scanLoop() {
    if (!scanning) return;
    try {
        const vals = await detectQrFromVideo();
        for (const v of vals) {
            if (v.startsWith('passend://pair?')) {
                await processQr(v);
                return;
            }
        }
    } catch (e) {}
    if (scanning) requestAnimationFrame(scanLoop);
}

async function startCamera() {
    if (hasBarcodeDetector) {
        try { detector = new BarcodeDetector({ formats: ['qr_code'] }); }
        catch (e) { detector = null; }
    }
    if (!detector && !hasJsQR) {
        setStatus('Tidak ada QR decoder. Pastikan jsqr.min.js ada di folder extensi.', 'error');
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
        setStatus('Kamera tidak bisa diakses: ' + e.message + ' — gunakan upload gambar.', 'error');
    }
}

async function decodeImageFile(file) {
    if (hasBarcodeDetector) {
        try {
            if (!detector) detector = new BarcodeDetector({ formats: ['qr_code'] });
            const bitmap = await createImageBitmap(file);
            const codes = await detector.detect(bitmap);
            if (codes.length > 0 && codes[0].rawValue) return codes[0].rawValue;
        } catch (e) { console.warn('BarcodeDetector image failed:', e); }
    }
    if (hasJsQR) {
        const bitmap = await createImageBitmap(file);
        const c = document.createElement('canvas');
        c.width = bitmap.width;
        c.height = bitmap.height;
        const g = c.getContext('2d', { willReadFrequently: true });
        g.drawImage(bitmap, 0, 0);
        const imgData = g.getImageData(0, 0, c.width, c.height);
        const code = jsQR(imgData.data, imgData.width, imgData.height, {
            inversionAttempts: 'attemptBoth'
        });
        if (code && code.data) return code.data;
    }
    return null;
}

fileInput.addEventListener('change', async () => {
    const file = fileInput.files && fileInput.files[0];
    if (!file) return;
    setStatus('Mendeteksi QR…');
    try {
        const raw = await decodeImageFile(file);
        if (!raw) { setStatus('QR tidak terdeteksi di gambar.', 'error'); return; }
        await processQr(raw);
    } catch (e) {
        setStatus('Gagal decode gambar: ' + e.message, 'error');
    }
});

window.addEventListener('beforeunload', () => {
    scanning = false;
    if (streamRef) { streamRef.getTracks().forEach(t => t.stop()); streamRef = null; }
});

// Mode upload: kalau dibuka dengan #upload, langsung file picker
if (location.hash === '#upload') {
    setStatus('Pilih gambar QR yang berisi URL pairing…');
    setTimeout(() => fileInput.click(), 400);
} else {
    startCamera();
}