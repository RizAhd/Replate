/**
 * REPLATE UI Shell - Sidebar renderer and common UI utilities
 */

const ReplateUI = (() => {

    /**
     * Render the sidebar nav into the element with the given selector/id.
     * @param {string} containerId  - id of the <nav> element (without #)
     * @param {object} opts
     *   opts.title    {string}  - HTML for the brand / title link
     *   opts.activeKey {string} - key that should be highlighted
     *   opts.links    {Array}   - [{key, href, icon, label, logout}]
     */
    function renderSidebar(containerId, opts) {
        const container = document.getElementById(containerId);
        if (!container) return;

        const { title = 'REPLATE', activeKey = '', links = [] } = opts;

        const items = links.map(link => {
            const active = link.key === activeKey ? ' active' : '';
            const onclick = link.logout ? ` onclick="logout(); return false;"` : '';
            return `<li class="nav-item">
                <a class="nav-link${active}" href="${link.href}"${onclick}>
                    <i class="${link.icon} me-2"></i>${link.label}
                </a>
            </li>`;
        }).join('');

        container.innerHTML = `
            <div class="position-sticky pt-3">
                <a class="navbar-brand d-block px-3 py-3 mb-2 text-center fw-bold" href="#">
                    ${title}
                </a>
                <ul class="nav flex-column">
                    ${items}
                </ul>
            </div>`;
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

    return { renderSidebar, showAlert };
})();

