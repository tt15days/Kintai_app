package com.attendance.app.service;

import com.attendance.app.entity.PaidLeaveBalance;
import com.attendance.app.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
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

    @Mock
    private PaidLeaveBalanceService paidLeaveBalanceService;

    @InjectMocks
    private BatchSchedulerService batchSchedulerService;

    @Test
    void testExecuteMonthlySummary_Success() {
        YearMonth targetMonth = YearMonth.of(2026, 5);
        AttendanceRecordService.MonthlyUserSummary summary = new AttendanceRecordService.MonthlyUserSummary(
                1L, 160.0, 10.0, 0.0, 20
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

    @Test
    void testExecuteAnnualLeaveGrant_Success() {
        User user1 = User.builder().userId(1L).annualLeaveGrantDays(10).build();
        User user2 = User.builder().userId(2L).annualLeaveGrantDays(11).build();

        when(userService.getActiveUsers()).thenReturn(List.of(user1, user2));
        when(paidLeaveBalanceService.getByUsersAndYear(anyList(), anyInt())).thenReturn(List.of());

        BatchSchedulerService.AnnualLeaveGrantResult result = batchSchedulerService.executeAnnualLeaveGrant();

        assertEquals(2, result.grantedCount());
        assertEquals(0, result.skippedCount());
        verify(userService).grantAnnualPaidLeave(eq(1L), eq(10));
        verify(userService).grantAnnualPaidLeave(eq(2L), eq(11));
        verify(batchSettingService).recordAnnualLeaveGrantExecutedAt(any(LocalDateTime.class));
    }

    @Test
    void testExecuteAnnualLeaveGrant_SkipsAlreadyGrantedUser() {
        User user1 = User.builder().userId(1L).annualLeaveGrantDays(10).build();
        User user2 = User.builder().userId(2L).annualLeaveGrantDays(11).build();

        when(userService.getActiveUsers()).thenReturn(List.of(user1, user2));
        when(paidLeaveBalanceService.getByUsersAndYear(anyList(), anyInt()))
                .thenReturn(List.of(PaidLeaveBalance.builder().userId(1L).build()));

        BatchSchedulerService.AnnualLeaveGrantResult result = batchSchedulerService.executeAnnualLeaveGrant();

        assertEquals(1, result.grantedCount());
        assertEquals(1, result.skippedCount());
        verify(userService, times(1)).grantAnnualPaidLeave(eq(2L), eq(11));
        verify(userService, never()).grantAnnualPaidLeave(eq(1L), anyInt());
        verify(batchSettingService).recordAnnualLeaveGrantExecutedAt(any(LocalDateTime.class));
    }

    @Test
    void testExecuteAnnualLeaveGrant_PropagatesException() {
        when(userService.getActiveUsers()).thenThrow(new RuntimeException("DB Error"));

        assertThrows(RuntimeException.class, () -> batchSchedulerService.executeAnnualLeaveGrant());

        verify(batchSettingService, never()).recordAnnualLeaveGrantExecutedAt(any());
    }
}
