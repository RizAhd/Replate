/**
 * REPLATE UI Shell - Modern sidebar renderer and common UI utilities
 */

const ReplateUI = (() => {

    /**
     * Render the sidebar nav into the element with the given selector/id.
     * Also injects the mobile toggle button and overlay.
     */
    function renderSidebar(containerId, opts) {
        const container = document.getElementById(containerId);
        if (!container) return;

        const logoTitle = '<img src="../assets/replate-icon.svg" alt="REPLATE" class="brand-logo-sm">REPLATE';
        const { title = logoTitle, activeKey = '', links = [] } = opts;

        const items = links.map(link => {
            const active = link.key === activeKey ? ' active' : '';
            const onclick = link.logout ? ` onclick="logout(); return false;"` : '';
            return `<li class="nav-item">
                <a class="nav-link${active}" href="${link.href}"${onclick}>
                    <i class="${link.icon}"></i><span>${link.label}</span>
                </a>
            </li>`;
        }).join('');

        container.innerHTML = `
            <div class="position-sticky">
                <a class="navbar-brand" href="#">
                    ${title}
                </a>
                <ul class="nav flex-column">
                    ${items}
                </ul>
            </div>`;

        // Inject mobile toggle & overlay if not already present
        if (!document.querySelector('.sidebar-toggle')) {
            const toggle = document.createElement('button');
            toggle.className = 'sidebar-toggle';
            toggle.setAttribute('aria-label', 'Toggle menu');
            toggle.innerHTML = '<i class="fas fa-bars"></i>';
            document.body.appendChild(toggle);

            const overlay = document.createElement('div');
            overlay.className = 'sidebar-overlay';
            document.body.appendChild(overlay);

            toggle.addEventListener('click', () => {
                container.classList.toggle('show');
                overlay.classList.toggle('show');
            });

            overlay.addEventListener('click', () => {
                container.classList.remove('show');
                overlay.classList.remove('show');
            });

            // Close sidebar on nav link click (mobile)
            container.querySelectorAll('.nav-link').forEach(link => {
                link.addEventListener('click', () => {
                    if (window.innerWidth <= 991) {
                        container.classList.remove('show');
                        overlay.classList.remove('show');
                    }
                });
            });
        }
    }

    /**
     * Show a dismissible alert at the top of <main>.
     */
    function showAlert(message, type = 'info') {
        const main = document.querySelector('main');
        if (!main) return;
        const div = document.createElement('div');
        div.className = `alert alert-${type} alert-dismissible fade show`;
        div.role = 'alert';
        div.innerHTML = `${message}
            <button type="button" class="btn-close" data-bs-dismiss="alert"></button>`;
        main.prepend(div);
        setTimeout(() => div.remove(), 5000);
    }

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
            btn.innerHTML = `<span class="spinner-border spinner-border-sm me-2" role="status" aria-hidden="true"></span>${loadingText}`;
        } else {
            btn.disabled = false;
            if (btn.dataset.originalText) btn.innerHTML = btn.dataset.originalText;
        }
    }

    return {
        renderSidebar,
        showAlert,
        attachPasswordToggle,
        setButtonLoading
    };
})();
