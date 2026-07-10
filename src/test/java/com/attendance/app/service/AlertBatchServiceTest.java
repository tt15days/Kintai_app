package com.attendance.app.service;

import com.attendance.app.dto.Article36AlertDto;
import com.attendance.app.dto.PaidLeaveAlertDto;
import com.attendance.app.entity.UserNotification;
import com.attendance.app.mapper.AlertBatchMapper;
import com.attendance.app.mapper.UserNotificationMapper;
import com.attendance.app.util.DateTimeUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AlertBatchService")
public class AlertBatchServiceTest {

    @Mock
    private AlertBatchMapper alertBatchMapper;

    @Mock
    private BatchSettingService batchSettingService;

    @Mock
    private UserNotificationMapper userNotificationMapper;

    @Mock
    private AttendancePeriodSettingService attendancePeriodSettingService;

    @InjectMocks
    private AlertBatchService alertBatchService;

    @Captor
    private ArgumentCaptor<UserNotification> notificationCaptor;

    @BeforeEach
    void setUp() {
        lenient().when(batchSettingService.getAlertArticle36Limit1()).thenReturn(30);
        lenient().when(batchSettingService.getAlertArticle36Limit2()).thenReturn(45);
        lenient().when(batchSettingService.getAlertPaidLeaveMonths()).thenReturn(9);
        lenient().when(batchSettingService.getAlertPaidLeaveDays()).thenReturn(3);
        lenient().when(attendancePeriodSettingService.getStartDay()).thenReturn(21);
        lenient().when(attendancePeriodSettingService.getEndDay()).thenReturn(20);
    }

    @Nested
    @DisplayName("runAlertBatch")
    class RunAlertBatch {

        @Test
        @DisplayName("定期実行バッチが現在日付（前月分、日本時間基準）を基準に正しく動作する")
        void testRunAlertBatch() {
            // Mock System Date to 2023-11-15 (JST基準)
            LocalDate mockNow = LocalDate.of(2023, 11, 15);
            try (MockedStatic<DateTimeUtil> mockedDateTimeUtil = mockStatic(DateTimeUtil.class, CALLS_REAL_METHODS)) {
                mockedDateTimeUtil.when(() -> DateTimeUtil.todayJapan()).thenReturn(mockNow);

                // 前月は 2023-10。勤怠期間は設定された締め日（開始日21日・終了日20日）基準
                LocalDate expectedStart = LocalDate.of(2023, 9, 21);
                LocalDate expectedEnd = LocalDate.of(2023, 10, 20);

                when(alertBatchMapper.findUsersExceedingOvertimeLimit(expectedStart, expectedEnd, 30))
                        .thenReturn(List.of());
                when(alertBatchMapper.findUsersWithInsufficientPaidLeave(9, 3, mockNow))
                        .thenReturn(List.of());

                alertBatchService.runAlertBatch();

                verify(alertBatchMapper).findUsersExceedingOvertimeLimit(expectedStart, expectedEnd, 30);
                verify(alertBatchMapper).findUsersWithInsufficientPaidLeave(9, 3, mockNow);
            }
        }
    }

    @Nested
    @DisplayName("runAlertBatchManually")
    class RunAlertBatchManually {

        @Test
        @DisplayName("36協定第1閾値超過時に正しい警告通知が作成される")
        void testArticle36Alert_Limit1_Exceeded() {
            YearMonth targetMonth = YearMonth.of(2023, 10);
            Article36AlertDto dto = new Article36AlertDto();
            dto.setUserId(1L);
            dto.setTotalOvertimeHours(BigDecimal.valueOf(35.5)); // 35.5時間

            when(alertBatchMapper.findUsersExceedingOvertimeLimit(any(), any(), eq(30)))
                    .thenReturn(List.of(dto));

            alertBatchService.runAlertBatchManually(targetMonth);

            verify(userNotificationMapper, times(1)).insert(notificationCaptor.capture());
            UserNotification notification = notificationCaptor.getValue();
            
            assertThat(notification.getUserId()).isEqualTo(1L);
            assertThat(notification.getNotificationType()).isEqualTo("ALERT_ARTICLE_36_LIMIT1");
            assertThat(notification.getMessage()).contains("第1警告閾値(30時間)を超過");
            assertThat(notification.getIsRead()).isFalse();
            assertThat(notification.getCreatedAt()).isNotNull();
        }

