package com.attendance.app.mapper;

import com.attendance.app.entity.PaidLeaveBalance;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * PaidLeaveBalance Mapper - 有給休暇年次残高のDB操作
 */
@Mapper
public interface PaidLeaveBalanceMapper {

    /**
     * ユーザーの全有給残高を付与年度降順で取得します。
     *
     * @param userId ユーザーID
     * @return 有給残高のリスト
     */
    List<PaidLeaveBalance> selectByUserId(@Param("userId") Long userId);

    /**
     * ユーザーの有効な有給残高（失効日が今日以降かつ残日数あり）を失効日昇順で取得します。
     *
     * @param userId ユーザーID
     * @param today 基準日（今日）
     * @return 有効な有給残高のリスト
     */
    List<PaidLeaveBalance> selectActiveByUserId(@Param("userId") Long userId, @Param("today") LocalDate today);

    /**
     * ユーザーの有効な有給残高（失効日が今日以降かつ残日数あり）を失効日昇順で取得し、排他ロックを獲得します。
     *
     * @param userId ユーザーID
     * @param today 基準日（今日）
     * @return 有効な有給残高のリスト
     */
    List<PaidLeaveBalance> selectActiveByUserIdForUpdate(@Param("userId") Long userId, @Param("today") LocalDate today);

    /**
     * ユーザーIDと付与年度で有給残高を取得します。
     *
     * @param userId ユーザーID
     * @param grantYear 付与年度
     * @return 有給残高。存在しない場合は Optional.empty()
     */
    Optional<PaidLeaveBalance> selectByUserAndYear(@Param("userId") Long userId, @Param("grantYear") Integer grantYear);

    /**
     * 有給残高を挿入します。
     *
     * @param balance 登録する有給残高情報
     * @return 登録された件数
     */
    int insert(PaidLeaveBalance balance);

    /**
     * 有給残高を更新します（used_days 等の更新に使用）。
     *
     * @param balance 更新する有給残高情報
     * @return 更新された件数
     */
    int update(PaidLeaveBalance balance);
}
