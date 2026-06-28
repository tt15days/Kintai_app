package com.attendance.app.security;

import com.attendance.app.entity.User;
import com.attendance.app.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Security Utility - セキュリティ関連ユーティリティ
 *
 * SecurityContextHolder からログイン中のユーザー情報を取得するヘルパー機能を提供します。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityUtil {

    private final UserService userService;

    /**
     * 現在ログイン中のユーザーの username (メールアドレスなど) を取得します。
     *
     * @return ユーザーの username。認証情報がない場合は Optional.empty() を返します。
     */
    public Optional<String> getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            return Optional.of(authentication.getName());
        }
        return Optional.empty();
    }

    /**
     * 現在ログイン中のユーザーIDを取得します。
     * 
     * SecurityContextHolder から username を取得し、UserService を通じて
     * ユーザーID に変換します。
     *
     * @return ログイン中のユーザーID
     * @throws IllegalArgumentException ユーザーが見つからない場合、または認証情報がない場合
     */
    public Long getCurrentUserId() {
        String username = getCurrentUsername()
                .orElseThrow(() -> new IllegalArgumentException("ログイン情報が見つかりません"));
        User user = userService.getUserByEmail(username)
                .orElseThrow(() -> new IllegalArgumentException("ユーザーが見つかりません: " + username));
        return user.getUserId();
    }

    /**
     * 現在ログイン中のユーザーのUser エンティティを取得します。
     *
     * @return ログイン中のユーザー情報
     * @throws IllegalArgumentException ユーザーが見つからない場合、または認証情報がない場合
     */
    public User getCurrentUser() {
        String username = getCurrentUsername()
                .orElseThrow(() -> new IllegalArgumentException("ログイン情報が見つかりません"));
        return userService.getUserByEmail(username)
                .orElseThrow(() -> new IllegalArgumentException("ユーザーが見つかりません: " + username));
    }

    /**
     * 現在ログイン中のユーザーが認証されているかどうかを確認します。
     *
     * @return 認証されている場合は true、未認証の場合は false
     */
    public boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated();
    }

    /**
     * 現在ログイン中のユーザーが指定されたロールを持つかどうかを確認します。
     *
     * @param role チェックするロール名（例："ROLE_USER", "ROLE_ADMIN"）
     * @return 指定されたロールを持つ場合は true、持たない場合は false
     */
    public boolean hasRole(String role) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals(role));
    }
}