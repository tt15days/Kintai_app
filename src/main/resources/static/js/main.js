// 共通モーダルユーティリティ（thymeleaf_ui.md 記載の hidden/flex パターン）
function openModal(id) {
    const el = document.getElementById(id);
    if (!el) return;
    el.classList.remove('hidden');
    el.classList.add('flex');
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

    // ==========================================
    // Notification Bell & Dropdown Logic
    // ==========================================
    const bell = document.getElementById('notification-bell');
    const badge = document.getElementById('notification-badge');
    const dropdown = document.getElementById('notification-dropdown');
    const dropdownList = document.getElementById('notification-dropdown-list');
    const readAllBtn = document.getElementById('notification-read-all-btn');

    if (bell && badge && dropdown && dropdownList) {
        // CSRF Token logic for POST request
        const csrfTokenHeader = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content');
        const csrfToken = document.querySelector('meta[name="_csrf"]')?.getAttribute('content');

        // Fetch notifications on load
        fetchUnreadNotifications();

        // Toggle dropdown display
        bell.addEventListener('click', (e) => {
            e.stopPropagation();
            dropdown.classList.toggle('show');
        });

        // Close dropdown when clicking outside
        document.addEventListener('click', (e) => {
            if (!dropdown.contains(e.target) && !bell.contains(e.target)) {
                dropdown.classList.remove('show');
            }
        });

        // "Mark all as read" button click
        if (readAllBtn) {
            readAllBtn.addEventListener('click', async () => {
                try {
                    const headers = {};
                    if (csrfTokenHeader && csrfToken) {
                        headers[csrfTokenHeader] = csrfToken;
                    }
                    const response = await fetch(`${contextPath}/dashboard/notifications/read-all`, {
                        method: 'POST',
                        headers: headers
                    });
                    if (response.ok || response.redirected) {
                        // Refresh notifications list and badge
                        badge.style.display = 'none';
                        badge.textContent = '0';
                        dropdownList.innerHTML = '<div class="notification-empty">新しい通知はありません</div>';
                    }
                } catch (error) {
                    console.error('Failed to mark all as read:', error);
                }
            });
        }

        async function fetchUnreadNotifications() {
            try {
                const response = await fetch(`${contextPath}/dashboard/notifications/unread`);
                if (!response.ok) throw new Error('Network response was not ok');
                const notifications = await response.json();
                updateNotificationUI(notifications);
            } catch (error) {
                console.error('Failed to fetch notifications:', error);
            }
        }

        function updateNotificationUI(notifications) {
            if (notifications && notifications.length > 0) {
                // Update badge
                badge.textContent = notifications.length;
                badge.style.display = 'block';

                // Update list
                dropdownList.innerHTML = '';
                notifications.forEach(notification => {
                    const item = document.createElement('div');
                    item.className = 'notification-item';
                    item.dataset.id = notification.notificationId;

                    // Format date time
                    let timeStr = '';
                    if (notification.createdAt) {
                        const date = new Date(notification.createdAt);
                        timeStr = date.toLocaleString('ja-JP', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' });
                    }

                    item.innerHTML = `
                        <div class="notification-item-text">${escapeHtml(notification.message)}</div>
                        <div class="notification-item-time">${timeStr}</div>
                    `;

                    // Mark individual notification as read on click
                    item.addEventListener('click', async () => {
                        await markAsRead(notification.notificationId, item);
                    });

                    dropdownList.appendChild(item);
                });
            } else {
                badge.style.display = 'none';
                badge.textContent = '0';
                dropdownList.innerHTML = '<div class="notification-empty">新しい通知はありません</div>';
            }
        }

        async function markAsRead(id, itemElement) {
            try {
                const headers = { 'Content-Type': 'application/json' };
                if (csrfTokenHeader && csrfToken) {
                    headers[csrfTokenHeader] = csrfToken;
                }
                const response = await fetch(`${contextPath}/dashboard/notifications/${id}/read`, {
                    method: 'POST',
                    headers: headers
                });
                if (response.ok) {
                    // Remove item visually
                    itemElement.style.opacity = '0';
                    setTimeout(() => {
                        itemElement.remove();
                        // Update badge count
                        const currentCount = parseInt(badge.textContent || '0') - 1;
                        if (currentCount > 0) {
                            badge.textContent = currentCount;
                        } else {
                            badge.style.display = 'none';
                            badge.textContent = '0';
                            dropdownList.innerHTML = '<div class="notification-empty">新しい通知はありません</div>';
                        }
                    }, 200);
                }
            } catch (error) {
                console.error('Failed to mark notification as read:', error);
            }
        }

        function escapeHtml(str) {
            if (!str) return '';
            return str.replace(/[&<>'"]/g,
                tag => ({
                    '&': '&amp;',
                    '<': '&lt;',
                    '>': '&gt;',
                    "'": '&#39;',
                    '"': '&quot;'
                }[tag] || tag)
            );
        }
    }
});
