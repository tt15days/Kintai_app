package com.attendance.app.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.attendance.app.entity.OvertimeRecord;
import com.attendance.app.mapper.OvertimeRecordMapper;
import com.attendance.app.util.DateTimeUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 残業記録（OvertimeRecord）に関する業務ロジックを提供するサービスです。
 * 残業の記録の取得、作成、更新、削除および勤怠データからの同期処理を行います。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class OvertimeRecordService {

    private static final String OVERTIME_RECORD_NOT_FOUND_MESSAGE = "残業記録が見つかりません";
    private static final String ATTENDANCE_SYNC_REASON_PREFIX = "勤怠連携(基準 ";

    private final OvertimeRecordMapper overtimeRecordMapper;

    /**
     * 指定されたIDに該当する残業記録を取得します。
     *
     * @param overtimeId 残業記録ID
     * @return 残業記録のOptional
     */
    public Optional<OvertimeRecord> getRecordById(Long overtimeId) {
        return overtimeRecordMapper.selectById(overtimeId);
    }

    /**
     * 指定されたユーザーの残業記録一覧を取得します。
     *
     * @param userId ユーザーID
     * @return 残業記録のリスト
     */
    public List<OvertimeRecord> getRecordsByUserId(Long userId) {
        return overtimeRecordMapper.selectByUserId(userId);
    }

    /**
     * 指定されたユーザーおよび年月に該当する残業記録一覧を取得します。
     *
     * @param userId ユーザーID
     * @param year 対象年
     * @param month 対象月
     * @return 残業記録のリスト
     */
    public List<OvertimeRecord> getRecordsByUserAndMonth(Long userId, int year, int month) {
        return overtimeRecordMapper.selectByUserAndMonth(userId, year, month);
    }

    /**
     * 指定されたユーザーおよび年月の残業合計時間を取得します。
     *
     * @param userId ユーザーID
     * @param year 対象年
     * @param month 対象月
     * @return 月別残業合計時間
     */
    public Double getOvertimeHoursSumByUserAndMonth(Long userId, int year, int month) {
        Double sum = overtimeRecordMapper.selectOvertimeHoursSumByUserAndMonth(userId, year, month);
        return sum != null ? sum : 0.0;
    }

    /**
     * 新しい残業記録を作成します。
     *
     * @param userId ユーザーID
     * @param overtimeDate 残業日
     * @param startTime 残業開始時刻
     * @param endTime 残業終了時刻
     * @param reason 残業理由
     * @param remarks 備考
     * @return 作成された残業記録
     */
    public OvertimeRecord createRecord(Long userId, LocalDate overtimeDate, Instant startTime, Instant endTime, String reason, String remarks) {
        double overtimeHours = calculateOvertimeHours(startTime, endTime);

        OvertimeRecord record = OvertimeRecord.builder()
                .userId(userId)
                .overtimeDate(overtimeDate)
                .overtimeStart(startTime)
                .overtimeEnd(endTime)
                .overtimeHours(overtimeHours)
                .reason(reason)
                .remarks(remarks)
                .createdAt(DateTimeUtil.now())
                .updatedAt(DateTimeUtil.now())
                .build();

        overtimeRecordMapper.insert(record);
        log.info("残業記録を作成しました: userId={}, date={}, hours={}", userId, overtimeDate, overtimeHours);
        return record;
    }

    /**
     * 勤怠データから残業記録を同期します。
     *
     * 残業は「退勤時刻が基準終了時刻(例: 18:00)を超えた時間」として算出します。
     *
     * @param userId ユーザーID
     * @param attendanceDate 勤怠日
     * @param standardStartTime 基準開始時刻
     * @param standardEndTime 基準終了時刻
     * @param actualEndTime 実際の退勤時刻
     */
    public void syncFromAttendance(
            Long userId,
            LocalDate attendanceDate,
            LocalTime standardStartTime,
            LocalTime standardEndTime,
            Instant actualEndTime) {
        syncFromAttendance(userId, attendanceDate, standardStartTime, standardEndTime, actualEndTime, null);
    }

    /**
     * 勤怠データから残業記録を同期します。
     *
     * 残業の正本は attendance_records.overtime_hours です。呼び出し元が算出済みの
     * overtimeHours（所定終業後の休憩控除込み）を渡した場合はそれを採用し、
     * null の場合のみ所定終業〜退勤の単純差分でフォールバック算出します。
     *
     * @param userId ユーザーID
     * @param attendanceDate 勤怠日
     * @param standardStartTime 基準開始時刻
     * @param standardEndTime 基準終了時刻
     * @param actualEndTime 実際の退勤時刻
     * @param overtimeHours 勤怠側で算出済みの残業時間（正本。null時はフォールバック算出）
     */
    public void syncFromAttendance(
            Long userId,
            LocalDate attendanceDate,
            LocalTime standardStartTime,
            LocalTime standardEndTime,
            Instant actualEndTime,
            Double overtimeHours) {
        if (actualEndTime == null || standardEndTime == null) {
            overtimeRecordMapper.deleteByUserAndDate(userId, attendanceDate);
            return;
        }

        LocalDate standardEndDate = standardStartTime != null && !standardEndTime.isAfter(standardStartTime)
                ? attendanceDate.plusDays(1)
                : attendanceDate;
        Instant standardEndInstant = DateTimeUtil.toInstant(standardEndDate, standardEndTime);
        if (standardEndInstant == null || !actualEndTime.isAfter(standardEndInstant)) {
            overtimeRecordMapper.deleteByUserAndDate(userId, attendanceDate);
            return;
        }

        double resolvedOvertimeHours = overtimeHours != null
                ? overtimeHours
                : calculateOvertimeHours(standardEndInstant, actualEndTime);
        if (resolvedOvertimeHours <= 0) {
            // 残業窓が丸ごと休憩などで残業が0になる場合は履歴を残さない
            overtimeRecordMapper.deleteByUserAndDate(userId, attendanceDate);
            return;
        }
        String reason = ATTENDANCE_SYNC_REASON_PREFIX + formatTime(standardStartTime) + "-" + formatTime(standardEndTime) + ")";

        Optional<OvertimeRecord> existing = overtimeRecordMapper.selectByUserAndDateForUpdate(userId, attendanceDate);
        if (existing.isPresent()) {
            OvertimeRecord record = existing.get();
            record.setOvertimeStart(standardEndInstant);
            record.setOvertimeEnd(actualEndTime);
            record.setOvertimeHours(resolvedOvertimeHours);
            record.setReason(reason);
            record.setRemarks("勤怠管理画面から自動計算");
            record.setUpdatedAt(DateTimeUtil.now());
            overtimeRecordMapper.update(record);
        } else {
            OvertimeRecord record = OvertimeRecord.builder()
                    .userId(userId)
                    .overtimeDate(attendanceDate)
                    .overtimeStart(standardEndInstant)
                    .overtimeEnd(actualEndTime)
                    .overtimeHours(resolvedOvertimeHours)
                    .reason(reason)
                    .remarks("勤怠管理画面から自動計算")
                    .createdAt(DateTimeUtil.now())
                    .updatedAt(DateTimeUtil.now())
                    .build();
            try {
                overtimeRecordMapper.insert(record);
            } catch (DuplicateKeyException e) {
                // uq_overtime_user_date_active に対する同時実行競合。
                // 他トランザクションが先にINSERTしたとみなし、既存行を取り直してUPDATEにフォールバックする。
                OvertimeRecord concurrent = overtimeRecordMapper.selectByUserAndDateForUpdate(userId, attendanceDate)
                        .orElseThrow(() -> e);
                concurrent.setOvertimeStart(standardEndInstant);
                concurrent.setOvertimeEnd(actualEndTime);
                concurrent.setOvertimeHours(resolvedOvertimeHours);
                concurrent.setReason(reason);
                concurrent.setRemarks("勤怠管理画面から自動計算");
                concurrent.setUpdatedAt(DateTimeUtil.now());
                overtimeRecordMapper.update(concurrent);
            }
        }
    }

    /**
     * 既存の残業記録を更新します。
     *
     * @param overtimeId 残業記録ID
     * @param overtimeDate 残業日
     * @param startTime 残業開始時刻
     * @param endTime 残業終了時刻
     * @param reason 残業理由
     * @param remarks 備考
     * @return 更新された残業記録
     */
    public OvertimeRecord updateRecord(Long overtimeId, LocalDate overtimeDate, Instant startTime, Instant endTime, String reason, String remarks) {
        OvertimeRecord record = findRecordForUpdateOrThrow(overtimeId);

        double overtimeHours = calculateOvertimeHours(startTime, endTime);

        record.setOvertimeDate(overtimeDate);
        record.setOvertimeStart(startTime);
        record.setOvertimeEnd(endTime);
        record.setOvertimeHours(overtimeHours);
        record.setReason(reason);
        record.setRemarks(remarks);
        record.setUpdatedAt(DateTimeUtil.now());

        overtimeRecordMapper.update(record);
        log.info("残業記録を更新しました: overtimeId={}, hours={}", overtimeId, overtimeHours);
        return record;
    }

    /**
     * 残業時間を計算します（時間単位）。
     *
     * @param startTime 残業開始時刻
     * @param endTime 残業終了時刻
     * @return 計算された残業時間
     */
    private double calculateOvertimeHours(Instant startTime, Instant endTime) {
        long minutes = java.time.Duration.between(startTime, endTime).toMinutes();
        if (minutes <= 0) {
            return 0.0;
        }
        return minutes / 60.0;
    }

    /**
     * 残業記録を削除します。
     *
     * @param recordId 削除対象の残業記録ID
     */
	public void deleteRecord(Long recordId) {
		overtimeRecordMapper.deleteById(recordId);
	}

    /**
     * LocalTimeを "HH:mm" 形式の文字列にフォーマットします。
     *
     * @param time フォーマット対象の時刻
     * @return フォーマットされた時刻文字列
     */
    private String formatTime(LocalTime time) {
        if (time == null) {
            return "--:--";
        }
        return time.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
    }

    /**
     * 残業記録IDから残業記録を取得し（排他ロックを獲得）、存在しない場合は例外を送出します。
     *
     * @param overtimeId 残業記録ID
     * @return 残業記録
     * @throws IllegalArgumentException 残業記録が存在しない場合
     */
    private OvertimeRecord findRecordForUpdateOrThrow(Long overtimeId) {
        return overtimeRecordMapper.selectByIdForUpdate(overtimeId)
                .orElseThrow(() -> new IllegalArgumentException(OVERTIME_RECORD_NOT_FOUND_MESSAGE));
    }
}