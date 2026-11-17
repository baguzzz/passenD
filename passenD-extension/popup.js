// PassenD Extension - Popup (Fase 15)

const $ = (id) => document.getElementById(id);
const $setup = $('setupView'), $main = $('mainView'), $locked = $('lockedView'), $add = $('addView');

let lastGenerated = '';
let autoSubmit = false;
let editingCredId = null;
let customFieldsDraft = [];
let currentDetailCred = null;
let lastPassphrase = '';

// Wordlist 200 kata diceware (identik dengan PassphraseGenerator Android)
const PP_WORDS = ('abacus abbey about acorn actor adapt adult after again agent ' +
'agile agree ahead alarm album alert alien align alive allow ' +
'alpha altar amber amino ample angel anger angle angry ankle ' +
'apple apply apron arbor arena argue arise armor aroma array ' +
'arrow aside asset atlas audio audit avail avert awake award ' +
'aware axis azure badge bagel baker bamboo banjo basic basil ' +
'basin batch beach beard beast begin below bench berry blank ' +
'blaze blend blink block bloom board bonus boost booth bounce ' +
'bound brain brake brave bread break breed brick bride brief ' +
'bring brisk broad brook brush budget build bulk bunch bundle ' +
'burrow burst cabin cable cache cadet camel canal candy cannon ' +
'cargo carve catch cause cedar chain chair chalk charm chart ' +
'chase cheer chess chest chief child chill choice choir chord ' +
'cider cigar civic civil claim clash clasp class claw clay ' +
'clean clear clerk click cliff climb cling cloak clock close ' +
'cloth cloud clown coach coast cobra cocoa colon color combo ' +
'comet comic coral core corner couch cough count court cover ' +
'craft crane crash crate crawl crazy cream creek crest crew ' +
'crisp crop cross crowd crown crust cubic curly curve cycle').split(' ');

