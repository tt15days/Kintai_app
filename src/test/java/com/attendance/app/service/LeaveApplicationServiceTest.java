package com.attendance.app.service;

import com.attendance.app.entity.LeaveApplication;
import com.attendance.app.entity.LeaveStatus;
import com.attendance.app.entity.LeaveType;
import com.attendance.app.mapper.LeaveApplicationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link LeaveApplicationService} の単体テスト。
 *
 * <p>休暇申請の作成・更新・承認・却下・削除のビジネスルールを検証します。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LeaveApplicationService")
class LeaveApplicationServiceTest {

    @Mock
    private LeaveApplicationMapper leaveApplicationMapper;

    @Mock
    private PaidLeaveBalanceService paidLeaveBalanceService;

    @Mock
    private HolidayService holidayService;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private LeaveApplicationService service;

    @BeforeEach
    void stubHolidayMaster() {
        lenient().when(holidayService.getHolidaysByYear(anyInt())).thenReturn(Collections.emptySet());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // createApplication
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("createApplication")
    class CreateApplication {

        @Test
        @DisplayName("正常な期間で PENDING の申請が作成される")
        void validPeriod_createsPendingApplication() {
            LocalDate start = LocalDate.of(2026, 6, 1);
            LocalDate end   = LocalDate.of(2026, 6, 3);
            when(paidLeaveBalanceService.getTotalRemainingDays(2L)).thenReturn(new BigDecimal("10"));

            service.createApplication(2L, start, end, LeaveType.PAID_LEAVE, "年休消化");

            ArgumentCaptor<LeaveApplication> captor = ArgumentCaptor.forClass(LeaveApplication.class);
            verify(leaveApplicationMapper).insert(captor.capture());

            LeaveApplication saved = captor.getValue();
            assertThat(saved.getUserId()).isEqualTo(2L);
            assertThat(saved.getLeaveStartDate()).isEqualTo(start);
            assertThat(saved.getLeaveEndDate()).isEqualTo(end);
            assertThat(saved.getLeaveType()).isEqualTo(LeaveType.PAID_LEAVE);
            assertThat(saved.getStatus()).isEqualTo(LeaveStatus.PENDING);
            assertThat(saved.getReason()).isEqualTo("年休消化");
            assertThat(saved.getCreatedAt()).isNotNull();
        }

        @Test
        @DisplayName("開始日が終了日より後の場合は例外を送出する")
        void startAfterEnd_throwsException() {
            assertThatThrownBy(() ->
                    service.createApplication(2L,
                            LocalDate.of(2026, 6, 5),
                            LocalDate.of(2026, 6, 3),
                            LeaveType.PAID_LEAVE, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("開始日は終了日より前");

            verify(leaveApplicationMapper, never()).insert(any());
        }

        @Test
        @DisplayName("同日（開始日＝終了日）は正常に作成される")
        void sameStartEnd_createsSuccessfully() {
            LocalDate sameDay = LocalDate.of(2026, 6, 10);
            service.createApplication(2L, sameDay, sameDay, LeaveType.SPECIAL_LEAVE, null);
            verify(leaveApplicationMapper).insert(any());
        }

        @Test
        @DisplayName("有給休暇で残日数が不足している場合は例外を送出する")
        void paidLeave_insufficientBalance_throwsException() {
            LocalDate start = LocalDate.of(2026, 6, 1);
            LocalDate end   = LocalDate.of(2026, 6, 5);
            when(paidLeaveBalanceService.getTotalRemainingDays(2L)).thenReturn(new BigDecimal("2"));

            assertThatThrownBy(() ->
                    service.createApplication(2L, start, end, LeaveType.PAID_LEAVE, "年休消化"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("不足");

            verify(leaveApplicationMapper, never()).insert(any());
        }

        @Test
        @DisplayName("半休は開始日と終了日が異なる場合は例外を送出する")
        void halfDay_multiDay_throwsException() {
            assertThatThrownBy(() ->
                    service.createApplication(2L,
                            LocalDate.of(2026, 6, 1),
                            LocalDate.of(2026, 6, 2),
                            "AM_HALF", LeaveType.PAID_LEAVE, "半休"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("半休");

            verify(leaveApplicationMapper, never()).insert(any());
        }

        @Test
        @DisplayName("既存の有効な申請と期間が重複する場合は例外を送出する")
        void overlappingApplication_throwsException() {
            LocalDate start = LocalDate.of(2026, 6, 1);
            LocalDate end   = LocalDate.of(2026, 6, 3);
            LeaveApplication existing = LeaveApplication.builder()
                    .applicationId(99L)
                    .userId(2L)
                    .leaveStartDate(LocalDate.of(2026, 6, 2))
                    .leaveEndDate(LocalDate.of(2026, 6, 2))
                    .status(LeaveStatus.APPROVED)
                    .build();
            when(leaveApplicationMapper.selectByUserAndDateRange(2L, start, end))
                    .thenReturn(List.of(existing));

            assertThatThrownBy(() ->
                    service.createApplication(2L, start, end, LeaveType.SPECIAL_LEAVE, "重複"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("既に休暇申請が存在します");

            verify(leaveApplicationMapper, never()).insert(any());
        }

        @Test
        @DisplayName("却下済みの申請とは重複判定されず正常に作成される")
        void rejectedApplication_doesNotBlockCreation() {
            LocalDate start = LocalDate.of(2026, 6, 1);
            LocalDate end   = LocalDate.of(2026, 6, 3);
            LeaveApplication rejected = LeaveApplication.builder()
                    .applicationId(98L)
                    .userId(2L)
                    .leaveStartDate(LocalDate.of(2026, 6, 2))
                    .leaveEndDate(LocalDate.of(2026, 6, 2))
                    .status(LeaveStatus.REJECTED)
                    .build();
            when(leaveApplicationMapper.selectByUserAndDateRange(2L, start, end))
                    .thenReturn(List.of(rejected));

            service.createApplication(2L, start, end, LeaveType.SPECIAL_LEAVE, "OK");

            verify(leaveApplicationMapper).insert(any());
        }

        @Test
        @DisplayName("重複チェック前にユーザー単位のadvisory lockを取得する（TOCTOU対策）")
        void acquiresUserLock_beforeOverlapCheck() {
            LocalDate start = LocalDate.of(2026, 6, 1);
            LocalDate end   = LocalDate.of(2026, 6, 3);

            service.createApplication(2L, start, end, LeaveType.SPECIAL_LEAVE, "OK");

            org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(leaveApplicationMapper);
            inOrder.verify(leaveApplicationMapper).acquireUserLock(2L);
            inOrder.verify(leaveApplicationMapper).selectByUserAndDateRange(2L, start, end);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // updateApplication
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("updateApplication")
    class UpdateApplication {

        @Test
        @DisplayName("PENDING の申請は更新できる")
        void pendingApplication_canBeUpdated() {
            LeaveApplication app = pendingApp(1L);
            when(leaveApplicationMapper.selectByIdForUpdate(1L)).thenReturn(Optional.of(app));

            service.updateApplication(1L, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 2), LeaveType.UNPAID_LEAVE, "変更");

            verify(leaveApplicationMapper).update(app);
            assertThat(app.getLeaveType()).isEqualTo(LeaveType.UNPAID_LEAVE);
        }

        @Test
        @DisplayName("APPROVED の申請は更新できない")
        void approvedApplication_throwsOnUpdate() {
            LeaveApplication app = LeaveApplication.builder()
                    .applicationId(1L).status(LeaveStatus.APPROVED).build();
            when(leaveApplicationMapper.selectByIdForUpdate(1L)).thenReturn(Optional.of(app));

            assertThatThrownBy(() ->
                    service.updateApplication(1L,
                            LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 2),
                            LeaveType.PAID_LEAVE, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("承認済みの申請は更新できません");

            verify(leaveApplicationMapper, never()).update(any());
        }

        @Test
        @DisplayName("存在しない申請 ID は例外を送出する")
        void notFound_throwsException() {
            when(leaveApplicationMapper.selectByIdForUpdate(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    service.updateApplication(99L,
                            LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 2),
                            LeaveType.PAID_LEAVE, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // approveApplication
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("approveApplication")
    class ApproveApplication {

        @Test
        @DisplayName("PENDING の申請を承認すると APPROVED になる")
        void pendingApplication_becomesApproved() {
            LeaveApplication app = pendingApp(1L);
            when(leaveApplicationMapper.selectByIdForUpdate(1L)).thenReturn(Optional.of(app));

            service.approveApplication(1L, 10L);

            assertThat(app.getStatus()).isEqualTo(LeaveStatus.APPROVED);
            assertThat(app.getApprovedBy()).isEqualTo(10L);
            assertThat(app.getApprovedAt()).isNotNull();
            verify(leaveApplicationMapper).update(app);
            verify(auditLogService).recordLeaveApplicationEvent(
                    eq(com.attendance.app.entity.AuditEventType.LEAVE_APPROVED), eq(10L), eq(2L), eq(1L), any());
        }

        @Test
        @DisplayName("有給休暇の承認時は paidLeaveBalanceService.deductBalance が呼ばれる")
        void paidLeaveApproval_deductsBalance() {
            LeaveApplication app = LeaveApplication.builder()
                    .applicationId(2L)
                    .userId(3L)
                    .leaveType(LeaveType.PAID_LEAVE)
                    .leaveStartDate(LocalDate.of(2026, 6, 1))
                    .leaveEndDate(LocalDate.of(2026, 6, 3))
                    .status(LeaveStatus.PENDING)
                    .build();
            when(leaveApplicationMapper.selectByIdForUpdate(2L)).thenReturn(Optional.of(app));

            service.approveApplication(2L, 10L);

            // 3日間（6/1, 6/2, 6/3）なので 3 日分を控除
            verify(paidLeaveBalanceService).deductBalance(3L, BigDecimal.valueOf(3L), LocalDate.of(2026, 6, 1));
        }

        @Test
        @DisplayName("半休（AM_HALF）の承認時は 0.5 日分のみ控除される")
        void halfDayPaidLeaveApproval_deductsHalfDay() {
            LeaveApplication app = LeaveApplication.builder()
                    .applicationId(7L)
                    .userId(3L)
                    .leaveType(LeaveType.PAID_LEAVE)
                    .leaveDurationType("AM_HALF")
                    .leaveStartDate(LocalDate.of(2026, 6, 1))
                    .leaveEndDate(LocalDate.of(2026, 6, 1))
                    .status(LeaveStatus.PENDING)
                    .build();
            when(leaveApplicationMapper.selectByIdForUpdate(7L)).thenReturn(Optional.of(app));

            service.approveApplication(7L, 10L);

            verify(paidLeaveBalanceService).deductBalance(3L, new BigDecimal("0.5"), LocalDate.of(2026, 6, 1));
        }

        @Test
        @DisplayName("残高不足の場合は例外が伝播しステータスが更新されない")
        void insufficientBalance_propagatesExceptionAndDoesNotUpdate() {
            LeaveApplication app = LeaveApplication.builder()
                    .applicationId(8L)
                    .userId(3L)
                    .leaveType(LeaveType.PAID_LEAVE)
                    .leaveStartDate(LocalDate.of(2026, 6, 1))
                    .leaveEndDate(LocalDate.of(2026, 6, 1))
                    .status(LeaveStatus.PENDING)
                    .build();
            when(leaveApplicationMapper.selectByIdForUpdate(8L)).thenReturn(Optional.of(app));
            org.mockito.Mockito.doThrow(new IllegalArgumentException("有給休暇の残日数が不足しています（不足: 1日）"))
                    .when(paidLeaveBalanceService).deductBalance(any(), any(), any());

            assertThatThrownBy(() -> service.approveApplication(8L, 10L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("不足");

            assertThat(app.getStatus()).isEqualTo(LeaveStatus.PENDING);
            verify(leaveApplicationMapper, never()).update(any());
        }

        @Test
        @DisplayName("有給以外の承認では paidLeaveBalanceService は呼ばれない")
        void nonPaidLeaveApproval_doesNotDeduct() {
            LeaveApplication app = LeaveApplication.builder()
                    .applicationId(3L)
                    .userId(4L)
                    .leaveType(LeaveType.SPECIAL_LEAVE)
                    .leaveStartDate(LocalDate.of(2026, 6, 1))
                    .leaveEndDate(LocalDate.of(2026, 6, 1))
                    .status(LeaveStatus.PENDING)
                    .build();
            when(leaveApplicationMapper.selectByIdForUpdate(3L)).thenReturn(Optional.of(app));

            service.approveApplication(3L, 10L);

            verify(paidLeaveBalanceService, never()).deductBalance(any(), any(), any());
        }

        @Test
        @DisplayName("承認時に計算した消化日数が申請に保存される")
        void approval_savesConsumedDaysOnApplication() {
            LeaveApplication app = LeaveApplication.builder()
                    .applicationId(2L)
                    .userId(3L)
                    .leaveType(LeaveType.PAID_LEAVE)
                    .leaveStartDate(LocalDate.of(2026, 6, 1))
                    .leaveEndDate(LocalDate.of(2026, 6, 3))
                    .status(LeaveStatus.PENDING)
                    .build();
            when(leaveApplicationMapper.selectByIdForUpdate(2L)).thenReturn(Optional.of(app));

            service.approveApplication(2L, 10L);

            assertThat(app.getConsumedDays()).isEqualByComparingTo(BigDecimal.valueOf(3L));
        }

        @Test
        @DisplayName("PENDING 以外の申請は承認できない")
        void alreadyApproved_throwsException() {
            LeaveApplication app = LeaveApplication.builder()
                    .applicationId(1L).status(LeaveStatus.APPROVED).build();
            when(leaveApplicationMapper.selectByIdForUpdate(1L)).thenReturn(Optional.of(app));

            assertThatThrownBy(() -> service.approveApplication(1L, 10L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("申請中のステータスのみ承認できます");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // rejectApplication
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("rejectApplication")
    class RejectApplication {

        @Test
        @DisplayName("PENDING の申請を却下すると REJECTED になる")
        void pendingApplication_becomesRejected() {
            LeaveApplication app = pendingApp(1L);
            when(leaveApplicationMapper.selectByIdForUpdate(1L)).thenReturn(Optional.of(app));

            service.rejectApplication(1L, 10L);

            assertThat(app.getStatus()).isEqualTo(LeaveStatus.REJECTED);
            verify(leaveApplicationMapper).update(app);
            verify(auditLogService).recordLeaveApplicationEvent(
                    eq(com.attendance.app.entity.AuditEventType.LEAVE_REJECTED), eq(10L), eq(2L), eq(1L), any());
        }

        @Test
        @DisplayName("APPROVED の申請は却下できない")
        void approvedApplication_throwsOnReject() {
            LeaveApplication app = LeaveApplication.builder()
                    .applicationId(1L).status(LeaveStatus.APPROVED).build();
            when(leaveApplicationMapper.selectByIdForUpdate(1L)).thenReturn(Optional.of(app));

            assertThatThrownBy(() -> service.rejectApplication(1L, 10L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("申請中のステータスのみ却下できます");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // deleteApplication
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("deleteApplication")
    class DeleteApplication {

        @Test
        @DisplayName("deleteApplication は Mapper の deleteById を呼び出し、有給未承認の場合は返還されない")
        void callsMapperDeleteById_notRefunded() {
            LeaveApplication app = LeaveApplication.builder()
                    .applicationId(5L)
                    .userId(3L)
                    .leaveType(LeaveType.PAID_LEAVE)
                    .leaveStartDate(LocalDate.of(2026, 6, 1))
                    .leaveEndDate(LocalDate.of(2026, 6, 1))
                    .status(LeaveStatus.PENDING)
                    .build();
            when(leaveApplicationMapper.selectByIdForUpdate(5L)).thenReturn(Optional.of(app));

            service.deleteApplication(5L);

            verify(paidLeaveBalanceService, never()).refundBalance(any(), any(), any());
            verify(leaveApplicationMapper).deleteById(5L);
        }

        @Test
        @DisplayName("承認済みの有給申請を削除した場合は paidLeaveBalanceService.refundBalance が呼ばれて返還される")
        void approvedPaidLeaveDelete_refundsBalance() {
            LeaveApplication app = LeaveApplication.builder()
                    .applicationId(6L)
                    .userId(3L)
                    .leaveType(LeaveType.PAID_LEAVE)
                    .leaveStartDate(LocalDate.of(2026, 6, 1))
                    .leaveEndDate(LocalDate.of(2026, 6, 3))
                    .status(LeaveStatus.APPROVED)
                    .build();
            when(leaveApplicationMapper.selectByIdForUpdate(6L)).thenReturn(Optional.of(app));

            service.deleteApplication(6L);

            // 3日間（6/1, 6/2, 6/3）なので 3 日分を返還
            verify(paidLeaveBalanceService).refundBalance(3L, BigDecimal.valueOf(3L), LocalDate.of(2026, 6, 1));
            verify(leaveApplicationMapper).deleteById(6L);
        }

        @Test
        @DisplayName("承認済みの半休（PM_HALF）申請を削除した場合は 0.5 日分のみ返還される")
        void approvedHalfDayPaidLeaveDelete_refundsHalfDay() {
            LeaveApplication app = LeaveApplication.builder()
                    .applicationId(9L)
                    .userId(3L)
                    .leaveType(LeaveType.PAID_LEAVE)
                    .leaveDurationType("PM_HALF")
                    .leaveStartDate(LocalDate.of(2026, 6, 1))
                    .leaveEndDate(LocalDate.of(2026, 6, 1))
                    .status(LeaveStatus.APPROVED)
                    .build();
            when(leaveApplicationMapper.selectByIdForUpdate(9L)).thenReturn(Optional.of(app));

            service.deleteApplication(9L);

            verify(paidLeaveBalanceService).refundBalance(3L, new BigDecimal("0.5"), LocalDate.of(2026, 6, 1));
            verify(leaveApplicationMapper).deleteById(9L);
        }

        @Test
        @DisplayName("承認時に保存された消化日数がある場合はそれを優先し、再計算しない（holidaysマスタ変更による乖離防止）")
        void approvedPaidLeaveDelete_prefersStoredConsumedDaysOverRecalculation() {
            LeaveApplication app = LeaveApplication.builder()
                    .applicationId(10L)
                    .userId(3L)
                    .leaveType(LeaveType.PAID_LEAVE)
                    .leaveStartDate(LocalDate.of(2026, 6, 1))
                    .leaveEndDate(LocalDate.of(2026, 6, 3))
                    .status(LeaveStatus.APPROVED)
                    .consumedDays(new BigDecimal("2.0"))
                    .build();
            when(leaveApplicationMapper.selectByIdForUpdate(10L)).thenReturn(Optional.of(app));

            service.deleteApplication(10L);

            // 期間だけなら3日分だが、保存された2.0日を優先して返還する
            verify(paidLeaveBalanceService).refundBalance(3L, new BigDecimal("2.0"), LocalDate.of(2026, 6, 1));
            verify(leaveApplicationMapper).deleteById(10L);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // calculateLeaveDays
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("calculateLeaveDays")
    class CalculateLeaveDays {

        @Test
        @DisplayName("同日（1日）は 1 を返す")
        void sameDay_returnsOne() {
            LocalDate d = LocalDate.of(2026, 6, 10);
            assertThat(service.calculateLeaveDays(d, d)).isEqualTo(1L);
        }

        @Test
        @DisplayName("3日間（6/1〜6/3）は 3 を返す")
        void threeDays_returnsThree() {
            assertThat(service.calculateLeaveDays(
                    LocalDate.of(2026, 6, 1),
                    LocalDate.of(2026, 6, 3))).isEqualTo(3L);
        }

        @Test
        @DisplayName("1週間（7/1〜7/7）は 7 を返す")
        void oneWeek_returnsSeven() {
            assertThat(service.calculateLeaveDays(
                    LocalDate.of(2026, 7, 1),
                    LocalDate.of(2026, 7, 7))).isEqualTo(7L);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ヘルパー
    // ─────────────────────────────────────────────────────────────────────────

    private LeaveApplication pendingApp(Long id) {
        return LeaveApplication.builder()
                .applicationId(id)
                .userId(2L)
                .leaveType(LeaveType.PAID_LEAVE)
                .leaveDurationType("FULL_DAY")
                .leaveStartDate(LocalDate.of(2026, 6, 1))
                .leaveEndDate(LocalDate.of(2026, 6, 1))
                .status(LeaveStatus.PENDING)
                .build();
    }
}
