package com.attendance.app.service;

import com.attendance.app.entity.AttendanceSubmission;
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
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AttendanceSubmissionService} の状態遷移・編集可否・ペイロール月解決ロジックの単体テスト。
 *
 * <p>監査ログ呼び出しのテストは {@link AttendanceSubmissionServiceAuditTest} に分離されています。
 * 本クラスはビジネスルール（状態遷移・権限チェック・編集制御）の回帰テストを担います。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AttendanceSubmissionService 状態遷移・編集制御")
class AttendanceSubmissionServiceStateTest {

    @Mock
    private AttendanceSubmissionMapper attendanceSubmissionMapper;
    @Mock
    private UserService userService;
    @Mock
    private AttendancePeriodSettingService attendancePeriodSettingService;
    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private AttendanceSubmissionService service;

    private User adminUser;
    private AttendanceSubmission pendingSubmission;
    private AttendanceSubmission approvedSubmission;

    @BeforeEach
    void setUp() {
        adminUser = User.builder()
                .userId(10L)
                .userRole(UserRole.ADMIN)
                .isActive(true)
                .canApproveAttendance(true)
                .build();

        pendingSubmission = AttendanceSubmission.builder()
                .submissionId(99L)
                .userId(20L)
                .targetYearMonth("2026-04")
                .status(AttendanceSubmissionService.STATUS_PENDING)
                .submittedAt(Instant.now())
                .build();

        approvedSubmission = AttendanceSubmission.builder()
                .submissionId(100L)
                .userId(20L)
                .targetYearMonth("2026-03")
                .status(AttendanceSubmissionService.STATUS_APPROVED)
                .submittedAt(Instant.now())
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // isEditableMonth
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("isEditableMonth")
    class IsEditableMonth {

        @Test
        @DisplayName("申請なし（empty）は編集可能")
        void noSubmission_isEditable() {
            when(attendanceSubmissionMapper.selectByUserAndMonth(2L, "2026-06"))
                    .thenReturn(Optional.empty());
            assertThat(service.isEditableMonth(2L, YearMonth.of(2026, 6))).isTrue();
        }

        @Test
        @DisplayName("RETURNED の月は編集可能")
        void returnedMonth_isEditable() {
            AttendanceSubmission returned = submissionWith("RETURNED");
            when(attendanceSubmissionMapper.selectByUserAndMonth(2L, "2026-06"))
                    .thenReturn(Optional.of(returned));
            assertThat(service.isEditableMonth(2L, YearMonth.of(2026, 6))).isTrue();
        }

        @Test
        @DisplayName("WITHDRAWN の月は編集可能")
        void withdrawnMonth_isEditable() {
            AttendanceSubmission withdrawn = submissionWith("WITHDRAWN");
            when(attendanceSubmissionMapper.selectByUserAndMonth(2L, "2026-06"))
                    .thenReturn(Optional.of(withdrawn));
            assertThat(service.isEditableMonth(2L, YearMonth.of(2026, 6))).isTrue();
        }

        @Test
        @DisplayName("PENDING の月は編集不可")
        void pendingMonth_isNotEditable() {
            when(attendanceSubmissionMapper.selectByUserAndMonth(2L, "2026-04"))
                    .thenReturn(Optional.of(pendingSubmission));
            assertThat(service.isEditableMonth(2L, YearMonth.of(2026, 4))).isFalse();
        }

        @Test
        @DisplayName("APPROVED の月は編集不可")
        void approvedMonth_isNotEditable() {
            when(attendanceSubmissionMapper.selectByUserAndMonth(2L, "2026-03"))
                    .thenReturn(Optional.of(approvedSubmission));
            assertThat(service.isEditableMonth(2L, YearMonth.of(2026, 3))).isFalse();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // assertEditableMonth
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("assertEditableMonth")
    class AssertEditableMonth {

        @Test
        @DisplayName("PENDING の月は例外を送出する")
        void pendingMonth_throwsException() {
            when(attendanceSubmissionMapper.selectByUserAndMonth(2L, "2026-04"))
                    .thenReturn(Optional.of(pendingSubmission));

            assertThatThrownBy(() -> service.assertEditableMonth(2L, YearMonth.of(2026, 4)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("申請中のため修正できません");
        }

        @Test
        @DisplayName("APPROVED の月は例外を送出する")
        void approvedMonth_throwsException() {
            when(attendanceSubmissionMapper.selectByUserAndMonth(2L, "2026-03"))
                    .thenReturn(Optional.of(approvedSubmission));

            assertThatThrownBy(() -> service.assertEditableMonth(2L, YearMonth.of(2026, 3)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("承認済みのため修正できません");
        }

        @Test
        @DisplayName("申請なし（empty）は例外を送出しない")
        void noSubmission_noException() {
            when(attendanceSubmissionMapper.selectByUserAndMonth(2L, "2026-06"))
                    .thenReturn(Optional.empty());

            // 例外が発生しなければOK
            service.assertEditableMonth(2L, YearMonth.of(2026, 6));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // submitMonth
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("submitMonth")
    class SubmitMonth {

        @Test
        @DisplayName("新規申請は PENDING で insert される")
        void newSubmission_insertsPending() {
            when(attendanceSubmissionMapper.selectByUserAndMonth(2L, "2026-06"))
                    .thenReturn(Optional.empty());

            service.submitMonth(2L, YearMonth.of(2026, 6));

            verify(attendanceSubmissionMapper).insert(any(AttendanceSubmission.class));
        }

        @Test
        @DisplayName("RETURNED の申請は再申請で PENDING に更新される")
        void returnedSubmission_resubmitsToPending() {
            AttendanceSubmission returned = submissionWith("RETURNED");
            returned.setSubmissionId(99L);
            returned.setUserId(2L);
            when(attendanceSubmissionMapper.selectByUserAndMonth(2L, "2026-06"))
                    .thenReturn(Optional.of(returned));

            service.submitMonth(2L, YearMonth.of(2026, 6));

            verify(attendanceSubmissionMapper).update(returned);
            assertThat(returned.getStatus()).isEqualTo(AttendanceSubmissionService.STATUS_PENDING);
        }

        @Test
        @DisplayName("PENDING の申請は再申請できない")
        void alreadyPending_throwsException() {
            when(attendanceSubmissionMapper.selectByUserAndMonth(20L, "2026-04"))
                    .thenReturn(Optional.of(pendingSubmission));

            assertThatThrownBy(() -> service.submitMonth(20L, YearMonth.of(2026, 4)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("既に申請中");

            verify(attendanceSubmissionMapper, never()).update(any());
        }

        @Test
        @DisplayName("APPROVED の申請は再申請できない")
        void alreadyApproved_throwsException() {
            when(attendanceSubmissionMapper.selectByUserAndMonth(20L, "2026-03"))
                    .thenReturn(Optional.of(approvedSubmission));

            assertThatThrownBy(() -> service.submitMonth(20L, YearMonth.of(2026, 3)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("既に承認済み");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // withdrawSubmission
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("withdrawSubmission")
    class WithdrawSubmission {

        @Test
        @DisplayName("PENDING を取り下げると WITHDRAWN になる")
        void pendingSubmission_becomesWithdrawn() {
            when(attendanceSubmissionMapper.selectByUserAndMonth(20L, "2026-04"))
                    .thenReturn(Optional.of(pendingSubmission));

            service.withdrawSubmission(20L, YearMonth.of(2026, 4));

            verify(attendanceSubmissionMapper).update(pendingSubmission);
            assertThat(pendingSubmission.getStatus()).isEqualTo(AttendanceSubmissionService.STATUS_WITHDRAWN);
        }

        @Test
        @DisplayName("APPROVED は取り下げできない")
        void approvedSubmission_throwsOnWithdraw() {
            when(attendanceSubmissionMapper.selectByUserAndMonth(20L, "2026-03"))
                    .thenReturn(Optional.of(approvedSubmission));

            assertThatThrownBy(() -> service.withdrawSubmission(20L, YearMonth.of(2026, 3)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("申請中のデータのみ取り下げできます");
        }

        @Test
        @DisplayName("申請なしは例外を送出する")
        void noSubmission_throwsException() {
            when(attendanceSubmissionMapper.selectByUserAndMonth(2L, "2026-06"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.withdrawSubmission(2L, YearMonth.of(2026, 6)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("申請が見つかりません");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // revokeApproval
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("revokeApproval")
    class RevokeApproval {

        @Test
        @DisplayName("APPROVED の申請を管理者が取り消すと RETURNED になる")
        void approvedSubmission_becomesReturned() {
            when(attendanceSubmissionMapper.selectByIdForUpdate(100L))
                    .thenReturn(Optional.of(approvedSubmission));

            service.revokeApproval(100L, 10L);

            verify(attendanceSubmissionMapper).update(approvedSubmission);
            assertThat(approvedSubmission.getStatus()).isEqualTo(AttendanceSubmissionService.STATUS_RETURNED);
        }

        @Test
        @DisplayName("PENDING の申請は承認取り消しできない")
        void pendingSubmission_throwsOnRevoke() {
            when(attendanceSubmissionMapper.selectByIdForUpdate(99L))
                    .thenReturn(Optional.of(pendingSubmission));

            assertThatThrownBy(() -> service.revokeApproval(99L, 10L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("承認済みの申請のみ取り消しできます");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // approve (権限チェック)
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("approve 権限チェック")
    class ApprovePermission {

        @Test
        @DisplayName("承認権限のないユーザーは承認できない")
        void noPermission_throwsException() {
            User nonApprover = User.builder()
                    .userId(5L).userRole(UserRole.USER).isActive(true).canApproveAttendance(false).build();
            when(userService.getUserById(5L)).thenReturn(Optional.of(nonApprover));
            when(userService.isAttendanceApprover(nonApprover)).thenReturn(false);

            assertThatThrownBy(() -> service.approve(99L, 5L, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("勤怠承認権限がありません");
        }

        @Test
        @DisplayName("PENDING 以外は承認できない")
        void nonPendingStatus_throwsException() {
            when(userService.getUserById(10L)).thenReturn(Optional.of(adminUser));
            when(userService.isAttendanceApprover(adminUser)).thenReturn(true);
            when(attendanceSubmissionMapper.selectByIdForUpdate(100L))
                    .thenReturn(Optional.of(approvedSubmission));

            assertThatThrownBy(() -> service.approve(100L, 10L, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("申請中のデータのみ承認できます");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // resolvePayrollMonth
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("resolvePayrollMonth")
    class ResolvePayrollMonth {

        @BeforeEach
        void setUp() {
            when(attendancePeriodSettingService.getStartDay()).thenReturn(21);
        }

        @Test
        @DisplayName("21日以降（開始日）は翌月扱い")
        void dayOnOrAfterStartDay_returnsNextMonth() {
            LocalDate date = LocalDate.of(2026, 5, 21);
            assertThat(service.resolvePayrollMonth(date)).isEqualTo(YearMonth.of(2026, 6));
        }

        @Test
        @DisplayName("20日（開始日前日）は当月扱い")
        void dayBeforeStartDay_returnsSameMonth() {
            LocalDate date = LocalDate.of(2026, 5, 20);
            assertThat(service.resolvePayrollMonth(date)).isEqualTo(YearMonth.of(2026, 5));
        }

        @Test
        @DisplayName("月末日（31日）は翌月扱い")
        void lastDayOfMonth_returnsNextMonth() {
            LocalDate date = LocalDate.of(2026, 5, 31);
            assertThat(service.resolvePayrollMonth(date)).isEqualTo(YearMonth.of(2026, 6));
        }

        @Test
        @DisplayName("月初（1日）は当月扱い")
        void firstDayOfMonth_returnsSameMonth() {
            LocalDate date = LocalDate.of(2026, 6, 1);
            assertThat(service.resolvePayrollMonth(date)).isEqualTo(YearMonth.of(2026, 6));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getPendingSubmissions
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getPendingSubmissions")
    class GetPendingSubmissions {

        @Test
        @DisplayName("管理者は全ての申請中一覧を取得できる")
        void admin_getsAllPending() {
            when(userService.isAttendanceApprover(adminUser)).thenReturn(true);
            when(attendanceSubmissionMapper.selectByStatus("PENDING"))
                    .thenReturn(List.of(pendingSubmission));

            List<AttendanceSubmission> result = service.getPendingSubmissions(adminUser);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getSubmissionId()).isEqualTo(99L);
        }

        @Test
        @DisplayName("承認権限のないユーザーは例外を送出する")
        void nonApprover_throwsException() {
            User nonApprover = User.builder()
                    .userId(5L).userRole(UserRole.USER).canApproveAttendance(false).build();
            when(userService.isAttendanceApprover(nonApprover)).thenReturn(false);

            assertThatThrownBy(() -> service.getPendingSubmissions(nonApprover))
                    .isInstanceOf(IllegalArgumentException.class);
        }

            @Test
            @DisplayName("一般承認者は同じ勤務クラスの申請のみ取得できる")
            void approver_getsOnlyAssignedByClass() {
                User approver = User.builder()
                    .userId(30L)
                    .userRole(UserRole.USER)
                    .className("A")
                    .isActive(true)
                    .canApproveAttendance(true)
                    .build();

                AttendanceSubmission sameClassSubmission = AttendanceSubmission.builder()
                    .submissionId(201L)
                    .userId(1001L)
                    .targetYearMonth("2026-05")
                    .status(AttendanceSubmissionService.STATUS_PENDING)
                    .build();
                AttendanceSubmission differentClassSubmission = AttendanceSubmission.builder()
                    .submissionId(202L)
                    .userId(1002L)
                    .targetYearMonth("2026-05")
                    .status(AttendanceSubmissionService.STATUS_PENDING)
                    .build();

                User applicantA = User.builder().userId(1001L).className("A").build();
                User applicantB = User.builder().userId(1002L).className("B").build();

                when(userService.isAttendanceApprover(approver)).thenReturn(true);
                when(attendanceSubmissionMapper.selectByStatus("PENDING"))
                    .thenReturn(List.of(sameClassSubmission, differentClassSubmission));
                when(userService.getUserById(1001L)).thenReturn(Optional.of(applicantA));
                when(userService.getUserById(1002L)).thenReturn(Optional.of(applicantB));

                List<AttendanceSubmission> result = service.getPendingSubmissions(approver);

                assertThat(result).extracting(s -> s.getSubmissionId())
                    .containsExactly(201L);
            }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ヘルパー
    // ─────────────────────────────────────────────────────────────────────────

    private AttendanceSubmission submissionWith(String status) {
        return AttendanceSubmission.builder()
                .submissionId(98L)
                .userId(2L)
                .targetYearMonth("2026-06")
                .status(status)
                .submittedAt(Instant.now())
                .build();
    }
}
