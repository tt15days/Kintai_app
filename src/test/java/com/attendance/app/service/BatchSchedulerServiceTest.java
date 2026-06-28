package com.attendance.app.service;

import com.attendance.app.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class BatchSchedulerServiceTest {

    @Mock
    private UserService userService;

    @Mock
    private AttendanceRecordService attendanceRecordService;

    @Mock
    private AttendancePeriodSettingService attendancePeriodSettingService;

    @Mock
    private BatchSettingService batchSettingService;

    @Mock
    private UserNotificationService userNotificationService;

    @InjectMocks
    private BatchSchedulerService batchSchedulerService;

    @Test
    void testExecuteMonthlySummary_Success() {
        YearMonth targetMonth = YearMonth.of(2026, 5);
        AttendanceRecordService.MonthlyUserSummary summary = new AttendanceRecordService.MonthlyUserSummary(
                1L, 160.0, 10.0, 20
        );

        when(attendanceRecordService.getMonthlyAggregateForAllUsers(targetMonth)).thenReturn(List.of(summary));

        batchSchedulerService.executeMonthlySummary(targetMonth);

        verify(attendanceRecordService).getMonthlyAggregateForAllUsers(targetMonth);
        verify(batchSettingService).recordMonthlySummaryExecutedAt(any(LocalDateTime.class));
    }

    @Test
    void testExecuteMonthlySummary_ExceptionHandling() {
        YearMonth targetMonth = YearMonth.of(2026, 5);
        when(attendanceRecordService.getMonthlyAggregateForAllUsers(targetMonth)).thenThrow(new RuntimeException("DB Error"));

        // Should not throw exception
        batchSchedulerService.executeMonthlySummary(targetMonth);

        verify(batchSettingService, never()).recordMonthlySummaryExecutedAt(any());
    }

    @Test
    void testExecuteAnnualPaidLeaveGrant_Success() {
        User user1 = new User();
        user1.setUserId(1L);
        User user2 = new User();
        user2.setUserId(2L);

        when(userService.getActiveUsers()).thenReturn(List.of(user1, user2));
        doNothing().when(userService).grantAnnualPaidLeave(1L);
        doThrow(new RuntimeException("Skip error")).when(userService).grantAnnualPaidLeave(2L);

        batchSchedulerService.executeAnnualPaidLeaveGrant();

        verify(userService).grantAnnualPaidLeave(1L);
        verify(userService).grantAnnualPaidLeave(2L);
        verify(batchSettingService).recordAnnualLeaveGrantExecutedAt(any(LocalDateTime.class));
    }

    @Test
    void testExecuteAnnualPaidLeaveGrant_ExceptionHandling() {
        when(userService.getActiveUsers()).thenThrow(new RuntimeException("DB Error"));

        batchSchedulerService.executeAnnualPaidLeaveGrant();

        verify(batchSettingService, never()).recordAnnualLeaveGrantExecutedAt(any());
    }

    @Test
    void testExecuteSubmissionReminder_Success() {
        when(userNotificationService.createRemindersForUnsubmittedUsers(any(YearMonth.class))).thenReturn(5);

        batchSchedulerService.executeSubmissionReminder();

        verify(userNotificationService).createRemindersForUnsubmittedUsers(any(YearMonth.class));
        verify(batchSettingService).recordReminderExecutedAt(any(LocalDateTime.class));
    }

    @Test
    void testExecuteSubmissionReminder_ExceptionHandling() {
        when(userNotificationService.createRemindersForUnsubmittedUsers(any(YearMonth.class))).thenThrow(new RuntimeException("DB Error"));

        batchSchedulerService.executeSubmissionReminder();

        verify(batchSettingService, never()).recordReminderExecutedAt(any());
    }
}
