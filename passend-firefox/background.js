// PassenD Extension - Background (Firefox MV3 compatible)
// Firefox-specific fixes:
//   - storage helper (callback-based, no Promise dependency)
//   - chrome.alarms untuk auto-reconnect (survive event page unload)
//   - keepalive Port listener (extends event page lifetime saat popup/content buka)
//   - debug logging

const DEBUG = true;
const log = (...a) => { if (DEBUG) try { console.log('[PassenD]', ...a); } catch (e) {} };
const warn = (...a) => { if (DEBUG) try { console.warn('[PassenD]', ...a); } catch (e) {} };

const ALARM_NAME = 'passend-reconnect';
const KEEPALIVE_PORT_NAME = 'passend-keepalive';

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

// ============================================================
//  Storage helpers — callback-based (Firefox-safe)
// ============================================================
function storageGet(keys) {
    return new Promise((resolve) => {
        try {
            chrome.storage.local.get(keys, (result) => {
                if (chrome.runtime.lastError) {
                    warn('storageGet error', chrome.runtime.lastError.message);
                    resolve({});
                } else resolve(result || {});
            });
        } catch (e) { warn('storageGet throw', e); resolve({}); }
    });
}
function storageSet(obj) {
    return new Promise((resolve) => {
        try {
            chrome.storage.local.set(obj, () => {
                if (chrome.runtime.lastError) warn('storageSet error', chrome.runtime.lastError.message);
                resolve();
            });
        } catch (e) { warn('storageSet throw', e); resolve(); }
    });
}
function storageRemove(keys) {
    return new Promise((resolve) => {
        try {
            chrome.storage.local.remove(keys, () => {
                if (chrome.runtime.lastError) warn('storageRemove error', chrome.runtime.lastError.message);
                resolve();
            });
        } catch (e) { warn('storageRemove throw', e); resolve(); }
    });
}

// ============================================================
//  Crypto
// ============================================================
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

// ============================================================
//  Badge
// ============================================================
function updateBadge() {
    try {
        if (state.locked) {
            chrome.action.setBadgeText({ text: '\uD83D\uDD12' });
            chrome.action.setBadgeBackgroundColor({ color: '#c62828' });
        } else { chrome.action.setBadgeText({ text: '' }); }
    } catch (e) {}
}

// ============================================================
//  Alarms — Firefox MV3 auto-reconnect mechanism
// ============================================================
function ensureAlarm() {
    try {
        chrome.alarms.create(ALARM_NAME, { periodInMinutes: 1 });
        log('alarm ensured:', ALARM_NAME);
    } catch (e) { warn('alarm create failed', e); }
}

function clearAlarm() {
    try {
        chrome.alarms.clear(ALARM_NAME, () => {
            if (chrome.runtime.lastError) warn('alarm clear error', chrome.runtime.lastError.message);
        });
    } catch (e) { warn('alarm clear throw', e); }
}

chrome.alarms.onAlarm.addListener((alarm) => {
    if (alarm.name !== ALARM_NAME) return;
    log('alarm tick: paired=%s auth=%s connecting=%s locked=%s',
        state.paired, state.authenticated, state.connecting, state.locked);
    // Wake up = reconnect kalau perlu
    if (state.paired && !state.authenticated && !state.connecting) {
        log('alarm -> reconnect');
        connect();
    }
});

// ============================================================
//  Keepalive Port — extensions hold event page alive saat popup/content buka
// ============================================================
const keepaliveClients = new Set();

chrome.runtime.onConnect.addListener((port) => {
    if (port.name !== KEEPALIVE_PORT_NAME) return;
    log('keepalive port opened');
    keepaliveClients.add(port);

    // Trigger connect kalau belum terhubung
    if (state.paired && !state.authenticated && !state.connecting) {
        log('keepalive -> connect');
        connect();
    }

    port.onDisconnect.addListener(() => {
        log('keepalive port closed');
        keepaliveClients.delete(port);
    });
});