async function bg(type, extra = {}) {
    return new Promise((resolve) => {
        chrome.runtime.sendMessage({ type, ...extra }, (r) => {
            if (chrome.runtime.lastError) resolve({ ok: false, error: chrome.runtime.lastError.message });
            else resolve(r || { ok: false, error: 'no response' });
        });
    });
}
function parsePairingUrl(url) {
    if (!url.startsWith('passend://pair?')) throw new Error('URL tidak valid');
    const qs = url.substring('passend://pair?'.length);
    const r = {};
    for (const part of qs.split('&')) {
        const idx = part.indexOf('=');
        if (idx < 0) continue;
        const k = part.substring(0, idx), v = part.substring(idx + 1);
        if (k === 'token') r.token = v.replace(/ /g, '+');
        else try { r[k] = decodeURIComponent(v); } catch { r[k] = v; }
    }
    if (!r.ip || !r.port || !r.token) throw new Error('URL tidak lengkap');
    return r;
}
function showView(view) {
    [$setup, $main, $locked, $add].forEach(v => v.style.display = 'none');
    if (view) view.style.display = '';
}
function setStatus(connected, locked) {
    const $b = $('statusBadge');
    if (locked) { $b.textContent = 'Terkunci'; $b.className = 'badge off'; }
    else if (connected) { $b.textContent = 'Terhubung'; $b.className = 'badge on'; }
    else { $b.textContent = 'Terputus'; $b.className = 'badge off'; }
}
function escapeHtml(s) {
    return String(s == null ? '' : s).replace(/[&<>"']/g, ch => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[ch]));
}
async function updateVersionText() {
    const s = await bg('getStatus');
    const extVer = chrome.runtime.getManifest().version;
    const srvVer = s.serverVersion || 0;
    const $v = $('versionText');
    if (!$v) return;
    if (srvVer > 0) $v.textContent = `v${extVer} · srv ${srvVer}`;
    else $v.textContent = `v${extVer}`;
    if (srvVer > 0 && srvVer < 14) { $v.textContent += ' ⚠'; $v.style.color = '#c62828'; }
}
async function refreshStatusAndRoute() {
    const s = await bg('getStatus');
    if (!s.paired) { showView($setup); return; }
    if (s.locked) { showView($locked); return; }
    if (!s.connected) {
        await bg('connect');
        await new Promise(r => setTimeout(r, 600));
        const s2 = await bg('getStatus');
        if (s2.locked) { showView($locked); return; }
    }
    showView($main); setStatus(true, false);
    await updateVersionText(); await loadCreds();
}

document.querySelectorAll('.tab').forEach(t => {
    t.addEventListener('click', () => {
        document.querySelectorAll('.tab').forEach(x => x.classList.remove('active'));
        t.classList.add('active');
        const target = t.dataset.tab;
        $('tabVault').style.display = target === 'vault' ? '' : 'none';
        $('tabGen').style.display = target === 'gen' ? '' : 'none';
        $('tabSettings').style.display = target === 'settings' ? '' : 'none';
        if (target === 'gen' && !lastGenerated) regenerate();
    });
});
document.querySelectorAll('.sub-tab').forEach(t => {
    t.addEventListener('click', () => {
        document.querySelectorAll('.sub-tab').forEach(x => x.classList.remove('active'));
        t.classList.add('active');
        const sub = t.dataset.subtab;
        $('genPasswordView').style.display = sub === 'password' ? '' : 'none';
        $('genPassphraseView').style.display = sub === 'passphrase' ? '' : 'none';
        if (sub === 'passphrase' && !lastPassphrase) regeneratePassphrase();
    });
});

$('btnSaveConnect').addEventListener('click', async () => {
    $('setupError').textContent = '';
    let parsed;
    try { parsed = parsePairingUrl($('pairingUrl').value.trim()); }
    catch (e) { $('setupError').textContent = e.message; return; }
    $('btnSaveConnect').disabled = true;
    const r = await bg('pairAndConnect', { ip: parsed.ip, port: parsed.port, token: parsed.token });
    $('btnSaveConnect').disabled = false;
    if (!r.ok) { $('setupError').textContent = r.error || 'Gagal'; return; }
    for (let i = 0; i < 20; i++) { await new Promise(r => setTimeout(r, 200)); const s = await bg('getStatus'); if (s.connected) break; }
    const s = await bg('getStatus');
    if (!s.connected) { $('setupError').textContent = 'Auth gagal'; return; }
    await refreshStatusAndRoute();
});
$('btnScanQr').addEventListener('click', () => { chrome.tabs.create({ url: chrome.runtime.getURL('qr-scan.html') }); window.close(); });
$('btnUploadQr').addEventListener('click', () => {
    const input = document.createElement('input');
    input.type = 'file'; input.accept = 'image/*';
    input.onchange = async () => {
        const file = input.files && input.files[0]; if (!file) return;
        if (!('BarcodeDetector' in window)) { $('setupError').textContent = 'BarcodeDetector tidak didukung'; return; }
        try {
            const d = new BarcodeDetector({ formats: ['qr_code'] });
            const bm = await createImageBitmap(file);
            const codes = await d.detect(bm);
            if (codes.length === 0) { $('setupError').textContent = 'QR tidak terdeteksi'; return; }
            $('pairingUrl').value = codes[0].rawValue; $('setupError').textContent = '';
        } catch (e) { $('setupError').textContent = 'Gagal: ' + e.message; }
    };
    input.click();
});

async function getActiveTab() {
    return new Promise(r => chrome.tabs.query({ active: true, currentWindow: true }, tabs => r(tabs && tabs[0])));
}
async function getActiveTabDomain() {
    const t = await getActiveTab();
    if (!t) return null;
    try { return new URL(t.url).hostname.replace(/^www\./, ''); } catch { return null; }
}

async function loadCreds() {
    const $list = $('credList');
    $list.innerHTML = '<div class="empty">Memuat…</div>';
    const r = await bg('api', { action: 'list', payload: {} });
    if (!r.ok) {
        if (r.error && r.error.includes('vault is locked')) { showView($locked); return; }
        $list.innerHTML = `<div class="empty">Error: ${r.error || 'unknown'}</div>`;
        return;
    }
    const creds = r.data.credentials || [];
    if (creds.length === 0) { $list.innerHTML = '<div class="empty">Belum ada kredensial</div>'; updateHealthBar(creds); return; }
    const domain = await getActiveTabDomain();
    renderCreds(creds, domain);
}
function updateHealthBar(creds) {
    const $h = $('healthBar');
    if (!$h) return;
    if (!creds || creds.length === 0) { $h.style.display = 'none'; return; }
    const hashCounts = {};
    for (const c of creds) if (c.h) hashCounts[c.h] = (hashCounts[c.h] || 0) + 1;
    let weak = 0, reused = 0;
    for (const c of creds) {
        if ((c.strength || 0) <= 2) weak++;
        if (c.h && hashCounts[c.h] > 1) reused++;
    }
    const parts = [`${creds.length} kredensial`];
    if (weak > 0) parts.push(`🔴 ${weak} lemah`);
    if (reused > 0) parts.push(`🟠 ${reused} reuse`);
    if (weak === 0 && reused === 0) parts.push('✅ semua sehat');
    $h.textContent = parts.join('  ·  ');
    $h.style.display = '';
}
function renderCreds(creds, domain) {
    const $list = $('credList'); $list.innerHTML = '';
    updateHealthBar(creds);
    const hashCounts = {};
    for (const c of creds) if (c.h) hashCounts[c.h] = (hashCounts[c.h] || 0) + 1;
    const scored = creds.map(c => {
        const site = (c.site || '').toLowerCase();
        const match = domain && (site.includes(domain) || domain.includes(site));
        return { ...c, match };
    }).sort((a, b) => (b.match ? 1 : 0) - (a.match ? 1 : 0));
    for (const c of scored) {
        const div = document.createElement('div');
        div.className = 'item' + (c.match ? ' matched' : '');
        const info = document.createElement('div');
        info.className = 'info';
        const strength = (typeof c.strength === 'number') ? c.strength : 0;
        const reused = (c.h && hashCounts[c.h] > 1);
        const dotHtml = `<span class="strength-dot s${strength}" title="Strength: ${strength}/5"></span>`;
        const reuseHtml = reused ? `<span class="reuse-badge" title="Dipakai ${hashCounts[c.h]}×">REUSE</span>` : '';
        info.innerHTML = `<div class="site">${dotHtml}${escapeHtml(c.site)}${reuseHtml}</div>` +
                         `<div class="user">${escapeHtml(c.username || '')}</div>`;
        info.addEventListener('click', () => fillCred(c.id));
        div.appendChild(info);
        if ((c.fieldCount || 0) > 0) {
            const infoBtn = document.createElement('button');
            infoBtn.className = 'info-btn'; infoBtn.textContent = 'ⓘ';
            infoBtn.title = `${c.fieldCount} custom field`;
            infoBtn.addEventListener('click', (e) => { e.stopPropagation(); showCredDetail(c.id); });
            div.appendChild(infoBtn);
        }
        if (c.hasOtp) {
            const totpBtn = document.createElement('button');
            totpBtn.className = 'totp-btn'; totpBtn.textContent = 'TOTP';
            totpBtn.addEventListener('click', (e) => { e.stopPropagation(); copyTotp(c.id); });
            div.appendChild(totpBtn);
        }
        const del = document.createElement('button');
        del.className = 'del'; del.textContent = '✕'; del.title = 'Hapus';
        del.addEventListener('click', (e) => { e.stopPropagation(); deleteCred(c.id, c.site); });
        div.appendChild(del);
        $list.appendChild(div);
    }
}

async function fillCred(id) {
    $('mainError').textContent = '';
    const r = await bg('api', { action: 'get', payload: { id } });
    if (!r.ok) { $('mainError').textContent = r.error || 'gagal'; return; }
    const { username, password } = r.data;
    const tab = await getActiveTab();
    if (!tab) { $('mainError').textContent = 'Tidak ada tab'; return; }
    chrome.tabs.sendMessage(tab.id, { type: 'fill', username, password, autoSubmit }, (resp) => {
        if (chrome.runtime.lastError) { $('mainError').textContent = 'Reload halaman'; return; }
        if (resp && resp.ok) { window.close(); }
        else { $('mainError').textContent = (resp && resp.error) || 'Tidak ada form'; }
    });
}

async function showCredDetail(id) {
    const r = await bg('api', { action: 'get', payload: { id } });
    if (!r.ok) { alert(r.error || 'gagal'); return; }
    const cred = r.data;
    currentDetailCred = cred;
    const $overlay = $('detailOverlay');
    const $content = $('detailContent');
    let html = '';
    html += `<div style="font-weight:bold;font-size:15px;">${escapeHtml(cred.site)}</div>`;
    html += `<div style="color:var(--sub);font-size:11px;margin-bottom:10px;">${escapeHtml(cred.username || '')}</div>`;
    const fields = cred.customFields || [];
    if (fields.length > 0) {
        html += `<div style="border-top:1px solid var(--border);padding-top:8px;">`;
        for (let i = 0; i < fields.length; i++) {
            const f = fields[i];
            const display = f.sensitive ? '••••••••' : escapeHtml(f.value);
            html += `<div style="margin-bottom:10px;">`;
            html += `<div style="font-size:10px;color:var(--sub);text-transform:uppercase;letter-spacing:0.5px;">${escapeHtml(f.label || '(tanpa label)')}</div>`;
            html += `<div class="field-val" data-val="${escapeHtml(f.value)}">${display}</div>`;
            html += `</div>`;
        }
        html += `</div>`;
    } else {
        html += `<div style="color:var(--sub);font-size:12px;margin-top:10px;">Tidak ada custom fields.</div>`;
    }
    // NEW: Notes section
    if (cred.notes && cred.notes.trim()) {
        html += `<div style="margin-top:12px;padding-top:8px;border-top:1px solid var(--border);">`;
        html += `<div style="font-size:10px;color:var(--sub);text-transform:uppercase;letter-spacing:0.5px;">Catatan</div>`;
        html += `<div class="field-val" data-val="${escapeHtml(cred.notes)}">${escapeHtml(cred.notes)}</div>`;
        html += `</div>`;
    }
    $content.innerHTML = html;
    $content.querySelectorAll('.field-val').forEach(el => {
        el.addEventListener('click', async () => {
            const val = el.getAttribute('data-val') || '';
            try {
                await navigator.clipboard.writeText(val);
                const orig = el.textContent;
                el.textContent = '✓ Disalin';
                setTimeout(() => { el.textContent = orig; }, 1000);
            } catch (e) { alert('Gagal copy: ' + e.message); }
        });
    });
    $overlay.style.display = '';
}

$('btnDetailClose').addEventListener('click', () => { $('detailOverlay').style.display = 'none'; });
$('detailOverlay').addEventListener('click', (e) => { if (e.target === $('detailOverlay')) $('detailOverlay').style.display = 'none'; });
$('btnDetailEdit').addEventListener('click', async () => {
    if (!currentDetailCred) return;
    $('detailOverlay').style.display = 'none';
    await openAddView(currentDetailCred);
});

async function copyTotp(id) {
    const r = await bg('api', { action: 'totp', payload: { id } });
    if (!r.ok) { alert(r.error || 'gagal'); return; }
    const d = r.data;
    try {
        await navigator.clipboard.writeText(d.code);
        $('mainError').textContent = '';
        $('mainError').style.color = '#2e7d32';
        if (d.otpType === 'HOTP') {
            $('mainError').textContent = `HOTP ${d.code} disalin (counter ${d.counter} → ${d.nextCounter})`;
        } else {
            $('mainError').textContent = `TOTP ${d.code} disalin (${d.remaining}s)`;
        }
        setTimeout(() => { $('mainError').textContent = ''; $('mainError').style.color = ''; }, 2500);
    } catch (e) { alert('Kode: ' + d.code + ' (copy manual)'); }
}
async function deleteCred(id, site) {
    if (!confirm(`Hapus "${site}"?`)) return;
    const r = await bg('api', { action: 'delete', payload: { id } });
    if (!r.ok) { $('mainError').textContent = r.error || 'gagal'; return; }
    await loadCreds();
}

function renderCustomFieldsAdd() {
    const $cf = $('addCustomFields');
    $cf.innerHTML = '';
    customFieldsDraft.forEach((f, i) => {
        const row = document.createElement('div');
        row.className = 'cf-row';
        const labelIn = document.createElement('input');
        labelIn.className = 'cf-label'; labelIn.placeholder = 'Label'; labelIn.value = f.label || '';
        labelIn.addEventListener('input', e => { f.label = e.target.value; });
        const valueIn = document.createElement('input');
        valueIn.className = 'cf-value'; valueIn.placeholder = 'Value'; valueIn.value = f.value || '';
        valueIn.type = f.sensitive ? 'password' : 'text';
        valueIn.addEventListener('input', e => { f.value = e.target.value; });
        const sensLabel = document.createElement('label');
        sensLabel.className = 'cf-sens';
        const sensCb = document.createElement('input');
        sensCb.type = 'checkbox'; sensCb.checked = !!f.sensitive;
        sensCb.addEventListener('change', e => {
            f.sensitive = e.target.checked;
            valueIn.type = e.target.checked ? 'password' : 'text';
        });
        sensLabel.appendChild(sensCb);
        sensLabel.appendChild(document.createTextNode('S'));
        const delBtn = document.createElement('button');
        delBtn.className = 'cf-del'; delBtn.textContent = '✕';
        delBtn.addEventListener('click', () => { customFieldsDraft.splice(i, 1); renderCustomFieldsAdd(); });
        row.appendChild(labelIn); row.appendChild(valueIn);
        row.appendChild(sensLabel); row.appendChild(delBtn);
        $cf.appendChild(row);
    });
}
async function openAddView(credential) {
    editingCredId = credential ? credential.id : null;
    $('addTitle').textContent = editingCredId ? 'Edit Kredensial' : 'Tambah Kredensial';
    $('addSite').value = credential ? (credential.site || '') : '';
    $('addUsername').value = credential ? (credential.username || '') : '';
    $('addPassword').value = credential ? (credential.password || '') : '';
    $('addNotes').value = credential ? (credential.notes || '') : '';
    customFieldsDraft = [];
    if (credential && credential.customFields) {
        for (const f of credential.customFields) {
            customFieldsDraft.push({ label: f.label || '', value: f.value || '', sensitive: !!f.sensitive });
        }
    }
    renderCustomFieldsAdd();
    $('addError').textContent = '';
    if (!credential) {
        const d = await getActiveTabDomain();
        if (d) $('addSite').value = d;
    }
    showView($add);
    setTimeout(() => $('addSite').focus(), 50);
}
$('btnAddCustomField').addEventListener('click', () => {
    customFieldsDraft.push({ label: '', value: '', sensitive: false });
    renderCustomFieldsAdd();
    const rows = $('addCustomFields').querySelectorAll('.cf-label');
    if (rows.length > 0) rows[rows.length - 1].focus();
});
$('btnAdd').addEventListener('click', async () => { await openAddView(null); });
$('btnAddCancel').addEventListener('click', () => {
    editingCredId = null; customFieldsDraft = []; showView($main);
});
$('btnAddSave').addEventListener('click', async () => {
    const site = $('addSite').value.trim();
    const password = $('addPassword').value;
    if (!site || !password) { $('addError').textContent = 'Site & password wajib'; return; }
    const payload = {
        site,
        username: $('addUsername').value.trim(),
        password,
        notes: $('addNotes').value.trim(),
        customFields: customFieldsDraft
            .filter(f => (f.label || '').trim() || (f.value || '').trim())
            .map(f => ({ label: f.label || '', value: f.value || '', sensitive: !!f.sensitive }))
    };
    if (editingCredId) payload.id = editingCredId;
    $('btnAddSave').disabled = true;
    const r = await bg('api', { action: 'save', payload });
    $('btnAddSave').disabled = false;
    if (!r.ok) { $('addError').textContent = r.error || 'gagal'; return; }
    editingCredId = null; customFieldsDraft = [];
    showView($main); await loadCreds();
});

$('btnRefresh').addEventListener('click', loadCreds);

// ---------- Password generator ----------
function generatePassword(opts) {
    const sets = {
        lower: 'abcdefghijklmnopqrstuvwxyz', upper: 'ABCDEFGHIJKLMNOPQRSTUVWXYZ',
        digits: '0123456789', symbols: '!@#$%^&*()_+-=[]{}|;:,.<>?'
    };
    let pool = ''; const required = [];
    for (const k of ['lower','upper','digits','symbols']) if (opts[k]) { pool += sets[k]; required.push(sets[k]); }
    if (!pool) pool = sets.lower;
    const len = Math.max(4, Math.min(64, opts.length || 20));
    const bytes = new Uint8Array(len * 3); crypto.getRandomValues(bytes);
    let pwd = ''; let bi = 0;
    for (const set of required) pwd += set[bytes[bi++] % set.length];
    while (pwd.length < len) pwd += pool[bytes[bi++] % pool.length];
    const arr = pwd.split('');
    for (let i = arr.length - 1; i > 0; i--) { const j = bytes[bi++] % (i + 1); [arr[i], arr[j]] = [arr[j], arr[i]]; }
    return arr.join('');
}
function strength(pwd) {
    let s = 0;
    if (pwd.length >= 8) s++; if (pwd.length >= 12) s++; if (pwd.length >= 16) s++;
    if (/[a-z]/.test(pwd) && /[A-Z]/.test(pwd)) s++;
    if (/\d/.test(pwd)) s++; if (/[^a-zA-Z0-9]/.test(pwd)) s++;
    return Math.min(5, s);
}
function regenerate() {
    lastGenerated = generatePassword({
        length: parseInt($('genLen').value, 10),
        lower: $('genLower').checked, upper: $('genUpper').checked,
        digits: $('genDigits').checked, symbols: $('genSymbols').checked
    });
    $('genPassword').textContent = lastGenerated;
    $('genLenLabel').textContent = $('genLen').value;
    $('genStrengthBar').className = 'bar s' + strength(lastGenerated);
}
$('btnGenRegen').addEventListener('click', regenerate);
$('genLen').addEventListener('input', regenerate);
['genLower','genUpper','genDigits','genSymbols'].forEach(id => $(id).addEventListener('change', regenerate));
$('btnGenCopy').addEventListener('click', async () => {
    if (!lastGenerated) return;
    await navigator.clipboard.writeText(lastGenerated);
    $('btnGenCopy').textContent = '✓ Tersalin';
    setTimeout(() => { $('btnGenCopy').textContent = 'Salin'; }, 1500);
});
$('btnGenSave').addEventListener('click', async () => {
    if (!lastGenerated) return;
    await openAddView(null);
    $('addPassword').value = lastGenerated;
});

// ---------- Passphrase generator (Fase 15) ----------
function generatePassphrase(opts) {
    const count = Math.max(3, Math.min(12, opts.wordCount || 6));
    const sep = opts.separator || '-';
    const capitalize = opts.capitalize !== false;
    const appendDigit = !!opts.appendDigit;
    const bytes = new Uint8Array(count * 2 + 1);
    crypto.getRandomValues(bytes);
    const words = [];
    for (let i = 0; i < count; i++) {
        const idx = ((bytes[i * 2] << 8) | bytes[i * 2 + 1]) % PP_WORDS.length;
        let w = PP_WORDS[idx];
        if (capitalize) w = w.charAt(0).toUpperCase() + w.slice(1);
        words.push(w);
    }
    let result = words.join(sep);
    if (appendDigit) result += (bytes[count * 2] % 10);
    return result;
}
function passphraseStrength(wordCount, appendDigit) {
    const bitsPerWord = Math.log2(PP_WORDS.length);
    const bits = wordCount * bitsPerWord + (appendDigit ? 3.3 : 0);
    let level, label;
    if (bits < 40) { level = 2; label = 'LEMAH'; }
    else if (bits < 50) { level = 3; label = 'SEDANG'; }
    else if (bits < 60) { level = 4; label = 'KUAT'; }
    else { level = 5; label = 'SANGAT KUAT'; }
    return { level, label, bits };
}
function regeneratePassphrase() {
    const count = parseInt($('ppWordCount').value, 10);
    const sep = $('ppSeparator').value || '-';
    const cap = $('ppCapitalize').checked;
    const digit = $('ppAppendDigit').checked;
    lastPassphrase = generatePassphrase({ wordCount: count, separator: sep, capitalize: cap, appendDigit: digit });
    $('ppOutput').textContent = lastPassphrase;
    $('ppWordCountLabel').textContent = count + ' kata';
    const s = passphraseStrength(count, digit);
    $('ppStrengthBar').className = 'bar s' + s.level;
    $('ppStats').textContent = `${count} kata · ~${Math.round(s.bits)} bit · ${s.label}`;
}
$('btnPpRegen').addEventListener('click', regeneratePassphrase);
$('ppWordCount').addEventListener('input', regeneratePassphrase);
$('ppSeparator').addEventListener('input', regeneratePassphrase);
$('ppCapitalize').addEventListener('change', regeneratePassphrase);
$('ppAppendDigit').addEventListener('change', regeneratePassphrase);
$('btnPpCopy').addEventListener('click', async () => {
    if (!lastPassphrase) return;
    await navigator.clipboard.writeText(lastPassphrase);
    $('btnPpCopy').textContent = '✓ Tersalin';
    setTimeout(() => { $('btnPpCopy').textContent = 'Salin'; }, 1500);
});
$('btnPpSave').addEventListener('click', async () => {
    if (!lastPassphrase) return;
    await openAddView(null);
    $('addPassword').value = lastPassphrase;
});

// ---------- Settings / Locked / Push / Init ----------
async function loadSettings() {
    const data = await chrome.storage.local.get(['autoSubmit', 'darkMode']);
    autoSubmit = !!data.autoSubmit;
    $('optAutoSubmit').checked = autoSubmit;
    const dark = !!data.darkMode;
    $('optDarkMode').checked = dark;
    document.body.classList.toggle('dark', dark);
}
$('optAutoSubmit').addEventListener('change', async () => {
    autoSubmit = $('optAutoSubmit').checked;
    await chrome.storage.local.set({ autoSubmit });
});
$('optDarkMode').addEventListener('change', async () => {
    const dark = $('optDarkMode').checked;
    document.body.classList.toggle('dark', dark);
    await chrome.storage.local.set({ darkMode: dark });
});
$('btnDisconnect').addEventListener('click', async () => { await bg('disconnect'); setStatus(false, false); });
$('btnUnpair').addEventListener('click', async () => {
    if (!confirm('Unpair?')) return;
    await bg('unpair'); showView($setup); $('pairingUrl').value = '';
});
$('btnRetryUnlock').addEventListener('click', async () => {
    $('lockedError').textContent = '';
    const r = await bg('api', { action: 'ping', payload: {} });
    if (r.ok) await refreshStatusAndRoute(); else $('lockedError').textContent = r.error || 'Masih terkunci';
});
$('btnUnpairFromLocked').addEventListener('click', async () => {
    if (!confirm('Unpair?')) return;
    await bg('unpair'); showView($setup);
});

chrome.runtime.onMessage.addListener((msg) => {
    if (msg.type === 'locked') showView($locked);
    if (msg.type === 'unlocked') refreshStatusAndRoute();
    if (msg.type === 'connected') { updateVersionText(); if ($main.style.display !== 'none') loadCreds(); }
    if (msg.type === 'disconnected') setStatus(false, false);
});


// Fase 16: template picker di add view
$('btnAddFromTemplate').addEventListener('click', async () => {
    const r = await bg('api', { action: 'templates.list', payload: {} });
    if (!r.ok) { $('addError').textContent = r.error || 'Gagal ambil template'; return; }
    const templates = (r.data && r.data.templates) || [];
    if (templates.length === 0) { $('addError').textContent = 'Belum ada template. Buat di Pengaturan Android.'; return; }
    const names = templates.map(t => t.name);
    const choice = prompt('Pilih template:\n' + names.map((n, i) => `${i + 1}. ${n}`).join('\n') + '\n\nKetik nomor:');
    if (!choice) return;
    const idx = parseInt(choice, 10) - 1;
    if (idx < 0 || idx >= templates.length) return;
    const t = templates[idx];
    for (const f of (t.fields || [])) {
        customFieldsDraft.push({ label: f.label || '', value: f.value || '', sensitive: !!f.sensitive });
    }
    renderCustomFieldsAdd();
});
(async () => { await loadSettings(); await updateVersionText(); await refreshStatusAndRoute(); })();