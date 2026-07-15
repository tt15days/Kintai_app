package com.attendance.app.service;

import com.attendance.app.entity.AttendanceRecord;
import com.attendance.app.entity.EventType;
import com.attendance.app.entity.User;
import com.attendance.app.entity.WorkScheduleClass;
import com.attendance.app.mapper.AttendanceRecordMapper;
import com.attendance.app.mapper.EventTypeMapper;
import com.attendance.app.mapper.UserMapper;
import com.attendance.app.mapper.WorkScheduleClassMapper;
import com.attendance.app.mapper.AttendanceSubmissionMapper;
import com.attendance.app.util.DateTimeUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.Map;
import java.util.Optional;
import java.util.List;
import java.util.Set;
import com.attendance.app.entity.UserRole;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * {@link AttendanceRecordService} の単体テスト。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AttendanceRecordService")
class AttendanceRecordServiceTest {

    @Mock
    private AttendanceRecordMapper attendanceRecordMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private WorkScheduleClassMapper workScheduleClassMapper;
    @Mock
    private AttendancePeriodSettingService attendancePeriodSettingService;
    @Mock
    private OvertimeRecordService overtimeRecordService;
    @Mock
    private EventTypeMapper eventTypeMapper;
    @Mock
    private AttendanceSubmissionMapper attendanceSubmissionMapper;
    @Mock
    private UserNotificationService userNotificationService;
    @Mock
    private BatchSettingService batchSettingService;

    private AttendanceRecordService service;