// ============================================================
//  Connect
// ============================================================
async function connect() {
    if (state.connecting) return { ok: false, error: 'connecting' };
    if (state.ws && state.ws.readyState === WebSocket.OPEN) return { ok: true, already: true };
    if (!state.paired) return { ok: false, error: 'not paired' };

    state.connecting = true; state.lastError = null;
    const url = `ws://${state.ip}:${state.port}/`;
    log('connect ->', url);
    const clientNonce = crypto.getRandomValues(new Uint8Array(16));
    let ws;
    try { ws = new WebSocket(url); }
    catch (e) {
        state.connecting = false; state.lastError = 'Invalid URL';
        warn('invalid URL', e);
        return { ok: false, error: state.lastError };
    }
    state.ws = ws;

    // Connect timeout 8 detik
    const connectTimer = setTimeout(() => {
        if (state.ws === ws && !state.authenticated) {
            log('connect timeout (8s)');
            state.lastError = 'Connection timeout (8s)';
            state.connecting = false;
            try { ws.close(4000, 'timeout'); } catch (e) {}
        }
    }, 8000);

    ws.onopen = () => {
        log('ws open, sending hello');
        state.sessionKey = null; state.authenticated = false;
        ws.send(JSON.stringify({ t: 'hello', n: b64encode(clientNonce) }));
    };

    ws.onmessage = async (ev) => {
        try {
            log('msg recv:', String(ev.data || '').slice(0, 80) + '...');
            const msg = JSON.parse(ev.data);
            if (msg.t === 'challenge') {
                log('challenge received, deriving session key');
                const sn = b64decode(msg.n);
                const salt = new Uint8Array([...clientNonce, ...sn]);
                state.sessionKey = await hkdf(state.token, salt, 'passend-ws-session-v1', 32);
                const env = await encrypt(state.sessionKey, JSON.stringify({ action: 'auth' }));
                ws.send(JSON.stringify(env));
                log('auth sent');
            } else if (msg.iv && msg.c) {
                const inner = JSON.parse(await decrypt(state.sessionKey, msg.iv, msg.c));
                if (inner.push === 'lock') {
                    log('push lock received');
                    state.locked = true; updateBadge();
                    broadcast({ type: 'locked' });
                    return;
                }
                if (!state.authenticated) {
                    if (inner.ok && inner.data && inner.data.authenticated) {
                        log('AUTH OK, serverVersion=', inner.data.serverVersion);
                        clearTimeout(connectTimer);
                        state.authenticated = true; state.connecting = false;
                        state.serverVersion = inner.data.serverVersion || 0;
                        reconnectAttempts = 0; startPing(); updateBadge();
                        broadcast({ type: 'connected', serverVersion: state.serverVersion });
                    } else {
                        log('AUTH REJECTED:', inner && inner.error);
                        clearTimeout(connectTimer);
                        state.connecting = false;
                        state.lastError = (inner && inner.error) || 'Authentication rejected';
                        try { ws.close(4001, 'auth_fail'); } catch (e) {}
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
        log('ws close code=%s reason=%s', ev.code, ev.reason);
        clearTimeout(connectTimer);
        stopPing();
        state.authenticated = false; state.sessionKey = null; state.ws = null;
        state.connecting = false;
        while (responseQueue.length > 0) { const p = responseQueue.shift(); clearTimeout(p.timer); p.reject(new Error('Disconnected')); }
        broadcast({ type: 'disconnected', code: ev.code });
        updateBadge();
        if (ev.code !== 1000 && state.paired && !state.lastError) scheduleReconnect();
    };
    ws.onerror = () => { log('ws error'); state.lastError = 'Connection error'; state.connecting = false; };
    return { ok: true };
}

function scheduleReconnect() {
    if (reconnectTimer) return;
    const delay = Math.min(3000 * Math.pow(2, reconnectAttempts), 30000);
    reconnectAttempts++;
    log('schedule reconnect in %d ms (attempt %d)', delay, reconnectAttempts);
    reconnectTimer = setTimeout(() => { reconnectTimer = null; if (state.paired) connect(); }, delay);
}
function disconnect() {
    if (reconnectTimer) { clearTimeout(reconnectTimer); reconnectTimer = null; }
    stopPing();
    if (state.ws) { try { state.ws.close(1000, 'user'); } catch (e) {} state.ws = null; }
    state.authenticated = false; state.sessionKey = null; state.connecting = false;
    updateBadge();
}
function startPing() {
    stopPing();
    pingTimer = setInterval(async () => {
        try {
            const r = await request('ping', {});
            if (r.ok && state.locked) { state.locked = false; updateBadge(); broadcast({ type: 'unlocked' }); }
        } catch (e) { log('ping fail', e.message); }
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

function broadcast(msg) {
    try {
        const p = chrome.runtime.sendMessage(msg);
        if (p && typeof p.catch === 'function') p.catch(() => {});
    } catch (e) {}
}

// ============================================================
//  Pairing state
// ============================================================
async function loadState() {
    const data = await storageGet(['pairing']);
    if (data.pairing) {
        state.paired = true;
        state.ip = data.pairing.ip; state.port = data.pairing.port;
        try {
            state.token = b64decode((data.pairing.token || '').replace(/ /g, '+'));
            log('loaded pairing:', state.ip + ':' + state.port, 'token len=' + (state.token ? state.token.length : 0));
        } catch (e) {
            warn('TOKEN DECODE FAILED:', e.message);
            state.lastError = 'Token corrupt: ' + e.message;
        }
    } else {
        log('no pairing saved');
    }
}
async function savePairing(ip, port, tokenStr) {
    await storageSet({ pairing: { ip, port, token: tokenStr } });
    state.paired = true; state.ip = ip; state.port = port;
    try { state.token = b64decode(tokenStr); } catch (e) {
        state.lastError = 'Invalid token: ' + e.message;
        warn('save pairing token invalid', e);
        return;
    }
    log('saved pairing: token len=' + state.token.length);
    ensureAlarm();
}
async function clearPairing() {
    disconnect();
    await storageRemove('pairing');
    clearAlarm();
    state.paired = false; state.ip = null; state.port = null; state.token = null;
    state.locked = false; state.serverVersion = 0; updateBadge();
}

// ============================================================
//  Message routing
// ============================================================
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
                        serverVersion: state.serverVersion,
                        connecting: state.connecting
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
                        if (k === 'token') {
                            let v2 = v;
                            try { v2 = decodeURIComponent(v); } catch (e) { v2 = v; }
                            r.token = v2.replace(/ /g, '+');
                        } else {
                            try { r[k] = decodeURIComponent(v); } catch { r[k] = v; }
                        }
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
                    // Fire and forget reconnect kalau perlu
                    if (!state.authenticated && state.paired && !state.connecting) {
                        log('autoFillList: not connected, triggering connect');
                        connect();
                    }
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
                        chrome.action.setBadgeText({ tabId: sender.tab.id, text: '\u25CF' });
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

// ============================================================
//  Startup
// ============================================================
log('background starting...');
loadState().then(() => {
    log('state loaded, paired=%s', state.paired);
    if (state.paired) {
        ensureAlarm();
        connect();
    }
}).catch(e => warn('loadState crashed', e));