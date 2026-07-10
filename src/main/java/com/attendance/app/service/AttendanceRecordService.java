package com.attendance.app.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.attendance.app.entity.AttendanceRecord;
import com.attendance.app.entity.WorkScheduleClass;
import com.attendance.app.entity.UserNotification;
import com.attendance.app.mapper.AttendanceRecordMapper;
import com.attendance.app.mapper.EventTypeMapper;
import com.attendance.app.mapper.UserMapper;
import com.attendance.app.mapper.WorkScheduleClassMapper;
import com.attendance.app.mapper.AttendanceSubmissionMapper;
import com.attendance.app.util.DateTimeUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * AttendanceRecord Service - 勤怠管理の業務ロジック
 *
 * タイムゾーン対応: すべてのタイムスタンプは Instant（UTC）で管理
 * Service層ではDateTimeUtilを使用してLocalDate/LocalTimeから変換
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AttendanceRecordService {

    private final AttendanceRecordMapper attendanceRecordMapper;
    private final OvertimeRecordService overtimeRecordService;
    private final UserMapper userMapper;
    private final WorkScheduleClassMapper workScheduleClassMapper;
    private final AttendancePeriodSettingService attendancePeriodSettingService;
    private final EventTypeMapper eventTypeMapper;
    private final AttendanceSubmissionMapper attendanceSubmissionMapper;
    private final UserNotificationService userNotificationService;
    private final BatchSettingService batchSettingService;

    private static final LocalTime DEFAULT_STANDARD_START_TIME = LocalTime.of(9, 0);
    private static final LocalTime DEFAULT_STANDARD_END_TIME = LocalTime.of(18, 0);
    private static final LocalTime DEFAULT_BREAK_START_TIME = LocalTime.of(12, 0);
    private static final LocalTime DEFAULT_BREAK_END_TIME = LocalTime.of(13, 0);
    private static final int DEFAULT_BREAK_MINUTES = 60;

    /**
     * ユーザーの勤務クラスに基づく所定開始・終了時刻などのスケジュール定義を取得します。
     * 勤務クラス未設定の場合はデフォルトのスケジュール（09:00-18:00）を返します。
     *
     * @param userId 対象のユーザーID
     * @return 勤務スケジュール定義
     */
    private WorkScheduleDefinition getScheduleForUserAndRecord(Long userId, Optional<AttendanceRecord> existingRecord) {
        if (existingRecord.isPresent() && existingRecord.get().getClassId() != null) {
            return workScheduleClassMapper.selectById(existingRecord.get().getClassId())
                    .map(o -> this.toWorkScheduleDefinition(o))
                    .orElseGet(() -> WorkScheduleDefinition.defaultSchedule());
        }
        return userMapper.selectById(userId)
                .map(user -> normalizeText(user.getClassName()))
                .flatMap(className -> className != null ? workScheduleClassMapper.selectByName(className) : Optional.empty())
                .map(o -> this.toWorkScheduleDefinition(o))
                .orElseGet(() -> WorkScheduleDefinition.defaultSchedule());
    }

    private WorkScheduleDefinition toWorkScheduleDefinition(WorkScheduleClass workScheduleClass) {
        List<BreakWindow> breakWindows = new ArrayList<>();
        if (workScheduleClass.getBreaks() != null) {
            for (com.attendance.app.entity.WorkScheduleClassBreak b : workScheduleClass.getBreaks()) {
                addBreakWindow(breakWindows, b.getBreakStartTime(), b.getBreakEndTime());
            }
        }

        return new WorkScheduleDefinition(
                workScheduleClass.getClassId(),
                workScheduleClass.getName(),
                workScheduleClass.getStartTime(),
                workScheduleClass.getEndTime(),
                workScheduleClass.getTotalBreakMinutes(),
                breakWindows);
    }

    private void addBreakWindow(List<BreakWindow> breakWindows, LocalTime startTime, LocalTime endTime) {
        if (startTime != null && endTime != null) {
            breakWindows.add(new BreakWindow(startTime, endTime));
        }
    }

    private Instant resolveAttendanceEndInstant(LocalDate attendanceDate, LocalTime startTime, LocalTime endTime) {
        if (attendanceDate == null || endTime == null) {
            return null;
        }

        LocalDate endDate = startTime != null && endTime.isBefore(startTime)
                ? attendanceDate.plusDays(1)
                : attendanceDate;
        return DateTimeUtil.toInstant(endDate, endTime);
    }

    /**
     * 記録IDに該当する勤怠記録を取得します。
     *
     * @param recordId 取得対象の勤怠記録ID
     * @return 勤怠記録。存在しない場合は empty
     */
    public Optional<AttendanceRecord> getRecordById(Long recordId) {
        return attendanceRecordMapper.selectById(recordId);
    }

    /**
     * 指定されたユーザーIDと日付に該当する勤怠記録を取得します。
     *
     * @param userId ユーザーID
     * @param date 対象日付
     * @return 勤怠記録。存在しない場合は empty
     */
    public Optional<AttendanceRecord> getRecordByUserAndDate(Long userId, LocalDate date) {
        return attendanceRecordMapper.selectByUserAndDate(userId, date);
    }

    /**
     * 指定されたユーザーIDと期間（開始・終了タイムスタンプ）に該当する勤怠記録のリストを取得します。
     *
     * @param userId ユーザーID
     * @param startDate 期間の開始日時（Instant）
     * @param endDate 期間の終了日時（Instant）
     * @return 該当する勤怠記録のリスト
     */
    public List<AttendanceRecord> getRecordsByUserAndDateRange(Long userId, Instant startDate, Instant endDate) {
        return attendanceRecordMapper.selectByUserAndDateRange(userId, startDate, endDate);
    }

    /**
     * 指定されたユーザーの月別勤怠一覧を取得します。
     * システムに設定された開始日と終了日に基づいて期間を判定します。
     *
     * @param userId ユーザーID
     * @param yearMonth 対象の年月
     * @return 月内の勤怠記録リスト
     */
    public List<AttendanceRecord> getRecordsByUserAndMonth(Long userId, YearMonth yearMonth) {
        String key = yearMonth.format(DateTimeFormatter.ofPattern("yyyy-MM"));
        Optional<com.attendance.app.entity.AttendanceSubmission> submission = attendanceSubmissionMapper.selectByUserAndMonth(userId, key);

        LocalDate startDate;
        LocalDate endDate;
        if (submission.isPresent() && submission.get().getStartDate() != null && submission.get().getEndDate() != null) {
            startDate = submission.get().getStartDate();
            endDate = submission.get().getEndDate();
        } else {
            startDate = yearMonth.minusMonths(1).atDay(attendancePeriodSettingService.getStartDay());
            endDate = yearMonth.atDay(attendancePeriodSettingService.getEndDay());
        }

        Instant startDateTime = DateTimeUtil.toInstant(startDate);
        Instant endDateTime = DateTimeUtil.toInstant(endDate.plusDays(1));

        return attendanceRecordMapper.selectByUserAndDateRange(userId, startDateTime, endDateTime);
    }

    /**
     * ユーザーの全勤怠記録を取得します。
     *
     * @param userId ユーザーID
     * @return 全ての勤怠記録リスト
     */
    public List<AttendanceRecord> getRecordsByUser(Long userId) {
        return attendanceRecordMapper.selectByUserId(userId);
    }

    /**
     * 勤怠記録を作成または更新します。
     * すでに同日の記録が存在する場合は更新し、存在しない場合は新規作成します。
     * 同時に、勤務時間や残業時間、休日出勤時間の計算も行います。
     *
     * @param userId ユーザーID
     * @param attendanceDate 対象となる勤務日
     * @param startTime 出勤時刻
     * @param endTime 退勤時刻
     * @param remarks 備考
     * @param isHolidayWork 休日出勤であるかどうかのフラグ
     * @return 保存された勤怠記録
     */
    public AttendanceRecord saveRecord(Long userId, LocalDate attendanceDate, LocalTime startTime, LocalTime endTime, String remarks, boolean isHolidayWork, Integer eventTypeId) {
        Optional<AttendanceRecord> existing = attendanceRecordMapper.selectByUserAndDateForUpdate(userId, attendanceDate);
        WorkScheduleDefinition schedule = getScheduleForUserAndRecord(userId, existing);

        java.time.Instant startInstant = DateTimeUtil.toInstant(attendanceDate, startTime);
        java.time.Instant endInstant = resolveAttendanceEndInstant(attendanceDate, startTime, endTime);

        if (startInstant != null) {
            checkIntervalAndNotify(userId, attendanceDate, startInstant);
        }

        Double workingHours = calculateWorkingHoursExcludingBreakOverlaps(startInstant, endInstant, attendanceDate, schedule);
        Double overtimeHours = calculateExcessOvertime(attendanceDate, schedule, endInstant);
        // 休日出勤時は実働を holidayWorkHours に計上し、workingHours は0.0にする（quickEndAttendance と統一）
        Double holidayWorkHours = isHolidayWork ? workingHours : 0.0;
        if (isHolidayWork) {
            workingHours = 0.0;
            // 休日労働はholidayWorkHoursに一本化し、overtimeHoursとの二重計上を避ける
            overtimeHours = 0.0;
        }
        Double nightShiftHours = calculateNightShiftHours(startInstant, endInstant, attendanceDate, schedule);
        int breakTimeMinutes = (int) calculateBreakOverlapMinutes(startInstant, endInstant, attendanceDate, schedule);

        AttendanceRecord record;
        if (existing.isPresent()) {
            record = existing.get();
            record.setStartTime(startInstant);
            record.setEndTime(endInstant);
            record.setWorkingHours(workingHours);
            record.setOvertimeHours(overtimeHours);
            record.setHolidayWorkHours(holidayWorkHours);
            record.setNightShiftHours(nightShiftHours);
            record.setBreakTimeMinutes(breakTimeMinutes);
            if (record.getClassId() == null) {
                record.setClassId(schedule.classId());
            }
            record.setRemarks(remarks);
            record.setEventTypeId(eventTypeId);
            record.setUpdatedAt(DateTimeUtil.now());
            attendanceRecordMapper.update(record);
            log.info("勤怠記録を更新しました: userId={}, date={}", userId, attendanceDate);
        } else {
            record = AttendanceRecord.builder()
                    .userId(userId)
                    .classId(schedule.classId())
                    .attendanceDate(DateTimeUtil.toInstant(attendanceDate))
                    .startTime(startInstant)
                    .endTime(endInstant)
                    .workingHours(workingHours)
                    .overtimeHours(overtimeHours)
                    .holidayWorkHours(holidayWorkHours)
                    .nightShiftHours(nightShiftHours)
                    .breakTimeMinutes(breakTimeMinutes)
                    .remarks(remarks)
                    .eventTypeId(eventTypeId)
                    .createdAt(DateTimeUtil.now())
                    .updatedAt(DateTimeUtil.now())
                    .build();
            attendanceRecordMapper.insert(record);
            log.info("勤怠記録を作成しました: userId={}, date={}", userId, attendanceDate);
        }

        overtimeRecordService.syncFromAttendance(
            userId,
            attendanceDate,
            schedule.standardStartTime(),
            schedule.standardEndTime(),
            endInstant,
            overtimeHours);

        return record;
    }

    /**
     * ワンクリック勤怠入力として、現在時刻で勤務開始（出勤）を記録します。
     *
     * @param userId 対象のユーザーID
     * @return 記録された勤怠情報
     * @throws IllegalArgumentException 既に開始時刻が記録されている場合など
     */
    public AttendanceRecord quickStartAttendance(Long userId) {
        LocalDate today = DateTimeUtil.todayJapan();
        LocalTime currentTime = DateTimeUtil.currentTimeJapan();
        Optional<AttendanceRecord> existing = attendanceRecordMapper.selectByUserAndDateForUpdate(userId, today);
        WorkScheduleDefinition schedule = getScheduleForUserAndRecord(userId, existing);

        if (schedule.isOvernight()) {
            attendanceRecordMapper.selectByUserAndDate(userId, today.minusDays(1))
                    .filter(record -> record.getStartTime() != null && record.getEndTime() == null)
                    .ifPresent(record -> {
                        throw new IllegalArgumentException("前日の勤務終了時刻が記録されていません");
                    });
        }

        checkIntervalAndNotify(userId, today, DateTimeUtil.toInstant(today, currentTime));

        if (existing.isPresent() && existing.get().getStartTime() != null) {
            throw new IllegalArgumentException("本日は既に勤務開始時刻が記録されています");
        }

        Integer defaultEventTypeId = eventTypeMapper.selectByCode("通常")
                .map(e -> e.getEventTypeId())
                .orElse(null);

        AttendanceRecord record;
        if (existing.isPresent()) {
            record = existing.get();
            record.setStartTime(DateTimeUtil.toInstant(today, currentTime));
            if (record.getClassId() == null) {
                record.setClassId(schedule.classId());
            }
            if (record.getEventTypeId() == null) {
                record.setEventTypeId(defaultEventTypeId);
            }
            record.setUpdatedAt(DateTimeUtil.now());
            attendanceRecordMapper.update(record);
        } else {
            record = AttendanceRecord.builder()
                    .userId(userId)
                    .classId(schedule.classId())
                    .attendanceDate(DateTimeUtil.toInstant(today))
                    .startTime(DateTimeUtil.toInstant(today, currentTime))
                    .eventTypeId(defaultEventTypeId)
                    .breakTimeMinutes(0)
                    .overtimeHours(0.0)
                    .holidayWorkHours(0.0)
                    .nightShiftHours(0.0)
                    .createdAt(DateTimeUtil.now())
                    .updatedAt(DateTimeUtil.now())
                    .build();
            attendanceRecordMapper.insert(record);
        }

        overtimeRecordService.syncFromAttendance(
                userId,
                today,
            schedule.standardStartTime(),
            schedule.standardEndTime(),
                record.getEndTime());

        log.info("勤務開始を記録しました: userId={}, time={}", userId, currentTime);
        return record;
    }

    /**
     * ワンクリック勤怠入力として、現在時刻で勤務終了（退勤）を記録します。
     *
     * @param userId 対象のユーザーID
     * @return 更新された勤怠情報
     * @throws IllegalArgumentException 勤務開始時刻が記録されていない場合など
     */
    public AttendanceRecord quickEndAttendance(Long userId, boolean isHolidayWork) {
        LocalDate today = DateTimeUtil.todayJapan();
        LocalTime currentTime = DateTimeUtil.currentTimeJapan();

        WorkScheduleDefinition tempSchedule = getScheduleForUserAndRecord(userId, Optional.empty());
        AttendanceRecord record = attendanceRecordMapper.selectByUserAndDateForUpdate(userId, today)
            .filter(existingRecord -> existingRecord.getStartTime() != null)
            .or(() -> tempSchedule.isOvernight()
                ? attendanceRecordMapper.selectByUserAndDateForUpdate(userId, today.minusDays(1))
                    .filter(existingRecord -> existingRecord.getStartTime() != null && existingRecord.getEndTime() == null)
                : Optional.empty())
            .orElseThrow(() -> new IllegalArgumentException("本日の勤務開始時刻が記録されていません"));

        WorkScheduleDefinition schedule = getScheduleForUserAndRecord(userId, Optional.of(record));

        if (record.getStartTime() == null) {
            throw new IllegalArgumentException("勤務開始時刻が記録されていません");
        }

        LocalDate attendanceDate = DateTimeUtil.toLocalDate(record.getAttendanceDate());
        LocalTime startTime = DateTimeUtil.toLocalTime(record.getStartTime());
        Instant endInstant = resolveAttendanceEndInstant(attendanceDate, startTime, currentTime);
        Double workingHours = calculateWorkingHoursExcludingBreakOverlaps(record.getStartTime(), endInstant, attendanceDate, schedule);
        Double overtimeHours = calculateExcessOvertime(attendanceDate, schedule, endInstant);
        Double nightShiftHours = calculateNightShiftHours(record.getStartTime(), endInstant, attendanceDate, schedule);
        int breakTimeMinutes = (int) calculateBreakOverlapMinutes(record.getStartTime(), endInstant, attendanceDate, schedule);

        record.setEndTime(endInstant);
        if (isHolidayWork) {
            record.setWorkingHours(0.0);
            record.setHolidayWorkHours(workingHours);
            // 休日労働はholidayWorkHoursに一本化し、overtimeHoursとの二重計上を避ける
            overtimeHours = 0.0;
            Integer holidayWorkTypeId = eventTypeMapper.selectByCode("休出")
                    .map(e -> e.getEventTypeId())
                    .orElse(null);
            record.setEventTypeId(holidayWorkTypeId);
        } else {
            record.setWorkingHours(workingHours);
            record.setHolidayWorkHours(0.0);
            if (record.getEventTypeId() == null) {
                Integer defaultEventTypeId = eventTypeMapper.selectByCode("通常")
                        .map(e -> e.getEventTypeId())
                        .orElse(null);
                record.setEventTypeId(defaultEventTypeId);
            }
        }
        record.setOvertimeHours(overtimeHours);
        record.setNightShiftHours(nightShiftHours);
        record.setBreakTimeMinutes(breakTimeMinutes);
        if (record.getClassId() == null) {
            record.setClassId(schedule.classId());
        }
        record.setUpdatedAt(DateTimeUtil.now());

        attendanceRecordMapper.update(record);
        overtimeRecordService.syncFromAttendance(
            userId,
            attendanceDate,
            schedule.standardStartTime(),
            schedule.standardEndTime(),
            record.getEndTime(),
            overtimeHours);
        log.info("勤務終了を記録しました: userId={}, time={}, workingHours={}", userId, currentTime, workingHours);
        return record;
    }

    /**
     * 休憩時間を追加します。
     *
     * @param userId 対象のユーザーID
     * @param breakTimeMinutes 追加する休憩時間（分）
     */
    public void addBreakTime(Long userId, int breakTimeMinutes) {
        LocalDate today = DateTimeUtil.todayJapan();
        AttendanceRecord record = attendanceRecordMapper.selectByUserAndDateForUpdate(userId, today)
            .orElseThrow(() -> new IllegalArgumentException("本日の勤務開始時刻が記録されていません"));

        if (record.getStartTime() == null) {
            throw new IllegalArgumentException("勤務開始時刻が記録されていません");
        }

        int currentBreak = record.getBreakTimeMinutes() != null ? record.getBreakTimeMinutes() : 0;
        record.setBreakTimeMinutes(currentBreak + breakTimeMinutes);

        if (record.getStartTime() != null && record.getEndTime() != null) {
            WorkScheduleDefinition schedule = getScheduleForUserAndRecord(userId, Optional.of(record));
            LocalDate attendanceDate = DateTimeUtil.toLocalDate(record.getAttendanceDate());
            Double workingHours = calculateWorkingHoursExcludingBreakOverlaps(record.getStartTime(), record.getEndTime(), attendanceDate, schedule);
            Double overtimeHours = calculateExcessOvertime(attendanceDate, schedule, record.getEndTime());
            Double nightShiftHours = calculateNightShiftHours(record.getStartTime(), record.getEndTime(), attendanceDate, schedule);

            boolean isHolidayWork = record.getHolidayWorkHours() != null && record.getHolidayWorkHours() > 0;
            if (isHolidayWork) {
                record.setWorkingHours(0.0);
                record.setHolidayWorkHours(workingHours);
                // 休日労働はholidayWorkHoursに一本化し、overtimeHoursとの二重計上を避ける
                overtimeHours = 0.0;
            } else {
                record.setWorkingHours(workingHours);
            }
            record.setOvertimeHours(overtimeHours);
            record.setNightShiftHours(nightShiftHours);
        }

        record.setUpdatedAt(DateTimeUtil.now());

        attendanceRecordMapper.update(record);
        log.info("休憩時間を追加しました: userId={}, breakTimeMinutes={}", userId, breakTimeMinutes);
    }

    /**
     * 指定された勤怠記録を削除します。
     *
     * @param recordId 削除対象の勤怠記録ID
     */
    public void deleteRecord(Long recordId) {
        attendanceRecordMapper.deleteById(recordId);
        log.info("勤怠記録を削除しました: recordId={}", recordId);
    }

    /**
     * ユーザーに関連する勤怠記録をすべて削除します。
     * 退職時のデータクリーンアップなどに使用されます。
     *
     * @param userId 対象のユーザーID
     */
    public void deleteRecordsByUser(Long userId) {
        attendanceRecordMapper.deleteByUserId(userId);
        log.info("ユーザーの勤怠記録をすべて削除しました: userId={}", userId);
    }


    /**
     * 規定の勤務終了時刻を超過して勤務した残業時間を計算します。
     *
     * @param attendanceDate 対象となる勤務日
     * @param schedule 対象ユーザーの勤務スケジュール
     * @param endInstant 実際の退勤日時
     * @return 計算された残業時間（時間単位の小数）
     */
    private Double calculateExcessOvertime(LocalDate attendanceDate, WorkScheduleDefinition schedule, Instant endInstant) {
        if (attendanceDate == null || schedule == null || schedule.standardEndTime() == null || endInstant == null) {
            return 0.0;
        }

        LocalDate standardEndDate = schedule.isOvernight() ? attendanceDate.plusDays(1) : attendanceDate;
        Instant standardEndInstant = DateTimeUtil.toInstant(standardEndDate, schedule.standardEndTime());
        if (!endInstant.isAfter(standardEndInstant)) {
            return 0.0;
        }

        long overtimeMinutes = java.time.Duration.between(standardEndInstant, endInstant).toMinutes();
        long breakOverlapMinutes = calculateBreakOverlapMinutes(standardEndInstant, endInstant, attendanceDate, schedule);
        long adjustedOvertimeMinutes = Math.max(0, overtimeMinutes - breakOverlapMinutes);
        return adjustedOvertimeMinutes / 60.0;
    }

    /**
     * 勤怠記録から残業時間を返します。
     * overtime_hours が未設定の場合、記録に紐づく勤務クラス（未設定時はデフォルトスケジュール）の
     * 所定終業時刻を基準に超過分をフォールバック算出します。
     *
     * @param record 対象の勤怠記録
     * @return 残業時間（時間単位の小数）
     */
    public double resolveOvertimeHours(AttendanceRecord record) {
        if (record == null) {
            return 0.0;
        }
        if (record.getOvertimeHours() != null) {
            return record.getOvertimeHours();
        }
        if (record.getAttendanceDate() == null || record.getEndTime() == null) {
            return 0.0;
        }

        LocalDate attendanceDate = DateTimeUtil.toLocalDate(record.getAttendanceDate());
        WorkScheduleDefinition schedule = resolveScheduleForRecord(record);
        Double overtime = calculateExcessOvertime(attendanceDate, schedule, record.getEndTime());
        return overtime != null ? overtime : 0.0;
    }

    private WorkScheduleDefinition resolveScheduleForRecord(AttendanceRecord record) {
        if (record.getClassId() != null) {
            return workScheduleClassMapper.selectById(record.getClassId())
                    .map(o -> this.toWorkScheduleDefinition(o))
                    .orElseGet(() -> WorkScheduleDefinition.defaultSchedule());
        }
        return WorkScheduleDefinition.defaultSchedule();
    }

    /**
     * デフォルトのスケジュールに基づく規定勤務時間を取得します。
     *
     * @return デフォルトスケジュールの規定勤務時間（時間単位）
     */
    public double getStandardWorkingHours() {
        return calculateStandardWorkingHours(WorkScheduleDefinition.defaultSchedule());
    }

    /**
     * 指定されたユーザーの勤務クラスに基づく規定勤務時間を取得します。
     *
     * @param userId 対象のユーザーID
     * @return ユーザーの規定勤務時間（時間単位）
     */
    public double getStandardWorkingHours(Long userId) {
        return calculateStandardWorkingHours(getScheduleForUserAndRecord(userId, Optional.empty()));
    }

    /**
     * 指定されたユーザーの、指定月における勤務クラスに基づく規定勤務時間を取得します。
     * 対象月の勤務実績から適用クラスを特定し、無ければ現在のクラスを使用します。
     *
     * @param userId 対象のユーザーID
     * @param yearMonth 対象の年月
     * @return ユーザーの規定勤務時間（時間単位）
     */
    public double getStandardWorkingHours(Long userId, YearMonth yearMonth) {
        List<AttendanceRecord> records = getRecordsByUserAndMonth(userId, yearMonth);
        Long mostFrequentClassId = records.stream()
                .map(r -> r.getClassId())
                .filter(obj -> obj != null)
                .collect(Collectors.groupingBy(id -> id, Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(entry -> entry.getKey())
                .orElse(null);

        if (mostFrequentClassId != null) {
            WorkScheduleDefinition schedule = workScheduleClassMapper.selectById(mostFrequentClassId)
                    .map(o -> this.toWorkScheduleDefinition(o))
                    .orElseGet(() -> WorkScheduleDefinition.defaultSchedule());
            return calculateStandardWorkingHours(schedule);
        }
        return calculateStandardWorkingHours(getScheduleForUserAndRecord(userId, Optional.empty()));
    }

    private double calculateStandardWorkingHours(WorkScheduleDefinition schedule) {
        if (schedule == null || schedule.standardStartTime() == null || schedule.standardEndTime() == null) {
            return 0.0;
        }
        LocalDate baseDate = LocalDate.of(2000, 1, 1);
        Instant baseStart = DateTimeUtil.toInstant(baseDate, schedule.standardStartTime());
        LocalDate endDate = schedule.isOvernight() ? baseDate.plusDays(1) : baseDate;
        Instant baseEnd = DateTimeUtil.toInstant(endDate, schedule.standardEndTime());
        return calculateWorkingHoursExcludingBreakOverlaps(baseStart, baseEnd, baseDate, schedule);
    }

    /**
     * 36協定チェック: 月次残業時間に対する警告レベルを返します。
     * システム設定（アラート閾値設定）に警告・上限値が設定されている場合はその値を、
     * 未設定の場合は既定値（警告30時間・上限45時間）を使用します。
     * NORMAL  : 警告閾値未満（問題なし）
     * WARNING : 警告閾値以上、上限閾値未満（注意）
     * ALERT   : 上限閾値以上（法定上限超過）
     */
    public String checkArticle36(double totalOvertimeHours) {
        double warningHours = batchSettingService.getAlertArticle36Limit1();
        double limitHours = batchSettingService.getAlertArticle36Limit2();
        return checkArticle36(totalOvertimeHours, warningHours, limitHours);
    }

    /**
     * 36協定チェック: 指定した警告・上限閾値を用いて月次残業時間の警告レベルを返します。
     *
     * @param totalOvertimeHours 月次残業時間
     * @param warningHours       警告閾値（時間）
     * @param limitHours         上限閾値（時間）
     */
    public String checkArticle36(double totalOvertimeHours, double warningHours, double limitHours) {
        if (totalOvertimeHours >= limitHours) {
            return "ALERT";
        } else if (totalOvertimeHours >= warningHours) {
            return "WARNING";
        }
        return "NORMAL";
    }

    /**
     * 指定年月の全ユーザー勤務時間合計をユーザーIDをキーとするMapで返します。
     * N+1対策として全ユーザー分を一括取得して集計します。
     */
    public Map<Long, Double> getWorkingSumByUserForMonth(YearMonth yearMonth) {
        LocalDate startDate = yearMonth.minusMonths(1).atDay(attendancePeriodSettingService.getStartDay());
        LocalDate endDate = yearMonth.atDay(attendancePeriodSettingService.getEndDay());
        Instant startInstant = DateTimeUtil.toInstant(startDate);
        Instant endInstant = DateTimeUtil.toInstant(endDate.plusDays(1));
        List<AttendanceRecord> records = attendanceRecordMapper.selectAllByDateRange(startInstant, endInstant);
        return records.stream()
                .filter(r -> r.getWorkingHours() != null && r.getWorkingHours() > 0)
                .collect(Collectors.groupingBy(
                        r -> r.getUserId(),
                        Collectors.summingDouble(r -> r.getWorkingHours())));
    }

    /**
     * 指定年月の全ユーザー残業時間合計をユーザーIDをキーとするMapで返します。
     * N+1対策として全ユーザー分を一括取得して集計します。
     * overtime_hours が未設定(null/0)の場合は基準終了時刻(18:00)からのフォールバック算出を行います。
     */
    public Map<Long, Double> getOvertimeSumByUserForMonth(YearMonth yearMonth) {
        LocalDate startDate = yearMonth.minusMonths(1).atDay(attendancePeriodSettingService.getStartDay());
        LocalDate endDate = yearMonth.atDay(attendancePeriodSettingService.getEndDay());
        Instant startInstant = DateTimeUtil.toInstant(startDate);
        Instant endInstant = DateTimeUtil.toInstant(endDate.plusDays(1));
        List<AttendanceRecord> records = attendanceRecordMapper.selectAllByDateRange(startInstant, endInstant);
        return records.stream()
                .collect(Collectors.groupingBy(
                        r -> r.getUserId(),
                        Collectors.summingDouble(r -> this.resolveOvertimeHoursForRecord(r))));
    }

    /**
     * 指定年月の全ユーザー深夜労働時間合計をユーザーIDをキーとするMapで返します。
     * N+1対策として全ユーザー分を一括取得して集計します。
     */
    public Map<Long, Double> getNightShiftSumByUserForMonth(YearMonth yearMonth) {
        LocalDate startDate = yearMonth.minusMonths(1).atDay(attendancePeriodSettingService.getStartDay());
        LocalDate endDate = yearMonth.atDay(attendancePeriodSettingService.getEndDay());
        Instant startInstant = DateTimeUtil.toInstant(startDate);
        Instant endInstant = DateTimeUtil.toInstant(endDate.plusDays(1));
        List<AttendanceRecord> records = attendanceRecordMapper.selectAllByDateRange(startInstant, endInstant);
        return records.stream()
                .filter(r -> r.getNightShiftHours() != null && r.getNightShiftHours() > 0)
                .collect(Collectors.groupingBy(
                        r -> r.getUserId(),
                        Collectors.summingDouble(r -> r.getNightShiftHours())));
    }

    /**
     * 勤怠記録から残業時間を返します。
     * overtime_hours が null（未設定）の場合のみ勤務クラスの所定終業時刻との差分でフォールバック算出します。
     * overtime_hours=0.0 は「残業なし確定」として扱い、フォールバック算出は行いません。
     */
    private double resolveOvertimeHoursForRecord(AttendanceRecord record) {
        return resolveOvertimeHours(record);
    }

    /**
     * 画面表示・集計で使う勤怠期間の開始日・終了日を返します。
     * 引数が null の場合は現在月を基準にします。
     */
    public MonthRange getMonthRange(YearMonth yearMonth) {
        YearMonth ym = (yearMonth == null) ? YearMonth.now() : yearMonth;
        return MonthRange.of(ym, attendancePeriodSettingService.getStartDay(), attendancePeriodSettingService.getEndDay());
    }

    /**
     * バッチで勤怠記録を保存します。
     * トランザクション内で全件検証し、問題があればロールバックします。
     * 開始・終了時刻がともに空の既存レコードを削除し、変更のあった後だけを保存件数に含めます。
     */
    @Transactional
    public int saveRecordsBatch(Long userId, List<LocalDate> dates, List<LocalTime> startTimes, List<LocalTime> endTimes, List<String> remarks, Set<LocalDate> holidayWorkDates, List<Integer> eventTypeIds) {
        if (dates == null || dates.isEmpty()) {
            throw new IllegalArgumentException("保存対象がありません");
        }

        List<String> errors = new java.util.ArrayList<>();

        for (int i = 0; i < dates.size(); i++) {
            LocalDate date = dates.get(i);
            if (date == null) {
                errors.add("日付が不正な行があります: 行=" + (i + 1));
                continue;
            }
            LocalTime s = (startTimes != null && startTimes.size() > i) ? startTimes.get(i) : null;
            LocalTime e = (endTimes != null && endTimes.size() > i) ? endTimes.get(i) : null;

            // 基本的な妥当性チェック（将来的にルールを追加）
            if (s != null && e != null) {
                // allow overnight (end before start) - handled by saveRecord
            }
        }

        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", errors));
        }

        int savedCount = 0;

        // 全件問題なければ実際の保存処理を行う
        for (int i = 0; i < dates.size(); i++) {
            LocalDate date = dates.get(i);
            LocalTime s = (startTimes != null && startTimes.size() > i) ? startTimes.get(i) : null;
            LocalTime e = (endTimes != null && endTimes.size() > i) ? endTimes.get(i) : null;
            String remark = normalizeText((remarks != null && remarks.size() > i) ? remarks.get(i) : null);
            Integer eventTypeId = (eventTypeIds != null && eventTypeIds.size() > i) ? eventTypeIds.get(i) : null;

            Optional<AttendanceRecord> existing = attendanceRecordMapper.selectByUserAndDateForUpdate(userId, date);
            WorkScheduleDefinition schedule = getScheduleForUserAndRecord(userId, existing);

            // 開始・終了の両方が空の場合は既存レコードを削除
            if (s == null && e == null) {
                if (existing.isPresent()) {
                    overtimeRecordService.syncFromAttendance(
                            userId,
                            date,
                            schedule.standardStartTime(),
                            schedule.standardEndTime(),
                            null);
                    attendanceRecordMapper.deleteById(existing.get().getRecordId());
                    savedCount++;
                }
                continue;
            }

            boolean newIsHolidayWork = holidayWorkDates != null && holidayWorkDates.contains(date);
            if (existing.isPresent()) {
                boolean existingIsHolidayWork = existing.get().getHolidayWorkHours() != null && existing.get().getHolidayWorkHours() > 0;
                if (!hasChanged(existing.get(), s, e, remark, eventTypeId) && newIsHolidayWork == existingIsHolidayWork) {
                    continue;
                }
            }

            // reuse existing saveRecord which contains business logic for overnight, working hours
            saveRecord(userId, date, s, e, remark, newIsHolidayWork, eventTypeId);
            savedCount++;
        }

        return savedCount;
    }

    private boolean hasChanged(AttendanceRecord existing, LocalTime newStart, LocalTime newEnd, String newRemark, Integer newEventTypeId) {
        LocalTime existingStart = existing.getStartTime() != null
                ? DateTimeUtil.toLocalTime(existing.getStartTime()).withSecond(0).withNano(0)
                : null;
        LocalTime existingEnd = existing.getEndTime() != null
                ? DateTimeUtil.toLocalTime(existing.getEndTime()).withSecond(0).withNano(0)
                : null;

        LocalTime normalizedStart = newStart != null ? newStart.withSecond(0).withNano(0) : null;
        LocalTime normalizedEnd = newEnd != null ? newEnd.withSecond(0).withNano(0) : null;
        String normalizedExistingRemark = normalizeText(existing.getRemarks());

        return !Objects.equals(existingStart, normalizedStart)
                || !Objects.equals(existingEnd, normalizedEnd)
                || !Objects.equals(normalizedExistingRemark, newRemark)
                || !Objects.equals(existing.getEventTypeId(), newEventTypeId);
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 実勤務時間から休憩時間帯との実重複分のみ控除して勤務時間を算出します。
     */
    private double calculateWorkingHoursExcludingBreakOverlaps(
            Instant workStart,
            Instant workEnd,
            LocalDate attendanceDate,
            WorkScheduleDefinition schedule) {
        if (workStart == null || workEnd == null) {
            return 0.0;
        }

        long workMinutes = java.time.Duration.between(workStart, workEnd).toMinutes();
        if (workMinutes <= 0) {
            return 0.0;
        }

        if (schedule == null || attendanceDate == null) {
            return workMinutes / 60.0;
        }

        long breakOverlapMinutes = calculateBreakOverlapMinutes(workStart, workEnd, attendanceDate, schedule);
        long adjustedMinutes = Math.max(0, workMinutes - breakOverlapMinutes);
        return adjustedMinutes / 60.0;
    }

    private List<TimeRange> getOverlappingBreakRanges(Instant workStart, Instant workEnd, LocalDate attendanceDate, WorkScheduleDefinition schedule) {
        List<TimeRange> overlapRanges = new ArrayList<>();
        if (schedule == null || schedule.breakWindows() == null) {
            return overlapRanges;
        }

        for (BreakWindow breakWindow : schedule.breakWindows()) {
            TimeRange breakRange = resolveBreakRange(attendanceDate, schedule, breakWindow);
            if (breakRange == null) {
                continue;
            }

            Instant overlapStart = workStart.isAfter(breakRange.start()) ? workStart : breakRange.start();
            Instant overlapEnd = workEnd.isBefore(breakRange.end()) ? workEnd : breakRange.end();
            if (overlapEnd.isAfter(overlapStart)) {
                overlapRanges.add(new TimeRange(overlapStart, overlapEnd));
            }
        }

        if (overlapRanges.isEmpty()) {
            return overlapRanges;
        }

        overlapRanges.sort((left, right) -> left.start().compareTo(right.start()));
        List<TimeRange> mergedRanges = new ArrayList<>();
        TimeRange current = overlapRanges.get(0);

        for (int i = 1; i < overlapRanges.size(); i++) {
            TimeRange next = overlapRanges.get(i);
            if (!next.start().isAfter(current.end())) {
                Instant mergedEnd = next.end().isAfter(current.end()) ? next.end() : current.end();
                current = new TimeRange(current.start(), mergedEnd);
            } else {
                mergedRanges.add(current);
                current = next;
            }
        }
        mergedRanges.add(current);
        return mergedRanges;
    }

    private long calculateBreakOverlapMinutes(
            Instant workStart,
            Instant workEnd,
            LocalDate attendanceDate,
            WorkScheduleDefinition schedule) {
        if (schedule.breakWindows() == null || schedule.breakWindows().isEmpty()) {
            return Math.max(0, schedule.breakMinutes());
        }

        List<TimeRange> mergedRanges = getOverlappingBreakRanges(workStart, workEnd, attendanceDate, schedule);
        long totalOverlap = 0;
        for (TimeRange mergedRange : mergedRanges) {
            totalOverlap += java.time.Duration.between(mergedRange.start(), mergedRange.end()).toMinutes();
        }
        return totalOverlap;
    }

    private long calculateOverlapWithNightWindows(Instant start, Instant end, LocalDate attendanceDate) {
        if (start == null || end == null || !end.isAfter(start)) return 0;

        List<TimeRange> nightWindows = new ArrayList<>();
        LocalDate[] dates = {attendanceDate.minusDays(1), attendanceDate, attendanceDate.plusDays(1)};
        for (LocalDate d : dates) {
            Instant nStart = DateTimeUtil.toInstant(d, LocalTime.of(22, 0));
            Instant nEnd = DateTimeUtil.toInstant(d.plusDays(1), LocalTime.of(5, 0));
            if (nStart != null && nEnd != null) {
                nightWindows.add(new TimeRange(nStart, nEnd));
            }
        }

        long overlapMinutes = 0;
        for (TimeRange night : nightWindows) {
            Instant overlapStart = start.isAfter(night.start()) ? start : night.start();
            Instant overlapEnd = end.isBefore(night.end()) ? end : night.end();
            if (overlapEnd.isAfter(overlapStart)) {
                overlapMinutes += java.time.Duration.between(overlapStart, overlapEnd).toMinutes();
            }
        }
        return overlapMinutes;
    }

    private double calculateNightShiftHours(Instant workStart, Instant workEnd, LocalDate attendanceDate, WorkScheduleDefinition schedule) {
        if (workStart == null || workEnd == null) return 0.0;

        long grossNightMinutes = calculateOverlapWithNightWindows(workStart, workEnd, attendanceDate);
        long breakNightMinutes = 0;

        List<TimeRange> breakOverlaps = getOverlappingBreakRanges(workStart, workEnd, attendanceDate, schedule);
        for (TimeRange overlap : breakOverlaps) {
            breakNightMinutes += calculateOverlapWithNightWindows(overlap.start(), overlap.end(), attendanceDate);
        }

        long netNightMinutes = Math.max(0, grossNightMinutes - breakNightMinutes);
        return netNightMinutes / 60.0;
    }

    private TimeRange resolveBreakRange(LocalDate attendanceDate, WorkScheduleDefinition schedule, BreakWindow breakWindow) {
        if (attendanceDate == null || schedule == null || breakWindow == null) {
            return null;
        }
        if (breakWindow.startTime() == null || breakWindow.endTime() == null) {
            return null;
        }

        LocalDate breakStartDate = attendanceDate;
        if (schedule.isOvernight()
                && schedule.standardStartTime() != null
                && breakWindow.startTime().isBefore(schedule.standardStartTime())) {
            breakStartDate = attendanceDate.plusDays(1);
        }

        LocalDate breakEndDate = breakStartDate;
        if (!breakWindow.endTime().isAfter(breakWindow.startTime())) {
            breakEndDate = breakStartDate.plusDays(1);
        }

        Instant breakStart = DateTimeUtil.toInstant(breakStartDate, breakWindow.startTime());
        Instant breakEnd = DateTimeUtil.toInstant(breakEndDate, breakWindow.endTime());
        if (breakStart == null || breakEnd == null || !breakEnd.isAfter(breakStart)) {
            return null;
        }
        return new TimeRange(breakStart, breakEnd);
    }

    private record WorkScheduleDefinition(
            Long classId,
            String className,
            LocalTime standardStartTime,
            LocalTime standardEndTime,
            int breakMinutes,
            List<BreakWindow> breakWindows) {

        private static WorkScheduleDefinition defaultSchedule() {
            return new WorkScheduleDefinition(
                    null,
                    null,
                    DEFAULT_STANDARD_START_TIME,
                    DEFAULT_STANDARD_END_TIME,
                    DEFAULT_BREAK_MINUTES,
                    List.of(new BreakWindow(DEFAULT_BREAK_START_TIME, DEFAULT_BREAK_END_TIME)));
        }

        private boolean isOvernight() {
            return standardStartTime != null && standardEndTime != null && !standardEndTime.isAfter(standardStartTime);
        }
    }

    private record BreakWindow(LocalTime startTime, LocalTime endTime) {
    }

    private record TimeRange(Instant start, Instant end) {
    }
    
    public static class MonthRange {
        private final YearMonth yearMonth;
        private final LocalDate startDate;
        private final LocalDate endDate;
        private final YearMonth previousMonth;
        private final YearMonth nextMonth;
        private final int daysInMonth;
        private final String displayMonth;

        private static final DateTimeFormatter DISPLAY_FORMATTER = DateTimeFormatter.ofPattern("yyyy年MM月");

        public MonthRange(YearMonth yearMonth, int startDay, int endDay) {
            this.yearMonth = yearMonth;
            // 対象月の表示範囲: 前月startDay日、当月endDay日
            this.startDate = yearMonth.minusMonths(1).atDay(startDay);
            this.endDate = yearMonth.atDay(endDay);
            this.previousMonth = yearMonth.minusMonths(1);
            this.nextMonth = yearMonth.plusMonths(1);
            // inclusive days between startDate and endDate
            this.daysInMonth = (int) ChronoUnit.DAYS.between(this.startDate, this.endDate) + 1;
            this.displayMonth = yearMonth.format(DISPLAY_FORMATTER);
        }

        public static MonthRange of(YearMonth yearMonth, int startDay, int endDay) {
            return new MonthRange(yearMonth, startDay, endDay);
        }

        /** 勤怠申請提出時のスナップショット日付など、既存の開始・終了日をそのまま用いて構築します。 */
        public MonthRange(YearMonth yearMonth, LocalDate startDate, LocalDate endDate) {
            this.yearMonth = yearMonth;
            this.startDate = startDate;
            this.endDate = endDate;
            this.previousMonth = yearMonth.minusMonths(1);
            this.nextMonth = yearMonth.plusMonths(1);
            this.daysInMonth = (int) ChronoUnit.DAYS.between(startDate, endDate) + 1;
            this.displayMonth = yearMonth.format(DISPLAY_FORMATTER);
        }

        public static MonthRange of(YearMonth yearMonth, LocalDate startDate, LocalDate endDate) {
            return new MonthRange(yearMonth, startDate, endDate);
        }

        public YearMonth getYearMonth() {
            return yearMonth;
        }

        public LocalDate getStartDate() {
            return startDate;
        }

        public LocalDate getEndDate() {
            return endDate;
        }

        public YearMonth getPreviousMonth() {
            return previousMonth;
        }

        public YearMonth getNextMonth() {
            return nextMonth;
        }

        public int getDaysInMonth() {
            return daysInMonth;
        }

        public String getDisplayMonth() {
            return displayMonth;
        }

        @Override
        public String toString() {
            return "MonthRange{" +
                    "yearMonth=" + yearMonth +
                    ", startDate=" + startDate +
                    ", endDate=" + endDate +
                    ", previousMonth=" + previousMonth +
                    ", nextMonth=" + nextMonth +
                    ", daysInMonth=" + daysInMonth +
                    ", displayMonth=" + displayMonth +
                    '}';
        }
    }   

    /**
     * 指定年月の全ユーザー月次集計を返します。
     * 勤務時間・残業時間・出勤日数をユーザーIDごとに集計します。
     * バッチ処理（前月集計ジョブ）での利用を想定しています。
     *
     * @param yearMonth 集計対象年月
     * @return ユーザーIDをキーとした月次集計リスト
     */
    public List<MonthlyUserSummary> getMonthlyAggregateForAllUsers(YearMonth yearMonth) {
        LocalDate startDate = yearMonth.minusMonths(1).atDay(attendancePeriodSettingService.getStartDay());
        LocalDate endDate = yearMonth.atDay(attendancePeriodSettingService.getEndDay());
        Instant startInstant = DateTimeUtil.toInstant(startDate);
        Instant endInstant = DateTimeUtil.toInstant(endDate.plusDays(1));
        List<AttendanceRecord> records = attendanceRecordMapper.selectAllByDateRange(startInstant, endInstant);

        Map<Long, List<AttendanceRecord>> byUser = records.stream()
                .collect(Collectors.groupingBy(r -> r.getUserId()));

        return byUser.entrySet().stream()
                .map(entry -> {
                    List<AttendanceRecord> userRecords = entry.getValue();
                    double working = userRecords.stream()
                            .filter(r -> r.getWorkingHours() != null)
                            .mapToDouble(r -> r.getWorkingHours())
                            .sum();
                    double overtime = userRecords.stream()
                            .mapToDouble(r -> this.resolveOvertimeHoursForRecord(r))
                            .sum();
                    return new MonthlyUserSummary(entry.getKey(), working, overtime, userRecords.size());
                })
                .toList();
    }

    /**
     * ユーザーごとの月次集計を保持するレコードです。
     *
     * @param userId      ユーザーID
     * @param workingHours  合計勤務時間
     * @param overtimeHours 合計残業時間
     * @param recordCount   出勤レコード数
     */
    public record MonthlyUserSummary(Long userId, double workingHours, double overtimeHours, int recordCount) {}

    private void checkIntervalAndNotify(Long userId, LocalDate attendanceDate, Instant startTime) {
        if (startTime == null) {
            return;
        }
        int minIntervalHours = batchSettingService.getAlertMinIntervalHours();
        if (minIntervalHours <= 0) {
            // 0時間の場合は警告機能を無効化
            return;
        }

        // 前日の退勤レコードを取得
        attendanceRecordMapper.selectByUserAndDate(userId, attendanceDate.minusDays(1))
                .filter(record -> record.getEndTime() != null)
                .ifPresent(record -> {
                    java.time.Duration interval = java.time.Duration.between(record.getEndTime(), startTime);
                    long intervalMinutes = interval.toMinutes();
                    double intervalHours = intervalMinutes / 60.0;

                    if (intervalHours < minIntervalHours) {
                        log.warn("【警告】勤務間インターバル不足: userId={}, intervalHours={}", userId, String.format("%.1f", intervalHours));
                        
                        String dateStr = attendanceDate.toString(); // "yyyy-MM-dd"
                        
                        // 1. 本人向け通知の送信と重複チェック
                        List<UserNotification> unreadNotifications = userNotificationService.getUnreadByUserId(userId);
                        boolean alreadyNotified = unreadNotifications.stream()
                                .anyMatch(note -> UserNotificationService.TYPE_INTERVAL_ALERT.equals(note.getNotificationType())
                                        && note.getMessage() != null && note.getMessage().contains(dateStr));
                        
                        if (!alreadyNotified) {
                            String message = String.format(
                                    "【警告】%s の勤務開始において、勤務間インターバルが%d時間に満たない状態で記録されました（前回の退勤から %.1f 時間）。十分な休息をとるようにしてください。",
                                    dateStr, minIntervalHours, intervalHours);
                            userNotificationService.notifyIntervalAlert(userId, message);
                        }

                        // 2. 管理者向け通知の送信（ユーザー名付き）と重複チェック
                        String userName = userMapper.selectById(userId)
                                .map(user -> user.getFullName())
                                .orElse("従業員");
                        String adminMessage = String.format(
                                "【警告】%s さんの %s の勤務開始において、勤務間インターバルが%d時間に満たない状態で記録されました（前回の退勤から %.1f 時間）。",
                                userName, dateStr, minIntervalHours, intervalHours);

                        List<com.attendance.app.entity.User> admins = userMapper.selectByRole("ADMIN");
                        for (com.attendance.app.entity.User admin : admins) {
                            List<UserNotification> adminUnread = userNotificationService.getUnreadByUserId(admin.getUserId());
                            boolean adminAlreadyNotified = adminUnread.stream()
                                    .anyMatch(note -> UserNotificationService.TYPE_INTERVAL_ALERT.equals(note.getNotificationType())
                                            && note.getMessage() != null
                                            && note.getMessage().contains(dateStr)
                                            && note.getMessage().contains(userName));
                            if (!adminAlreadyNotified) {
                                userNotificationService.notifyIntervalAlert(admin.getUserId(), adminMessage);
                            }
                        }
                    }
                });
    }
}