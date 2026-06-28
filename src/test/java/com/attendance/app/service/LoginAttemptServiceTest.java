package com.attendance.app.service;

import com.attendance.app.entity.User;
import com.attendance.app.entity.UserRole;
import com.attendance.app.mapper.UserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("LoginAttemptService")
class LoginAttemptServiceTest {

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private LoginAttemptService service;

    @Test
    @DisplayName("ログイン失敗時は失敗回数をインクリメントする")
    void handleFailure_incrementsCounter() {
        User user = User.builder()
                .userId(1L)
                .userRole(UserRole.USER)
                .failedLoginCount(1)
                .accountLocked(false)
                .build();
        when(userMapper.selectByEmailForUpdate("user@example.com")).thenReturn(Optional.of(user));

        LoginAttemptResult result = service.handleFailure("user@example.com");

        assertThat(result).isEqualTo(LoginAttemptResult.NORMAL);
        verify(userMapper).updateLoginAttempt(eq(1L), eq(2), isNull(), eq(false));
    }

    @Test
    @DisplayName("失敗回数が閾値に達すると永久ロックする")
    void handleFailure_locksAccountAfterThreshold() {
        User user = User.builder()
                .userId(2L)
                .userRole(UserRole.USER)
                .failedLoginCount(4)
                .accountLocked(false)
                .build();
        when(userMapper.selectByEmailForUpdate("locked@example.com")).thenReturn(Optional.of(user));

        LoginAttemptResult result = service.handleFailure("locked@example.com");

        assertThat(result).isEqualTo(LoginAttemptResult.LOCKED);
        verify(userMapper).updateLoginAttempt(eq(2L), eq(5), isNull(), eq(true));
    }

    @Test
    @DisplayName("ログイン成功時は失敗回数とロック情報をリセットする")
    void handleSuccess_resetsCounter() {
        User user = User.builder()
                .userId(3L)
                .failedLoginCount(2)
                .accountLocked(false)
                .lockedUntil(Instant.now().plusSeconds(600))
                .build();
        when(userMapper.selectByEmailForUpdate("ok@example.com")).thenReturn(Optional.of(user));

        service.handleSuccess("ok@example.com");

        verify(userMapper).resetLoginAttempt(3L);
    }
}
