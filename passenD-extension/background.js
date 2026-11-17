// PassenD Extension - Background (Fase 14)

const state = {
    paired: false, ip: null, port: null, token: null,
    ws: null, sessionKey: null, authenticated: false,
    connecting: false, lastError: null, locked: false,
    serverVersion: 0
};

let responseQueue = [];
let pingTimer = null;
let reconnectTimer = null;
let reconnectAttempts = 0;

async function hkdf(token, salt, info, len) {
    const key = await crypto.subtle.importKey('raw', token, { name: 'HKDF' }, false, ['deriveBits']);
    const bits = await crypto.subtle.deriveBits(
        { name: 'HKDF', hash: 'SHA-256', salt, info: new TextEncoder().encode(info) },
        key, len * 8);
    return new Uint8Array(bits);
}
function b64encode(bytes) { let s = ''; for (let i = 0; i < bytes.length; i++) s += String.fromCharCode(bytes[i]); return btoa(s); }
function b64decode(s) { const bin = atob(s); const out = new Uint8Array(bin.length); for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i); return out; }
async function encrypt(key, plaintext) {
    const iv = crypto.getRandomValues(new Uint8Array(12));
    const k = await crypto.subtle.importKey('raw', key, { name: 'AES-GCM' }, false, ['encrypt']);
    const ct = await crypto.subtle.encrypt({ name: 'AES-GCM', iv, tagLength: 128 }, k, new TextEncoder().encode(plaintext));
    return { iv: b64encode(iv), c: b64encode(new Uint8Array(ct)) };
}
async function decrypt(key, ivB64, ctB64) {
    const iv = b64decode(ivB64); const ct = b64decode(ctB64);
    const k = await crypto.subtle.importKey('raw', key, { name: 'AES-GCM' }, false, ['decrypt']);
    const pt = await crypto.subtle.decrypt({ name: 'AES-GCM', iv, tagLength: 128 }, k, ct);
    return new TextDecoder().decode(pt);
}

function updateBadge() {
    try {
        if (state.locked) {
            chrome.action.setBadgeText({ text: '🔒' });
            chrome.action.setBadgeBackgroundColor({ color: '#c62828' });
        } else { chrome.action.setBadgeText({ text: '' }); }
    } catch (e) {}
}

async function connect() {
    if (state.connecting) return { ok: false, error: 'connecting' };
    if (state.ws && state.ws.readyState === WebSocket.OPEN) return { ok: true, already: true };
    if (!state.paired) return { ok: false, error: 'not paired' };

    state.connecting = true; state.lastError = null;
    const url = `ws://${state.ip}:${state.port}/`;
    const clientNonce = crypto.getRandomValues(new Uint8Array(16));
    let ws;
    try { ws = new WebSocket(url); }
    catch (e) { state.connecting = false; state.lastError = 'Invalid URL'; return { ok: false, error: state.lastError }; }
    state.ws = ws;

    ws.onopen = () => {
        state.sessionKey = null; state.authenticated = false;
        ws.send(JSON.stringify({ t: 'hello', n: b64encode(clientNonce) }));
    };

    ws.onmessage = async (ev) => {
        try {
            const msg = JSON.parse(ev.data);
            if (msg.t === 'challenge') {
                const sn = b64decode(msg.n);
                const salt = new Uint8Array([...clientNonce, ...sn]);
                state.sessionKey = await hkdf(state.token, salt, 'passend-ws-session-v1', 32);
                const env = await encrypt(state.sessionKey, JSON.stringify({ action: 'auth' }));
                ws.send(JSON.stringify(env));
            } else if (msg.iv && msg.c) {
                const inner = JSON.parse(await decrypt(state.sessionKey, msg.iv, msg.c));
                if (inner.push === 'lock') {
                    state.locked = true; updateBadge();
                    broadcast({ type: 'locked' });
                    return;
                }
                if (!state.authenticated) {
                    if (inner.ok && inner.data && inner.data.authenticated) {
                        state.authenticated = true; state.connecting = false;
                        state.serverVersion = inner.data.serverVersion || 0;
                        reconnectAttempts = 0; startPing(); updateBadge();
                        broadcast({ type: 'connected', serverVersion: state.serverVersion });
                    }
                } else {
                    if (responseQueue.length > 0) {
                        const pending = responseQueue.shift();
                        clearTimeout(pending.timer);
                        pending.resolve(inner);
                    }
                }
            }
        } catch (e) { console.error('[PassenD] onmessage', e); }
    };

    ws.onclose = (ev) => {
        stopPing();
        state.authenticated = false; state.sessionKey = null; state.ws = null;
        state.connecting = false;
        while (responseQueue.length > 0) { const p = responseQueue.shift(); clearTimeout(p.timer); p.reject(new Error('Disconnected')); }
        broadcast({ type: 'disconnected', code: ev.code });
        updateBadge();
        if (ev.code !== 1000 && state.paired) scheduleReconnect();
    };
    ws.onerror = () => { state.lastError = 'Connection error'; state.connecting = false; };
    return { ok: true };
}

function scheduleReconnect() {
    if (reconnectTimer) return;
    const delay = Math.min(3000 * Math.pow(2, reconnectAttempts), 30000);
    reconnectAttempts++;
    reconnectTimer = setTimeout(() => { reconnectTimer = null; if (state.paired) connect(); }, delay);
}
function disconnect() {
    if (reconnectTimer) { clearTimeout(reconnectTimer); reconnectTimer = null; }
    stopPing();
    if (state.ws) { try { state.ws.close(1000, 'user'); } catch (e) {} state.ws = null; }
    state.authenticated = false; state.sessionKey = null; updateBadge();
}
function startPing() {
    stopPing();
    pingTimer = setInterval(async () => {
        try {
            const r = await request('ping', {});
            if (r.ok && state.locked) { state.locked = false; updateBadge(); broadcast({ type: 'unlocked' }); }
        } catch (e) {}
    }, 25000);
}
function stopPing() { if (pingTimer) { clearInterval(pingTimer); pingTimer = null; } }

