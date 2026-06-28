package com.attendance.app.mapper;

import com.attendance.app.entity.OvertimeRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * OvertimeRecord Mapper - 残業管理に関するDB操作
 */
@Mapper
public interface OvertimeRecordMapper {
    
    /**
     * IDで残業記録を取得
     *
     * @param overtimeId 残業記録ID
     * @return 残業記録。存在しない場合は Optional.empty()
     */
    Optional<OvertimeRecord> selectById(@Param("overtimeId") Long overtimeId);

    /**
     * IDで残業記録を取得し、排他ロックを獲得
     *
     * @param overtimeId 残業記録ID
     * @return 残業記録。存在しない場合は Optional.empty()
     */
    Optional<OvertimeRecord> selectByIdForUpdate(@Param("overtimeId") Long overtimeId);
    
    /**
     * ユーザーIDで残業記録を取得
     *
     * @param userId ユーザーID
     * @return 残業記録のリスト
     */
    List<OvertimeRecord> selectByUserId(@Param("userId") Long userId);
    
    /**
     * ユーザーIDと期間で残業記録を取得
     *
     * @param userId ユーザーID
     * @param startDate 開始日
     * @param endDate 終了日
     * @return 残業記録のリスト
     */
    List<OvertimeRecord> selectByUserAndDateRange(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
    
    /**
     * ユーザーIDと月で残業記録を取得
     *
     * @param userId ユーザーID
     * @param year 年
     * @param month 月
     * @return 残業記録のリスト
     */
    List<OvertimeRecord> selectByUserAndMonth(
            @Param("userId") Long userId,
            @Param("year") int year,
            @Param("month") int month
    );
    
    /**
     * 月別の残業時間合計を取得
     *
     * @param userId ユーザーID
     * @param year 年
     * @param month 月
     * @return 残業時間合計
     */
    Double selectOvertimeHoursSumByUserAndMonth(
            @Param("userId") Long userId,
            @Param("year") int year,
            @Param("month") int month
    );

    /**
     * ユーザーIDと日付で残業記録を取得
     *
     * @param userId ユーザーID
     * @param overtimeDate 残業日
     * @return 残業記録。存在しない場合は Optional.empty()
     */
    Optional<OvertimeRecord> selectByUserAndDate(
            @Param("userId") Long userId,
            @Param("overtimeDate") LocalDate overtimeDate
    );

    /**
     * ユーザーIDと日付で残業記録を取得し、排他ロックを獲得
     *
     * @param userId ユーザーID
     * @param overtimeDate 残業日
     * @return 残業記録。存在しない場合は Optional.empty()
     */
    Optional<OvertimeRecord> selectByUserAndDateForUpdate(
            @Param("userId") Long userId,
            @Param("overtimeDate") LocalDate overtimeDate
    );
    
    /**
     * 残業記録を新規作成
     *
     * @param record 登録する残業記録
     * @return 登録された件数
     */
    int insert(OvertimeRecord record);
    
    /**
     * 残業記録を更新
     *
     * @param record 更新する残業記録
     * @return 更新された件数
     */
    int update(OvertimeRecord record);
    
    /**
     * 残業記録を削除
     *
     * @param overtimeId 残業記録ID
     * @return 削除された件数
     */
    int deleteById(@Param("overtimeId") Long overtimeId);

    /**
     * ユーザーIDと日付で残業記録を削除
     *
     * @param userId ユーザーID
     * @param overtimeDate 残業日
     * @return 削除された件数
     */
    int deleteByUserAndDate(
            @Param("userId") Long userId,
            @Param("overtimeDate") LocalDate overtimeDate
    );
}
