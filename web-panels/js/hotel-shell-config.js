/**
 * Shared hotel panel sidebar configuration.
 * Include this file in every hotel-panel page instead of duplicating the links array.
 */
const hotelShellLinks = [
    { key: 'dashboard',  href: 'dashboard.html',       icon: 'fas fa-home',         label: 'Dashboard' },
    { key: 'add-food',   href: 'add-food.html',        icon: 'fas fa-plus-circle',  label: 'Add Food' },
    { key: 'listings',   href: 'manage-listings.html',  icon: 'fas fa-list',         label: 'My Listings' },
    { key: 'orders',     href: 'orders.html',           icon: 'fas fa-receipt',      label: 'Orders' },
    { key: 'analytics',  href: 'analytics.html',        icon: 'fas fa-chart-line',   label: 'Analytics' },
    { key: 'chat',       href: 'chat.html',             icon: 'fas fa-comments',     label: 'Chat' },
    { key: 'settings',   href: 'settings.html',         icon: 'fas fa-cog',          label: 'Settings' },
    { key: 'logout',     href: '#',                     icon: 'fas fa-sign-out-alt', label: 'Logout', logout: true }
];

