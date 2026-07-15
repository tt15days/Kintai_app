// 共通モーダルユーティリティ（thymeleaf_ui.md 記載の hidden/flex パターン）
const modalStates = new WeakMap();

function getFocusableElements(container) {
    return Array.from(container.querySelectorAll(
        'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'
    )).filter((element) => !element.hidden && element.getClientRects().length > 0);
}

function setModalBackgroundInert(modal, inert) {
    const state = modalStates.get(modal);
    if (!state) return;

    if (inert) {
        const backgroundElements = [];
        let branch = modal;

        while (branch.parentElement) {
            const parent = branch.parentElement;
            Array.from(parent.children)
                .filter((element) => element !== branch)
                .forEach((element) => backgroundElements.push({
                    element,
                    inert: element.hasAttribute('inert'),
                    ariaHidden: element.getAttribute('aria-hidden')
                }));

            if (parent === document.body) break;
            branch = parent;
        }

        state.backgroundElements = backgroundElements;
        state.backgroundElements.forEach(({ element }) => {
            element.setAttribute('inert', '');
            element.setAttribute('aria-hidden', 'true');
        });
        return;
    }

    [...state.backgroundElements].reverse().forEach(({ element, inert: previousInert, ariaHidden }) => {
        element.toggleAttribute('inert', previousInert);
        if (ariaHidden === null) {
            element.removeAttribute('aria-hidden');
        } else {
            element.setAttribute('aria-hidden', ariaHidden);
        }
    });
}

function openModal(id) {
    const modal = document.getElementById(id);
    if (!modal || !modal.classList.contains('hidden')) return;

    modalStates.set(modal, {
        returnFocus: document.activeElement instanceof HTMLElement ? document.activeElement : null,
        backgroundElements: []
    });
    modal.setAttribute('role', 'dialog');
    modal.setAttribute('aria-modal', 'true');
    modal.setAttribute('aria-hidden', 'false');
    modal.classList.remove('hidden');
    modal.classList.add('flex');
    setModalBackgroundInert(modal, true);

    const focusable = getFocusableElements(modal);
    (focusable[0] || modal).focus();
}
function closeModal(id) {
    const modal = document.getElementById(id);
    if (!modal || modal.classList.contains('hidden')) return;

    const state = modalStates.get(modal);
    setModalBackgroundInert(modal, false);
    modal.setAttribute('aria-hidden', 'true');
    modal.classList.add('hidden');
    modal.classList.remove('flex');
    modalStates.delete(modal);

    if (state?.returnFocus?.isConnected) {
        state.returnFocus.focus();
    }
}
document.addEventListener('click', function (e) {
    document.querySelectorAll('.modal').forEach(function (modal) {
        if (e.target === modal) closeModal(modal.id);
    });
});
document.addEventListener('keydown', function (e) {
    const openModals = Array.from(document.querySelectorAll('.modal:not(.hidden)'));
    const modal = openModals.at(-1);
    if (!modal) return;

    if (e.key === 'Escape') {
        e.preventDefault();
        closeModal(modal.id);
        return;
    }

    if (e.key !== 'Tab') return;
    const focusable = getFocusableElements(modal);
    if (focusable.length === 0) {
        e.preventDefault();
        modal.focus();
        return;
    }

    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    if (e.shiftKey && document.activeElement === first) {
        e.preventDefault();
        last.focus();
    } else if (!e.shiftKey && document.activeElement === last) {
        e.preventDefault();
        first.focus();
    }
});

document.addEventListener('DOMContentLoaded', () => {
    console.log('Nexus Time initialized.');

    document.querySelectorAll('[data-history-back]').forEach((button) => {
        button.addEventListener('click', () => window.history.back());
    });

    let contextPath = document.querySelector('meta[name="context-path"]')?.getAttribute('content') || '';
    if (contextPath === '/') contextPath = '';
    else if (contextPath.endsWith('/')) contextPath = contextPath.slice(0, -1);



    // Mobile Sidebar Toggle Logic
    const menuToggle = document.getElementById('menu-toggle');
    const sidebarClose = document.getElementById('sidebar-close');
    const sidebar = document.getElementById('sidebar');
    const mobileOverlay = document.getElementById('mobile-overlay');

    if (menuToggle && sidebar && mobileOverlay) {
        const mobileMedia = window.matchMedia('(max-width: 767px)');

        function setMenuOpen(isOpen, restoreFocus = true) {
            sidebar.classList.toggle('open', isOpen);
            mobileOverlay.classList.toggle('open', isOpen);
            menuToggle.setAttribute('aria-expanded', String(isOpen));
            menuToggle.setAttribute('aria-label', isOpen ? 'メニューを閉じる' : 'メニューを開く');
            mobileOverlay.setAttribute('aria-hidden', String(!isOpen));

            if (mobileMedia.matches) {
                sidebar.inert = !isOpen;
                sidebar.setAttribute('aria-hidden', String(!isOpen));
            }

            if (isOpen) {
                sidebar.querySelector('a, button')?.focus();
            } else if (restoreFocus) {
                menuToggle.focus();
            }
        }

        function syncMenuForViewport() {
            if (mobileMedia.matches) {
                setMenuOpen(false, false);
            } else {
                sidebar.classList.remove('open');
                mobileOverlay.classList.remove('open');
                sidebar.inert = false;
                sidebar.removeAttribute('aria-hidden');
                mobileOverlay.setAttribute('aria-hidden', 'true');
                menuToggle.setAttribute('aria-expanded', 'false');
            }
        }

        menuToggle.addEventListener('click', () => setMenuOpen(menuToggle.getAttribute('aria-expanded') !== 'true'));
        sidebarClose?.addEventListener('click', () => setMenuOpen(false));
        mobileOverlay.addEventListener('click', () => setMenuOpen(false));
        document.addEventListener('keydown', (event) => {
            if (event.key === 'Escape' && menuToggle.getAttribute('aria-expanded') === 'true') {
                event.preventDefault();
                setMenuOpen(false);
            }
        });
        mobileMedia.addEventListener('change', syncMenuForViewport);
        syncMenuForViewport();
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
