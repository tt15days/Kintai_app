package com.attendance.app.mapper;

import com.attendance.app.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * User Mapper - ユーザー情報に関するDB操作 
 */
@Mapper
public interface UserMapper {
    
    /**
     * ユーザーIDでユーザー情報を取得
     *
     * @param userId ユーザーID
     * @return ユーザー情報。存在しない場合は Optional.empty()
     */
    Optional<User> selectById(@Param("userId") Long userId);

    /**
     * ユーザーIDでユーザー情報を取得し、排他ロックを獲得
     *
     * @param userId ユーザーID
     * @return ユーザー情報。存在しない場合は Optional.empty()
     */
    Optional<User> selectByIdForUpdate(@Param("userId") Long userId);
    

    /**
     * メールアドレスでユーザー情報を取得
     *
     * @param email メールアドレス
     * @return ユーザー情報。存在しない場合は Optional.empty()
     */
    Optional<User> selectByEmail(@Param("email") String email);

    /**
     * メールアドレスでユーザー情報を取得し、排他ロックを獲得
     *
     * @param email メールアドレス
     * @return ユーザー情報。存在しない場合は Optional.empty()
     */
    Optional<User> selectByEmailForUpdate(@Param("email") String email);
    
    /**
     * すべてのアクティブなユーザーを取得
     *
     * @return アクティブなユーザー情報のリスト
     */
    List<User> selectAllActive();
    
    /**
     * すべてのユーザーを取得（全件取得。ページング引数はなし）
     *
     * @return ユーザー情報のリスト
     */
    List<User> selectAll();
    
    /**
     * ユーザーロール別にユーザーを取得
     *
     * @param role ユーザーロール
     * @return ユーザー情報のリスト
     */
    List<User> selectByRole(@Param("role") String role);
    
    /**
     * ユーザー情報を新規作成
     *
     * @param user 新規作成するユーザー情報
     * @return 作成された件数
     */
    int insert(User user);

    /**
     * 次のユーザーIDを取得（現在最大値 + 1）
     *
     * @return 次のユーザーID
     */
    Long selectNextUserId();
    
    /**
     * ユーザー情報を更新
     *
     * @param user 更新するユーザー情報
     * @return 更新された件数
     */
    int update(User user);

    /**
     * 最終ログイン日時を更新
     *
     * @param userId ユーザーID
     * @param lastLoginAt 最終ログイン日時
     * @return 更新された件数
     */
    int updateLastLogin(@Param("userId") Long userId, @Param("lastLoginAt") Instant lastLoginAt);

    /**
     * パスワードと初期化フラグを更新
     *
     * @param userId ユーザーID
     * @param password パスワード
     * @param passwordResetRequired パスワード初期化が必要かどうか
     * @return 更新された件数
     */
    int updatePassword(
            @Param("userId") Long userId,
            @Param("password") String password,
            @Param("passwordResetRequired") boolean passwordResetRequired);
    
    /**
     * ユーザーをソフトデリート（isActiveをfalseに）
     *
     * @param userId ユーザーID
     * @return 更新された件数
     */
    int softDeleteById(@Param("userId") Long userId);
    
    /**
     * メールアドレスが存在するか確認
     *
     * @param email メールアドレス
     * @return 存在する場合は true
     */
    boolean existsByEmail(@Param("email") String email);
    
    /**
     * ユーザー総数を取得
     *
     * @return ユーザー総数
     */
    long countAll();

    /**
     * ユーザーの所属クラス名を更新
     *
     * @param userId ユーザーID
     * @param className クラス名
     * @return 更新された件数
     */
    int updateWorkScheduleClass(@Param("userId") Long userId, @Param("className") String className);

    /**
     * 勤務クラス名の変更に追従してユーザーの所属クラス名を一括更新
     *
     * @param oldClassName 変更前のクラス名
     * @param newClassName 変更後のクラス名
     * @return 更新された件数
     */
    int replaceClassName(@Param("oldClassName") String oldClassName, @Param("newClassName") String newClassName);

    /**
     * 勤務クラス削除時に所属クラス名を解除
     *
     * @param className クラス名
     * @return 更新された件数
     */
    int clearClassName(@Param("className") String className);

    /**
     * 有給残日数を更新（バッチ付与用）
     *
     * @param userId ユーザーID
     * @param paidLeaveDays 有給残日数
     * @return 更新された件数
     */
    int updatePaidLeaveDays(@Param("userId") Long userId, @Param("paidLeaveDays") java.math.BigDecimal paidLeaveDays);

    /**
     * ユーザー別年次有給設定を更新（管理者用）
     *
     * @param userId ユーザーID
     * @param annualLeaveGrantDays 年次有給付与日数
     * @param annualLeaveIncrement 年次有給増加日数
     * @param maxPaidLeaveDays 最大有給日数
     * @return 更新された件数
     */
    int updatePaidLeaveSettings(
            @Param("userId") Long userId,
            @Param("annualLeaveGrantDays") int annualLeaveGrantDays,
            @Param("annualLeaveIncrement") java.math.BigDecimal annualLeaveIncrement,
            @Param("maxPaidLeaveDays") int maxPaidLeaveDays);

    /**
     * ログイン試行情報を更新（失敗カウント・一時ロック・永久ロック）
     *
     * @param userId ユーザーID
     * @param failedLoginCount ログイン失敗回数
     * @param lockedUntil ロック終了日時
     * @param accountLocked アカウントがロックされているかどうか
     * @return 更新された件数
     */
    int updateLoginAttempt(
            @Param("userId") Long userId,
            @Param("failedLoginCount") int failedLoginCount,
            @Param("lockedUntil") Instant lockedUntil,
            @Param("accountLocked") boolean accountLocked);

    /**
     * ログイン試行情報をリセット（ログイン成功時）
     *
     * @param userId ユーザーID
     * @return 更新された件数
     */
    int resetLoginAttempt(@Param("userId") Long userId);
}
