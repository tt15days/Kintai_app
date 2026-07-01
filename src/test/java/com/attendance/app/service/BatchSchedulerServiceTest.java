package com.attendance.app.service;

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
