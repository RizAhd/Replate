/**
 * Shared admin panel sidebar configuration.
 * Include this file in every admin-panel page instead of repeating the links array.
 */
const adminShellLinks = [
    { key: 'approve-hotels', href: 'approve-hotels.html', icon: 'fas fa-hotel',           label: 'Approve hotels' },
    { key: 'users',          href: 'users.html',          icon: 'fas fa-users',           label: 'Users' },
    { key: 'listings',       href: 'listings.html',       icon: 'fas fa-utensils',        label: 'Listings' },
    { key: 'transactions',   href: 'transactions.html',   icon: 'fas fa-money-bill-wave', label: 'Transactions' },
    { key: 'categories',     href: 'categories.html',     icon: 'fas fa-tags',            label: 'Categories' },
    { key: 'banners',        href: 'banners.html',        icon: 'fas fa-images',          label: 'Banners' },
    { key: 'analytics',      href: 'analytics.html',      icon: 'fas fa-chart-line',      label: 'Analytics' },
    { key: 'reports',        href: 'reports.html',        icon: 'fas fa-file-alt',        label: 'Reports' },
    { key: 'notifications',  href: 'notifications.html',  icon: 'fas fa-bell',            label: 'Notifications' },
    { key: 'logout',         href: '#',                   icon: 'fas fa-arrow-right-from-bracket', label: 'Log out', logout: true }
];
