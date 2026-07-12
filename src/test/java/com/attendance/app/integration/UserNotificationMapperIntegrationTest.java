package com.attendance.app.integration;

import com.attendance.app.entity.UserNotification;
import com.attendance.app.mapper.UserMapper;
import com.attendance.app.mapper.UserNotificationMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = com.attendance.app.AttendanceApplication.class)
@ActiveProfiles("integration")
@Transactional
@DisplayName("UserNotificationMapper Integration")
class UserNotificationMapperIntegrationTest {

    @Autowired
    private UserNotificationMapper userNotificationMapper;

    @Autowired
    private UserMapper userMapper;

    @Test
    @DisplayName("insert -> unread取得 -> 既読化 の往復ができる")
    void unreadAndReadFlow_roundTrip() {
        Long userId = userMapper.selectByEmail("user@example.com").orElseThrow().getUserId();
        String prefix = "[IT-NOTIFY]";

        UserNotification n1 = UserNotification.builder()
                .userId(userId)
                .message(prefix + "-1")
                .notificationType("REMINDER")
                .build();
        UserNotification n2 = UserNotification.builder()
                .userId(userId)
                .message(prefix + "-2")
                .notificationType("APPROVAL_REQUEST")
                .build();

        assertThat(userNotificationMapper.insert(n1)).isEqualTo(1);
        assertThat(userNotificationMapper.insert(n2)).isEqualTo(1);
        assertThat(n1.getNotificationId()).isNotNull();
        assertThat(n2.getNotificationId()).isNotNull();

        List<UserNotification> unread = userNotificationMapper.selectUnreadByUserId(userId).stream()
                .filter(n -> n.getMessage() != null && n.getMessage().startsWith(prefix))
                .toList();
        assertThat(unread).extracting(u -> u.getMessage()).containsExactlyInAnyOrder(prefix + "-1", prefix + "-2");

        assertThat(userNotificationMapper.markAsRead(n1.getNotificationId(), userId)).isEqualTo(1);
        List<UserNotification> unreadAfterOneRead = userNotificationMapper.selectUnreadByUserId(userId).stream()
                .filter(n -> n.getMessage() != null && n.getMessage().startsWith(prefix))
                .toList();
        assertThat(unreadAfterOneRead).extracting(u -> u.getMessage()).containsExactly(prefix + "-2");

        int bulk = userNotificationMapper.markAllAsRead(userId);
        assertThat(bulk).isGreaterThanOrEqualTo(1);
        List<UserNotification> unreadAfterAllRead = userNotificationMapper.selectUnreadByUserId(userId).stream()
                .filter(n -> n.getMessage() != null && n.getMessage().startsWith(prefix))
                .toList();
        assertThat(unreadAfterAllRead).isEmpty();
    }

    @Test
    @DisplayName("AlertBatchServiceが使う通知種別(ALERT_ARTICLE_36_LIMIT1/2, ALERT_PAID_LEAVE)がchk_un_notification_typeで拒否されない")
    void alertBatchNotificationTypes_areAcceptedByCheckConstraint() {
        Long userId = userMapper.selectByEmail("user@example.com").orElseThrow().getUserId();

        for (String type : List.of("ALERT_ARTICLE_36_LIMIT1", "ALERT_ARTICLE_36_LIMIT2", "ALERT_PAID_LEAVE")) {
            UserNotification notification = UserNotification.builder()
                    .userId(userId)
                    .message("[IT-ALERT-TYPE] " + type)
                    .notificationType(type)
                    .build();
            assertThat(userNotificationMapper.insert(notification)).isEqualTo(1);
            assertThat(notification.getNotificationId()).isNotNull();
        }
    }
}
