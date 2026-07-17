package com.attendance.app.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

/**
 * アカウントの無効化・削除・ロール変更・パスワード変更時に、
 * 対象ユーザーの既存 HTTP セッションを即時失効させるサービスです。
 *
 * SessionRegistry はインメモリ実装のため、単一インスタンス構成でのみ有効です。
 * 複数インスタンス構成に移行する場合は Spring Session への置き換えが必要です。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionInvalidationService {

    private final SessionRegistry sessionRegistry;

    /**
     * 指定メールアドレスのユーザーが持つすべてのセッションを失効させます。
     *
     * @param email 対象ユーザーのメールアドレス（ログインID）
     */
    public void expireSessions(String email) {
        expireSessionsExcept(email, null);
    }

    /**
     * 指定メールアドレスのユーザーが持つセッションのうち、
     * 指定セッション以外をすべて失効させます（本人パスワード変更時に使用）。
     *
     * @param email             対象ユーザーのメールアドレス（ログインID）
     * @param excludedSessionId 失効させないセッションID（null 可）
     */
    public void expireSessionsExcept(String email, String excludedSessionId) {
        if (email == null || email.isBlank()) {
            return;
        }
        int expiredCount = 0;
        for (Object principal : sessionRegistry.getAllPrincipals()) {
            if (!principalMatches(principal, email)) {
                continue;
            }
            for (SessionInformation session : sessionRegistry.getAllSessions(principal, false)) {
                if (excludedSessionId != null && excludedSessionId.equals(session.getSessionId())) {
                    continue;
                }
                session.expireNow();
                expiredCount++;
            }
        }
        if (expiredCount > 0) {
            log.info("既存セッションを失効させました: count={}", expiredCount);
        }
    }

    private boolean principalMatches(Object principal, String email) {
        if (principal instanceof UserDetails userDetails) {
            return email.equals(userDetails.getUsername());
        }
        return email.equals(String.valueOf(principal));
    }
}
