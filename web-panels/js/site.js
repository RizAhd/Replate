/**
 * REPLATE public site: the parts of each section move into place once, in reading order, as the section
 * scrolls into view. Nothing is hidden while it waits; without this script (or with reduced motion) nothing moves.
 */
(() => {
    if (!('IntersectionObserver' in window)) return;
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;

    const items = Array.from(document.querySelectorAll('[data-reveal]'));
    if (!items.length) return;

    // Stagger within each section: heading, then text, then picture, then details
    const bySection = new Map();
    items.forEach((el) => {
        const section = el.closest('section') || document.body;
        const index = bySection.get(section) || 0;
        el.style.setProperty('--i', String(index));
        bySection.set(section, index + 1);
    });

    document.documentElement.classList.add('js');

    const observer = new IntersectionObserver((entries) => {
        entries.forEach((entry) => {
            if (!entry.isIntersecting) return;
            entry.target.classList.add('is-in');
            observer.unobserve(entry.target);
        });
    }, { rootMargin: '0px 0px -10% 0px', threshold: 0.12 });

    items.forEach((el) => observer.observe(el));
})();
