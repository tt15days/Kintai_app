package com.attendance.app.controller;

import com.attendance.app.entity.AttendanceSubmission;
import com.attendance.app.entity.LeaveApplication;
import com.attendance.app.entity.LeaveType;
import com.attendance.app.security.SecurityUtil;
import com.attendance.app.service.AttendanceSubmissionService;
import com.attendance.app.service.LeaveApplicationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("LeaveApplicationController")
class LeaveApplicationControllerTest {

    @Mock private LeaveApplicationService leaveApplicationService;
    @Mock private AttendanceSubmissionService attendanceSubmissionService;
    @Mock private SecurityUtil securityUtil;

    @InjectMocks
    private LeaveApplicationController controller;

    @Test
    @DisplayName("showLeaveApplicationList: ロック対象申請IDをモデルへ設定する")
    void showLeaveApplicationList_populatesLockedApplicationIds() {
        Long userId = 1L;
        LeaveApplication appLocked = LeaveApplication.builder()
                .applicationId(101L)
                .leaveStartDate(LocalDate.of(2026, 5, 10))
                .build();
        LeaveApplication appOpen = LeaveApplication.builder()
                .applicationId(102L)
                .leaveStartDate(LocalDate.of(2026, 6, 5))
                .build();

        when(securityUtil.getCurrentUserId()).thenReturn(userId);
        when(leaveApplicationService.getApplicationsByUserId(userId)).thenReturn(List.of(appLocked, appOpen));
        when(attendanceSubmissionService.getSubmissionsByUserId(userId)).thenReturn(List.of(
                AttendanceSubmission.builder().targetYearMonth("2026-05").status(AttendanceSubmissionService.STATUS_PENDING).build(),
                AttendanceSubmission.builder().targetYearMonth("2026-04").status(AttendanceSubmissionService.STATUS_APPROVED).build()));
        when(attendanceSubmissionService.resolvePayrollMonth(LocalDate.of(2026, 5, 10))).thenReturn(YearMonth.of(2026, 5));
        when(attendanceSubmissionService.resolvePayrollMonth(LocalDate.of(2026, 6, 5))).thenReturn(YearMonth.of(2026, 6));

        ExtendedModelMap model = new ExtendedModelMap();
        String view = controller.showLeaveApplicationList(null, model);

        assertThat(view).isEqualTo("user/leave-list");
        @SuppressWarnings("unchecked")
        Set<Long> lockedIds = (Set<Long>) model.getAttribute("lockedApplicationIds");
        assertThat(lockedIds).containsExactly(101L);
    }

    @Test
    @DisplayName("applyPaid: 編集可能月なら作成して即時承認し勤怠へ戻る")
    void applyPaidLeave_success_createsAndApproves() {
        Long userId = 2L;
        LocalDate date = LocalDate.of(2026, 6, 10);
        LeaveApplication created = LeaveApplication.builder().applicationId(201L).build();

        when(securityUtil.getCurrentUserId()).thenReturn(userId);
        when(attendanceSubmissionService.resolvePayrollMonth(date)).thenReturn(YearMonth.of(2026, 6));
        when(attendanceSubmissionService.isEditableMonth(userId, YearMonth.of(2026, 6))).thenReturn(true);
        when(leaveApplicationService.createApplication(userId, date, date, LeaveType.PAID_LEAVE, "勤怠からの有給申請"))
                .thenReturn(created);

        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();
        String view = controller.applyPaidLeave(date, "2026-06", null, redirect);

        assertThat(view).isEqualTo("redirect:/attendance?yearMonth=2026-06");
        verify(leaveApplicationService).approveApplication(201L, userId);
        assertThat(redirect.getFlashAttributes().get("message")).isEqualTo("有給休暇を申請しました");
    }

    @Test
    @DisplayName("applyLeave: ロック月の場合はエラーで保存しない")
    void applyLeave_lockedMonth_setsError() {
        Long userId = 3L;
        LocalDate date = LocalDate.of(2026, 5, 15);

        when(securityUtil.getCurrentUserId()).thenReturn(userId);
        when(attendanceSubmissionService.resolvePayrollMonth(date)).thenReturn(YearMonth.of(2026, 5));
        when(attendanceSubmissionService.isEditableMonth(userId, YearMonth.of(2026, 5))).thenReturn(false);

        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();
        String view = controller.applyLeave(date, LeaveType.SPECIAL_LEAVE, "2026-05", null, redirect);

        assertThat(view).isEqualTo("redirect:/attendance?yearMonth=2026-05");
        verify(leaveApplicationService, never()).createApplication(any(), any(), any(), any(), any());
        assertThat((String) redirect.getFlashAttributes().get("error"))
                .contains("2026-05")
                .contains("申請中または承認済み");
    }

    @Test
    @DisplayName("deleteLeaveApplication: ロック月の場合は削除せずエラーを返す")
    void deleteLeaveApplication_lockedMonth_doesNotDelete() {
        Long userId = 4L;
        Long appId = 301L;
        LeaveApplication app = LeaveApplication.builder()
                .applicationId(appId)
                .leaveStartDate(LocalDate.of(2026, 5, 20))
                .userId(userId)
                .build();

        when(securityUtil.getCurrentUserId()).thenReturn(userId);
        when(leaveApplicationService.getApplicationById(appId)).thenReturn(Optional.of(app));
        when(attendanceSubmissionService.resolvePayrollMonth(LocalDate.of(2026, 5, 20))).thenReturn(YearMonth.of(2026, 5));
        when(attendanceSubmissionService.isEditableMonth(userId, YearMonth.of(2026, 5))).thenReturn(false);

        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();
        String view = controller.deleteLeaveApplication(appId, redirect);

        assertThat(view).isEqualTo("redirect:/leave");
        verify(leaveApplicationService, never()).deleteApplication(appId);
        assertThat(redirect.getFlashAttributes().get("error")).isNotNull();
    }
}
