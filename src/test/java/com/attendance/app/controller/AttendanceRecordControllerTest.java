package com.attendance.app.controller;

import com.attendance.app.entity.AttendanceCorrectionRequest;
import com.attendance.app.entity.AttendanceRecord;
import com.attendance.app.entity.User;
import com.attendance.app.entity.UserRole;
import com.attendance.app.security.SecurityUtil;
import com.attendance.app.service.AttendanceCorrectionRequestService;
import com.attendance.app.service.AttendancePeriodSettingService;
import com.attendance.app.service.AttendanceRecordService;
import com.attendance.app.service.AttendanceSubmissionService;
import com.attendance.app.service.BatchSettingService;
import com.attendance.app.service.HolidayService;
import com.attendance.app.service.LeaveApplicationService;
import com.attendance.app.service.PaidLeaveBalanceService;
import com.attendance.app.service.EventTypeService;
import com.attendance.app.service.UserNotificationService;
import com.attendance.app.service.UserService;
import com.attendance.app.service.WorkScheduleClassService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AttendanceRecordControllerTest {

    @Mock
    private AttendanceRecordService attendanceRecordService;

    @Mock
    private AttendanceSubmissionService attendanceSubmissionService;

    @Mock
    private AttendanceCorrectionRequestService correctionRequestService;

    @Mock
    private LeaveApplicationService leaveApplicationService;

    @Mock
    private HolidayService holidayService;

    @Mock
    private SecurityUtil securityUtil;

    @Mock
    private UserService userService;

    @Mock
    private AttendancePeriodSettingService attendancePeriodSettingService;

    @Mock
    private UserNotificationService userNotificationService;

    @Mock
    private PaidLeaveBalanceService paidLeaveBalanceService;

    @Mock
    private EventTypeService eventTypeService;

    @Mock
    private BatchSettingService batchSettingService;

    @Mock
    private WorkScheduleClassService workScheduleClassService;

    @InjectMocks
    private AttendanceRecordController controller;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setUserId(1L);
        testUser.setFullName("テスト ユーザー");
        testUser.setUserRole(UserRole.USER);

        lenient().when(securityUtil.getCurrentUser()).thenReturn(testUser);
        lenient().when(securityUtil.getCurrentUserId()).thenReturn(1L);
        lenient().when(attendancePeriodSettingService.getStartDay()).thenReturn(21);
        lenient().when(attendanceRecordService.getMonthRange(any(YearMonth.class)))
                .thenAnswer(invocation -> new AttendanceRecordService.MonthRange(invocation.getArgument(0), 21, 20));
        lenient().when(attendanceSubmissionService.getSubmission(anyLong(), any(YearMonth.class))).thenReturn(Optional.empty());
        lenient().when(attendanceSubmissionService.isEditableMonth(anyLong(), any(YearMonth.class))).thenReturn(true);
        lenient().when(holidayService.getHolidaysByYear(org.mockito.ArgumentMatchers.anyInt())).thenReturn(Collections.emptySet());
        lenient().when(leaveApplicationService.getApplicationsByUserAndDateRange(anyLong(), any(), any()))
                .thenReturn(Collections.emptyList());
        lenient().when(userService.isAttendanceApprover(testUser)).thenReturn(false);
        lenient().when(userService.getUserById(1L)).thenReturn(Optional.of(testUser));
        lenient().when(attendanceRecordService.getStandardWorkingHours(1L)).thenReturn(8.0);
        lenient().when(attendanceRecordService.getRecordByUserAndDate(anyLong(), any())).thenReturn(Optional.empty());
        lenient().when(paidLeaveBalanceService.getBalancesByUserId(1L)).thenReturn(Collections.emptyList());
        lenient().when(paidLeaveBalanceService.getTotalRemainingDays(1L)).thenReturn(BigDecimal.ZERO);
        lenient().when(attendanceRecordService.checkArticle36(any(Double.class)))
                .thenReturn("NORMAL");
        lenient().when(eventTypeService.getAllActiveEventTypes()).thenReturn(Collections.emptyList());
        lenient().when(batchSettingService.getAlertArticle36Limit1()).thenReturn(36);
        lenient().when(batchSettingService.getAlertArticle36Limit2()).thenReturn(45);
    }

    @Test
    void testShowAttendanceForm_Success() {
        when(attendanceRecordService.getRecordsByUserAndMonth(anyLong(), any(YearMonth.class)))
                .thenReturn(Collections.emptyList());

        ExtendedModelMap model = new ExtendedModelMap();
        String view = controller.showAttendanceForm(null, model);

        assertEquals("user/attendance", view);
        assertEquals(1L, model.getAttribute("userId"));
    }

    @Test
    void testShowAttendanceForm_WithTargetDate() {
        when(attendanceRecordService.getRecordsByUserAndMonth(anyLong(), any(YearMonth.class)))
                .thenReturn(Collections.emptyList());

        ExtendedModelMap model = new ExtendedModelMap();
        String view = controller.showAttendanceForm("2026-05", model);

        assertEquals("user/attendance", view);
        assertEquals("2026-05", model.getAttribute("yearMonth"));
    }

    @Test
    void testShowAttendanceForm_ExceptionHandling() {
        when(attendanceRecordService.getRecordsByUserAndMonth(anyLong(), any(YearMonth.class)))
                .thenThrow(new RuntimeException("DB Connection Error"));

        ExtendedModelMap model = new ExtendedModelMap();
        String view = controller.showAttendanceForm(null, model);

        assertEquals("user/attendance", view);
        assertEquals("画面の表示に失敗しました", model.getAttribute("error"));
    }

    // -------------------------------------------------------
    // 勤怠修正申請関連エンドポイント
    // -------------------------------------------------------

    @Test
    void testShowCorrectionRequestList_Success() {
        AttendanceCorrectionRequest request = AttendanceCorrectionRequest.builder()
                .requestId(1L)
                .userId(1L)
                .status("PENDING")
                .build();
        when(correctionRequestService.getRequestsByUserId(1L)).thenReturn(List.of(request));

        ExtendedModelMap model = new ExtendedModelMap();
        String view = controller.showCorrectionRequestList(model);

        assertEquals("user/correction-request-list", view);
        assertEquals(List.of(request), model.getAttribute("requests"));
    }

    @Test
    void testShowCorrectionRequestList_ExceptionHandling() {
        when(correctionRequestService.getRequestsByUserId(1L)).thenThrow(new RuntimeException("DB error"));

        ExtendedModelMap model = new ExtendedModelMap();
        String view = controller.showCorrectionRequestList(model);

        assertEquals("user/correction-request-list", view);
        assertEquals("修正申請一覧の表示に失敗しました", model.getAttribute("error"));
        assertEquals(Collections.emptyList(), model.getAttribute("requests"));
    }

    @Test
    void testShowCorrectionRequestForm_WithExistingRecord() {
        LocalDate targetDate = LocalDate.of(2026, 5, 10);
        AttendanceRecord record = AttendanceRecord.builder()
                .startTime(java.time.Instant.parse("2026-05-10T00:00:00Z"))
                .endTime(java.time.Instant.parse("2026-05-10T09:00:00Z"))
                .remarks("既存備考")
                .build();
        when(attendanceRecordService.getRecordByUserAndDate(1L, targetDate)).thenReturn(Optional.of(record));

        ExtendedModelMap model = new ExtendedModelMap();
        String view = controller.showCorrectionRequestForm("2026-05-10", "2026-05", model);

        assertEquals("user/correction-request-create", view);
        assertEquals(targetDate, model.getAttribute("targetDate"));
        assertEquals("既存備考", model.getAttribute("currentRemarks"));
    }

    @Test
    void testShowCorrectionRequestForm_NoDate() {
        ExtendedModelMap model = new ExtendedModelMap();
        String view = controller.showCorrectionRequestForm(null, null, model);

        assertEquals("user/correction-request-create", view);
        assertEquals(null, model.getAttribute("targetDate"));
    }

    @Test
    void testSubmitCorrectionRequest_Success() {
        LocalDate date = LocalDate.of(2026, 5, 10);
        when(userService.getUserById(1L)).thenReturn(Optional.of(testUser));

        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();
        String view = controller.submitCorrectionRequest("2026-05-10", "09:00", "18:00", "残業対応", "打刻漏れ", redirect);

        assertEquals("redirect:/attendance/corrections", view);
        verify(correctionRequestService).submitRequest(eq(1L), eq(date), any(), any(), eq("残業対応"), eq("打刻漏れ"));
        assertThat(redirect.getFlashAttributes().get("message")).isEqualTo("2026-05-10 の勤怠修正申請を提出しました。承認者の確認をお待ちください。");
    }

    @Test
    void testSubmitCorrectionRequest_IllegalArgument_SetsError() {
        when(correctionRequestService.submitRequest(eq(1L), any(LocalDate.class), any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("既に申請済みです"));

        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();
        String view = controller.submitCorrectionRequest("2026-05-10", "09:00", "18:00", null, "打刻漏れ", redirect);

        assertEquals("redirect:/attendance/corrections", view);
        assertThat(redirect.getFlashAttributes().get("error")).isEqualTo("既に申請済みです");
    }

    @Test
    void testWithdrawCorrectionRequest_Success() {
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();
        String view = controller.withdrawCorrectionRequest(5L, redirect);

        assertEquals("redirect:/attendance/corrections", view);
        verify(correctionRequestService).withdrawRequest(5L, 1L);
        assertThat(redirect.getFlashAttributes().get("message")).isEqualTo("勤怠修正申請を取り下げました");
    }

    @Test
    void testWithdrawCorrectionRequest_IllegalArgument_SetsError() {
        org.mockito.Mockito.doThrow(new IllegalArgumentException("取り下げできません"))
                .when(correctionRequestService).withdrawRequest(5L, 1L);

        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();
        String view = controller.withdrawCorrectionRequest(5L, redirect);

        assertEquals("redirect:/attendance/corrections", view);
        assertThat(redirect.getFlashAttributes().get("error")).isEqualTo("取り下げできません");
        verify(correctionRequestService, never()).submitRequest(any(), any(), any(), any(), any(), any());
    }
}
