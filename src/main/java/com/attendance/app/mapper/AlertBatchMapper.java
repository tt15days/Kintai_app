package com.attendance.app.mapper;

import com.attendance.app.dto.Article36AlertDto;
import com.attendance.app.dto.PaidLeaveAlertDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface AlertBatchMapper {

    /**
     * 指定された期間内で、残業時間の合計が閾値を超えているユーザーを取得します。
     *
     * @param startDate 集計開始日
     * @param endDate 集計終了日
     * @param limitHours 閾値（時間）
     * @return 閾値を超えたユーザーと残業時間のリスト
     */
    List<Article36AlertDto> findUsersExceedingOvertimeLimit(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("limitHours") int limitHours,
            @Param("afterUserId") long afterUserId,
            @Param("limit") int limit);

    /**
     * 現在の有効な有給休暇において、付与日から指定月数以上経過し、
     * かつ消化日数が指定日数未満のユーザーを取得します。
     *
     * @param monthsPassed 経過月数（例：9）
     * @param daysLimit 基準日数（例：3）
     * @param currentDate 基準となる現在日
     * @return 条件に合致するユーザー情報のリスト
     */
    List<PaidLeaveAlertDto> findUsersWithInsufficientPaidLeave(
            @Param("monthsPassed") int monthsPassed,
            @Param("daysLimit") int daysLimit,
            @Param("currentDate") LocalDate currentDate,
            @Param("afterUserId") long afterUserId,
            @Param("limit") int limit);
}
