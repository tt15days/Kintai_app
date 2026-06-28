package com.attendance.app.service;

import com.attendance.app.entity.LeaveApplication;
import com.attendance.app.entity.LeaveStatus;
import com.attendance.app.entity.LeaveType;
import com.attendance.app.mapper.LeaveApplicationMapper;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

    @InjectMocks
    private LeaveApplicationService service;

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

            service.rejectApplication(1L);

            assertThat(app.getStatus()).isEqualTo(LeaveStatus.REJECTED);
            verify(leaveApplicationMapper).update(app);
        }

        @Test
        @DisplayName("APPROVED の申請は却下できない")
        void approvedApplication_throwsOnReject() {
            LeaveApplication app = LeaveApplication.builder()
                    .applicationId(1L).status(LeaveStatus.APPROVED).build();
            when(leaveApplicationMapper.selectByIdForUpdate(1L)).thenReturn(Optional.of(app));

            assertThatThrownBy(() -> service.rejectApplication(1L))
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
                .leaveStartDate(LocalDate.of(2026, 6, 1))
                .leaveEndDate(LocalDate.of(2026, 6, 1))
                .status(LeaveStatus.PENDING)
                .build();
    }
}
