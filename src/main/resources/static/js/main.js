// 共通モーダルユーティリティ（thymeleaf_ui.md 記載の hidden/flex パターン）
function openModal(id) {
    const el = document.getElementById(id);
    if (!el) return;
    el.classList.remove('hidden');
    el.classList.add('flex');
    const focusable = el.querySelector('button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])');
    if (focusable) focusable.focus();
}
function closeModal(id) {
    const el = document.getElementById(id);
    if (!el) return;
    el.classList.add('hidden');
    el.classList.remove('flex');
}
document.addEventListener('click', function (e) {
    document.querySelectorAll('.modal').forEach(function (modal) {
        if (e.target === modal) closeModal(modal.id);
    });
});
document.addEventListener('keydown', function (e) {
    if (e.key !== 'Escape') return;
    document.querySelectorAll('.modal').forEach(function (modal) {
        if (!modal.classList.contains('hidden')) closeModal(modal.id);
    });
});

document.addEventListener('DOMContentLoaded', () => {
    console.log('Nexus Time initialized.');

    let contextPath = document.querySelector('meta[name="context-path"]')?.getAttribute('content') || '';
    if (contextPath === '/') contextPath = '';
    else if (contextPath.endsWith('/')) contextPath = contextPath.slice(0, -1);



    // Mobile Sidebar Toggle Logic
    const menuToggle = document.getElementById('menu-toggle');
    const sidebar = document.getElementById('sidebar');
    const mobileOverlay = document.getElementById('mobile-overlay');

    if (menuToggle && sidebar && mobileOverlay) {
        function toggleMenu() {
            const isOpen = sidebar.classList.toggle('open');
            mobileOverlay.classList.toggle('open');
            menuToggle.setAttribute('aria-expanded', String(isOpen));
        }

        menuToggle.addEventListener('click', toggleMenu);
        mobileOverlay.addEventListener('click', toggleMenu);
    }

    // Alert close logic (Native replacement for Bootstrap's alert dismiss)
    document.addEventListener('click', (e) => {
        const closeBtn = e.target.closest('.btn-close');
        if (closeBtn) {
            const alert = closeBtn.closest('.alert');
            if (alert) {
                alert.style.transition = 'opacity 0.15s linear';
                alert.style.opacity = '0';
                setTimeout(() => alert.remove(), 150);
            }
        }
    });

});
