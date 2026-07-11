package com.attendance.app.service;

import com.attendance.app.entity.User;
import com.attendance.app.entity.UserNotification;
import com.attendance.app.entity.UserRole;
import com.attendance.app.entity.AttendanceSubmission;
import com.attendance.app.mapper.UserNotificationMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.time.YearMonth;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserNotificationService")
class UserNotificationServiceTest {

    @Mock
    private UserNotificationMapper userNotificationMapper;

    @Mock
    private UserService userService;

    @Mock
    private AttendanceSubmissionService attendanceSubmissionService;

    @InjectMocks
    private UserNotificationService service;

    @Nested
    @DisplayName("sendCustomNotification")
    class SendCustomNotification {

        @Test
        @DisplayName("指定ユーザーへ ADMIN_MESSAGE 通知を保存する")
        void sendsAdminMessageToSingleUser() {
            service.sendCustomNotification(2L, "管理者メッセージ", 1L);
 
            ArgumentCaptor<UserNotification> captor = ArgumentCaptor.forClass(UserNotification.class);
            verify(userNotificationMapper).insert(captor.capture());
 
            UserNotification saved = captor.getValue();
            assertThat(saved.getUserId()).isEqualTo(2L);
            assertThat(saved.getSenderUserId()).isEqualTo(1L);
            assertThat(saved.getMessage()).isEqualTo("管理者メッセージ");
            assertThat(saved.getNotificationType()).isEqualTo(UserNotificationService.TYPE_ADMIN_MESSAGE);
            assertThat(saved.getIsRead()).isFalse();
        }
 
        @Test
        @DisplayName("空メッセージは拒否する")
        void rejectsBlankMessage() {
            assertThatThrownBy(() -> service.sendCustomNotification(2L, "  ", 1L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("メッセージを入力してください");

            verify(userNotificationMapper, never()).insert(org.mockito.ArgumentMatchers.any());
        }
    }

    @Nested
    @DisplayName("sendCustomNotificationToAll")
    class SendCustomNotificationToAll {

        @Test
        @DisplayName("ADMIN を除くアクティブユーザー全員へ送信する")
        void skipsAdminUsers() {
            User admin = User.builder().userId(1L).userRole(UserRole.ADMIN).build();
            User user = User.builder().userId(2L).userRole(UserRole.USER).build();
            User approver = User.builder().userId(3L).userRole(UserRole.USER).build();
            when(userService.getActiveUsers()).thenReturn(List.of(admin, user, approver));

            int count = service.sendCustomNotificationToAll("一括通知", 1L);

            ArgumentCaptor<UserNotification> captor = ArgumentCaptor.forClass(UserNotification.class);
            verify(userNotificationMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
            assertThat(count).isEqualTo(2);
            assertThat(captor.getAllValues())
                    .extracting(u -> u.getUserId())
                    .containsExactly(2L, 3L);
            assertThat(captor.getAllValues())
                    .extracting(u -> u.getSenderUserId())
                    .containsOnly(1L);
            assertThat(captor.getAllValues())
                    .extracting(u -> u.getNotificationType())
                    .containsOnly(UserNotificationService.TYPE_ADMIN_MESSAGE);
        }
    }

    @Test
    @DisplayName("notifyArticle36Alert: ARTICLE36_ALERT 通知を保存する")
    void notifyArticle36Alert_insertsAlertNotification() {
        service.notifyArticle36Alert(2L, "36協定の注意通知");

        ArgumentCaptor<UserNotification> captor = ArgumentCaptor.forClass(UserNotification.class);
        verify(userNotificationMapper).insert(captor.capture());
        assertThat(captor.getValue().getNotificationType())
                .isEqualTo(UserNotificationService.TYPE_ARTICLE36_ALERT);
        assertThat(captor.getValue().getMessage()).isEqualTo("36協定の注意通知");
    }

        @Nested
        @DisplayName("createRemindersForUnsubmittedUsers")
        class CreateRemindersForUnsubmittedUsers {

        @Test
        @DisplayName("未提出・差戻し・取下げユーザーへリマインドを作成し、承認済みは除外する")
        void createsReminderForUnsubmittedReturnedWithdrawn() {
            YearMonth target = YearMonth.of(2026, 6);
            User admin = User.builder().userId(1L).userRole(UserRole.ADMIN).build();
            User unsubmitted = User.builder().userId(2L).userRole(UserRole.USER).build();
            User returned = User.builder().userId(3L).userRole(UserRole.USER).build();
            User withdrawn = User.builder().userId(4L).userRole(UserRole.USER).build();
            User approved = User.builder().userId(5L).userRole(UserRole.USER).build();

            when(userService.getActiveUsers()).thenReturn(List.of(admin, unsubmitted, returned, withdrawn, approved));
            when(attendanceSubmissionService.getSubmissionsByTargetYearMonth(target)).thenReturn(List.of(
                AttendanceSubmission.builder().userId(3L).status(AttendanceSubmissionService.STATUS_RETURNED).build(),
                AttendanceSubmission.builder().userId(4L).status(AttendanceSubmissionService.STATUS_WITHDRAWN).build(),
                AttendanceSubmission.builder().userId(5L).status(AttendanceSubmissionService.STATUS_APPROVED).build()));

            int count = service.createRemindersForUnsubmittedUsers(target);

            ArgumentCaptor<UserNotification> captor = ArgumentCaptor.forClass(UserNotification.class);
            verify(userNotificationMapper, org.mockito.Mockito.times(3)).insert(captor.capture());
            assertThat(count).isEqualTo(3);
            assertThat(captor.getAllValues())
                .extracting(u -> u.getUserId())
                .containsExactly(2L, 3L, 4L);
            assertThat(captor.getAllValues())
                .extracting(u -> u.getNotificationType())
                .containsOnly(UserNotificationService.TYPE_REMINDER);
        }
        }

        @Test
        @DisplayName("notifyApproversNewSubmission: 申請者を除く承認者のみに通知する")
        void notifyApproversNewSubmission_sendsOnlyApproversExceptApplicant() {
        User applicant = User.builder().userId(10L).userRole(UserRole.USER).build();
        User approver = User.builder().userId(11L).userRole(UserRole.USER).canApproveAttendance(true).isActive(true).build();
        User nonApprover = User.builder().userId(12L).userRole(UserRole.USER).canApproveAttendance(false).isActive(true).build();
        User admin = User.builder().userId(13L).userRole(UserRole.ADMIN).isActive(true).build();

        when(userService.getActiveUsers()).thenReturn(List.of(applicant, approver, nonApprover, admin));
        when(userService.isAttendanceApprover(approver)).thenReturn(true);
        when(userService.isAttendanceApprover(nonApprover)).thenReturn(false);
        when(userService.isAttendanceApprover(admin)).thenReturn(true);

        service.notifyApproversNewSubmission(10L, "申請者A", "2026-06");

        ArgumentCaptor<UserNotification> captor = ArgumentCaptor.forClass(UserNotification.class);
        verify(userNotificationMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        assertThat(captor.getAllValues())
            .extracting(u -> u.getUserId())
            .containsExactly(11L, 13L);
        assertThat(captor.getAllValues())
            .extracting(u -> u.getNotificationType())
            .containsOnly(UserNotificationService.TYPE_APPROVAL_REQUEST);
        }
}