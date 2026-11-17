// PassenD Extension - Content (Firefox-compatible, AMO lint clean)

function isVisible(el) {
    if (!el || el.disabled) return false;
    const rect = el.getBoundingClientRect();
    return rect.width > 0 && rect.height > 0 && el.offsetParent !== null;
}
function findPasswordInputs() {
    return Array.from(document.querySelectorAll('input[type="password"]')).filter(isVisible);
}
function findUsernameInput(pwdInput) {
    const form = pwdInput.form || pwdInput.closest('form');
    if (form) {
        const sels = [
            'input[type="email"]','input[type="text"]','input[type="tel"]',
            'input[name*="user" i]','input[name*="login" i]','input[name*="email" i]',
            'input[id*="user" i]','input[id*="login" i]','input[id*="email" i]',
            'input[autocomplete*="username"]','input[autocomplete*="email"]'
        ];
        for (const sel of sels) {
            for (const el of form.querySelectorAll(sel)) {
                if (el !== pwdInput && isVisible(el)) return el;
            }
        }
    }
    let el = pwdInput.previousElementSibling;
    while (el) { if (el.tagName === 'INPUT' && isVisible(el)) return el; el = el.previousElementSibling; }
    for (const el of document.querySelectorAll('input[type="text"], input[type="email"]')) {
        if (isVisible(el)) return el;
    }
    return null;
}
function setNativeValue(el, value) {
    const proto = Object.getPrototypeOf(el);
    const desc = Object.getOwnPropertyDescriptor(proto, 'value');
    if (desc && desc.set) desc.set.call(el, value); else el.value = value;
    el.dispatchEvent(new Event('input', { bubbles: true }));
    el.dispatchEvent(new Event('change', { bubbles: true }));
    el.dispatchEvent(new KeyboardEvent('keyup', { bubbles: true }));
}
function trySubmit(pwdInput) {
    const form = pwdInput.form || pwdInput.closest('form');
    if (form) {
        const btn = form.querySelector('button[type="submit"], input[type="submit"]');
        if (btn) { btn.click(); return true; }
        if (typeof form.requestSubmit === 'function') { form.requestSubmit(); return true; }
        try { form.submit(); return true; } catch (e) {}
    }
    return false;
}
function fillForm(username, password, autoSubmit) {
    const pwds = findPasswordInputs();
    if (pwds.length === 0) return { ok: false, error: 'no password field' };
    let filled = 0;
    for (const pwd of pwds) {
        try {
            const userEl = findUsernameInput(pwd);
            if (userEl) setNativeValue(userEl, username);
            setNativeValue(pwd, password);
            filled++;
        } catch (e) {}
    }
    if (filled > 0 && autoSubmit) {
        setTimeout(() => { trySubmit(pwds[0]); }, 400);
    }
    return filled > 0 ? { ok: true, filled } : { ok: false, error: 'fill failed' };
}

// ---------- Inline button ----------
let fillBtn = null, fillMenu = null, activePwd = null;