        @Test
        @DisplayName("36協定第2警告閾値超過時に正しい警告通知が作成される")
        void testArticle36Alert_Limit2_Exceeded() {
            YearMonth targetMonth = YearMonth.of(2023, 10);
            Article36AlertDto dto = new Article36AlertDto();
            dto.setUserId(2L);
            dto.setTotalOvertimeHours(BigDecimal.valueOf(50.0)); // 50時間

            when(alertBatchMapper.findUsersExceedingOvertimeLimit(any(), any(), eq(30)))
                    .thenReturn(List.of(dto));

            alertBatchService.runAlertBatchManually(targetMonth);

            verify(userNotificationMapper, times(1)).insert(notificationCaptor.capture());
            UserNotification notification = notificationCaptor.getValue();
            
            assertThat(notification.getUserId()).isEqualTo(2L);
            assertThat(notification.getNotificationType()).isEqualTo("ALERT_ARTICLE_36_LIMIT2");
            assertThat(notification.getMessage()).contains("第2警告閾値(45時間)を超過");
        }

        @Test
        @DisplayName("有給消化日数不足時に有給消化アラート通知が作成される")
        void testPaidLeaveAlert() {
            YearMonth targetMonth = YearMonth.of(2023, 10);
            PaidLeaveAlertDto dto = new PaidLeaveAlertDto();
            dto.setUserId(3L);
            dto.setGrantDate(LocalDate.of(2023, 1, 1));
            dto.setUsedDays(BigDecimal.valueOf(1.5)); // 消化1.5日（基準3日未満）

            when(alertBatchMapper.findUsersExceedingOvertimeLimit(any(), any(), anyInt()))
                    .thenReturn(List.of()); // 36協定は該当なし

            when(alertBatchMapper.findUsersWithInsufficientPaidLeave(eq(9), eq(3), any()))
                    .thenReturn(List.of(dto));

            alertBatchService.runAlertBatchManually(targetMonth);

            verify(userNotificationMapper, times(1)).insert(notificationCaptor.capture());
            UserNotification notification = notificationCaptor.getValue();
            
            assertThat(notification.getUserId()).isEqualTo(3L);
            assertThat(notification.getNotificationType()).isEqualTo("ALERT_PAID_LEAVE");
            assertThat(notification.getMessage()).contains("消化日数が 1.5 日となっており、基準である 3 日を下回っています");
        }

        @Test
        @DisplayName("同一期間の36協定アラートが通知済みの場合は重複通知しない")
        void testArticle36Alert_AlreadyNotified_Skipped() {
            YearMonth targetMonth = YearMonth.of(2023, 10);
            Article36AlertDto dto = new Article36AlertDto();
            dto.setUserId(1L);
            dto.setTotalOvertimeHours(BigDecimal.valueOf(35.5));

            when(alertBatchMapper.findUsersExceedingOvertimeLimit(any(), any(), eq(30)))
                    .thenReturn(List.of(dto));
            when(userNotificationMapper.countByUserAndTypeSince(eq(1L), eq("ALERT_ARTICLE_36_LIMIT1"), any()))
                    .thenReturn(1);

            alertBatchService.runAlertBatchManually(targetMonth);

            verify(userNotificationMapper, never()).insert(any());
        }

        @Test
        @DisplayName("当月分の有給消化アラートが通知済みの場合は重複通知しない")
        void testPaidLeaveAlert_AlreadyNotified_Skipped() {
            YearMonth targetMonth = YearMonth.of(2023, 10);
            PaidLeaveAlertDto dto = new PaidLeaveAlertDto();
            dto.setUserId(3L);
            dto.setGrantDate(LocalDate.of(2023, 1, 1));
            dto.setUsedDays(BigDecimal.valueOf(1.5));

            when(alertBatchMapper.findUsersExceedingOvertimeLimit(any(), any(), anyInt()))
                    .thenReturn(List.of());
            when(alertBatchMapper.findUsersWithInsufficientPaidLeave(eq(9), eq(3), any()))
                    .thenReturn(List.of(dto));
            when(userNotificationMapper.countByUserAndTypeSince(eq(3L), eq("ALERT_PAID_LEAVE"), any()))
                    .thenReturn(1);

            alertBatchService.runAlertBatchManually(targetMonth);

            verify(userNotificationMapper, never()).insert(any());
        }
    }
}
