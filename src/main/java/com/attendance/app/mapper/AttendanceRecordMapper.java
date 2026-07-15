package com.attendance.app.mapper;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.attendance.app.entity.AttendanceRecord;

/**
 * AttendanceRecord Mapper - 勤怠記録に関するDB操作
 */
@Mapper
public interface AttendanceRecordMapper {
    
    /**
     * 記録IDで勤怠記録を取得
     *
     * @param recordId 勤怠記録ID
     * @return 勤怠記録。存在しない場合は Optional.empty()
     */
    Optional<AttendanceRecord> selectById(@Param("recordId") Long recordId);

    /**
     * 記録IDで勤怠記録を取得し、排他ロックを獲得
     *
     * @param recordId 勤怠記録ID
     * @return 勤怠記録。存在しない場合は Optional.empty()
     */
    Optional<AttendanceRecord> selectByIdForUpdate(@Param("recordId") Long recordId);
    
    /**
     * ユーザーIDと日付で勤怠記録を取得
     *
     * @param userId ユーザーID
     * @param attendanceDate 勤怠日付
     * @return 勤怠記録。存在しない場合は Optional.empty()
     */
    Optional<AttendanceRecord> selectByUserAndDate(@Param("userId") Long userId, @Param("attendanceDate") LocalDate attendanceDate);

    /**
     * ユーザーIDと日付で勤怠記録を取得し、排他ロックを獲得
     *
     * @param userId ユーザーID
     * @param attendanceDate 勤怠日付
     * @return 勤怠記録。存在しない場合は Optional.empty()
     */
    Optional<AttendanceRecord> selectByUserAndDateForUpdate(@Param("userId") Long userId, @Param("attendanceDate") LocalDate attendanceDate);
    
    /**
     * ユーザーIDと期間で勤怠記録を取得
     *
     * @param userId ユーザーID
     * @param startDate 開始日時
     * @param endDate 終了日時
     * @return 勤怠記録のリスト
     */
    List<AttendanceRecord> selectByUserAndDateRange(
            @Param("userId") Long userId,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate
    );
    
    /**
     * ユーザーの全勤怠記録を取得
     *
     * @param userId ユーザーID
     * @return 勤怠記録のリスト
     */
    List<AttendanceRecord> selectByUserId(@Param("userId") Long userId);
    
    /**
     * 勤怠記録を新規作成
     *
     * @param record 新規作成する勤怠記録
     * @return 作成された件数
     */
    int insert(AttendanceRecord record);
    
    /**
     * 勤怠記録を更新
     *
     * @param record 更新する勤怠記録
     * @return 更新された件数
     */
    int update(AttendanceRecord record);
    
    /**
     * 勤怠記録を削除
     *
     * @param recordId 勤怠記録ID
     * @return 削除された件数
     */
    int deleteById(@Param("recordId") Long recordId);
    

    /**
     * ユーザーIDで勤怠記録をすべて削除
     *
     * @param userId ユーザーID
     * @return 削除された件数
     */
    int deleteByUserId(@Param("userId") Long userId);

    /**
     * 全ユーザーの指定期間内の勤怠記録を取得（管理者用・一括集計向け）
     *
     * @param startDate 開始日時
     * @param endDate 終了日時
     * @return 勤怠記録のリスト
     */
    List<AttendanceRecord> selectAllByDateRange(
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate
    );

    List<AttendanceRecord> selectByDateRangeAndUserIds(
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate,
            @Param("userIds") java.util.Collection<Long> userIds
    );


}
