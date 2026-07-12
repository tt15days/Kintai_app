package com.attendance.app.service;

import com.attendance.app.entity.AttendanceSubmission;
import com.attendance.app.entity.AuditEventType;
import com.attendance.app.entity.User;
import com.attendance.app.entity.UserRole;
import com.attendance.app.mapper.AttendanceSubmissionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.YearMonth;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link AttendanceSubmissionService} における監査ログ呼び出しの単体テスト。
 *
 * <p>承認・差戻し・取下げ・申請提出・承認取消の各操作で
 * {@link AuditLogService#recordSubmissionEvent} が正しいパラメータで
 * 呼び出されることを検証する。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AttendanceSubmissionService 監査ログ")
class AttendanceSubmissionServiceAuditTest {

    @Mock
    private AttendanceSubmissionMapper attendanceSubmissionMapper;

    @Mock
    private UserService userService;

    @Mock
    private AttendancePeriodSettingService attendancePeriodSettingService;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private AttendanceApproverAssignmentService approverAssignmentService;

    @InjectMocks
    private AttendanceSubmissionService service;

    /** 管理者ユーザー（承認権限あり） */
    private User adminUser;

    /** 申請サンプル */
    private AttendanceSubmission pendingSubmission;

    @BeforeEach
    void setUp() {
        adminUser = User.builder()
                .userId(10L)
                .userRole(UserRole.ADMIN)
                .canApproveAttendance(true)
                .build();

        pendingSubmission = AttendanceSubmission.builder()
                .submissionId(99L)
                .userId(20L)
                .targetYearMonth("2026-04")
                .status(AttendanceSubmissionService.STATUS_PENDING)
                .submittedAt(Instant.now())
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // approve
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("approve")
    class Approve {

        @Test
        @DisplayName("承認成功時に SUBMISSION_APPROVED が記録される")
        void approve_recordsApprovedEvent() {
            when(userService.getUserById(10L)).thenReturn(Optional.of(adminUser));
            when(userService.isAttendanceApprover(adminUser)).thenReturn(true);
            when(attendanceSubmissionMapper.selectByIdForUpdate(99L))
                    .thenReturn(Optional.of(pendingSubmission));

            service.approve(99L, 10L, "問題なし");

            verify(auditLogService).recordSubmissionEvent(
                    eq(AuditEventType.SUBMISSION_APPROVED),
                    eq(10L),
                    eq(20L),
                    eq(99L),
                    contains("2026-04"));
        }

        @Test
        @DisplayName("承認権限なしの場合は監査ログは記録されない")
        void approve_noAuditWhenNotAuthorized() {
            User nonApprover = User.builder()
                    .userId(10L)
                    .userRole(UserRole.USER)
                    .canApproveAttendance(false)
                    .build();
            when(userService.getUserById(10L)).thenReturn(Optional.of(nonApprover));
            when(userService.isAttendanceApprover(nonApprover)).thenReturn(false);

            assertThatThrownBy(() -> service.approve(99L, 10L, null))
                    .isInstanceOf(IllegalArgumentException.class);

            verify(auditLogService, never()).recordSubmissionEvent(any(), any(), any(), any(), any());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // returnForCorrection
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("returnForCorrection")
    class ReturnForCorrection {

        @Test
        @DisplayName("差戻し成功時に SUBMISSION_RETURNED が記録される")
        void return_recordsReturnedEvent() {
            when(userService.getUserById(10L)).thenReturn(Optional.of(adminUser));
            when(userService.isAttendanceApprover(adminUser)).thenReturn(true);
            when(attendanceSubmissionMapper.selectByIdForUpdate(99L))
                    .thenReturn(Optional.of(pendingSubmission));

            service.returnForCorrection(99L, 10L, "修正してください");

            verify(auditLogService).recordSubmissionEvent(
                    eq(AuditEventType.SUBMISSION_RETURNED),
                    eq(10L),
                    eq(20L),
                    eq(99L),
                    contains("2026-04"));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // submitMonth
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("submitMonth")
    class SubmitMonth {

        @Test
        @DisplayName("初回申請時に SUBMISSION_SUBMITTED が記録される")
        void submit_newSubmission_recordsSubmittedEvent() {
            YearMonth ym = YearMonth.of(2026, 4);
            when(attendanceSubmissionMapper.selectByUserAndMonth(eq(20L), anyString()))
                    .thenReturn(Optional.empty());

            doAnswer(inv -> {
                AttendanceSubmission s = inv.getArgument(0);
                s.setSubmissionId(100L);
                return null;
            }).when(attendanceSubmissionMapper).insert(any());

            service.submitMonth(20L, ym);

            verify(auditLogService).recordSubmissionEvent(
                    eq(AuditEventType.SUBMISSION_SUBMITTED),
                    eq(20L),
                    eq(20L),
                    eq(100L),
                    anyString());
        }

        @Test
        @DisplayName("再申請時（RETURNED）にも SUBMISSION_SUBMITTED が記録される")
        void submit_resubmit_recordsSubmittedEvent() {
            YearMonth ym = YearMonth.of(2026, 4);
            AttendanceSubmission returnedSub = AttendanceSubmission.builder()
                    .submissionId(50L)
                    .userId(20L)
                    .targetYearMonth("2026-04")
                    .status(AttendanceSubmissionService.STATUS_RETURNED)
                    .submittedAt(Instant.now())
                    .build();
            when(attendanceSubmissionMapper.selectByUserAndMonth(eq(20L), anyString()))
                    .thenReturn(Optional.of(returnedSub));

            service.submitMonth(20L, ym);

            verify(auditLogService).recordSubmissionEvent(
                    eq(AuditEventType.SUBMISSION_SUBMITTED),
                    eq(20L),
                    eq(20L),
                    eq(50L),
                    contains("再申請"));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // withdrawSubmission
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("withdrawSubmission")
    class WithdrawSubmission {

        @Test
        @DisplayName("取下げ成功時に SUBMISSION_WITHDRAWN が記録される")
        void withdraw_recordsWithdrawnEvent() {
            YearMonth ym = YearMonth.of(2026, 4);
            when(attendanceSubmissionMapper.selectByUserAndMonth(eq(20L), anyString()))
                    .thenReturn(Optional.of(pendingSubmission));

            service.withdrawSubmission(20L, ym);

            verify(auditLogService).recordSubmissionEvent(
                    eq(AuditEventType.SUBMISSION_WITHDRAWN),
                    eq(20L),
                    eq(20L),
                    eq(99L),
                    contains("2026-04"));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // revokeApproval
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("revokeApproval")
    class RevokeApproval {

        @Test
        @DisplayName("承認取消成功時に APPROVAL_REVOKED が記録される")
        void revoke_recordsRevokedEvent() {
            AttendanceSubmission approvedSub = AttendanceSubmission.builder()
                    .submissionId(99L)
                    .userId(20L)
                    .targetYearMonth("2026-04")
                    .status(AttendanceSubmissionService.STATUS_APPROVED)
                    .build();
            when(attendanceSubmissionMapper.selectByIdForUpdate(99L))
                    .thenReturn(Optional.of(approvedSub));

            service.revokeApproval(99L, 10L);

            verify(auditLogService).recordSubmissionEvent(
                    eq(AuditEventType.APPROVAL_REVOKED),
                    eq(10L),
                    eq(20L),
                    eq(99L),
                    contains("2026-04"));
        }
    }
}
