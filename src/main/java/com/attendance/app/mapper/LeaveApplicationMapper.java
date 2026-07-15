package com.attendance.app.mapper;

import com.attendance.app.entity.LeaveApplication;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * LeaveApplication Mapper - 休暇申請に関するDB操作
 */
@Mapper
public interface LeaveApplicationMapper {
    
    /**
     * 申請IDで休暇申請を取得
     *
     * @param applicationId 申請ID
     * @return 休暇申請。存在しない場合は Optional.empty()
     */
    Optional<LeaveApplication> selectById(@Param("applicationId") Long applicationId);

    /**
     * 申請IDで休暇申請を取得し、排他ロックを獲得
     *
     * @param applicationId 申請ID
     * @return 休暇申請。存在しない場合は Optional.empty()
     */
    Optional<LeaveApplication> selectByIdForUpdate(@Param("applicationId") Long applicationId);
    
    /**
     * ユーザーIDで休暇申請を取得（ページング対応）
     *
     * @param userId ユーザーID
     * @return 休暇申請のリスト
     */
    List<LeaveApplication> selectByUserId(@Param("userId") Long userId);
    
    /**
     * ユーザーIDとステータスで休暇申請を取得
     *
     * @param userId ユーザーID
     * @param status ステータス
     * @return 休暇申請のリスト
     */
    List<LeaveApplication> selectByUserIdAndStatus(@Param("userId") Long userId, @Param("status") String status);
    
    /**
     * 全ユーザーの申請中の休暇申請を取得（管理者用）
     *
     * @return 休暇申請のリスト
     */
    List<LeaveApplication> selectPendingApplications();
    
    /**
     * ユーザーIDと期間で休暇申請を取得
     *
     * @param userId ユーザーID
     * @param startDate 開始日
     * @param endDate 終了日
     * @return 休暇申請のリスト
     */
    List<LeaveApplication> selectByUserAndDateRange(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    /**
     * 全ユーザーの指定期間内の休暇申請を取得（管理者用・一括集計向け）
     *
     * @param startDate 開始日
     * @param endDate 終了日
     * @return 休暇申請のリスト
     */
    List<LeaveApplication> selectAllByDateRange(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    List<LeaveApplication> selectByDateRangeAndUserIds(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("userIds") java.util.Collection<Long> userIds
    );

    /**
     * 休暇申請を新規作成
     *
     * @param application 休暇申請情報
     * @return 作成された件数
     */
    int insert(LeaveApplication application);
    
    /**
     * 休暇申請を更新
     *
     * @param application 休暇申請情報
     * @return 更新された件数
     */
    int update(LeaveApplication application);
    
    /**
     * 休暇申請を削除
     *
     * @param applicationId 申請ID
     * @return 削除された件数
     */
    int deleteById(@Param("applicationId") Long applicationId);
    
    /**
     * 休暇申請を承認
     *
     * @param applicationId 申請ID
     * @param approvedBy 承認者ユーザーID
     * @return 更新された件数
     */
    int approve(@Param("applicationId") Long applicationId, @Param("approvedBy") Long approvedBy);
    
    /**
     * 休暇申請を却下
     *
     * @param applicationId 申請ID
     * @return 更新された件数
     */
    int reject(@Param("applicationId") Long applicationId);

    /**
     * 指定年の承認済み有給使用日数を集計（ユーザー別）。
     * 半休（AM_HALF/PM_HALF）は0.5日として集計されます。
     *
     * @param userId ユーザーID
     * @param year 年
     * @return 使用日数
     */
    BigDecimal countApprovedPaidLeaveDays(@Param("userId") Long userId, @Param("year") int year);

    /**
     * ユーザー単位のトランザクションスコープ advisory lock を取得します。
     * 休暇申請の重複チェック（TOCTOUレース）を直列化するために、重複チェック前に呼び出します。
     * トランザクション終了時に自動的に解放されます。
     *
     * @param userId 対象ユーザーID
     */
    void acquireUserLock(@Param("userId") Long userId);
}
