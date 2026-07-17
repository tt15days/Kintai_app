package com.attendance.app.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link SessionInvalidationService} の単体テスト。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SessionInvalidationService")
class SessionInvalidationServiceTest {

    @Mock
    private SessionRegistry sessionRegistry;

    @InjectMocks
    private SessionInvalidationService service;

    private UserDetails principal(String email) {
        return User.withUsername(email).password("x").authorities("ROLE_USER").build();
    }

    @Test
    @DisplayName("expireSessions: 対象ユーザーのセッションのみすべて失効させる")
    void expireSessions_expiresAllSessionsOfTargetUser() {
        UserDetails target = principal("target@example.com");
        UserDetails other = principal("other@example.com");
        SessionInformation targetSession = new SessionInformation(target, "sid-1", new Date());
        SessionInformation otherSession = new SessionInformation(other, "sid-2", new Date());
        when(sessionRegistry.getAllPrincipals()).thenReturn(List.of(target, other));
        when(sessionRegistry.getAllSessions(target, false)).thenReturn(List.of(targetSession));

        service.expireSessions("target@example.com");

        assertThat(targetSession.isExpired()).isTrue();
        assertThat(otherSession.isExpired()).isFalse();
    }

    @Test
    @DisplayName("expireSessionsExcept: 指定セッションだけ失効から除外する")
    void expireSessionsExcept_keepsExcludedSession() {
        UserDetails target = principal("target@example.com");
        SessionInformation keep = new SessionInformation(target, "keep-sid", new Date());
        SessionInformation expire = new SessionInformation(target, "other-sid", new Date());
        when(sessionRegistry.getAllPrincipals()).thenReturn(List.of(target));
        when(sessionRegistry.getAllSessions(target, false)).thenReturn(List.of(keep, expire));

        service.expireSessionsExcept("target@example.com", "keep-sid");

        assertThat(keep.isExpired()).isFalse();
        assertThat(expire.isExpired()).isTrue();
    }

    @Test
    @DisplayName("expireSessions: メールアドレスが null/空なら何もしない")
    void expireSessions_ignoresBlankEmail() {
        service.expireSessions(null);
        service.expireSessions(" ");

        verifyNoInteractions(sessionRegistry);
    }
}
