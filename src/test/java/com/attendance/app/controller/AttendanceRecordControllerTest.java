package com.attendance.app.controller;

import com.attendance.app.entity.User;
import com.attendance.app.entity.UserRole;
import com.attendance.app.security.SecurityUtil;
import com.attendance.app.service.AttendanceCorrectionRequestService;
import com.attendance.app.service.AttendancePeriodSettingService;
import com.attendance.app.service.AttendanceRecordService;
import com.attendance.app.service.AttendanceSubmissionService;
import com.attendance.app.service.HolidayService;
import com.attendance.app.service.LeaveApplicationService;
import com.attendance.app.service.PaidLeaveBalanceService;
import com.attendance.app.service.EventTypeService;
import com.attendance.app.service.UserNotificationService;
import com.attendance.app.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ExtendedModelMap;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
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

    @InjectMocks
    private AttendanceRecordController controller;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setUserId(1L);
        testUser.setFullName("テスト ユーザー");
        testUser.setUserRole(UserRole.USER);

        when(securityUtil.getCurrentUser()).thenReturn(testUser);
        when(securityUtil.getCurrentUserId()).thenReturn(1L);
        when(attendancePeriodSettingService.getStartDay()).thenReturn(21);
        when(attendanceRecordService.getMonthRange(any(YearMonth.class)))
                .thenAnswer(invocation -> new AttendanceRecordService.MonthRange(invocation.getArgument(0), 21, 20));
        when(attendanceSubmissionService.getSubmission(anyLong(), any(YearMonth.class))).thenReturn(Optional.empty());
        when(attendanceSubmissionService.isEditableMonth(anyLong(), any(YearMonth.class))).thenReturn(true);
        when(holidayService.loadHolidays()).thenReturn(Collections.emptySet());
        lenient().when(leaveApplicationService.getApplicationsByUserAndDateRange(anyLong(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(userService.isAttendanceApprover(testUser)).thenReturn(false);
        lenient().when(userService.getUserById(1L)).thenReturn(Optional.of(testUser));
        lenient().when(attendanceRecordService.getStandardWorkingHours(1L)).thenReturn(8.0);
        lenient().when(attendanceRecordService.getRecordByUserAndDate(anyLong(), any())).thenReturn(Optional.empty());
        when(paidLeaveBalanceService.getBalancesByUserId(1L)).thenReturn(Collections.emptyList());
        when(paidLeaveBalanceService.getTotalRemainingDays(1L)).thenReturn(BigDecimal.ZERO);
        when(attendanceRecordService.checkArticle36(any(Double.class)))
                .thenReturn("NORMAL");
        lenient().when(eventTypeService.getAllActiveEventTypes()).thenReturn(Collections.emptyList());
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
}