async function request(action, payload) {
    if (!state.ws || state.ws.readyState !== WebSocket.OPEN || !state.sessionKey) throw new Error('Not connected');
    const inner = JSON.stringify({ action, payload });
    const env = await encrypt(state.sessionKey, inner);
    return new Promise((resolve, reject) => {
        const pending = {
            resolve, reject,
            timer: setTimeout(() => {
                const idx = responseQueue.indexOf(pending);
                if (idx >= 0) responseQueue.splice(idx, 1);
                reject(new Error('Request timeout'));
            }, 15000)
        };
        responseQueue.push(pending);
        state.ws.send(JSON.stringify(env));
    });
}

function broadcast(msg) { chrome.runtime.sendMessage(msg).catch(() => {}); }

async function loadState() {
    const data = await chrome.storage.local.get(['pairing']);
    if (data.pairing) {
        state.paired = true;
        state.ip = data.pairing.ip; state.port = data.pairing.port;
        state.token = b64decode((data.pairing.token || '').replace(/ /g, '+'));
    }
}
async function savePairing(ip, port, tokenStr) {
    await chrome.storage.local.set({ pairing: { ip, port, token: tokenStr } });
    state.paired = true; state.ip = ip; state.port = port; state.token = b64decode(tokenStr);
}
async function clearPairing() {
    disconnect();
    await chrome.storage.local.remove('pairing');
    state.paired = false; state.ip = null; state.port = null; state.token = null;
    state.locked = false; state.serverVersion = 0; updateBadge();
}

chrome.runtime.onMessage.addListener((msg, sender, sendResponse) => {
    (async () => {
        try {
            switch (msg.type) {
                case 'getStatus':
                    sendResponse({
                        paired: state.paired,
                        connected: !!state.ws && state.authenticated,
                        locked: state.locked,
                        ip: state.ip, port: state.port,
                        error: state.lastError,
                        serverVersion: state.serverVersion
                    }); return;

                case 'pairAndConnect': {
                    await savePairing(msg.ip, msg.port, msg.token);
                    sendResponse(await connect());
                    return;
                }
                case 'qrDetected': {
                    const url = msg.payload || '';
                    if (!url.startsWith('passend://pair?')) { sendResponse({ ok: false, error: 'URL tidak valid' }); return; }
                    const qs = url.substring('passend://pair?'.length);
                    const r = {};
                    for (const part of qs.split('&')) {
                        const idx = part.indexOf('=');
                        if (idx < 0) continue;
                        const k = part.substring(0, idx), v = part.substring(idx + 1);
                        if (k === 'token') r.token = v.replace(/ /g, '+');
                        else try { r[k] = decodeURIComponent(v); } catch { r[k] = v; }
                    }
                    if (!r.ip || !r.port || !r.token) { sendResponse({ ok: false, error: 'URL tidak lengkap' }); return; }
                    await savePairing(r.ip, r.port, r.token);
                    sendResponse(await connect());
                    return;
                }
                case 'connect': sendResponse(await connect()); return;
                case 'disconnect': disconnect(); sendResponse({ ok: true }); return;
                case 'unpair': await clearPairing(); sendResponse({ ok: true }); return;

                case 'api': {
                    const result = await request(msg.action, msg.payload || {});
                    if (!result.ok && result.error && result.error.includes('vault is locked')) {
                        if (!state.locked) { state.locked = true; updateBadge(); broadcast({ type: 'locked' }); }
                    } else if (result.ok) {
                        if (state.locked) { state.locked = false; updateBadge(); broadcast({ type: 'unlocked' }); }
                    }
                    sendResponse(result);
                    return;
                }

                case 'autoFillList': {
                    if (!state.authenticated || state.locked) { sendResponse({ ok: false, error: 'locked' }); return; }
                    try {
                        const r = await request('list', {});
                        if (!r.ok) { sendResponse({ ok: false, error: r.error }); return; }
                        const domain = (msg.domain || '').toLowerCase();
                        const matches = (r.data.credentials || []).filter(c => {
                            const site = (c.site || '').toLowerCase();
                            return !domain || site.includes(domain) || domain.includes(site);
                        });
                        sendResponse({ ok: true, list: matches });
                    } catch (e) { sendResponse({ ok: false, error: e.message }); }
                    return;
                }

                case 'autoFillGet': {
                    if (!state.authenticated || state.locked) { sendResponse({ ok: false, error: 'locked' }); return; }
                    try {
                        const r = await request('get', { id: msg.id });
                        sendResponse(r.ok ? { ok: true, credential: r.data } : { ok: false, error: r.error });
                    } catch (e) { sendResponse({ ok: false, error: e.message }); }
                    return;
                }

                case 'formDetected': {
                    if (sender.tab && !state.locked && state.authenticated) {
                        chrome.action.setBadgeText({ tabId: sender.tab.id, text: '●' });
                        chrome.action.setBadgeBackgroundColor({ tabId: sender.tab.id, color: '#2e7d32' });
                    }
                    sendResponse({ ok: true });
                    return;
                }

                default: sendResponse({ ok: false, error: 'unknown' });
            }
        } catch (e) { sendResponse({ ok: false, error: e.message }); }
    })();
    return true;
});

loadState().then(() => { if (state.paired) connect(); });