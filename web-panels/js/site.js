/**
 * REPLATE public site: the parts of each section arrive once, in reading order, as the section scrolls
 * into view. Without this script (or with reduced motion) everything is simply shown.
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

    // A picture opens only once it has loaded, so it never opens onto an empty frame
    const show = (el) => {
        const img = el.classList.contains('shutter') ? el.querySelector('img') : null;
        if (!img || (img.complete && img.naturalWidth)) { el.classList.add('is-in'); return; }
        const done = () => el.classList.add('is-in');
        img.addEventListener('load', done, { once: true });
        img.addEventListener('error', done, { once: true });
    };

    const observer = new IntersectionObserver((entries) => {
        entries.forEach((entry) => {
            if (!entry.isIntersecting) return;
            show(entry.target);
            observer.unobserve(entry.target);
        });
    }, { rootMargin: '0px 0px -10% 0px', threshold: 0.12 });

    items.forEach((el) => observer.observe(el));
})();
