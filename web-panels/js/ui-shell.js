/**
 * REPLATE UI shell: sidebar, toasts, confirm dialog, safe HTML helpers, role guard.
 * Loaded on every page. Everything is on the global ReplateUI object.
 */

const ReplateUI = (() => {

    // ── Safe HTML helpers ───────────────────────────────────────────────────
    const ESCAPES = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' };

    /** Escape any value for use inside HTML text or a quoted attribute. */
    function esc(value) {
        if (value === null || value === undefined) return '';
        return String(value).replace(/[&<>"']/g, (c) => ESCAPES[c]);
    }

    /** Only http(s) links may be used as href/src. Anything else (javascript:, data:) becomes ''. */
    function safeUrl(url) {
        if (!url) return '';
        try {
            const u = new URL(String(url), window.location.href);
            return (u.protocol === 'https:' || u.protocol === 'http:') ? u.href : '';
        } catch (e) {
            return '';
        }
    }

    // ── Toasts ──────────────────────────────────────────────────────────────
    function toastRegion() {
        let region = document.getElementById('toastRegion');
        if (!region) {
            region = document.createElement('div');
            region.id = 'toastRegion';
            region.className = 'toast-region';
            region.setAttribute('role', 'status');
            region.setAttribute('aria-live', 'polite');
            document.body.appendChild(region);
        }
        return region;
    }

    const TOAST_ICONS = {
        success: 'fa-circle-check',
        danger: 'fa-circle-exclamation',
        warning: 'fa-triangle-exclamation',
        info: 'fa-circle-info'
    };

    /** Small message that is always visible (fixed position) and goes away by itself. */
    function toast(message, type = 'info', duration = 4500) {
        const kind = TOAST_ICONS[type] ? type : 'info';
        const el = document.createElement('div');
        el.className = 'toast-item toast-' + kind;
        const icon = document.createElement('i');
        icon.className = 'fas ' + TOAST_ICONS[kind];
        icon.setAttribute('aria-hidden', 'true');
        const text = document.createElement('span');
        text.textContent = message;
        const close = document.createElement('button');
        close.type = 'button';
        close.className = 'toast-close';
        close.setAttribute('aria-label', 'Dismiss');
        close.innerHTML = '<span aria-hidden="true">×</span>';
        el.append(icon, text, close);
        toastRegion().appendChild(el);

        const remove = () => {
            el.classList.add('leaving');
            setTimeout(() => el.remove(), 200);
        };
        close.addEventListener('click', remove);
        if (duration > 0) setTimeout(remove, duration);
        return el;
    }

    /** Show a message on the NEXT page (used right before redirecting). */
    function flash(message, type = 'info') {
        try { sessionStorage.setItem('replate.flash', JSON.stringify({ message, type })); } catch (e) { /* storage blocked */ }
    }

    // Shows the message left by flash on the new page.
    function showPendingFlash() {
        try {
            const raw = sessionStorage.getItem('replate.flash');
            if (!raw) return;
            sessionStorage.removeItem('replate.flash');
            const { message, type } = JSON.parse(raw);
            if (message) toast(message, type || 'info');
        } catch (e) { /* ignore */ }
    }
    document.addEventListener('DOMContentLoaded', showPendingFlash);

    /** Kept for older pages: now a toast, so it is never scrolled out of sight. */
    function showAlert(message, type = 'info') {
        return toast(message, type);
    }

    // ── Confirm / prompt dialog (replaces window.confirm / window.prompt) ───
    function openDialog({ title, message, confirmText = 'Confirm', cancelText = 'Cancel', danger = false, input = null }) {
        return new Promise((resolve) => {
            const previouslyFocused = document.activeElement;
            const overlay = document.createElement('div');
            overlay.className = 'rp-dialog-overlay';

            const box = document.createElement('div');
            box.className = 'rp-dialog';
            box.setAttribute('role', 'dialog');
            box.setAttribute('aria-modal', 'true');

            const heading = document.createElement('h2');
            heading.className = 'rp-dialog-title';
            heading.id = 'rpDialogTitle';
            heading.textContent = title || 'Please confirm';
            box.setAttribute('aria-labelledby', 'rpDialogTitle');

            const body = document.createElement('p');
            body.className = 'rp-dialog-body';
            body.textContent = message || '';

            let field = null;
            let fieldLabel = null;
            if (input) {
                field = document.createElement(input.multiline ? 'textarea' : 'input');
                field.className = 'form-control rp-dialog-input';
                field.id = 'rpDialogInput';
                field.name = 'rpDialogInput';
                field.autocomplete = 'off';
                if (!input.multiline) field.type = 'text';
                field.placeholder = input.placeholder || '';
                if (input.multiline) field.rows = 3;
                fieldLabel = document.createElement('label');
                fieldLabel.className = 'form-label';
                fieldLabel.htmlFor = field.id;
                fieldLabel.textContent = input.label || title || 'Value';
            }

            const actions = document.createElement('div');
            actions.className = 'rp-dialog-actions';
            const cancel = document.createElement('button');
            cancel.type = 'button';
            cancel.className = 'btn btn-outline-secondary';
            cancel.textContent = cancelText;
            const ok = document.createElement('button');
            ok.type = 'button';
            ok.className = 'btn ' + (danger ? 'btn-danger' : 'btn-primary');
            ok.textContent = confirmText;
            actions.append(cancel, ok);

            box.append(heading, body);
            if (field) box.append(fieldLabel, field);
            box.append(actions);
            overlay.appendChild(box);
            document.body.appendChild(overlay);
            document.body.style.overflow = 'hidden';

            const close = (result) => {
                document.removeEventListener('keydown', onKey, true);
                overlay.remove();
                document.body.style.overflow = '';
                if (previouslyFocused && previouslyFocused.focus) previouslyFocused.focus();
                resolve(result);
            };
            const accept = () => close(field ? field.value.trim() : true);
            const reject = () => close(field ? null : false);

            function onKey(e) {
                if (e.key === 'Escape') { e.preventDefault(); reject(); }
                else if (e.key === 'Enter' && !(e.target && e.target.tagName === 'TEXTAREA')) { e.preventDefault(); accept(); }
                else if (e.key === 'Tab') {
                    // keep focus inside the dialog
                    const focusable = box.querySelectorAll('button, input, textarea');
                    const first = focusable[0], last = focusable[focusable.length - 1];
                    if (e.shiftKey && document.activeElement === first) { e.preventDefault(); last.focus(); }
                    else if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first.focus(); }
                }
            }
            document.addEventListener('keydown', onKey, true);
            cancel.addEventListener('click', reject);
            ok.addEventListener('click', accept);
            overlay.addEventListener('mousedown', (e) => { if (e.target === overlay) reject(); });
            (field || ok).focus();
        });
    }

    /** confirmAction('Delete this?', {title, confirmText, danger}) -> Promise<boolean> */
    function confirmAction(message, opts = {}) {
        return openDialog({ message, ...opts });
    }

    /** askText('Reason for rejection', {placeholder}) -> Promise<string|null> (null = cancelled) */
    function askText(message, opts = {}) {
        const { label, placeholder, multiline, ...rest } = opts;
        return openDialog({ message, input: { placeholder, label, multiline: !!multiline }, ...rest });
    }

    // ── Role guard ──────────────────────────────────────────────────────────
    const ADMIN_EMAILS = ['admin@replate.com', 'superadmin@replate.com'];

    /**
     * Resolve with the signed-in user if they hold the given role ('admin' | 'hotel'), otherwise send
     * them to the right login page. This only hides the UI; the database rules are what really protect data.
     */
    async function requireRole(role, loginPage = 'login.html') {
        const user = await new Promise((resolve) => {
            const stop = auth.onAuthStateChanged((u) => { stop(); resolve(u); });
        });
        if (!user) { window.location.replace(loginPage); return null; }
        let reason = 'This account does not have ' + (role === 'admin' ? 'admin' : 'hotel') + ' access.';
        try {
            const snap = await database.ref('users/' + user.uid).once('value');
            const profile = snap.val() || {};
            const value = String(profile.role || profile.userType || '').toLowerCase();
            const isAdmin = value === 'admin' && ADMIN_EMAILS.includes((user.email || '').toLowerCase());
            if ((role === 'admin' && isAdmin) || (role === 'hotel' && value === 'hotel')) return user;
            if (!snap.exists()) {
                reason = 'No REPLATE profile was found for this account (users/' + user.uid + ').';
            } else if (role === 'admin' && value === 'admin') {
                reason = 'This email is not on the admin list.';
            } else {
                reason = 'The role on this account is "' + (value || 'not set') + '", not ' + role + '.';
            }
        } catch (e) {
            console.error('Role check failed', e);
            if (e && e.code !== 'PERMISSION_DENIED' && e.code !== 'permission-denied') {
                // Probably a connection problem: do not sign anyone out for that, offer a retry instead
                toast('Could not check your account. Check your connection and reload the page.', 'danger', 0);
                return null;
            }
            reason = 'Could not read your profile (' + (e && (e.code || e.message) || 'unknown error') + '). Check your connection and the database rules.';
        }
        flash(reason + ' You have been signed out.', 'danger');
        await auth.signOut();
        window.location.replace(loginPage);
        return null;
    }

    /**
     * For hotel pages that manage orders, listings or chats. Resolves with the hotel record
     * ({ key, value }) once an admin has approved it. Otherwise the owner is sent back to the
     * dashboard with a message that says why, and the promise resolves with null.
     * (This hides the UI; the database rules are what really stop an unapproved hotel.)
     */
    async function requireApprovedHotel(user, homePage = 'dashboard.html') {
        const messages = {
            pending: 'Your hotel is still waiting for admin approval.',
            rejected: 'Your hotel registration was not approved. Contact REPLATE support for details.',
            suspended: 'Your hotel is suspended. Contact REPLATE support.'
        };
        let record = null;
        try {
            record = await ReplateSchema.findHotelByUserId(database, user.uid);
        } catch (e) {
            console.error('Could not check the hotel status', e);
        }
        const status = record && record.value ? ReplateSchema.normalizeHotelStatus(record.value.status) : 'pending';
        if (record && status === 'approved') return record;

        flash(record ? (messages[status] || messages.pending) : 'Hotel profile not found. Please register first.', 'warning');
        window.location.replace(record ? homePage : 'register.html');
        return null;
    }

    // ── Sidebar ─────────────────────────────────────────────────────────────
    let sidebarBound = false;

    // Works out which sidebar link matches this page.
    function currentPageKey(links) {
        const file = window.location.pathname.split('/').pop() || '';
        const hit = links.find((l) => l.href === file);
        return hit ? hit.key : '';
    }

    /**
     * Render the sidebar nav into the element with the given id and wire up the mobile toggle.
     * Safe to call more than once (the page calls it again after sign-in).
     */
    function renderSidebar(containerId, opts) {
        const container = document.getElementById(containerId);
        if (!container) return;

        const logoTitle = '<img src="../assets/replate-icon.svg" alt="" class="brand-logo-sm">REPLATE';
        const { title = logoTitle, links = [] } = opts;
        const activeKey = opts.activeKey || currentPageKey(links);

        const items = links.map((link) => {
            const active = link.key === activeKey;
            const attrs = (active ? ' aria-current="page"' : '') + (link.logout ? ' data-logout="true"' : '');
            return `<li class="nav-item">
                <a class="nav-link${active ? ' active' : ''}" href="${esc(link.href)}"${attrs}>
                    <i class="${esc(link.icon)}" aria-hidden="true"></i><span>${esc(link.label)}</span>
                </a>
            </li>`;
        }).join('');

        container.innerHTML = `
            <div class="position-sticky">
                <div class="sidebar-head">
                    <a class="navbar-brand" href="#">${title}</a>
                    <button type="button" class="sidebar-close" aria-label="Close menu"><i class="fas fa-xmark" aria-hidden="true"></i></button>
                </div>
                <nav aria-label="Main">
                    <ul class="nav flex-column">${items}</ul>
                </nav>
            </div>`;
        container.dataset.shellReady = 'true';

        if (sidebarBound) return;
        sidebarBound = true;

        // Phones and tablets: a top bar with the menu button and the page name (the sidebar becomes a drawer)
        const appBar = document.createElement('header');
        appBar.className = 'app-bar';
        const toggle = document.createElement('button');
        toggle.type = 'button';
        toggle.className = 'sidebar-toggle';
        toggle.setAttribute('aria-label', 'Open menu');
        toggle.setAttribute('aria-expanded', 'false');
        toggle.setAttribute('aria-controls', containerId);
        toggle.innerHTML = '<i class="fas fa-bars" aria-hidden="true"></i>';
        const pageName = document.createElement('span');
        pageName.className = 'app-bar-title';
        const heading = document.querySelector('main h1');
        pageName.textContent = heading ? heading.textContent.trim() : document.title.replace(/\s+-\s+REPLATE.*$/, '');
        appBar.append(toggle, pageName);
        const skip = document.querySelector('.skip-link');
        if (skip) skip.after(appBar); else document.body.prepend(appBar);

        const overlay = document.createElement('div');
        overlay.className = 'sidebar-overlay';
        document.body.appendChild(overlay);

        // On narrow screens the sidebar is a drawer: while it is closed it must not take keyboard focus
        const drawerQuery = window.matchMedia('(max-width: 991.98px)');
        const syncDrawer = () => {
            const hidden = drawerQuery.matches && !container.classList.contains('show');
            container.inert = hidden;
            if (hidden) container.setAttribute('aria-hidden', 'true');
            else container.removeAttribute('aria-hidden');
        };

        const setOpen = (open) => {
            container.classList.toggle('show', open);
            overlay.classList.toggle('show', open);
            toggle.setAttribute('aria-expanded', String(open));
            toggle.setAttribute('aria-label', open ? 'Close menu' : 'Open menu');
            document.body.style.overflow = open ? 'hidden' : '';
            syncDrawer();
            if (open) {
                const first = container.querySelector('.nav-link');
                if (first) first.focus();
            }
        };
        syncDrawer();
        if (drawerQuery.addEventListener) drawerQuery.addEventListener('change', syncDrawer);

        toggle.addEventListener('click', () => setOpen(!container.classList.contains('show')));
        overlay.addEventListener('click', () => { setOpen(false); toggle.focus(); });
        document.addEventListener('keydown', (e) => {
            if (e.key === 'Escape' && container.classList.contains('show')) { setOpen(false); toggle.focus(); }
        });
        window.addEventListener('resize', () => { if (window.innerWidth > 991) setOpen(false); });

        // One delegated listener survives every re-render of the sidebar
        container.addEventListener('click', (e) => {
            if (e.target.closest('.sidebar-close')) { setOpen(false); toggle.focus(); return; }
            const link = e.target.closest('.nav-link');
            if (!link) return;
            if (link.dataset.logout === 'true') {
                e.preventDefault();
                if (typeof logout === 'function') logout();
            }
            if (window.innerWidth <= 991) setOpen(false);
        });
    }

    /** Paint the sidebar as soon as the page loads, before Firebase has answered, so the rail never flashes empty. */
    function autoRenderSidebar() {
        const container = document.getElementById('appShellSidebar');
        if (!container || container.dataset.shellReady) return;
        let links = null;
        let title;
        if (typeof hotelShellLinks !== 'undefined') {
            links = hotelShellLinks;
        } else if (typeof adminShellLinks !== 'undefined') {
            links = adminShellLinks;
            title = '<img src="../assets/replate-icon.svg" alt="" class="brand-logo-sm">ADMIN';
        }
        if (links) renderSidebar('appShellSidebar', { links, title });
    }
    document.addEventListener('DOMContentLoaded', autoRenderSidebar);

    // ── Photos ──────────────────────────────────────────────────────────────
    /**
     * Shrinks a photo in the browser before upload: longest side at most maxSide px, re-encoded as
     * JPEG. Phone photos are often 4-8 MB; this makes them a few hundred KB, so the app loads
     * listings faster on mobile data. Resolves with the original file if anything goes wrong or the
     * result would not be smaller.
     */
    function compressImage(file, { maxSide = 1280, quality = 0.82 } = {}) {
        return new Promise((resolve) => {
            if (!file || !/^image\/(jpeg|png|webp)$/.test(file.type)) { resolve(file); return; }
            const url = URL.createObjectURL(file);
            const img = new Image();
            img.onload = () => {
                try {
                    const scale = Math.min(1, maxSide / Math.max(img.width, img.height));
                    const canvas = document.createElement('canvas');
                    canvas.width = Math.max(1, Math.round(img.width * scale));
                    canvas.height = Math.max(1, Math.round(img.height * scale));
                    const ctx = canvas.getContext('2d');
                    ctx.fillStyle = '#ffffff'; // PNG transparency would turn black in a JPEG
                    ctx.fillRect(0, 0, canvas.width, canvas.height);
                    ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
                    canvas.toBlob((blob) => {
                        URL.revokeObjectURL(url);
                        if (!blob || blob.size >= file.size) { resolve(file); return; }
                        const name = file.name.replace(/\.[^.]+$/, '') + '.jpg';
                        resolve(new File([blob], name, { type: 'image/jpeg' }));
                    }, 'image/jpeg', quality);
                } catch (e) {
                    URL.revokeObjectURL(url);
                    resolve(file);
                }
            };
            img.onerror = () => { URL.revokeObjectURL(url); resolve(file); };
            img.src = url;
        });
    }

    // ── Small form helpers ──────────────────────────────────────────────────
    function attachPasswordToggle(inputId, toggleBtnId, iconId) {
        const input = document.getElementById(inputId);
        const toggleBtn = document.getElementById(toggleBtnId);
        const icon = document.getElementById(iconId);
        if (!input || !toggleBtn) return;

        toggleBtn.addEventListener('click', () => {
            const show = input.type === 'password';
            input.type = show ? 'text' : 'password';
            toggleBtn.setAttribute('aria-label', show ? 'Hide password' : 'Show password');
            if (icon) {
                icon.classList.toggle('fa-eye', !show);
                icon.classList.toggle('fa-eye-slash', show);
            }
        });
    }

    // Disables a button and shows a waiting label while it works.
    function setButtonLoading(buttonId, isLoading, loadingText = 'Please wait…') {
        const btn = document.getElementById(buttonId);
        if (!btn) return;
        if (isLoading) {
            if (!btn.dataset.originalText) btn.dataset.originalText = btn.innerHTML;
            btn.disabled = true;
            // an icon-only button keeps its size: spinner only, the words go to screen readers
            btn.innerHTML = btn.classList.contains('btn-icon')
                ? `<span class="spinner-border spinner-border-sm" aria-hidden="true"></span><span class="visually-hidden">${esc(loadingText)}</span>`
                : `<span class="spinner-border spinner-border-sm" aria-hidden="true"></span>${esc(loadingText)}`;
        } else {
            btn.disabled = false;
            if (btn.dataset.originalText) btn.innerHTML = btn.dataset.originalText;
        }
    }

    // ── Inline field errors ─────────────────────────────────────────────────
    const byId = (target) => (typeof target === 'string' ? document.getElementById(target) : target);

    // Where the message goes: under the input group, inside a checkbox row, or right after the field
    function feedbackAnchor(el) {
        if (el.type === 'checkbox' && el.closest('.form-check')) return el.closest('.form-check').lastElementChild;
        return el.closest('.input-with-icon') || el;
    }

    /** Mark one field invalid and show the message under it. Returns the field. */
    function setFieldError(target, message) {
        const el = byId(target);
        if (!el) return null;
        el.classList.add('is-invalid');
        el.setAttribute('aria-invalid', 'true');
        const id = (el.id || 'field') + 'Error';
        let note = document.getElementById(id);
        if (!note) {
            note = document.createElement('div');
            note.id = id;
            note.className = 'invalid-feedback d-block';
            feedbackAnchor(el).insertAdjacentElement('afterend', note);
        }
        note.textContent = message;
        const described = (el.getAttribute('aria-describedby') || '').split(/\s+/).filter(Boolean);
        if (!described.includes(id)) described.push(id);
        el.setAttribute('aria-describedby', described.join(' '));
        return el;
    }

    /** Remove the error from one field. */
    function clearFieldError(target) {
        const el = byId(target);
        if (!el || !el.classList.contains('is-invalid')) return;
        el.classList.remove('is-invalid');
        el.removeAttribute('aria-invalid');
        const id = (el.id || 'field') + 'Error';
        const note = document.getElementById(id);
        if (note) note.remove();
        const described = (el.getAttribute('aria-describedby') || '').split(/\s+/).filter((x) => x && x !== id);
        if (described.length) el.setAttribute('aria-describedby', described.join(' '));
        else el.removeAttribute('aria-describedby');
    }

    /** Remove every field error inside a form (or the whole page). */
    function clearFieldErrors(scope) {
        (byId(scope) || document).querySelectorAll('.is-invalid').forEach(clearFieldError);
    }

    /**
     * A form problem: the message goes under the field, the field scrolls into view and takes focus,
     * and a toast repeats it as a short summary.
     * opts.focus: a different element to focus and scroll to; opts.toast: false to skip the toast.
     */
    function fieldProblem(target, message, opts = {}) {
        const el = setFieldError(target, message);
        if (opts.toast !== false) toast(message, 'warning');
        const focusEl = byId(opts.focus) || el;
        if (focusEl) {
            const smooth = !window.matchMedia('(prefers-reduced-motion: reduce)').matches;
            focusEl.scrollIntoView({ block: 'center', behavior: smooth ? 'smooth' : 'auto' });
            if (focusEl.focus) focusEl.focus({ preventScroll: true });
        }
        return el;
    }

    // Typing in a field (or picking a value) clears its error
    ['input', 'change'].forEach((type) => document.addEventListener(type, (e) => {
        if (e.target && e.target.classList && e.target.classList.contains('is-invalid')) clearFieldError(e.target);
    }, true));

    // ── Charts (Chart.js) ──────────────────────────────────────────────────
    const chartColors = {
        brand: '#1a6b3c',
        brandSoft: 'rgba(26, 107, 60, 0.12)',
        blue: '#2563eb',
        success: '#047857',
        warning: '#b45309',
        danger: '#b91c1c',
        empty: '#e5e7eb',
        grid: 'rgba(17, 24, 39, 0.06)',
        // categories: easy to tell apart, none of them reused for a status colour
        series: ['#1a6b3c', '#2563eb', '#7c3aed', '#db2777', '#0891b2', '#64748b', '#65a30d', '#a16207']
    };

    // Poppins, readable sizes and calm motion for every chart on the page
    function applyChartDefaults() {
        if (typeof Chart === 'undefined') return;
        Chart.defaults.font.family = "'Poppins', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif";
        Chart.defaults.font.size = 12;
        Chart.defaults.color = '#4b5563';
        Chart.defaults.borderColor = 'rgba(17, 24, 39, 0.08)';
        Chart.defaults.plugins.legend.labels.usePointStyle = true;
        Chart.defaults.plugins.legend.labels.pointStyle = 'circle';
        Chart.defaults.plugins.legend.labels.padding = 16;
        Chart.defaults.plugins.legend.labels.font = { size: 13 };
        Chart.defaults.plugins.tooltip.backgroundColor = '#111827';
        Chart.defaults.plugins.tooltip.padding = 10;
        Chart.defaults.plugins.tooltip.cornerRadius = 8;
        Chart.defaults.plugins.tooltip.titleFont = { size: 13, weight: '600' };
        Chart.defaults.plugins.tooltip.bodyFont = { size: 13 };
        if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) Chart.defaults.animation = false;
        else Chart.defaults.animation.duration = 400;
    }
    applyChartDefaults();

    /** Money on a chart axis: "LKR 950", "LKR 1.5k", "LKR 12k", "LKR 1.25M" (never two ticks with the same text). */
    function axisMoney(value) {
        const v = Number(value) || 0;
        const short = (n) => Number(n.toFixed(2)).toString();
        if (Math.abs(v) >= 1e6) return 'LKR ' + short(v / 1e6) + 'M';
        if (Math.abs(v) >= 1000) return 'LKR ' + short(v / 1000) + 'k';
        return 'LKR ' + short(v);
    }

    /** Shows a short "nothing to chart yet" message over an empty chart instead of bare axes. */
    function chartEmpty(canvasId, isEmpty, title, text) {
        const canvas = document.getElementById(canvasId);
        const wrap = canvas && canvas.closest('.chart-wrap');
        if (!wrap) return;
        wrap.classList.toggle('is-empty', !!isEmpty);
        let note = wrap.querySelector('.chart-empty-note');
        if (!isEmpty) { if (note) note.remove(); return; }
        if (!note) {
            note = document.createElement('div');
            note.className = 'chart-empty-note';
            wrap.appendChild(note);
        }
        note.innerHTML = emptyState(title, text, 'fa-chart-simple');
    }

    /** A plain-language summary of the chart for screen readers. */
    function describeChart(canvasId, text) {
        const canvas = document.getElementById(canvasId);
        if (canvas) canvas.setAttribute('aria-label', text);
    }

    // ── File download ───────────────────────────────────────────────────────
    /** Save CSV text as a file (UTF-8 with a BOM so Excel shows accents and currency correctly). */
    function downloadCsv(filename, csvText) {
        const blob = new Blob(['﻿', csvText], { type: 'text/csv;charset=utf-8' });
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = filename;
        document.body.appendChild(link);
        link.click();
        link.remove();
        setTimeout(() => URL.revokeObjectURL(url), 1000);
    }

    // ── Loading / empty / error states for lists and tables ────────────────
    /** Placeholder shaped like what is loading. kind: 'list' (cards in a column) | 'rows' (table rows) */
    function skeleton(kind = 'rows', count = 4) {
        const one = kind === 'list' || kind === 'cards'
            ? '<div class="card sk-card"><div class="skeleton sk-line w-50"></div><div class="skeleton sk-line w-75 mt-3"></div><div class="skeleton sk-line w-25 mt-2"></div></div>'
            : '<div class="skeleton sk-row"></div>';
        return `<div class="skeleton-group${kind === 'rows' ? '' : ' sk-list'}" aria-hidden="true">${one.repeat(count)}</div>`;
    }

    /**
     * The "nothing here yet" box. action (optional): { href, label } for one clear next step.
     */
    function emptyState(title, text = '', icon = 'fa-inbox', action = null) {
        const cta = action && action.href
            ? `<a class="btn btn-primary btn-sm" href="${esc(action.href)}">${action.icon ? `<i class="fas ${esc(action.icon)}" aria-hidden="true"></i>` : ''}${esc(action.label)}</a>`
            : '';
        return `<div class="empty-state" role="status">
            <div class="empty-icon"><i class="fas ${esc(icon)}" aria-hidden="true"></i></div>
            <h3>${esc(title)}</h3>${text ? `<p>${esc(text)}</p>` : ''}${cta}</div>`;
    }

    /** Money for a big stat number: a small "LKR" unit and the amount ("LKR 6,340.00" when read). */
    function moneyHtml(amount) {
        return '<span class="stat-unit">LKR</span> ' + esc(ReplateSchema.formatNumber2(amount));
    }

    // Returns an error box with a Try again button.
    function errorState(message = 'Could not load this data.', retryLabel = 'Try again') {
        return `<div class="empty-state error" role="alert">
            <div class="empty-icon"><i class="fas fa-triangle-exclamation" aria-hidden="true"></i></div>
            <h3>Something went wrong</h3><p>${esc(message)}</p>
            <button type="button" class="btn btn-outline-primary btn-sm" onclick="window.location.reload()">${esc(retryLabel)}</button></div>`;
    }

    return {
        esc,
        safeUrl,
        toast,
        flash,
        showAlert,
        confirmAction,
        askText,
        ADMIN_EMAILS,
        requireRole,
        requireApprovedHotel,
        compressImage,
        renderSidebar,
        attachPasswordToggle,
        setButtonLoading,
        setFieldError,
        clearFieldError,
        clearFieldErrors,
        fieldProblem,
        downloadCsv,
        skeleton,
        emptyState,
        moneyHtml,
        chartColors,
        axisMoney,
        chartEmpty,
        describeChart,
        errorState
    };
})();
