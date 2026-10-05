/**
 * Shared admin panel sidebar configuration.
 * Include this file in every admin-panel page instead of repeating the links array.
 */
const adminShellLinks = [
    { key: 'approve-hotels', href: 'approve-hotels.html', icon: 'fas fa-hotel',           label: 'Approve Hotels' },
    { key: 'users',          href: 'users.html',          icon: 'fas fa-users',           label: 'Users' },
    { key: 'transactions',   href: 'transactions.html',   icon: 'fas fa-money-bill-wave', label: 'Transactions' },
    { key: 'categories',     href: 'categories.html',     icon: 'fas fa-tags',            label: 'Categories' },
    { key: 'banners',        href: 'banners.html',        icon: 'fas fa-images',          label: 'Banners' },
    { key: 'analytics',      href: 'analytics.html',      icon: 'fas fa-chart-line',      label: 'Analytics' },
    { key: 'notifications',  href: 'notifications.html',  icon: 'fas fa-bell',            label: 'Notifications' },
    { key: 'logout',         href: '#',                   icon: 'fas fa-sign-out-alt',    label: 'Logout', logout: true }
];
