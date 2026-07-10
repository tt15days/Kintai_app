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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
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
    @DisplayName("失敗回数が警告閾値に達すると一時ロックする")
    void handleFailure_tempLocksAccountAfterWarningThreshold() {
        User user = User.builder()
                .userId(1L)
                .userRole(UserRole.USER)
                .failedLoginCount(2)
                .accountLocked(false)
                .build();
        when(userMapper.selectByEmailForUpdate("warning@example.com")).thenReturn(Optional.of(user));

        LoginAttemptResult result = service.handleFailure("warning@example.com");

        assertThat(result).isEqualTo(LoginAttemptResult.TEMP_LOCKED);
        verify(userMapper).updateLoginAttempt(eq(1L), eq(3), any(Instant.class), eq(false));
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

    @Test
    @DisplayName("存在しないメールアドレスの場合はNORMALを返し、DB更新を行わない")
    void handleFailure_withNonExistentUser_returnsNormalAndDoesNotUpdate() {
        when(userMapper.selectByEmailForUpdate("nonexistent@example.com")).thenReturn(Optional.empty());

        LoginAttemptResult result = service.handleFailure("nonexistent@example.com");

        assertThat(result).isEqualTo(LoginAttemptResult.NORMAL);
        verify(userMapper, never()).updateLoginAttempt(any(), anyInt(), any(), anyBoolean());
    }

    @Test
    @DisplayName("失敗カウントがnullの場合は1として扱われインクリメントされる")
    void handleFailure_withNullFailedCount_treatsAsZeroAndIncrements() {
        User user = User.builder()
                .userId(10L)
                .userRole(UserRole.USER)
                .failedLoginCount(null)
                .accountLocked(false)
                .build();
        when(userMapper.selectByEmailForUpdate("nullcount@example.com")).thenReturn(Optional.of(user));

        LoginAttemptResult result = service.handleFailure("nullcount@example.com");

        assertThat(result).isEqualTo(LoginAttemptResult.NORMAL);
        verify(userMapper).updateLoginAttempt(eq(10L), eq(1), isNull(), eq(false));
    }

    @Test
    @DisplayName("すでに永久ロックされている場合は再カウントせずLOCKEDを返す")
    void handleFailure_alreadyLocked_returnsLockedWithoutDbUpdate() {
        User user = User.builder()
                .userId(11L)
                .userRole(UserRole.USER)
                .failedLoginCount(5)
                .accountLocked(true)
                .build();
        when(userMapper.selectByEmailForUpdate("already@example.com")).thenReturn(Optional.of(user));

        LoginAttemptResult result = service.handleFailure("already@example.com");

        assertThat(result).isEqualTo(LoginAttemptResult.LOCKED);
        verify(userMapper, never()).updateLoginAttempt(any(), anyInt(), any(), anyBoolean());
    }

    @Test
    @DisplayName("管理者も一時ロック対象だが永久ロック閾値では一時ロックを継続する")
    void handleFailure_adminUser_tempLockedButNeverPermanentlyLocked() {
        // 警告閾値（3回目）の失敗時: 一時ロック
        User admin1 = User.builder()
                .userId(100L)
                .userRole(UserRole.ADMIN)
                .failedLoginCount(2)
                .accountLocked(false)
                .build();
        when(userMapper.selectByEmailForUpdate("admin1@example.com")).thenReturn(Optional.of(admin1));

        LoginAttemptResult result1 = service.handleFailure("admin1@example.com");

        assertThat(result1).isEqualTo(LoginAttemptResult.TEMP_LOCKED);
        verify(userMapper).updateLoginAttempt(eq(100L), eq(3), any(Instant.class), eq(false));

        // 永久ロック閾値（5回目）の失敗時: 永久ロックせず一時ロックを継続
        User admin2 = User.builder()
                .userId(101L)
                .userRole(UserRole.ADMIN)
                .failedLoginCount(4)
                .accountLocked(false)
                .build();
        when(userMapper.selectByEmailForUpdate("admin2@example.com")).thenReturn(Optional.of(admin2));

        LoginAttemptResult result2 = service.handleFailure("admin2@example.com");

        assertThat(result2).isEqualTo(LoginAttemptResult.TEMP_LOCKED);
        verify(userMapper).updateLoginAttempt(eq(101L), eq(5), any(Instant.class), eq(false));
    }

    @Test
    @DisplayName("失敗カウントもロックもない場合はログイン成功時にリセット処理を行わない")
    void handleSuccess_noFailedCount_doesNotResetDb() {
        User user = User.builder()
                .userId(12L)
                .failedLoginCount(0)
                .accountLocked(false)
                .lockedUntil(null)
                .build();
        when(userMapper.selectByEmailForUpdate("clear@example.com")).thenReturn(Optional.of(user));

        service.handleSuccess("clear@example.com");

        verify(userMapper, never()).resetLoginAttempt(any());
    }
}

