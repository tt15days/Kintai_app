package com.attendance.app.service;

import com.attendance.app.entity.AuditEventType;
import com.attendance.app.entity.User;
import com.attendance.app.entity.UserRole;
import com.attendance.app.mapper.SystemSettingMapper;
import com.attendance.app.mapper.UserMapper;
import com.attendance.app.mapper.WorkScheduleClassMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link UserService} における監査ログ呼び出しの単体テスト。
 *
 * <p>
 * ユーザー作成・更新・削除・パスワード初期化の各操作で
 * {@link AuditLogService#recordUserEvent} が正しいパラメータで
 * 呼び出されることを検証する。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserService 監査ログ")
class UserServiceAuditTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private WorkScheduleClassMapper workScheduleClassMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private SystemSettingMapper systemSettingMapper;

    @InjectMocks
    private UserService userService;

    private static final Long ACTOR_ID = 1L;
    private static final Long TARGET_ID = 42L;

    /** テスト用の既存ユーザー */
    private User existingUser;

    @BeforeEach
    void setUp() {
        existingUser = User.builder()
                .userId(TARGET_ID)
                .email("user@example.com")
                .fullName("テストユーザー")
                .userRole(UserRole.USER)
                .isActive(true)
                .canApproveAttendance(false)
                .paidLeaveDays(new BigDecimal("10.0"))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        lenient().when(userMapper.selectNextUserId()).thenReturn(TARGET_ID);
        lenient().when(systemSettingMapper.selectValueByKey("EMP_NO_PREFIX")).thenReturn(null);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // createUser
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("createUser")
    class CreateUser {

        @Test
        @DisplayName("ユーザー作成成功時に USER_CREATED が記録される")
        void create_recordsCreatedEvent() {
            when(userMapper.existsByEmail("new@example.com")).thenReturn(false);
            when(passwordEncoder.encode(anyString())).thenReturn("hashed");
            doAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setUserId(TARGET_ID);
                return null;
            }).when(userMapper).insert(any(User.class));

            userService.createUser("new@example.com", "pass9999", "新規ユーザー", UserRole.USER, null, ACTOR_ID);

            verify(auditLogService).recordUserEvent(
                    eq(AuditEventType.USER_CREATED),
                    eq(ACTOR_ID),
                    eq(TARGET_ID),
                    contains("USER"));
        }

        @Test
        @DisplayName("メール重複の場合は監査ログが記録されない")
        void create_duplicate_noAuditLog() {
            when(userMapper.existsByEmail(anyString())).thenReturn(true);

            org.assertj.core.api.Assertions.assertThatThrownBy(
                    () -> userService.createUser("dup@example.com", "pass9999", "重複", UserRole.USER, null, ACTOR_ID))
                    .isInstanceOf(IllegalArgumentException.class);

            verify(auditLogService, never()).recordUserEvent(any(), any(), any(), any());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // updateUser
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("updateUser")
    class UpdateUser {

        @Test
        @DisplayName("ユーザー更新成功時に USER_UPDATED が記録される")
        void update_recordsUpdatedEvent() {
            when(userMapper.selectById(TARGET_ID)).thenReturn(Optional.of(existingUser));

            userService.updateUser(
                    TARGET_ID, "user@example.com", "更新ユーザー",
                    UserRole.USER, null, null, null,
                    new BigDecimal("10.0"), null, false, null, ACTOR_ID);

            verify(auditLogService).recordUserEvent(
                    eq(AuditEventType.USER_UPDATED),
                    eq(ACTOR_ID),
                    eq(TARGET_ID),
                    contains("USER"));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // deleteUser
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("deleteUser")
    class DeleteUser {

        @Test
        @DisplayName("削除成功時に USER_DELETED が記録される")
        void delete_recordsDeletedEvent() {
            userService.deleteUser(TARGET_ID, ACTOR_ID);

            verify(auditLogService).recordUserEvent(
                    eq(AuditEventType.USER_DELETED),
                    eq(ACTOR_ID),
                    eq(TARGET_ID),
                    isNull());
            verify(userMapper).softDeleteById(TARGET_ID);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // resetPasswordByAdmin
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("resetPasswordByAdmin")
    class ResetPasswordByAdmin {

        @Test
        @DisplayName("パスワード初期化成功時に USER_PASSWORD_RESET が記録される")
        void resetPassword_recordsResetEvent() {
            when(userMapper.selectById(TARGET_ID)).thenReturn(Optional.of(existingUser));
            when(passwordEncoder.encode(anyString())).thenReturn("hashed-initial");

            userService.resetPasswordByAdmin(TARGET_ID, ACTOR_ID);

            verify(auditLogService).recordUserEvent(
                    eq(AuditEventType.USER_PASSWORD_RESET),
                    eq(ACTOR_ID),
                    eq(TARGET_ID),
                    isNull());
            verify(userMapper).updatePassword(eq(TARGET_ID), eq("hashed-initial"), eq(true));
        }
    }
}