function removeUI() {
    if (fillBtn) { fillBtn.remove(); fillBtn = null; }
    if (fillMenu) { fillMenu.remove(); fillMenu = null; }
}
function positionBtn(pwd) {
    if (!fillBtn) return;
    const r = pwd.getBoundingClientRect();
    fillBtn.style.top = Math.max(4, r.top - 34) + 'px';
    fillBtn.style.left = r.left + 'px';
}
function showFillButton(pwd) {
    removeUI(); activePwd = pwd;
    fillBtn = document.createElement('div');
    fillBtn.id = 'passend-fill-btn';
    fillBtn.textContent = '\uD83D\uDD11 PassenD';
    fillBtn.addEventListener('mousedown', e => e.preventDefault());
    fillBtn.addEventListener('click', async (e) => {
        e.preventDefault(); e.stopPropagation();
        await showFillMenu(pwd);
    });
    document.body.appendChild(fillBtn);
    positionBtn(pwd);
}
function makeEmptyItem(text) {
    const d = document.createElement('div');
    d.className = 'item empty';
    d.textContent = text;
    return d;
}
function makeMenuItem(site, username) {
    const it = document.createElement('div');
    it.className = 'item';
    const s = document.createElement('div');
    s.className = 'site';
    s.textContent = site;
    const u = document.createElement('div');
    u.className = 'user';
    u.textContent = username || '';
    it.appendChild(s);
    it.appendChild(u);
    return it;
}
async function showFillMenu(pwd) {
    if (fillMenu) { fillMenu.remove(); fillMenu = null; }
    const domain = location.hostname.replace(/^www\./, '');
    const resp = await new Promise(resolve => {
        chrome.runtime.sendMessage({ type: 'autoFillList', domain }, resolve);
    });
    fillMenu = document.createElement('div');
    fillMenu.id = 'passend-fill-menu';
    if (!resp || !resp.ok) {
        const msg = (resp && resp.error === 'locked')
            ? 'Vault terkunci. Buka PassenD di HP.'
            : 'Tidak terhubung ke PassenD.';
        fillMenu.appendChild(makeEmptyItem(msg));
        positionMenu(pwd);
        document.body.appendChild(fillMenu);
        return;
    }
    if (!resp.list || resp.list.length === 0) {
        fillMenu.appendChild(makeEmptyItem('Tidak ada kredensial untuk domain ini.'));
        positionMenu(pwd);
        document.body.appendChild(fillMenu);
        return;
    }
    for (const c of resp.list) {
        const it = makeMenuItem(c.site, c.username);
        it.addEventListener('mousedown', e => e.preventDefault());
        it.addEventListener('click', async (e) => {
            e.preventDefault(); e.stopPropagation();
            await fillFromCred(c.id, pwd);
        });
        fillMenu.appendChild(it);
    }
    positionMenu(pwd);
    document.body.appendChild(fillMenu);
}
function positionMenu(pwd) {
    if (!fillMenu) return;
    const r = pwd.getBoundingClientRect();
    fillMenu.style.top = (r.bottom + 4) + 'px';
    fillMenu.style.left = r.left + 'px';
}
async function fillFromCred(id, pwd) {
    const resp = await new Promise(resolve => {
        chrome.runtime.sendMessage({ type: 'autoFillGet', id }, resolve);
    });
    if (resp && resp.ok && resp.credential) {
        const userEl = findUsernameInput(pwd);
        if (userEl) setNativeValue(userEl, resp.credential.username);
        setNativeValue(pwd, resp.credential.password);
    }
    removeUI();
}

document.addEventListener('focusin', e => {
    if (e.target.matches('input[type="password"]')) showFillButton(e.target);
});
document.addEventListener('focusout', e => {
    if (e.target.matches('input[type="password"]')) {
        setTimeout(() => {
            if (!document.activeElement || !document.activeElement.closest('#passend-fill-btn, #passend-fill-menu')) removeUI();
        }, 200);
    }
});
window.addEventListener('scroll', () => { if (activePwd && fillBtn) positionBtn(activePwd); }, true);
window.addEventListener('resize', () => { if (activePwd && fillBtn) positionBtn(activePwd); });

chrome.runtime.onMessage.addListener((msg, sender, sendResponse) => {
    if (msg.type === 'fill') {
        sendResponse(fillForm(msg.username, msg.password, !!msg.autoSubmit));
        return true;
    }
    if (msg.type === 'detectForm') {
        sendResponse({ ok: true, hasForm: findPasswordInputs().length > 0 });
        return true;
    }
});

setTimeout(() => {
    try {
        if (findPasswordInputs().length > 0) {
            chrome.runtime.sendMessage({ type: 'formDetected' }, () => { void chrome.runtime.lastError; });
        }
    } catch (e) {}
}, 1000);