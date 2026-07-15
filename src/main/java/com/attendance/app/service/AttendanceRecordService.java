package com.attendance.app.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.dao.DuplicateKeyException;
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
    /** 労働基準法上の深夜労働時間帯の開始時刻（22:00） */
    private static final LocalTime NIGHT_WORK_START = LocalTime.of(22, 0);
    /** 労働基準法上の深夜労働時間帯の終了時刻（翌5:00） */
    private static final LocalTime NIGHT_WORK_END = LocalTime.of(5, 0);

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

    /**
     * 勤務クラスIDからスケジュール定義を解決します（現場移動時のクラス指定用）。
     *
     * @param classId 勤務クラスID
     * @return 勤務スケジュール定義
     * @throws IllegalArgumentException クラスが存在しない場合
     */
    private WorkScheduleDefinition resolveScheduleByClassId(Long classId) {
        return workScheduleClassMapper.selectById(classId)
                .map(this::toWorkScheduleDefinition)
                .orElseThrow(() -> new IllegalArgumentException("指定された勤務クラスが存在しません: classId=" + classId));
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
            AttendancePeriodSettingService.AttendancePeriod period =
                    attendancePeriodSettingService.resolvePeriod(yearMonth);
            startDate = period.startDate();
            endDate = period.endDate();
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
        return saveRecord(userId, attendanceDate, startTime, endTime, remarks, isHolidayWork, eventTypeId, null);
    }

    /**
     * 勤怠記録を保存します。classId 指定時はそのクラス（現場）の所定時間で計算し、
     * レコードに紐付けます（現場移動対応）。未指定時は既存レコードまたは所属クラスに従います。
     *
     * @param classId 勤務クラスID（任意。指定時はレコードのクラスを上書き）
     */
    public AttendanceRecord saveRecord(Long userId, LocalDate attendanceDate, LocalTime startTime, LocalTime endTime, String remarks, boolean isHolidayWork, Integer eventTypeId, Long classId) {
        Optional<AttendanceRecord> existing = attendanceRecordMapper.selectByUserAndDateForUpdate(userId, attendanceDate);
        WorkScheduleDefinition schedule = classId != null
                ? resolveScheduleByClassId(classId)
                : getScheduleForUserAndRecord(userId, existing);

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
            if (classId != null || record.getClassId() == null) {
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
            try {
                attendanceRecordMapper.insert(record);
            } catch (DuplicateKeyException e) {
                // uq_attendance_user_date_active に対する同時実行競合（多重送信等）。
                // FOR UPDATEはロック対象行が無い初回INSERTでは効かないため、一意制約違反を業務例外に変換する。
                throw new IllegalArgumentException("この日の勤怠記録は既に登録されています", e);
            }
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
        return quickStartAttendance(userId, null);
    }

    /**
     * ワンクリック勤怠入力として、現在時刻で勤務開始（出勤）を記録します。
     * classId 指定時はそのクラス（現場）で打刻します（現場移動対応）。
     *
     * @param userId 対象のユーザーID
     * @param classId 勤務クラスID（任意）
     * @return 記録された勤怠情報
     */
    public AttendanceRecord quickStartAttendance(Long userId, Long classId) {
        LocalDate today = DateTimeUtil.todayJapan();
        LocalTime currentTime = DateTimeUtil.currentTimeJapan();
        Optional<AttendanceRecord> existing = attendanceRecordMapper.selectByUserAndDateForUpdate(userId, today);
        WorkScheduleDefinition schedule = classId != null
                ? resolveScheduleByClassId(classId)
                : getScheduleForUserAndRecord(userId, existing);

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
            if (classId != null || record.getClassId() == null) {
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
            try {
                attendanceRecordMapper.insert(record);
            } catch (DuplicateKeyException e) {
                // uq_attendance_user_date_active に対する同時実行競合（出勤ボタンの二重クリック等）。
                // FOR UPDATEはロック対象行が無い初回INSERTでは効かないため、一意制約違反を業務例外に変換する。
                throw new IllegalArgumentException("本日は既に勤務開始時刻が記録されています", e);
            }
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
     * 指定した複数年月の全ユーザー残業時間合計を、年月ごとのユーザーIDをキーとするMapで返します。
     * 36協定ダッシュボードの複数月表示など、月ごとの個別フェッチをまとめて1回で取得するために使用します。
     * 対象月群の最小〜最大範囲を1回取得し、各レコードの勤怠期間に対応する年月へ振り分けて集計します。
     *
     * @param months 集計対象の年月リスト
     * @return 年月をキー、ユーザーIDをキーとする残業時間合計Mapを値とするMap
     */
    public Map<YearMonth, Map<Long, Double>> getOvertimeSumByUserForMonthRange(List<YearMonth> months) {
        return getOvertimeSumByUserForMonthRange(months, null);
    }

    public Map<YearMonth, Map<Long, Double>> getOvertimeSumByUserForMonthRange(
            List<YearMonth> months, java.util.Collection<Long> userIds) {
        if (months == null || months.isEmpty()) {
            return Map.of();
        }
        if (userIds != null && userIds.isEmpty()) {
            return Map.of();
        }
        List<YearMonth> sortedMonths = months.stream().sorted().toList();
        YearMonth rangeStart = sortedMonths.get(0);
        YearMonth rangeEnd = sortedMonths.get(sortedMonths.size() - 1);
        int closingDay = attendancePeriodSettingService.getEndDay();

        LocalDate rangeStartDate = AttendancePeriodSettingService.calculatePeriod(rangeStart, closingDay).startDate();
        LocalDate rangeEndDate = AttendancePeriodSettingService.calculatePeriod(rangeEnd, closingDay).endDate();
        Instant rangeStartInstant = DateTimeUtil.toInstant(rangeStartDate);
        Instant rangeEndInstant = DateTimeUtil.toInstant(rangeEndDate.plusDays(1));
        List<AttendanceRecord> records = userIds == null
                ? attendanceRecordMapper.selectAllByDateRange(rangeStartInstant, rangeEndInstant)
                : attendanceRecordMapper.selectByDateRangeAndUserIds(rangeStartInstant, rangeEndInstant, userIds);
        Map<Long, WorkScheduleDefinition> scheduleByClassId = bulkResolveScheduleDefinitions(records);

        // 各対象月の勤怠期間（前月締め日の翌日〜当月締め日）をあらかじめ算出しておく
        Map<YearMonth, LocalDate[]> periodByMonth = new java.util.LinkedHashMap<>();
        Map<YearMonth, Map<Long, Double>> result = new java.util.LinkedHashMap<>();
        for (YearMonth ym : sortedMonths) {
            AttendancePeriodSettingService.AttendancePeriod period =
                    AttendancePeriodSettingService.calculatePeriod(ym, closingDay);
            periodByMonth.put(ym, new LocalDate[]{period.startDate(), period.endDate()});
            result.put(ym, new java.util.HashMap<>());
        }

        for (AttendanceRecord r : records) {
            LocalDate attendanceDate = DateTimeUtil.toLocalDate(r.getAttendanceDate());
            for (YearMonth ym : sortedMonths) {
                LocalDate[] period = periodByMonth.get(ym);
                if (!attendanceDate.isBefore(period[0]) && !attendanceDate.isAfter(period[1])) {
                    double overtime = resolveOvertimeHoursForRecord(r, scheduleByClassId);
                    result.get(ym).merge(r.getUserId(), overtime, Double::sum);
                    break;
                }
            }
        }
        return result;
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
     * 指定日の全ユーザーの勤怠記録を取得します（当日状況一覧用）。
     *
     * @param date 対象日（JST）
     * @return 勤怠記録のリスト
     */
    public List<AttendanceRecord> getRecordsForAllUsersByDate(LocalDate date) {
        Instant startInstant = DateTimeUtil.toInstant(date);
        Instant endInstant = DateTimeUtil.toInstant(date.plusDays(1));
        return attendanceRecordMapper.selectAllByDateRange(startInstant, endInstant);
    }

    /**
     * 指定年月の全ユーザー勤務時間合計をユーザーIDをキーとするMapで返します。
     * N+1対策として全ユーザー分を一括取得して集計します。
     */
    public Map<Long, Double> getWorkingSumByUserForMonth(YearMonth yearMonth) {
        AttendancePeriodSettingService.AttendancePeriod period =
                attendancePeriodSettingService.resolvePeriod(yearMonth);
        LocalDate startDate = period.startDate();
        LocalDate endDate = period.endDate();
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
        AttendancePeriodSettingService.AttendancePeriod period =
                attendancePeriodSettingService.resolvePeriod(yearMonth);
        LocalDate startDate = period.startDate();
        LocalDate endDate = period.endDate();
        Instant startInstant = DateTimeUtil.toInstant(startDate);
        Instant endInstant = DateTimeUtil.toInstant(endDate.plusDays(1));
        List<AttendanceRecord> records = attendanceRecordMapper.selectAllByDateRange(startInstant, endInstant);
        Map<Long, WorkScheduleDefinition> scheduleByClassId = bulkResolveScheduleDefinitions(records);
        return records.stream()
                .collect(Collectors.groupingBy(
                        r -> r.getUserId(),
                        Collectors.summingDouble(r -> this.resolveOvertimeHoursForRecord(r, scheduleByClassId))));
    }

    /**
     * 指定年月の全ユーザー深夜労働時間合計をユーザーIDをキーとするMapで返します。
     * N+1対策として全ユーザー分を一括取得して集計します。
     */
    public Map<Long, Double> getNightShiftSumByUserForMonth(YearMonth yearMonth) {
        AttendancePeriodSettingService.AttendancePeriod period =
                attendancePeriodSettingService.resolvePeriod(yearMonth);
        LocalDate startDate = period.startDate();
        LocalDate endDate = period.endDate();
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
     * 対象レコード群が参照する勤務クラスIDを事前に一括取得し、classId をキーとする
     * スケジュール定義のMapを構築します。集計処理でのN+1対策として、
     * overtime_hours が null（フォールバック算出が必要）なレコードのclassIdのみを対象とします。
     */
    private Map<Long, WorkScheduleDefinition> bulkResolveScheduleDefinitions(List<AttendanceRecord> records) {
        Set<Long> classIds = records.stream()
                .filter(r -> r.getOvertimeHours() == null && r.getClassId() != null)
                .map(AttendanceRecord::getClassId)
                .collect(Collectors.toSet());
        if (classIds.isEmpty()) {
            return Map.of();
        }
        return workScheduleClassMapper.selectByIds(classIds).stream()
                .collect(Collectors.toMap(WorkScheduleClass::getClassId, this::toWorkScheduleDefinition));
    }

    /**
     * 勤怠記録から残業時間を返します。
     * overtime_hours が null（未設定）の場合のみ勤務クラスの所定終業時刻との差分でフォールバック算出します。
     * overtime_hours=0.0 は「残業なし確定」として扱い、フォールバック算出は行いません。
     * スケジュール定義は事前に一括取得したMap（{@link #bulkResolveScheduleDefinitions}）から参照し、
     * レコードごとのMapper個別呼び出し（N+1）を避けます。
     */
    private double resolveOvertimeHoursForRecord(AttendanceRecord record, Map<Long, WorkScheduleDefinition> scheduleByClassId) {
        if (record.getOvertimeHours() != null) {
            return record.getOvertimeHours();
        }
        if (record.getAttendanceDate() == null || record.getEndTime() == null) {
            return 0.0;
        }

        LocalDate attendanceDate = DateTimeUtil.toLocalDate(record.getAttendanceDate());
        WorkScheduleDefinition schedule = record.getClassId() != null
                ? scheduleByClassId.getOrDefault(record.getClassId(), WorkScheduleDefinition.defaultSchedule())
                : WorkScheduleDefinition.defaultSchedule();
        Double overtime = calculateExcessOvertime(attendanceDate, schedule, record.getEndTime());
        return overtime != null ? overtime : 0.0;
    }

    /**
     * 画面表示・集計で使う勤怠期間の開始日・終了日を返します。
     * 引数が null の場合は現在月を基準にします。
     */
    public MonthRange getMonthRange(YearMonth yearMonth) {
        YearMonth ym = (yearMonth == null) ? YearMonth.now() : yearMonth;
        AttendancePeriodSettingService.AttendancePeriod period =
                attendancePeriodSettingService.resolvePeriod(ym);
        return MonthRange.of(ym, period.startDate(), period.endDate());
    }

    /**
     * バッチで勤怠記録を保存します。
     * トランザクション内で全件検証し、問題があればロールバックします。
     * 開始・終了時刻がともに空の既存レコードを削除し、変更のあった後だけを保存件数に含めます。
     */
    @Transactional
    public int saveRecordsBatch(Long userId, List<LocalDate> dates, List<LocalTime> startTimes, List<LocalTime> endTimes, List<String> remarks, Set<LocalDate> holidayWorkDates, List<Integer> eventTypeIds) {
        return saveRecordsBatch(userId, dates, startTimes, endTimes, remarks, holidayWorkDates, eventTypeIds, null);
    }

    /**
     * 勤怠記録を一括保存します。classIds 指定時は行ごとの勤務クラス（現場）で計算・紐付けします。
     *
     * @param classIds 行ごとの勤務クラスID（任意。null要素は既存/所属クラスを維持）
     */
    public int saveRecordsBatch(Long userId, List<LocalDate> dates, List<LocalTime> startTimes, List<LocalTime> endTimes, List<String> remarks, Set<LocalDate> holidayWorkDates, List<Integer> eventTypeIds, List<Long> classIds) {
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

        // 同一ユーザーの並行な一括保存リクエスト間でロック取得順序が入れ替わりデッドロックするのを防ぐため、日付昇順で処理する
        List<Integer> processingOrder = java.util.stream.IntStream.range(0, dates.size())
                .boxed()
                .sorted(Comparator.comparing(dates::get, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());

        // 全件問題なければ実際の保存処理を行う
        for (int i : processingOrder) {
            LocalDate date = dates.get(i);
            LocalTime s = (startTimes != null && startTimes.size() > i) ? startTimes.get(i) : null;
            LocalTime e = (endTimes != null && endTimes.size() > i) ? endTimes.get(i) : null;
            String remark = normalizeText((remarks != null && remarks.size() > i) ? remarks.get(i) : null);
            Integer eventTypeId = (eventTypeIds != null && eventTypeIds.size() > i) ? eventTypeIds.get(i) : null;
            Long classId = (classIds != null && classIds.size() > i) ? classIds.get(i) : null;

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
                boolean classChanged = classId != null && !classId.equals(existing.get().getClassId());
                if (!hasChanged(existing.get(), s, e, remark, eventTypeId) && newIsHolidayWork == existingIsHolidayWork
                        && !classChanged) {
                    continue;
                }
            }

            // reuse existing saveRecord which contains business logic for overnight, working hours
            saveRecord(userId, date, s, e, remark, newIsHolidayWork, eventTypeId, classId);
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
            Instant nStart = DateTimeUtil.toInstant(d, NIGHT_WORK_START);
            Instant nEnd = DateTimeUtil.toInstant(d.plusDays(1), NIGHT_WORK_END);
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
            return DateTimeUtil.isOvernight(standardStartTime, standardEndTime);
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
            // 互換シグネチャ。開始日は締め日から導出し、引数startDayには依存しない。
            this.endDate = yearMonth.atDay(Math.min(endDay, yearMonth.lengthOfMonth()));
            YearMonth previous = yearMonth.minusMonths(1);
            LocalDate previousEnd = previous.atDay(Math.min(endDay, previous.lengthOfMonth()));
            this.startDate = previousEnd.plusDays(1);
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
        return getMonthlyAggregate(yearMonth, null);
    }

    public List<MonthlyUserSummary> getMonthlyAggregateForUsers(YearMonth yearMonth, java.util.Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        return getMonthlyAggregate(yearMonth, userIds);
    }

    private List<MonthlyUserSummary> getMonthlyAggregate(YearMonth yearMonth, java.util.Collection<Long> userIds) {
        AttendancePeriodSettingService.AttendancePeriod period =
                attendancePeriodSettingService.resolvePeriod(yearMonth);
        LocalDate startDate = period.startDate();
        LocalDate endDate = period.endDate();
        Instant startInstant = DateTimeUtil.toInstant(startDate);
        Instant endInstant = DateTimeUtil.toInstant(endDate.plusDays(1));
        List<AttendanceRecord> records = userIds == null
                ? attendanceRecordMapper.selectAllByDateRange(startInstant, endInstant)
                : attendanceRecordMapper.selectByDateRangeAndUserIds(startInstant, endInstant, userIds);
        Map<Long, WorkScheduleDefinition> scheduleByClassId = bulkResolveScheduleDefinitions(records);

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
                            .mapToDouble(r -> this.resolveOvertimeHoursForRecord(r, scheduleByClassId))
                            .sum();
                    double nightShift = userRecords.stream()
                            .filter(r -> r.getNightShiftHours() != null)
                            .mapToDouble(r -> r.getNightShiftHours())
                            .sum();
                    return new MonthlyUserSummary(entry.getKey(), working, overtime, nightShift, userRecords.size());
                })
                .toList();
    }

    /**
     * ユーザーごとの月次集計を保持するレコードです。
     *
     * @param userId      ユーザーID
     * @param workingHours  合計勤務時間
     * @param overtimeHours 合計残業時間
     * @param nightShiftHours 合計深夜労働時間
     * @param recordCount   出勤レコード数
     */
    public record MonthlyUserSummary(Long userId, double workingHours, double overtimeHours, double nightShiftHours, int recordCount) {}

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