    @BeforeEach
    void setUp() {
        service = new AttendanceRecordService(
                attendanceRecordMapper,
                overtimeRecordService,
                userMapper,
                workScheduleClassMapper,
                attendancePeriodSettingService,
                eventTypeMapper,
                attendanceSubmissionMapper,
                userNotificationService,
                batchSettingService);
        lenient().when(batchSettingService.getAlertArticle36Limit1())
                .thenReturn(BatchSettingService.DEFAULT_ALERT_ARTICLE36_LIMIT1);
        lenient().when(batchSettingService.getAlertArticle36Limit2())
                .thenReturn(BatchSettingService.DEFAULT_ALERT_ARTICLE36_LIMIT2);
        lenient().when(attendancePeriodSettingService.resolvePeriod(any(YearMonth.class)))
                .thenAnswer(invocation -> {
                    YearMonth month = invocation.getArgument(0);
                    return new AttendancePeriodSettingService.AttendancePeriod(
                            month.minusMonths(1).atDay(20).plusDays(1), month.atDay(20));
                });
        lenient().when(attendancePeriodSettingService.getEndDay()).thenReturn(20);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // checkArticle36
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("checkArticle36")
    class CheckArticle36 {

        @Test
        @DisplayName("残業0時間は NORMAL を返す")
        void zeroHours_returnsNormal() {
            assertThat(service.checkArticle36(0.0)).isEqualTo("NORMAL");
        }

        @Test
        @DisplayName("残業29.9時間（既定WARNING閾値30時間未満）は NORMAL を返す")
        void justBelowWarning_returnsNormal() {
            assertThat(service.checkArticle36(29.9)).isEqualTo("NORMAL");
        }

        @Test
        @DisplayName("残業30.0時間（既定WARNING閾値ちょうど）は WARNING を返す")
        void exactWarningThreshold_returnsWarning() {
            assertThat(service.checkArticle36(30.0)).isEqualTo("WARNING");
        }

        @Test
        @DisplayName("残業44.9時間（既定ALERT閾値45時間未満）は WARNING を返す")
        void justBelowAlert_returnsWarning() {
            assertThat(service.checkArticle36(44.9)).isEqualTo("WARNING");
        }

        @Test
        @DisplayName("残業45.0時間（既定ALERT閾値ちょうど）は ALERT を返す")
        void exactAlertThreshold_returnsAlert() {
            assertThat(service.checkArticle36(45.0)).isEqualTo("ALERT");
        }

        @ParameterizedTest(name = "{0}時間 → {1}")
        @CsvSource({
            "0.0,    NORMAL",
            "29.99,  NORMAL",
            "30.0,   WARNING",
            "44.99,  WARNING",
            "45.0,   ALERT",
            "80.0,   ALERT"
        })
        @DisplayName("パラメータ化: 既定閾値（30h/45h）での各時間帯のステータスが正しい")
        void parameterized_allBoundaries(double hours, String expectedStatus) {
            assertThat(service.checkArticle36(hours)).isEqualTo(expectedStatus);
        }

        @Test
        @DisplayName("システム設定のアラート閾値が変更されている場合はその値で判定する")
        void usesConfiguredThresholds() {
            when(batchSettingService.getAlertArticle36Limit1()).thenReturn(20);
            when(batchSettingService.getAlertArticle36Limit2()).thenReturn(30);

            assertThat(service.checkArticle36(19.9)).isEqualTo("NORMAL");
            assertThat(service.checkArticle36(20.0)).isEqualTo("WARNING");
            assertThat(service.checkArticle36(30.0)).isEqualTo("ALERT");
        }

        @Test
        @DisplayName("明示的な閾値を指定するオーバーロードは指定値で判定する")
        void explicitThresholds_usesGivenValues() {
            assertThat(service.checkArticle36(10.0, 15.0, 20.0)).isEqualTo("NORMAL");
            assertThat(service.checkArticle36(15.0, 15.0, 20.0)).isEqualTo("WARNING");
            assertThat(service.checkArticle36(20.0, 15.0, 20.0)).isEqualTo("ALERT");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getMonthRange
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("getMonthRange")
    class GetMonthRange {

        @Test
        @DisplayName("開始日21日・終了日20日で payroll-style の範囲を返す")
        void withStartDay21EndDay20_returnsPayrollStyleDates() {
            AttendanceRecordService.MonthRange range = service.getMonthRange(YearMonth.of(2026, 5));

            assertThat(range.getStartDate()).isEqualTo(LocalDate.of(2026, 4, 21));
            assertThat(range.getEndDate()).isEqualTo(LocalDate.of(2026, 5, 20));
        }

        @Test
        @DisplayName("対象月 null の場合は現在月を基準に範囲を返す")
        void withNullYearMonth_defaultsToCurrentMonth() {
            YearMonth now = YearMonth.now();
            AttendanceRecordService.MonthRange range = service.getMonthRange(null);

            assertThat(range.getYearMonth()).isEqualTo(now);
            assertThat(range.getStartDate()).isEqualTo(now.minusMonths(1).atDay(21));
            assertThat(range.getEndDate()).isEqualTo(now.atDay(20));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // saveRecord
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("saveRecord")
    class SaveRecord {

        @Test
        @DisplayName("正常系: 新規レコードの登録")
        void noOverlapBreakWindow_doesNotDeductBreak() {
            Long userId = 10L;
            LocalDate attendanceDate = LocalDate.of(2026, 6, 1);

            User user = User.builder().userId(userId).className("標準勤務").build();
            WorkScheduleClass schedule = WorkScheduleClass.builder()
                    .name("標準勤務")
                    .startTime(LocalTime.of(9, 0))
                    .endTime(LocalTime.of(18, 0))
                    .breaks(List.of(
                            com.attendance.app.entity.WorkScheduleClassBreak.builder()
                                    .breakStartTime(LocalTime.of(12, 0))
                                    .breakEndTime(LocalTime.of(13, 0))
                                    .build()
                    ))
                    .build();

            when(attendanceRecordMapper.selectByUserAndDateForUpdate(userId, attendanceDate)).thenReturn(Optional.empty());
            when(userMapper.selectById(userId)).thenReturn(Optional.of(user));
            when(workScheduleClassMapper.selectByName("標準勤務")).thenReturn(Optional.of(schedule));

            AttendanceRecord result = service.saveRecord(
                    userId, attendanceDate, LocalTime.of(9, 0), LocalTime.of(12, 0), "", false, null);

            assertThat(result.getWorkingHours()).isEqualTo(3.0);
            verify(attendanceRecordMapper).insert(any(AttendanceRecord.class));
        }

        @Test
        @DisplayName("正常系: 既存レコードの更新")
        void existingRecord_updatesDetails() {
            Long userId = 10L;
            LocalDate attendanceDate = LocalDate.of(2026, 6, 1);
            AttendanceRecord existing = AttendanceRecord.builder().recordId(100L).build();

            User user = User.builder().userId(userId).className("標準勤務").build();
            WorkScheduleClass schedule = WorkScheduleClass.builder()
                    .name("標準勤務")
                    .startTime(LocalTime.of(9, 0))
                    .endTime(LocalTime.of(18, 0))
                    .breaks(List.of(
                            com.attendance.app.entity.WorkScheduleClassBreak.builder()
                                    .breakStartTime(LocalTime.of(12, 0))
                                    .breakEndTime(LocalTime.of(13, 0))
                                    .build()
                    ))
                    .build();

            when(attendanceRecordMapper.selectByUserAndDateForUpdate(userId, attendanceDate)).thenReturn(Optional.of(existing));
            when(userMapper.selectById(userId)).thenReturn(Optional.of(user));
            when(workScheduleClassMapper.selectByName("標準勤務")).thenReturn(Optional.of(schedule));

            service.saveRecord(userId, attendanceDate, LocalTime.of(9, 0), LocalTime.of(18, 0), "更新備考", false, 3);

            verify(attendanceRecordMapper).update(existing);
            assertThat(existing.getRemarks()).isEqualTo("更新備考");
            assertThat(existing.getEventTypeId()).isEqualTo(3);
        }

        @Test
        @DisplayName("休日出勤かつ所定終業時刻超過時は、overtimeHoursを0にしholidayWorkHoursのみへ計上する（二重計上防止）")
        void holidayWork_pastStandardEndTime_zeroesOvertimeHours() {
            Long userId = 10L;
            LocalDate attendanceDate = LocalDate.of(2026, 6, 6);

            User user = User.builder().userId(userId).className("標準勤務").build();
            WorkScheduleClass schedule = WorkScheduleClass.builder()
                    .name("標準勤務")
                    .startTime(LocalTime.of(9, 0))
                    .endTime(LocalTime.of(18, 0))
                    .breaks(List.of(
                            com.attendance.app.entity.WorkScheduleClassBreak.builder()
                                    .breakStartTime(LocalTime.of(12, 0))
                                    .breakEndTime(LocalTime.of(13, 0))
                                    .build()
                    ))
                    .build();

            when(attendanceRecordMapper.selectByUserAndDateForUpdate(userId, attendanceDate)).thenReturn(Optional.empty());
            when(userMapper.selectById(userId)).thenReturn(Optional.of(user));
            when(workScheduleClassMapper.selectByName("標準勤務")).thenReturn(Optional.of(schedule));

            // 09:00〜19:00勤務（所定終業18:00を1時間超過）を休日出勤として登録
            AttendanceRecord result = service.saveRecord(
                    userId, attendanceDate, LocalTime.of(9, 0), LocalTime.of(19, 0), "", true, null);

            assertThat(result.getWorkingHours()).isEqualTo(0.0);
            assertThat(result.getHolidayWorkHours()).isEqualTo(9.0);
            assertThat(result.getOvertimeHours()).isEqualTo(0.0);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // quickStartAttendance
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("quickStartAttendance")
    class QuickStartAttendance {

        @Test
        @DisplayName("正常系: 出勤打刻の登録")
        void quickStart_success() {
            Long userId = 10L;
            User user = User.builder().userId(userId).className("標準勤務").build();
            WorkScheduleClass schedule = WorkScheduleClass.builder()
                    .name("標準勤務")
                    .startTime(LocalTime.of(9, 0))
                    .endTime(LocalTime.of(18, 0))
                    .build();

            when(userMapper.selectById(userId)).thenReturn(Optional.of(user));
            when(workScheduleClassMapper.selectByName("標準勤務")).thenReturn(Optional.of(schedule));
            when(eventTypeMapper.selectByCode("通常")).thenReturn(Optional.of(EventType.builder().eventTypeId(1).build()));

            try (MockedStatic<DateTimeUtil> mockedDateTimeUtil = mockStatic(DateTimeUtil.class)) {

                LocalDate mockToday = LocalDate.of(2026, 6, 1);
                Instant mockNow = Instant.parse("2026-06-01T09:00:00Z");
                LocalTime mockTime = LocalTime.of(9, 0);

                mockedDateTimeUtil.when(() -> DateTimeUtil.todayJapan()).thenReturn(mockToday);
                mockedDateTimeUtil.when(() -> DateTimeUtil.currentTimeJapan()).thenReturn(mockTime);
                mockedDateTimeUtil.when(() -> DateTimeUtil.toInstant(mockToday)).thenReturn(mockNow);
                mockedDateTimeUtil.when(() -> DateTimeUtil.toInstant(mockToday, mockTime)).thenReturn(mockNow);
                mockedDateTimeUtil.when(() -> DateTimeUtil.now()).thenReturn(mockNow);

                when(attendanceRecordMapper.selectByUserAndDateForUpdate(eq(userId), any(LocalDate.class))).thenReturn(Optional.empty());

                AttendanceRecord result = service.quickStartAttendance(userId);

                assertThat(result).isNotNull();
                verify(attendanceRecordMapper).insert(any(AttendanceRecord.class));
            }
        }

        @Test
        @DisplayName("異常系: 同日に既に出勤打刻がある場合は例外")
        void quickStart_alreadyStarted_throwsException() {
            Long userId = 10L;
            User user = User.builder().userId(userId).className("標準勤務").build();
            WorkScheduleClass schedule = WorkScheduleClass.builder()
                    .name("標準勤務").startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(18, 0)).build();

            when(userMapper.selectById(userId)).thenReturn(Optional.of(user));
            when(workScheduleClassMapper.selectByName("標準勤務")).thenReturn(Optional.of(schedule));

            try (MockedStatic<DateTimeUtil> mockedDateTimeUtil = mockStatic(DateTimeUtil.class, CALLS_REAL_METHODS)) {
                LocalDate mockToday = LocalDate.of(2026, 6, 1);
                mockedDateTimeUtil.when(() -> DateTimeUtil.todayJapan()).thenReturn(mockToday);

                AttendanceRecord existing = AttendanceRecord.builder()
                        .startTime(Instant.parse("2026-06-01T09:00:00Z")).build();

                // 前日分(5/31)と当日分(6/1)の戻り値を設定
                when(attendanceRecordMapper.selectByUserAndDateForUpdate(eq(userId), any(LocalDate.class)))
                        .thenAnswer(invocation -> {
                            LocalDate date = invocation.getArgument(1);
                            if (date.equals(mockToday)) {
                                return Optional.of(existing);
                            }
                            return Optional.empty();
                        });

                assertThatThrownBy(() -> service.quickStartAttendance(userId))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("本日は既に勤務開始時刻が記録されています");
            }
        }

        @Test
        @DisplayName("異常系: 夜勤勤務で前日の退勤が記録されていない場合は例外")
        void quickStart_overnightPrevDayNotEnded_throwsException() {
            Long userId = 10L;
            User user = User.builder().userId(userId).className("夜勤勤務").build();
            WorkScheduleClass schedule = WorkScheduleClass.builder()
                    .name("夜勤勤務").startTime(LocalTime.of(17, 0)).endTime(LocalTime.of(2, 0)).build();

            when(userMapper.selectById(userId)).thenReturn(Optional.of(user));
            when(workScheduleClassMapper.selectByName("夜勤勤務")).thenReturn(Optional.of(schedule));

            try (MockedStatic<DateTimeUtil> mockedDateTimeUtil = mockStatic(DateTimeUtil.class, CALLS_REAL_METHODS)) {
                LocalDate mockToday = LocalDate.of(2026, 6, 2);
                mockedDateTimeUtil.when(() -> DateTimeUtil.todayJapan()).thenReturn(mockToday);

                // 前日(6/1)が出勤打刻あり、退勤打刻なし状態
                AttendanceRecord prevRecord = AttendanceRecord.builder()
                        .startTime(Instant.parse("2026-06-01T17:00:00Z")).endTime(null).build();

                when(attendanceRecordMapper.selectByUserAndDateForUpdate(userId, mockToday)).thenReturn(Optional.empty());
                when(attendanceRecordMapper.selectByUserAndDate(userId, LocalDate.of(2026, 6, 1))).thenReturn(Optional.of(prevRecord));

                assertThatThrownBy(() -> service.quickStartAttendance(userId))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("前日の勤務終了時刻が記録されていません");
            }
        }

        @Test
        @DisplayName("正常系: 勤務間インターバル不足時に本人と管理者に警告通知を送信する")
        void quickStart_intervalGapLessThanMin_sendsAlertNotifications() {
            Long userId = 10L;
            User user = User.builder().userId(userId).fullName("テスト太郎").className("標準勤務").build();
            WorkScheduleClass schedule = WorkScheduleClass.builder()
                    .name("標準勤務").startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(18, 0)).build();

            when(userMapper.selectById(userId)).thenReturn(Optional.of(user));
            when(workScheduleClassMapper.selectByName("標準勤務")).thenReturn(Optional.of(schedule));
            when(eventTypeMapper.selectByCode("通常")).thenReturn(Optional.of(EventType.builder().eventTypeId(1).build()));
            when(batchSettingService.getAlertMinIntervalHours()).thenReturn(11);
            
            // 前日の退勤が18:00 (JST) = 09:00 (UTC)
            AttendanceRecord prevRecord = AttendanceRecord.builder()
                    .endTime(Instant.parse("2026-05-31T09:00:00Z")).build();
            
            // 当日の出勤が04:00 (JST) = 前日19:00 (UTC) (インターバル10時間)
            LocalDate mockToday = LocalDate.of(2026, 6, 1);
            LocalTime mockTime = LocalTime.of(4, 0);
            Instant mockNow = Instant.parse("2026-05-31T19:00:00Z");

            when(attendanceRecordMapper.selectByUserAndDate(userId, LocalDate.of(2026, 5, 31))).thenReturn(Optional.of(prevRecord));
            when(userNotificationService.getUnreadByUserId(userId)).thenReturn(java.util.Collections.emptyList());

            // 管理者を取得するモック
            User admin = User.builder().userId(99L).fullName("管理者").userRole(UserRole.ADMIN).build();
            when(userMapper.selectByRole("ADMIN")).thenReturn(List.of(admin));
            when(userNotificationService.getUnreadByUserId(99L)).thenReturn(java.util.Collections.emptyList());

            try (MockedStatic<DateTimeUtil> mockedDateTimeUtil = mockStatic(DateTimeUtil.class)) {
                mockedDateTimeUtil.when(() -> DateTimeUtil.todayJapan()).thenReturn(mockToday);
                mockedDateTimeUtil.when(() -> DateTimeUtil.currentTimeJapan()).thenReturn(mockTime);
                mockedDateTimeUtil.when(() -> DateTimeUtil.toInstant(mockToday)).thenReturn(mockNow);
                mockedDateTimeUtil.when(() -> DateTimeUtil.toInstant(mockToday, mockTime)).thenReturn(mockNow);
                mockedDateTimeUtil.when(() -> DateTimeUtil.now()).thenReturn(mockNow);

                when(attendanceRecordMapper.selectByUserAndDateForUpdate(eq(userId), any(LocalDate.class))).thenReturn(Optional.empty());

                AttendanceRecord result = service.quickStartAttendance(userId);

                assertThat(result).isNotNull();
                verify(userNotificationService, times(1)).notifyIntervalAlert(eq(userId), anyString());
                verify(userNotificationService, times(1)).notifyIntervalAlert(eq(99L), anyString());
            }
        }

        @Test
        @DisplayName("正常系: 勤務間インターバル不足時でも、しきい値が0（無効）の場合は通知しない")
        void quickStart_intervalZero_doesNotSendNotifications() {
            Long userId = 10L;
            User user = User.builder().userId(userId).className("標準勤務").build();
            WorkScheduleClass schedule = WorkScheduleClass.builder()
                    .name("標準勤務").startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(18, 0)).build();

            when(userMapper.selectById(userId)).thenReturn(Optional.of(user));
            when(workScheduleClassMapper.selectByName("標準勤務")).thenReturn(Optional.of(schedule));
            when(eventTypeMapper.selectByCode("通常")).thenReturn(Optional.of(EventType.builder().eventTypeId(1).build()));
            when(batchSettingService.getAlertMinIntervalHours()).thenReturn(0); // 警告無効

            LocalDate mockToday = LocalDate.of(2026, 6, 1);
            LocalTime mockTime = LocalTime.of(4, 0);
            Instant mockNow = Instant.parse("2026-05-31T19:00:00Z");

            try (MockedStatic<DateTimeUtil> mockedDateTimeUtil = mockStatic(DateTimeUtil.class)) {
                mockedDateTimeUtil.when(() -> DateTimeUtil.todayJapan()).thenReturn(mockToday);
                mockedDateTimeUtil.when(() -> DateTimeUtil.currentTimeJapan()).thenReturn(mockTime);
                mockedDateTimeUtil.when(() -> DateTimeUtil.toInstant(mockToday)).thenReturn(mockNow);
                mockedDateTimeUtil.when(() -> DateTimeUtil.toInstant(mockToday, mockTime)).thenReturn(mockNow);
                mockedDateTimeUtil.when(() -> DateTimeUtil.now()).thenReturn(mockNow);

                when(attendanceRecordMapper.selectByUserAndDateForUpdate(eq(userId), any(LocalDate.class))).thenReturn(Optional.empty());

                AttendanceRecord result = service.quickStartAttendance(userId);

                assertThat(result).isNotNull();
                verify(userNotificationService, never()).notifyIntervalAlert(anyLong(), anyString());
            }
        }

        @Test
        @DisplayName("正常系: 勤務間インターバルがちょうど最小時間（例: 11時間）の場合は警告通知を送信しない")
        void quickStart_intervalGapEqualToMin_doesNotSendNotifications() {
            Long userId = 10L;
            User user = User.builder().userId(userId).className("標準勤務").build();
            WorkScheduleClass schedule = WorkScheduleClass.builder()
                    .name("標準勤務").startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(18, 0)).build();

            when(userMapper.selectById(userId)).thenReturn(Optional.of(user));
            when(workScheduleClassMapper.selectByName("標準勤務")).thenReturn(Optional.of(schedule));
            when(eventTypeMapper.selectByCode("通常")).thenReturn(Optional.of(EventType.builder().eventTypeId(1).build()));
            when(batchSettingService.getAlertMinIntervalHours()).thenReturn(11);
            
            // 前日の退勤が18:00 (JST) = 09:00 (UTC)
            AttendanceRecord prevRecord = AttendanceRecord.builder()
                    .endTime(Instant.parse("2026-05-31T09:00:00Z")).build();
            
            // 当日の出勤が05:00 (JST) = 前日20:00 (UTC) (インターバルちょうど11時間)
            LocalDate mockToday = LocalDate.of(2026, 6, 1);
            LocalTime mockTime = LocalTime.of(5, 0);
            Instant mockNow = Instant.parse("2026-05-31T20:00:00Z");

            when(attendanceRecordMapper.selectByUserAndDate(userId, LocalDate.of(2026, 5, 31))).thenReturn(Optional.of(prevRecord));

            try (MockedStatic<DateTimeUtil> mockedDateTimeUtil = mockStatic(DateTimeUtil.class)) {
                mockedDateTimeUtil.when(() -> DateTimeUtil.todayJapan()).thenReturn(mockToday);
                mockedDateTimeUtil.when(() -> DateTimeUtil.currentTimeJapan()).thenReturn(mockTime);
                mockedDateTimeUtil.when(() -> DateTimeUtil.toInstant(mockToday)).thenReturn(mockNow);
                mockedDateTimeUtil.when(() -> DateTimeUtil.toInstant(mockToday, mockTime)).thenReturn(mockNow);
                mockedDateTimeUtil.when(() -> DateTimeUtil.now()).thenReturn(mockNow);

                when(attendanceRecordMapper.selectByUserAndDateForUpdate(eq(userId), any(LocalDate.class))).thenReturn(Optional.empty());

                AttendanceRecord result = service.quickStartAttendance(userId);

                assertThat(result).isNotNull();
                verify(userNotificationService, never()).notifyIntervalAlert(anyLong(), anyString());
            }
        }

        @Test
        @DisplayName("正常系: 勤務間インターバルが最小時間未満（例: 10時間59分）の場合は警告通知を送信する")
        void quickStart_intervalGapJustBelowMin_sendsNotifications() {
            Long userId = 10L;
            User user = User.builder().userId(userId).fullName("テスト太郎").className("標準勤務").build();
            WorkScheduleClass schedule = WorkScheduleClass.builder()
                    .name("標準勤務").startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(18, 0)).build();

            when(userMapper.selectById(userId)).thenReturn(Optional.of(user));
            when(workScheduleClassMapper.selectByName("標準勤務")).thenReturn(Optional.of(schedule));
            when(eventTypeMapper.selectByCode("通常")).thenReturn(Optional.of(EventType.builder().eventTypeId(1).build()));
            when(batchSettingService.getAlertMinIntervalHours()).thenReturn(11);
            
            // 前日の退勤が18:00 (JST) = 09:00 (UTC)
            AttendanceRecord prevRecord = AttendanceRecord.builder()
                    .endTime(Instant.parse("2026-05-31T09:00:00Z")).build();
            
            // 当日の出勤が04:59 (JST) = 前日19:59 (UTC) (インターバル10時間59分 = 11時間未満)
            LocalDate mockToday = LocalDate.of(2026, 6, 1);
            LocalTime mockTime = LocalTime.of(4, 59);
            Instant mockNow = Instant.parse("2026-05-31T19:59:00Z");

            when(attendanceRecordMapper.selectByUserAndDate(userId, LocalDate.of(2026, 5, 31))).thenReturn(Optional.of(prevRecord));
            when(userNotificationService.getUnreadByUserId(userId)).thenReturn(java.util.Collections.emptyList());

            // 管理者を取得するモック
            User admin = User.builder().userId(99L).fullName("管理者").userRole(UserRole.ADMIN).build();
            when(userMapper.selectByRole("ADMIN")).thenReturn(List.of(admin));
            when(userNotificationService.getUnreadByUserId(99L)).thenReturn(java.util.Collections.emptyList());

            try (MockedStatic<DateTimeUtil> mockedDateTimeUtil = mockStatic(DateTimeUtil.class)) {
                mockedDateTimeUtil.when(() -> DateTimeUtil.todayJapan()).thenReturn(mockToday);
                mockedDateTimeUtil.when(() -> DateTimeUtil.currentTimeJapan()).thenReturn(mockTime);
                mockedDateTimeUtil.when(() -> DateTimeUtil.toInstant(mockToday)).thenReturn(mockNow);
                mockedDateTimeUtil.when(() -> DateTimeUtil.toInstant(mockToday, mockTime)).thenReturn(mockNow);
                mockedDateTimeUtil.when(() -> DateTimeUtil.now()).thenReturn(mockNow);

                when(attendanceRecordMapper.selectByUserAndDateForUpdate(eq(userId), any(LocalDate.class))).thenReturn(Optional.empty());

                AttendanceRecord result = service.quickStartAttendance(userId);

                assertThat(result).isNotNull();
                verify(userNotificationService, times(1)).notifyIntervalAlert(eq(userId), anyString());
                verify(userNotificationService, times(1)).notifyIntervalAlert(eq(99L), anyString());
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // quickEndAttendance
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("quickEndAttendance")
    class QuickEndAttendance {

        @Test
        @DisplayName("正常系: 退勤打刻の登録")
        void quickEnd_success() {
            Long userId = 10L;
            User user = User.builder().userId(userId).className("標準勤務").build();
            WorkScheduleClass schedule = WorkScheduleClass.builder()
                    .name("標準勤務").startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(18, 0)).build();

            when(userMapper.selectById(userId)).thenReturn(Optional.of(user));
            when(workScheduleClassMapper.selectByName("標準勤務")).thenReturn(Optional.of(schedule));

            try (MockedStatic<DateTimeUtil> mockedDateTimeUtil = mockStatic(DateTimeUtil.class, CALLS_REAL_METHODS)) {

                LocalDate mockToday = LocalDate.of(2026, 6, 1);
                Instant mockStart = Instant.parse("2026-06-01T00:00:00Z"); // 09:00 JST
                Instant mockEnd = Instant.parse("2026-06-01T09:00:00Z");   // 18:00 JST

                mockedDateTimeUtil.when(() -> DateTimeUtil.todayJapan()).thenReturn(mockToday);
                mockedDateTimeUtil.when(() -> DateTimeUtil.currentTimeJapan()).thenReturn(LocalTime.of(18, 0));
                mockedDateTimeUtil.when(() -> DateTimeUtil.now()).thenReturn(mockEnd);

                AttendanceRecord existing = AttendanceRecord.builder()
                        .userId(userId).attendanceDate(mockStart).startTime(mockStart).build();
                when(attendanceRecordMapper.selectByUserAndDateForUpdate(eq(userId), any(LocalDate.class)))
                        .thenAnswer(invocation -> {
                            LocalDate date = invocation.getArgument(1);
                            if (date.equals(mockToday)) {
                                return Optional.of(existing);
                            }
                            return Optional.empty();
                        });

                AttendanceRecord result = service.quickEndAttendance(userId, false);

                assertThat(result.getEndTime()).isEqualTo(mockEnd);
                verify(attendanceRecordMapper).update(existing);
            }
        }

        @Test
        @DisplayName("異常系: 出勤打刻がない場合は例外")
        void quickEnd_notStarted_throwsException() {
            Long userId = 10L;
            User user = User.builder().userId(userId).className("標準勤務").build();
            WorkScheduleClass schedule = WorkScheduleClass.builder()
                    .name("標準勤務").startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(18, 0)).build();

            when(userMapper.selectById(userId)).thenReturn(Optional.of(user));
            when(workScheduleClassMapper.selectByName("標準勤務")).thenReturn(Optional.of(schedule));

            try (MockedStatic<DateTimeUtil> mockedDateTimeUtil = mockStatic(DateTimeUtil.class, CALLS_REAL_METHODS)) {
                LocalDate mockToday = LocalDate.of(2026, 6, 1);
                mockedDateTimeUtil.when(() -> DateTimeUtil.todayJapan()).thenReturn(mockToday);
                when(attendanceRecordMapper.selectByUserAndDateForUpdate(eq(userId), any(LocalDate.class))).thenReturn(Optional.empty());

                assertThatThrownBy(() -> service.quickEndAttendance(userId, false))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("本日の勤務開始時刻が記録されていません");
            }
        }

        @Test
        @DisplayName("正常系: 休日出勤の場合は休出イベントタイプと休日出勤時間を設定")
        void quickEnd_holidayWork_setsHolidayWork() {
            Long userId = 10L;
            User user = User.builder().userId(userId).className("標準勤務").build();
            WorkScheduleClass schedule = WorkScheduleClass.builder()
                    .name("標準勤務").startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(18, 0)).build();

            when(userMapper.selectById(userId)).thenReturn(Optional.of(user));
            when(workScheduleClassMapper.selectByName("標準勤務")).thenReturn(Optional.of(schedule));
            when(eventTypeMapper.selectByCode("休出")).thenReturn(Optional.of(EventType.builder().eventTypeId(2).build()));

            try (MockedStatic<DateTimeUtil> mockedDateTimeUtil = mockStatic(DateTimeUtil.class, CALLS_REAL_METHODS)) {

                LocalDate mockToday = LocalDate.of(2026, 6, 1);
                Instant mockStart = Instant.parse("2026-06-01T00:00:00Z"); // 09:00 JST
                Instant mockEnd = Instant.parse("2026-06-01T04:00:00Z");   // 13:00 JST (実働4時間)

                mockedDateTimeUtil.when(() -> DateTimeUtil.todayJapan()).thenReturn(mockToday);
                mockedDateTimeUtil.when(() -> DateTimeUtil.currentTimeJapan()).thenReturn(LocalTime.of(13, 0));
                mockedDateTimeUtil.when(() -> DateTimeUtil.now()).thenReturn(mockEnd);

                AttendanceRecord existing = AttendanceRecord.builder()
                        .userId(userId).attendanceDate(mockStart).startTime(mockStart).build();
                when(attendanceRecordMapper.selectByUserAndDateForUpdate(eq(userId), any(LocalDate.class)))
                        .thenAnswer(invocation -> {
                            LocalDate date = invocation.getArgument(1);
                            if (date.equals(mockToday)) {
                                return Optional.of(existing);
                            }
                            return Optional.empty();
                        });

                AttendanceRecord result = service.quickEndAttendance(userId, true);

                assertThat(result.getWorkingHours()).isEqualTo(0.0);
                assertThat(result.getHolidayWorkHours()).isGreaterThan(0.0);
                assertThat(result.getEventTypeId()).isEqualTo(2);
                verify(attendanceRecordMapper).update(existing);
            }
        }

        @Test
        @DisplayName("休日出勤かつ所定終業時刻超過時は、overtimeHoursを0にする（二重計上防止）")
        void quickEnd_holidayWorkPastStandardEnd_zeroesOvertimeHours() {
            Long userId = 10L;
            User user = User.builder().userId(userId).className("標準勤務").build();
            WorkScheduleClass schedule = WorkScheduleClass.builder()
                    .name("標準勤務").startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(18, 0)).build();

            when(userMapper.selectById(userId)).thenReturn(Optional.of(user));
            when(workScheduleClassMapper.selectByName("標準勤務")).thenReturn(Optional.of(schedule));
            when(eventTypeMapper.selectByCode("休出")).thenReturn(Optional.of(EventType.builder().eventTypeId(2).build()));

            try (MockedStatic<DateTimeUtil> mockedDateTimeUtil = mockStatic(DateTimeUtil.class, CALLS_REAL_METHODS)) {

                LocalDate mockToday = LocalDate.of(2026, 6, 1);
                Instant mockStart = Instant.parse("2026-06-01T00:00:00Z"); // 09:00 JST
                Instant mockEnd = Instant.parse("2026-06-01T10:00:00Z");   // 19:00 JST（所定18:00を1時間超過）

                mockedDateTimeUtil.when(() -> DateTimeUtil.todayJapan()).thenReturn(mockToday);
                mockedDateTimeUtil.when(() -> DateTimeUtil.currentTimeJapan()).thenReturn(LocalTime.of(19, 0));
                mockedDateTimeUtil.when(() -> DateTimeUtil.now()).thenReturn(mockEnd);

                AttendanceRecord existing = AttendanceRecord.builder()
                        .userId(userId).attendanceDate(mockStart).startTime(mockStart).build();
                when(attendanceRecordMapper.selectByUserAndDateForUpdate(eq(userId), any(LocalDate.class)))
                        .thenAnswer(invocation -> {
                            LocalDate date = invocation.getArgument(1);
                            if (date.equals(mockToday)) {
                                return Optional.of(existing);
                            }
                            return Optional.empty();
                        });

                AttendanceRecord result = service.quickEndAttendance(userId, true);

                assertThat(result.getWorkingHours()).isEqualTo(0.0);
                assertThat(result.getHolidayWorkHours()).isGreaterThan(0.0);
                assertThat(result.getOvertimeHours()).isEqualTo(0.0);
                verify(attendanceRecordMapper).update(existing);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // addBreakTime
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("addBreakTime")
    class AddBreakTime {

        @Test
        @DisplayName("正常系: 既存の休憩時間に指定時間を加算する")
        void addBreak_success() {
            Long userId = 10L;
            try (MockedStatic<DateTimeUtil> mockedDateTimeUtil = mockStatic(DateTimeUtil.class, CALLS_REAL_METHODS)) {
                LocalDate mockToday = LocalDate.of(2026, 6, 1);
                mockedDateTimeUtil.when(() -> DateTimeUtil.todayJapan()).thenReturn(mockToday);
                mockedDateTimeUtil.when(() -> DateTimeUtil.now()).thenReturn(Instant.parse("2026-06-01T12:00:00Z"));

                AttendanceRecord existing = AttendanceRecord.builder()
                        .startTime(Instant.parse("2026-06-01T09:00:00Z"))
                        .breakTimeMinutes(30)
                        .build();

                when(attendanceRecordMapper.selectByUserAndDateForUpdate(userId, mockToday)).thenReturn(Optional.of(existing));

                service.addBreakTime(userId, 15);

                assertThat(existing.getBreakTimeMinutes()).isEqualTo(45);
                verify(attendanceRecordMapper).update(existing);
            }
        }

        @Test
        @DisplayName("異常系: 開始打刻がない場合は例外")
        void addBreak_notStarted_throwsException() {
            Long userId = 10L;
            try (MockedStatic<DateTimeUtil> mockedDateTimeUtil = mockStatic(DateTimeUtil.class, CALLS_REAL_METHODS)) {
                LocalDate mockToday = LocalDate.of(2026, 6, 1);
                mockedDateTimeUtil.when(() -> DateTimeUtil.todayJapan()).thenReturn(mockToday);
                when(attendanceRecordMapper.selectByUserAndDateForUpdate(userId, mockToday)).thenReturn(Optional.empty());

                assertThatThrownBy(() -> service.addBreakTime(userId, 15))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("本日の勤務開始時刻が記録されていません");
            }
        }

        @Test
        @DisplayName("正常系: 退勤済みの場合、休憩追加後に勤務時間・残業時間が再計算される")
        void addBreak_afterEnd_recalculatesWorkingAndOvertimeHours() {
            Long userId = 10L;
            LocalDate mockToday = LocalDate.of(2026, 6, 1);

            User user = User.builder().userId(userId).className("標準勤務").build();
            WorkScheduleClass schedule = WorkScheduleClass.builder()
                    .name("標準勤務")
                    .startTime(LocalTime.of(9, 0))
                    .endTime(LocalTime.of(18, 0))
                    .breaks(List.of(
                            com.attendance.app.entity.WorkScheduleClassBreak.builder()
                                    .breakStartTime(LocalTime.of(19, 0))
                                    .breakEndTime(LocalTime.of(19, 30))
                                    .build()
                    ))
                    .build();

            try (MockedStatic<DateTimeUtil> mockedDateTimeUtil = mockStatic(DateTimeUtil.class, CALLS_REAL_METHODS)) {
                mockedDateTimeUtil.when(() -> DateTimeUtil.todayJapan()).thenReturn(mockToday);
                mockedDateTimeUtil.when(() -> DateTimeUtil.now()).thenReturn(Instant.parse("2026-06-01T21:00:00Z"));

                // 出勤09:00(JST)〜退勤20:00(JST) (所定終業18:00、残業2時間のうち19:00-19:30の休憩30分を控除)
                AttendanceRecord existing = AttendanceRecord.builder()
                        .attendanceDate(Instant.parse("2026-05-31T15:00:00Z")) // 2026-06-01 00:00 JST
                        .startTime(Instant.parse("2026-06-01T00:00:00Z")) // 09:00 JST
                        .endTime(Instant.parse("2026-06-01T11:00:00Z")) // 20:00 JST
                        .breakTimeMinutes(60)
                        .workingHours(9.0)
                        .overtimeHours(2.0)
                        .build();

                when(attendanceRecordMapper.selectByUserAndDateForUpdate(userId, mockToday)).thenReturn(Optional.of(existing));
                when(userMapper.selectById(userId)).thenReturn(Optional.of(user));
                when(workScheduleClassMapper.selectByName("標準勤務")).thenReturn(Optional.of(schedule));

                service.addBreakTime(userId, 30);

                assertThat(existing.getBreakTimeMinutes()).isEqualTo(90);
                assertThat(existing.getOvertimeHours()).isEqualTo(1.5);
                verify(attendanceRecordMapper).update(existing);
            }
        }

        @Test
        @DisplayName("正常系: 休日出勤レコードへの休憩追加後は overtimeHours を 0 に保つ（二重計上防止）")
        void addBreak_holidayWork_keepsOvertimeHoursZero() {
            Long userId = 10L;
            LocalDate mockToday = LocalDate.of(2026, 6, 1);

            User user = User.builder().userId(userId).className("標準勤務").build();
            WorkScheduleClass schedule = WorkScheduleClass.builder()
                    .name("標準勤務")
                    .startTime(LocalTime.of(9, 0))
                    .endTime(LocalTime.of(18, 0))
                    .build();

            try (MockedStatic<DateTimeUtil> mockedDateTimeUtil = mockStatic(DateTimeUtil.class, CALLS_REAL_METHODS)) {
                mockedDateTimeUtil.when(() -> DateTimeUtil.todayJapan()).thenReturn(mockToday);
                mockedDateTimeUtil.when(() -> DateTimeUtil.now()).thenReturn(Instant.parse("2026-06-01T21:00:00Z"));

                // 休日出勤: 09:00(JST)〜20:00(JST)、holidayWorkHours>0で休日出勤済みと判定される
                AttendanceRecord existing = AttendanceRecord.builder()
                        .attendanceDate(Instant.parse("2026-05-31T15:00:00Z")) // 2026-06-01 00:00 JST
                        .startTime(Instant.parse("2026-06-01T00:00:00Z")) // 09:00 JST
                        .endTime(Instant.parse("2026-06-01T11:00:00Z")) // 20:00 JST
                        .breakTimeMinutes(60)
                        .workingHours(0.0)
                        .holidayWorkHours(10.0)
                        .overtimeHours(2.0)
                        .build();

                when(attendanceRecordMapper.selectByUserAndDateForUpdate(userId, mockToday)).thenReturn(Optional.of(existing));
                when(userMapper.selectById(userId)).thenReturn(Optional.of(user));
                when(workScheduleClassMapper.selectByName("標準勤務")).thenReturn(Optional.of(schedule));

                service.addBreakTime(userId, 30);

                assertThat(existing.getWorkingHours()).isEqualTo(0.0);
                assertThat(existing.getHolidayWorkHours()).isGreaterThan(0.0);
                assertThat(existing.getOvertimeHours()).isEqualTo(0.0);
                verify(attendanceRecordMapper).update(existing);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // calculateNightShiftHours（saveRecord経由）
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("深夜労働時間の計算（saveRecord経由）")
    class CalculateNightShiftHours {

        @Test
        @DisplayName("通常勤務が22:00をまたぐ場合、22:00以降の1時間のみ深夜時間として計上する")
        void normalShiftCrossing22_countsOnlyOverlapWithNightWindow() {
            Long userId = 10L;
            LocalDate attendanceDate = LocalDate.of(2026, 6, 1);

            User user = User.builder().userId(userId).className("遅番勤務").build();
            WorkScheduleClass schedule = WorkScheduleClass.builder()
                    .name("遅番勤務")
                    .startTime(LocalTime.of(14, 0))
                    .endTime(LocalTime.of(23, 0))
                    .build();

            when(attendanceRecordMapper.selectByUserAndDateForUpdate(userId, attendanceDate)).thenReturn(Optional.empty());
            when(userMapper.selectById(userId)).thenReturn(Optional.of(user));
            when(workScheduleClassMapper.selectByName("遅番勤務")).thenReturn(Optional.of(schedule));

            // 14:00〜23:00勤務のうち22:00〜23:00の1時間のみ深夜帯と重複する
            AttendanceRecord result = service.saveRecord(
                    userId, attendanceDate, LocalTime.of(14, 0), LocalTime.of(23, 0), "", false, null);

            assertThat(result.getNightShiftHours()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("日跨ぎ夜勤（前日21:00〜翌6:00）は22:00〜翌5:00の7時間を深夜時間として計上する")
        void overnightShift_countsHoursAcrossMidnight() {
            Long userId = 10L;
            LocalDate attendanceDate = LocalDate.of(2026, 6, 1);

            User user = User.builder().userId(userId).className("夜勤勤務").build();
            WorkScheduleClass schedule = WorkScheduleClass.builder()
                    .name("夜勤勤務")
                    .startTime(LocalTime.of(21, 0))
                    .endTime(LocalTime.of(6, 0))
                    .build();

            when(attendanceRecordMapper.selectByUserAndDateForUpdate(userId, attendanceDate)).thenReturn(Optional.empty());
            when(userMapper.selectById(userId)).thenReturn(Optional.of(user));
            when(workScheduleClassMapper.selectByName("夜勤勤務")).thenReturn(Optional.of(schedule));

            // 21:00〜翌6:00勤務のうち22:00〜翌5:00の7時間が深夜帯と重複する
            AttendanceRecord result = service.saveRecord(
                    userId, attendanceDate, LocalTime.of(21, 0), LocalTime.of(6, 0), "", false, null);

            assertThat(result.getNightShiftHours()).isEqualTo(7.0);
        }

        @Test
        @DisplayName("休憩が深夜帯と重複する場合は重複分を控除する")
        void breakOverlappingNightWindow_deductsFromNightShiftHours() {
            Long userId = 10L;
            LocalDate attendanceDate = LocalDate.of(2026, 6, 1);

            User user = User.builder().userId(userId).className("夜間休憩あり勤務").build();
            WorkScheduleClass schedule = WorkScheduleClass.builder()
                    .name("夜間休憩あり勤務")
                    .startTime(LocalTime.of(20, 0))
                    .endTime(LocalTime.of(23, 0))
                    .breaks(List.of(
                            com.attendance.app.entity.WorkScheduleClassBreak.builder()
                                    .breakStartTime(LocalTime.of(22, 0))
                                    .breakEndTime(LocalTime.of(22, 30))
                                    .build()
                    ))
                    .build();

            when(attendanceRecordMapper.selectByUserAndDateForUpdate(userId, attendanceDate)).thenReturn(Optional.empty());
            when(userMapper.selectById(userId)).thenReturn(Optional.of(user));
            when(workScheduleClassMapper.selectByName("夜間休憩あり勤務")).thenReturn(Optional.of(schedule));

            // 20:00〜23:00勤務のうち22:00〜23:00(1時間)が深夜帯と重複するが、
            // 深夜帯にかかる休憩22:00〜22:30(30分)を控除して0.5時間となる
            AttendanceRecord result = service.saveRecord(
                    userId, attendanceDate, LocalTime.of(20, 0), LocalTime.of(23, 0), "", false, null);

            assertThat(result.getNightShiftHours()).isEqualTo(0.5);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // saveRecordsBatch
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("saveRecordsBatch")
    class SaveRecordsBatch {

        @Test
        @DisplayName("新規行は insert を呼び保存件数を1件加算する")
        void newRecord_insertsAndCountsAsSaved() {
            Long userId = 10L;
            LocalDate date = LocalDate.of(2026, 6, 1);
            User user = User.builder().userId(userId).className("標準勤務").build();
            WorkScheduleClass schedule = WorkScheduleClass.builder()
                    .name("標準勤務").startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(18, 0)).build();

            when(attendanceRecordMapper.selectByUserAndDateForUpdate(userId, date)).thenReturn(Optional.empty());
            when(userMapper.selectById(userId)).thenReturn(Optional.of(user));
            when(workScheduleClassMapper.selectByName("標準勤務")).thenReturn(Optional.of(schedule));

            int savedCount = service.saveRecordsBatch(
                    userId,
                    List.of(date),
                    List.of(LocalTime.of(9, 0)),
                    List.of(LocalTime.of(18, 0)),
                    List.of(""),
                    Set.of(),
                    java.util.Collections.singletonList(null));

            assertThat(savedCount).isEqualTo(1);
            verify(attendanceRecordMapper).insert(any(AttendanceRecord.class));
        }

        @Test
        @DisplayName("既存行の内容が変わっていれば update を呼び保存件数を1件加算する")
        void changedExistingRecord_updatesAndCountsAsSaved() {
            Long userId = 10L;
            LocalDate date = LocalDate.of(2026, 6, 1);
            AttendanceRecord existing = AttendanceRecord.builder()
                    .recordId(100L)
                    .startTime(Instant.parse("2026-06-01T00:00:00Z")) // 09:00 JST
                    .endTime(Instant.parse("2026-06-01T08:00:00Z"))   // 17:00 JST
                    .remarks("旧備考")
                    .build();
            User user = User.builder().userId(userId).className("標準勤務").build();
            WorkScheduleClass schedule = WorkScheduleClass.builder()
                    .name("標準勤務").startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(18, 0)).build();

            when(attendanceRecordMapper.selectByUserAndDateForUpdate(userId, date)).thenReturn(Optional.of(existing));
            when(userMapper.selectById(userId)).thenReturn(Optional.of(user));
            when(workScheduleClassMapper.selectByName("標準勤務")).thenReturn(Optional.of(schedule));

            int savedCount = service.saveRecordsBatch(
                    userId,
                    List.of(date),
                    List.of(LocalTime.of(9, 0)),
                    List.of(LocalTime.of(18, 0)), // 終業時刻が17:00→18:00に変更
                    List.of("旧備考"),
                    Set.of(),
                    java.util.Collections.singletonList(null));

            assertThat(savedCount).isEqualTo(1);
            verify(attendanceRecordMapper).update(existing);
        }

        @Test
        @DisplayName("開始・終了ともにnullの行は既存レコードを削除する")
        void bothTimesNull_deletesExistingRecord() {
            Long userId = 10L;
            LocalDate date = LocalDate.of(2026, 6, 1);
            AttendanceRecord existing = AttendanceRecord.builder().recordId(100L).build();
            User user = User.builder().userId(userId).className("標準勤務").build();
            WorkScheduleClass schedule = WorkScheduleClass.builder()
                    .name("標準勤務").startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(18, 0)).build();

            when(attendanceRecordMapper.selectByUserAndDateForUpdate(userId, date)).thenReturn(Optional.of(existing));
            when(userMapper.selectById(userId)).thenReturn(Optional.of(user));
            when(workScheduleClassMapper.selectByName("標準勤務")).thenReturn(Optional.of(schedule));

            int savedCount = service.saveRecordsBatch(
                    userId,
                    List.of(date),
                    java.util.Collections.singletonList(null),
                    java.util.Collections.singletonList(null),
                    java.util.Collections.singletonList(null),
                    Set.of(),
                    java.util.Collections.singletonList(null));

            assertThat(savedCount).isEqualTo(1);
            verify(attendanceRecordMapper).deleteById(100L);
        }

        @Test
        @DisplayName("既存行と内容が変わらなければ保存をスキップし件数に含めない")
        void unchangedExistingRecord_isSkipped() {
            Long userId = 10L;
            LocalDate date = LocalDate.of(2026, 6, 1);
            AttendanceRecord existing = AttendanceRecord.builder()
                    .recordId(100L)
                    .startTime(Instant.parse("2026-06-01T00:00:00Z")) // 09:00 JST
                    .endTime(Instant.parse("2026-06-01T09:00:00Z"))   // 18:00 JST
                    .remarks("同じ備考")
                    .eventTypeId(1)
                    .holidayWorkHours(0.0)
                    .build();

            when(attendanceRecordMapper.selectByUserAndDateForUpdate(userId, date)).thenReturn(Optional.of(existing));

            int savedCount = service.saveRecordsBatch(
                    userId,
                    List.of(date),
                    List.of(LocalTime.of(9, 0)),
                    List.of(LocalTime.of(18, 0)),
                    List.of("同じ備考"),
                    Set.of(),
                    List.of(1));

            assertThat(savedCount).isEqualTo(0);
            verify(attendanceRecordMapper, never()).update(any());
            verify(attendanceRecordMapper, never()).insert(any());
            verify(attendanceRecordMapper, never()).deleteById(any());
        }

        @Test
        @DisplayName("休日出勤フラグが変化した場合は他の内容が同じでも保存する")
        void holidayWorkFlagToggled_stillSavesEvenIfOtherFieldsUnchanged() {
            Long userId = 10L;
            LocalDate date = LocalDate.of(2026, 6, 6);
            AttendanceRecord existing = AttendanceRecord.builder()
                    .recordId(100L)
                    .startTime(Instant.parse("2026-06-06T00:00:00Z")) // 09:00 JST
                    .endTime(Instant.parse("2026-06-06T09:00:00Z"))   // 18:00 JST
                    .remarks("")
                    .holidayWorkHours(0.0)
                    .build();
            User user = User.builder().userId(userId).className("標準勤務").build();
            WorkScheduleClass schedule = WorkScheduleClass.builder()
                    .name("標準勤務").startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(18, 0)).build();

            when(attendanceRecordMapper.selectByUserAndDateForUpdate(userId, date)).thenReturn(Optional.of(existing));
            when(userMapper.selectById(userId)).thenReturn(Optional.of(user));
            when(workScheduleClassMapper.selectByName("標準勤務")).thenReturn(Optional.of(schedule));

            int savedCount = service.saveRecordsBatch(
                    userId,
                    List.of(date),
                    List.of(LocalTime.of(9, 0)),
                    List.of(LocalTime.of(18, 0)),
                    List.of(""),
                    Set.of(date), // 休日出勤フラグON
                    java.util.Collections.singletonList(null));

            assertThat(savedCount).isEqualTo(1);
            assertThat(existing.getHolidayWorkHours()).isGreaterThan(0.0);
            verify(attendanceRecordMapper).update(existing);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getMonthlyAggregateForAllUsers
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("getMonthlyAggregateForAllUsers")
    class GetMonthlyAggregateForAllUsers {

        @Test
        @DisplayName("正常系: ユーザーごとに勤務・残業・深夜時間を合算する")
        void aggregatesHoursPerUser() {
            AttendanceRecord user1Record1 = AttendanceRecord.builder()
                    .userId(1L).workingHours(8.0).overtimeHours(2.0).nightShiftHours(1.0).build();
            AttendanceRecord user1Record2 = AttendanceRecord.builder()
                    .userId(1L).workingHours(7.5).overtimeHours(0.0).nightShiftHours(null).build();
            AttendanceRecord user2Record1 = AttendanceRecord.builder()
                    .userId(2L).workingHours(8.0).overtimeHours(1.0).nightShiftHours(0.5).build();

            when(attendanceRecordMapper.selectAllByDateRange(any(Instant.class), any(Instant.class)))
                    .thenReturn(List.of(user1Record1, user1Record2, user2Record1));

            List<AttendanceRecordService.MonthlyUserSummary> result =
                    service.getMonthlyAggregateForAllUsers(YearMonth.of(2026, 6));

            AttendanceRecordService.MonthlyUserSummary user1Summary = result.stream()
                    .filter(s -> s.userId().equals(1L)).findFirst().orElseThrow();
            AttendanceRecordService.MonthlyUserSummary user2Summary = result.stream()
                    .filter(s -> s.userId().equals(2L)).findFirst().orElseThrow();

            assertThat(user1Summary.workingHours()).isEqualTo(15.5);
            assertThat(user1Summary.overtimeHours()).isEqualTo(2.0);
            assertThat(user1Summary.nightShiftHours()).isEqualTo(1.0);
            assertThat(user1Summary.recordCount()).isEqualTo(2);

            assertThat(user2Summary.workingHours()).isEqualTo(8.0);
            assertThat(user2Summary.overtimeHours()).isEqualTo(1.0);
            assertThat(user2Summary.nightShiftHours()).isEqualTo(0.5);
            assertThat(user2Summary.recordCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("月境界: 年をまたぐ場合も前月開始日〜当月終了日の範囲で取得する")
        void yearBoundary_computesCorrectDateRange() {
            when(attendanceRecordMapper.selectAllByDateRange(any(Instant.class), any(Instant.class)))
                    .thenReturn(List.of());

            service.getMonthlyAggregateForAllUsers(YearMonth.of(2026, 1));

            Instant expectedStart = DateTimeUtil.toInstant(LocalDate.of(2025, 12, 21));
            Instant expectedEnd = DateTimeUtil.toInstant(LocalDate.of(2026, 1, 21));
            verify(attendanceRecordMapper).selectAllByDateRange(expectedStart, expectedEnd);
        }

        @Test
        @DisplayName("大量データ: 一覧上限100ユーザーだけを取得して集計する")
        void aggregatesOnlyRequestedHundredUsersForLargeData() {
            List<Long> userIds = java.util.stream.LongStream.rangeClosed(1, 100).boxed().toList();
            List<AttendanceRecord> records = new java.util.ArrayList<>();
            for (Long userId : userIds) {
                for (int day = 0; day < 31; day++) {
                    records.add(AttendanceRecord.builder()
                            .userId(userId).workingHours(8.0).overtimeHours(1.0).nightShiftHours(0.5).build());
                }
            }
            when(attendanceRecordMapper.selectByDateRangeAndUserIds(any(Instant.class), any(Instant.class), eq(userIds)))
                    .thenReturn(records);

            List<AttendanceRecordService.MonthlyUserSummary> result =
                    service.getMonthlyAggregateForUsers(YearMonth.of(2026, 6), userIds);

            assertThat(result).hasSize(100);
            assertThat(result).allSatisfy(summary -> {
                assertThat(summary.workingHours()).isEqualTo(248.0);
                assertThat(summary.overtimeHours()).isEqualTo(31.0);
                assertThat(summary.nightShiftHours()).isEqualTo(15.5);
                assertThat(summary.recordCount()).isEqualTo(31);
            });
            verify(attendanceRecordMapper).selectByDateRangeAndUserIds(any(Instant.class), any(Instant.class), eq(userIds));
            verify(attendanceRecordMapper, never()).selectAllByDateRange(any(Instant.class), any(Instant.class));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getOvertimeSumByUserForMonthRange
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("getOvertimeSumByUserForMonthRange")
    class GetOvertimeSumByUserForMonthRange {

        @Test
        @DisplayName("正常系: 複数月・複数ユーザーの残業時間を年月ごとに集計する")
        void aggregatesOvertimePerMonthAndUser() {
            AttendanceRecord mayRecord = AttendanceRecord.builder()
                    .userId(1L)
                    .attendanceDate(DateTimeUtil.toInstant(LocalDate.of(2026, 5, 10)))
                    .overtimeHours(2.0)
                    .build();
            AttendanceRecord juneRecord = AttendanceRecord.builder()
                    .userId(1L)
                    .attendanceDate(DateTimeUtil.toInstant(LocalDate.of(2026, 6, 10)))
                    .overtimeHours(3.0)
                    .build();

            when(attendanceRecordMapper.selectAllByDateRange(any(Instant.class), any(Instant.class)))
                    .thenReturn(List.of(mayRecord, juneRecord));

            Map<YearMonth, Map<Long, Double>> result = service.getOvertimeSumByUserForMonthRange(
                    List.of(YearMonth.of(2026, 5), YearMonth.of(2026, 6)));

            assertThat(result.get(YearMonth.of(2026, 5))).containsEntry(1L, 2.0);
            assertThat(result.get(YearMonth.of(2026, 6))).containsEntry(1L, 3.0);
        }

        @Test
        @DisplayName("月境界: 前月最終日は前月の集計へ、当月初日は当月の集計へ振り分ける")
        void monthBoundaryDates_areAssignedToCorrectMonth() {
            // 2026-05の集計期間は 2026-04-21〜2026-05-20、2026-06の集計期間は 2026-05-21〜2026-06-20
            AttendanceRecord mayLastDay = AttendanceRecord.builder()
                    .userId(1L)
                    .attendanceDate(DateTimeUtil.toInstant(LocalDate.of(2026, 5, 20))) // 5月期間の最終日
                    .overtimeHours(2.0)
                    .build();
            AttendanceRecord juneFirstDay = AttendanceRecord.builder()
                    .userId(1L)
                    .attendanceDate(DateTimeUtil.toInstant(LocalDate.of(2026, 5, 21))) // 6月期間の初日
                    .overtimeHours(3.0)
                    .build();
            AttendanceRecord juneLastDay = AttendanceRecord.builder()
                    .userId(2L)
                    .attendanceDate(DateTimeUtil.toInstant(LocalDate.of(2026, 6, 20))) // 6月期間の最終日
                    .overtimeHours(1.5)
                    .build();

            when(attendanceRecordMapper.selectAllByDateRange(any(Instant.class), any(Instant.class)))
                    .thenReturn(List.of(mayLastDay, juneFirstDay, juneLastDay));

            Map<YearMonth, Map<Long, Double>> result = service.getOvertimeSumByUserForMonthRange(
                    List.of(YearMonth.of(2026, 5), YearMonth.of(2026, 6)));

            assertThat(result.get(YearMonth.of(2026, 5))).containsEntry(1L, 2.0);
            assertThat(result.get(YearMonth.of(2026, 5))).doesNotContainKey(2L);
            assertThat(result.get(YearMonth.of(2026, 6))).containsEntry(1L, 3.0);
            assertThat(result.get(YearMonth.of(2026, 6))).containsEntry(2L, 1.5);
        }
    }
}
