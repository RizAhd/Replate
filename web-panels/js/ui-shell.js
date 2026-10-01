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
        close.textContent = '×';
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
            if (input) {
                field = document.createElement(input.multiline ? 'textarea' : 'input');
                field.className = 'form-control rp-dialog-input';
                if (!input.multiline) field.type = 'text';
                field.placeholder = input.placeholder || '';
                field.setAttribute('aria-label', input.label || title || 'Value');
                if (input.multiline) field.rows = 3;
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
            if (field) box.append(field);
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
        return openDialog({ message, input: { placeholder: opts.placeholder, label: opts.label, multiline: !!opts.multiline }, ...opts });
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
        try {
            const snap = await database.ref('users/' + user.uid).once('value');
            const profile = snap.val() || {};
            const value = String(profile.role || profile.userType || '').toLowerCase();
            const isAdmin = value === 'admin' && ADMIN_EMAILS.includes((user.email || '').toLowerCase());
            if ((role === 'admin' && isAdmin) || (role === 'hotel' && value === 'hotel')) return user;
        } catch (e) {
            console.error('Role check failed', e);
        }
        await auth.signOut();
        window.location.replace(loginPage);
        return null;
    }

    // ── Sidebar ─────────────────────────────────────────────────────────────
    let sidebarBound = false;

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
                <a class="navbar-brand" href="#">${title}</a>
                <nav aria-label="Main">
                    <ul class="nav flex-column">${items}</ul>
                </nav>
            </div>`;
        container.dataset.shellReady = 'true';

        if (sidebarBound) return;
        sidebarBound = true;

        const toggle = document.createElement('button');
        toggle.type = 'button';
        toggle.className = 'sidebar-toggle';
        toggle.setAttribute('aria-label', 'Open menu');
        toggle.setAttribute('aria-expanded', 'false');
        toggle.setAttribute('aria-controls', containerId);
        toggle.innerHTML = '<i class="fas fa-bars" aria-hidden="true"></i>';
        document.body.appendChild(toggle);

        const overlay = document.createElement('div');
        overlay.className = 'sidebar-overlay';
        document.body.appendChild(overlay);

        const setOpen = (open) => {
            container.classList.toggle('show', open);
            overlay.classList.toggle('show', open);
            toggle.setAttribute('aria-expanded', String(open));
            toggle.setAttribute('aria-label', open ? 'Close menu' : 'Open menu');
            document.body.style.overflow = open ? 'hidden' : '';
            if (open) {
                const first = container.querySelector('.nav-link');
                if (first) first.focus();
            }
        };

        toggle.addEventListener('click', () => setOpen(!container.classList.contains('show')));
        overlay.addEventListener('click', () => { setOpen(false); toggle.focus(); });
        document.addEventListener('keydown', (e) => {
            if (e.key === 'Escape' && container.classList.contains('show')) { setOpen(false); toggle.focus(); }
        });
        window.addEventListener('resize', () => { if (window.innerWidth > 991) setOpen(false); });

        // One delegated listener survives every re-render of the sidebar
        container.addEventListener('click', (e) => {
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

    function setButtonLoading(buttonId, isLoading, loadingText = 'Please wait...') {
        const btn = document.getElementById(buttonId);
        if (!btn) return;
        if (isLoading) {
            if (!btn.dataset.originalText) btn.dataset.originalText = btn.innerHTML;
            btn.disabled = true;
            btn.innerHTML = `<span class="spinner-border spinner-border-sm me-2" role="status" aria-hidden="true"></span>${esc(loadingText)}`;
        } else {
            btn.disabled = false;
            if (btn.dataset.originalText) btn.innerHTML = btn.dataset.originalText;
        }
    }

    // ── Loading / empty / error states for lists and tables ────────────────
    /** Placeholder rows while data loads. kind: 'cards' | 'rows' */
    function skeleton(kind = 'rows', count = 4) {
        const one = kind === 'cards'
            ? '<div class="col-md-6 col-xl-4 mb-4"><div class="card"><div class="card-body"><div class="skeleton sk-line w-75"></div><div class="skeleton sk-line w-50 mt-2"></div><div class="skeleton sk-line w-100 mt-3"></div></div></div></div>'
            : '<div class="skeleton sk-row"></div>';
        return `<div class="skeleton-group" aria-hidden="true">${one.repeat(count)}</div>`;
    }

    function emptyState(title, text = '', icon = 'fa-inbox') {
        return `<div class="empty-state" role="status">
            <div class="empty-icon"><i class="fas ${esc(icon)}" aria-hidden="true"></i></div>
            <h3>${esc(title)}</h3>${text ? `<p>${esc(text)}</p>` : ''}</div>`;
    }

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
        requireRole,
        renderSidebar,
        attachPasswordToggle,
        setButtonLoading,
        skeleton,
        emptyState,
        errorState
    };
})();
