package com.attendance.app.service;

import com.attendance.app.entity.PaidLeaveBalance;
import com.attendance.app.entity.User;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

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
        User user = User.builder().userId(1L).build();
        AttendanceRecordService.MonthlyUserSummary summary = new AttendanceRecordService.MonthlyUserSummary(
                1L, 160.0, 10.0, 0.0, 20
        );

        when(userService.getUsersAfterId(null, null, true, false, 0, 100)).thenReturn(List.of(user));
        when(attendanceRecordService.getMonthlyAggregateForUsers(targetMonth, List.of(1L))).thenReturn(List.of(summary));

        batchSchedulerService.executeMonthlySummary(targetMonth);

        verify(attendanceRecordService).getMonthlyAggregateForUsers(targetMonth, List.of(1L));
        verify(batchSettingService).recordMonthlySummaryExecutedAt(any(LocalDateTime.class));
    }

    @Test
    void testExecuteMonthlySummary_ExceptionHandling() {
        YearMonth targetMonth = YearMonth.of(2026, 5);
        when(userService.getUsersAfterId(null, null, true, false, 0, 100))
                .thenReturn(List.of(User.builder().userId(1L).build()));
        when(attendanceRecordService.getMonthlyAggregateForUsers(targetMonth, List.of(1L)))
                .thenThrow(new RuntimeException("DB Error"));

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
    void testExecuteMonthlySummary_LogsOnlyAggregateAtInfo() {
        YearMonth targetMonth = YearMonth.of(2026, 5);
        when(batchSettingService.getAlertArticle36Limit1()).thenReturn(40);
        when(batchSettingService.getAlertArticle36Limit2()).thenReturn(45);
        when(userService.getUsersAfterId(null, null, true, false, 0, 100)).thenReturn(List.of(
                User.builder().userId(101L).build(), User.builder().userId(102L).build()));
        when(attendanceRecordService.getMonthlyAggregateForUsers(targetMonth, List.of(101L, 102L))).thenReturn(List.of(
                new AttendanceRecordService.MonthlyUserSummary(101L, 160.0, 10.0, 0.0, 20),
                new AttendanceRecordService.MonthlyUserSummary(102L, 160.0, 46.0, 0.0, 20)));
        Logger logger = (Logger) LoggerFactory.getLogger(BatchSchedulerService.class);
        logger.setLevel(Level.INFO);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            batchSchedulerService.executeMonthlySummary(targetMonth);

            assertEquals(0, appender.list.stream()
                    .filter(event -> event.getLevel() == Level.INFO)
                    .filter(event -> event.getFormattedMessage().contains("userId="))
                    .count());
            org.junit.jupiter.api.Assertions.assertTrue(appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .anyMatch(message -> message.contains("processed=2, warning=0, alert=1")));
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void testExecuteMonthlySummary_UsesUserIdCursorAcrossPages() {
        YearMonth targetMonth = YearMonth.of(2026, 5);
        List<User> firstPage = java.util.stream.LongStream.rangeClosed(1, 100)
                .mapToObj(id -> User.builder().userId(id).build()).toList();
        User newlyAddedUser = User.builder().userId(101L).build();
        when(userService.getUsersAfterId(null, null, true, false, 0, 100)).thenReturn(firstPage);
        when(userService.getUsersAfterId(null, null, true, false, 100, 100)).thenReturn(List.of(newlyAddedUser));
        when(userService.getUsersAfterId(null, null, true, false, 101, 100)).thenReturn(List.of());
        when(attendanceRecordService.getMonthlyAggregateForUsers(eq(targetMonth), anyList())).thenReturn(List.of());

        batchSchedulerService.executeMonthlySummary(targetMonth);

        verify(userService).getUsersAfterId(null, null, true, false, 0, 100);
        verify(userService).getUsersAfterId(null, null, true, false, 100, 100);
        verify(userService).getUsersAfterId(null, null, true, false, 101, 100);
        verify(batchSettingService).recordMonthlySummaryExecutedAt(any(LocalDateTime.class));
    }

    @Test
    void testRunExpiredUserDeactivationCheck_DelegatesToUserService() {
        when(userService.deactivateExpiredUsers()).thenReturn(2);

        batchSchedulerService.runExpiredUserDeactivationCheck();

        verify(userService).deactivateExpiredUsers();
    }

    @Test
    void testExecuteExpiredUserDeactivation_ReturnsDeactivatedCount() {
        when(userService.deactivateExpiredUsers()).thenReturn(2);

        int count = batchSchedulerService.executeExpiredUserDeactivation();

        assertEquals(2, count);
    }

    @Test
    void testExecuteExpiredUserDeactivation_PropagatesFailure() {
        when(userService.deactivateExpiredUsers()).thenThrow(new RuntimeException("DB Error"));

        assertThrows(RuntimeException.class, () -> batchSchedulerService.executeExpiredUserDeactivation());
    }

    @Test
    void testRunExpiredUserDeactivationCheck_HandlesFailure() {
        when(userService.deactivateExpiredUsers()).thenThrow(new RuntimeException("DB Error"));

        batchSchedulerService.runExpiredUserDeactivationCheck();

        verify(userService).deactivateExpiredUsers();
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
        verify(userService).grantAnnualPaidLeave(1L);
        verify(userService).grantAnnualPaidLeave(2L);
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
        verify(userService).grantAnnualPaidLeave(2L);
        verify(userService, never()).grantAnnualPaidLeave(1L);
        verify(batchSettingService).recordAnnualLeaveGrantExecutedAt(any(LocalDateTime.class));
    }

    @Test
    void testExecuteAnnualLeaveGrant_PropagatesException() {
        when(userService.getActiveUsers()).thenThrow(new RuntimeException("DB Error"));

        assertThrows(RuntimeException.class, () -> batchSchedulerService.executeAnnualLeaveGrant());

        verify(batchSettingService, never()).recordAnnualLeaveGrantExecutedAt(any());
    }
}
