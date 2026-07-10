package com.attendance.app.service;

import com.attendance.app.entity.User;
import com.attendance.app.entity.UserRole;
import com.attendance.app.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * ログイン試行制限サービス
 *
 * ログイン失敗・成功時のカウント管理と、一時ロック・永久ロックを制御する。
 * <ul>
 *   <li>3回失敗: 30分の一時ロックを設定し警告</li>
 *   <li>5回失敗: アカウント永久ロック（管理者ユーザーを除く）</li>
 * </ul>
 * 管理者ユーザー（UserRole.ADMIN）も一般ユーザーと同じ一時ロック（3回/30分）の対象とする。
 * ただし全管理者ロックアウトを防ぐため、永久ロック（5回）は管理者には適用せず一時ロックを継続する。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private static final int WARNING_THRESHOLD = 3;
    private static final int LOCK_THRESHOLD    = 5;
    private static final int TEMP_LOCK_MINUTES = 30;

    private final UserMapper userMapper;

    /**
     * ログイン失敗時の処理。失敗カウントをインクリメントしロック判定を行う。
     * 管理者ユーザーも一時ロック対象だが、永久ロックは適用しない。ユーザーが存在しない場合は NORMAL を返す。
     *
     * @param email ログイン失敗したメールアドレス
     * @return 試行結果 {@link LoginAttemptResult}
     */
    @Transactional
    public LoginAttemptResult handleFailure(String email) {
        Optional<User> userOpt = userMapper.selectByEmailForUpdate(email);
        if (userOpt.isEmpty()) {
            return LoginAttemptResult.NORMAL;
        }
        User user = userOpt.get();
        boolean isAdmin = user.getUserRole() == UserRole.ADMIN;

        // すでに永続ロック済みの場合は再カウント不要（管理者は永久ロック対象外のため到達しない）
        if (Boolean.TRUE.equals(user.getAccountLocked())) {
            return LoginAttemptResult.LOCKED;
        }

        int newCount = (user.getFailedLoginCount() != null ? user.getFailedLoginCount() : 0) + 1;

        if (newCount >= LOCK_THRESHOLD && !isAdmin) {
            userMapper.updateLoginAttempt(user.getUserId(), newCount, null, true);
            log.warn("アカウントを永久ロックしました: userId={}, failedCount={}", user.getUserId(), newCount);
            return LoginAttemptResult.LOCKED;
        } else if (newCount >= WARNING_THRESHOLD) {
            // 管理者は全管理者ロックアウト防止のため永久ロックを適用せず、一時ロックを繰り返し延長する
            Instant lockedUntil = Instant.now().plus(TEMP_LOCK_MINUTES, ChronoUnit.MINUTES);
            userMapper.updateLoginAttempt(user.getUserId(), newCount, lockedUntil, false);
            if (isAdmin && newCount >= LOCK_THRESHOLD) {
                log.warn("[ALERT] 管理者アカウントへのブルートフォース攻撃の可能性があります（永久ロックは適用せず一時ロックを継続）: userId={}, failedCount={}, lockedUntil={}",
                        user.getUserId(), newCount, lockedUntil);
            } else {
                log.warn("一時ロックを設定しました: userId={}, failedCount={}, lockedUntil={}",
                        user.getUserId(), newCount, lockedUntil);
            }
            return LoginAttemptResult.TEMP_LOCKED;
        } else {
            userMapper.updateLoginAttempt(user.getUserId(), newCount, null, false);
            log.info("ログイン失敗カウントを更新: userId={}, failedCount={}", user.getUserId(), newCount);
            return LoginAttemptResult.NORMAL;
        }
    }

    /**
     * ログイン成功時の処理。失敗カウントをリセットしロック状態を解除する。
     *
     * @param email ログイン成功したメールアドレス
     */
    @Transactional
    public void handleSuccess(String email) {
        userMapper.selectByEmailForUpdate(email).ifPresent(user -> {
            int count = user.getFailedLoginCount() != null ? user.getFailedLoginCount() : 0;
            if (count > 0 || Boolean.TRUE.equals(user.getAccountLocked())
                    || user.getLockedUntil() != null) {
                userMapper.resetLoginAttempt(user.getUserId());
                log.info("ログイン試行情報をリセットしました: userId={}", user.getUserId());
            }
        });
    }
}
