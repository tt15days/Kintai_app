package com.attendance.app.security;

import com.attendance.app.entity.User;
import com.attendance.app.entity.UserRole;
import com.attendance.app.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

/**
 * Custom UserDetailsService - Spring Security用ユーザー詳細サービス
 *
 * Spring Security がユーザー認証時に使用するUserDetailsを提供する。
 * データベースからユーザー情報を取得し、ユーザー詳細オブジェクトに変換する。
 * 
 * 注: 循環参照を避けるため、UserService に @Lazy アノテーションを付与して
 * 遅延初期化を実装している。
 */
@Slf4j
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserService userService;

    /**
     * コンストラクタ - Lazy 初期化
     * @param userService 遅延初期化されたユーザーサービス
     */
    public CustomUserDetailsService(@Lazy UserService userService) {
        this.userService = userService;
    }

    /**
     * メールアドレスからユーザー詳細情報を取得
     *
     * @param email ユーザーのメールアドレス（username として使用）
     * @return Spring Security の UserDetails オブジェクト
     * @throws UsernameNotFoundException ユーザーが見つからない場合、または非アクティブな場合
     */
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        log.debug("ユーザー認証情報を読み込んでいます");

        Optional<User> user = userService.getUserByEmail(email);

        if (user.isEmpty()) {
            log.warn("指定されたユーザーが見つかりません");
            throw new UsernameNotFoundException("ユーザーが見つかりません: " + email);
        }

        User dbUser = user.get();

        // ユーザーがアクティブでない場合は例外を投げる
        if (!dbUser.getIsActive()) {
            log.warn("無効化されたユーザーへのログイン試行を検知しました");
            throw new UsernameNotFoundException("このユーザーは無効化されています: " + email);
        }

        log.debug("ユーザー認証情報を読み込みました: role={}", dbUser.getUserRole());

        // ロック状態を確認する（管理者も一時ロックの対象。永久ロックは LoginAttemptService 側で管理者に設定されない）
        boolean isLocked = false;
        if (Boolean.TRUE.equals(dbUser.getAccountLocked())) {
            isLocked = true;
        } else if (dbUser.getLockedUntil() != null && Instant.now().isBefore(dbUser.getLockedUntil())) {
            isLocked = true;
        }

        // Spring Security の UserDetails オブジェクトを返す
        return org.springframework.security.core.userdetails.User
                .withUsername(dbUser.getEmail())
                .password(dbUser.getPassword())
                .authorities(new SimpleGrantedAuthority("ROLE_" + dbUser.getUserRole().name()))
                .accountExpired(false)
                .accountLocked(isLocked)
                .credentialsExpired(false)
                .disabled(false)
                .build();
    }
}

