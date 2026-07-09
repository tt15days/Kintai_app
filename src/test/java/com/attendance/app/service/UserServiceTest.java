package com.attendance.app.service;

import com.attendance.app.entity.User;
import com.attendance.app.entity.UserRole;
import com.attendance.app.mapper.SystemSettingMapper;
import com.attendance.app.mapper.UserMapper;
import com.attendance.app.mapper.WorkScheduleClassMapper;
import com.attendance.app.mapper.PaidLeaveBalanceMapper;
import com.attendance.app.mapper.AttendanceApproverAssignmentMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link UserService} の単体テスト。
 *
 * <p>
 * ユーザー作成・パスワード変更・パスワード初期化・ソフトデリート・
 * 勤怠承認権限判定・認証ロジックを検証します。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserService")
class UserServiceTest {

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
    @Mock
    private PaidLeaveBalanceMapper paidLeaveBalanceMapper;
    @Mock
    private AttendanceApproverAssignmentMapper approverAssignmentMapper;
    @Mock
    private LeaveApplicationService leaveApplicationService;

    @InjectMocks
    private UserService service;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(userMapper.selectNextUserId()).thenReturn(4L);
        org.mockito.Mockito.lenient().when(systemSettingMapper.selectValueByKey("EMP_NO_PREFIX")).thenReturn(null);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // createUser
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("createUser")
    class CreateUser {

        @Test
        @DisplayName("新規ユーザーを作成して UserMapper.insert を呼ぶ")
        void newUser_callsInsert() {
            when(userMapper.existsByEmail("new@example.com")).thenReturn(false);
            when(passwordEncoder.encode("Pass1234")).thenReturn("$encoded$");

            User result = service.createUser("new@example.com", "Pass1234", "新規ユーザー", UserRole.USER, null, 1L);

            verify(userMapper).insert(any(User.class));
            assertThat(result.getEmail()).isEqualTo("new@example.com");
            assertThat(result.getFullName()).isEqualTo("新規ユーザー");
            assertThat(result.getUserRole()).isEqualTo(UserRole.USER);
            assertThat(result.getIsActive()).isTrue();
            assertThat(result.getPassword()).isEqualTo("$encoded$");
        }

        @Test
        @DisplayName("メールアドレス重複の場合は例外を送出して insert しない")
        void duplicateEmail_throwsException() {
            when(userMapper.existsByEmail("dup@example.com")).thenReturn(true);

            assertThatThrownBy(() -> service.createUser("dup@example.com", "Pass1234", "ユーザー", UserRole.USER, null, 1L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("既に登録されています");

            verify(userMapper, never()).insert(any());
        }

        @Test
        @DisplayName("7文字パスワードはポリシー違反で例外")
        void minimumLength7_throwsException() {
            assertThatThrownBy(() -> service.createUser("weak7@example.com", "Abc1234", "弱い", UserRole.USER, null, 1L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("8文字以上");

            verify(userMapper, never()).insert(any());
        }

        @Test
        @DisplayName("数字を含まないパスワードはポリシー違反で例外")
        void withoutNumbers_throwsException() {
            assertThatThrownBy(() -> service.createUser("weakn@example.com", "Password", "弱い", UserRole.USER, null, 1L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("英字と数字");

            verify(userMapper, never()).insert(any());
        }

        @Test
        @DisplayName("英字を含まないパスワードはポリシー違反で例外")
        void withoutLetters_throwsException() {
            assertThatThrownBy(() -> service.createUser("weakl@example.com", "12345678", "弱い", UserRole.USER, null, 1L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("英字と数字");

            verify(userMapper, never()).insert(any());
        }

        @Test
        @DisplayName("プレフィックス未設定時は数値のみの社員番号が生成される")
        void noPrefix_empNoIsNumberOnly() {
            when(userMapper.existsByEmail("no-prefix@example.com")).thenReturn(false);
            when(passwordEncoder.encode("Pass1234")).thenReturn("$encoded$");
            when(systemSettingMapper.selectValueByKey("EMP_NO_PREFIX")).thenReturn(null);

            User result = service.createUser("no-prefix@example.com", "Pass1234", "プレフィックスなし", UserRole.USER, null, 1L);

            assertThat(result.getEmpNo()).isEqualTo("004");
        }

        @Test
        @DisplayName("プレフィックスを設定した場合は先頭に付与される")
        void withPrefix_empNoPrefixed() {
            when(userMapper.existsByEmail("with-prefix@example.com")).thenReturn(false);
            when(passwordEncoder.encode("Pass1234")).thenReturn("$encoded$");
            when(systemSettingMapper.selectValueByKey("EMP_NO_PREFIX")).thenReturn("EMP-");

            User result = service.createUser("with-prefix@example.com", "Pass1234", "プレフィックスあり", UserRole.USER, null, 1L);

            assertThat(result.getEmpNo()).isEqualTo("EMP-004");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // changePassword
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("changePassword")
    class ChangePassword {

        @Test
        @DisplayName("現在のパスワードが一致すれば更新される")
        void correctOldPassword_updatesPassword() {
            User user = activeUser(2L, "$stored$");
            when(userMapper.selectById(2L)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("oldpass", "$stored$")).thenReturn(true);
            when(passwordEncoder.encode("Newpass12")).thenReturn("$newEncoded$");

            service.changePassword(2L, "oldpass", "Newpass12");

            verify(userMapper).updatePassword(eq(2L), eq("$newEncoded$"), eq(false));
        }

        @Test
        @DisplayName("現在のパスワードが不一致の場合は例外を送出する")
        void wrongOldPassword_throwsException() {
            User user = activeUser(2L, "$stored$");
            when(userMapper.selectById(2L)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("wrongpass", "$stored$")).thenReturn(false);

            assertThatThrownBy(() -> service.changePassword(2L, "wrongpass", "Newpass12"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("現在のパスワードが正しくありません");

            verify(userMapper, never()).updatePassword(any(), any(), anyBoolean());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // resetPasswordByAdmin
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("resetPasswordByAdmin")
    class ResetPasswordByAdmin {

        @Test
        @DisplayName("初期パスワード文字列を返し updatePassword が呼ばれる")
        void resetsPasswordAndReturnsInitialPassword() {
            User user = activeUser(2L, "$stored$");
            when(userMapper.selectById(2L)).thenReturn(Optional.of(user));
            when(passwordEncoder.encode(anyString())).thenReturn("$resetEncoded$");

            String resetPw = service.resetPasswordByAdmin(2L, 1L);

            assertThat(resetPw).isNotBlank();
            verify(userMapper).updatePassword(eq(2L), eq("$resetEncoded$"), eq(true));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // deleteUser
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("deleteUser")
    class DeleteUser {

        @Test
        @DisplayName("softDeleteById を呼び出す（ハードデリートしない）")
        void callsSoftDelete() {
            service.deleteUser(3L, 1L);
            verify(userMapper).softDeleteById(3L);
            verify(userMapper, never()).deleteById(any());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // isAttendanceApprover
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("isAttendanceApprover")
    class IsAttendanceApprover {

        @Test
        @DisplayName("null ユーザーは false を返す")
        void nullUser_returnsFalse() {
            assertThat(service.isAttendanceApprover(null)).isFalse();
        }

        @Test
        @DisplayName("無効ユーザー（isActive=false）は false を返す")
        void inactiveUser_returnsFalse() {
            User user = User.builder()
                    .userId(2L).userRole(UserRole.USER).isActive(false).canApproveAttendance(true).build();
            assertThat(service.isAttendanceApprover(user)).isFalse();
        }

        @Test
        @DisplayName("管理者（ADMIN）は常に true を返す")
        void adminUser_returnsTrue() {
            User admin = User.builder()
                    .userId(1L).userRole(UserRole.ADMIN).isActive(true).build();
            assertThat(service.isAttendanceApprover(admin)).isTrue();
        }

        @Test
        @DisplayName("一般管理者（MANAGER）は常に true を返す")
        void managerUser_returnsTrue() {
            User manager = User.builder()
                    .userId(3L).userRole(UserRole.MANAGER).isActive(true).build();
            assertThat(service.isAttendanceApprover(manager)).isTrue();
        }

        @Test
        @DisplayName("USER ロールで承認フラグ=true かつ有効は true を返す")
        void activeUserWithApproveFlag_returnsTrue() {
            User user = User.builder()
                    .userId(2L).userRole(UserRole.USER).isActive(true).canApproveAttendance(true).build();
            assertThat(service.isAttendanceApprover(user)).isTrue();
        }

        @Test
        @DisplayName("USER ロールで承認フラグ=false は false を返す")
        void activeUserWithoutApproveFlag_returnsFalse() {
            User user = User.builder()
                    .userId(2L).userRole(UserRole.USER).isActive(true).canApproveAttendance(false).build();
            assertThat(service.isAttendanceApprover(user)).isFalse();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // authenticate
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("authenticate")
    class Authenticate {

        @Test
        @DisplayName("正しいメール・パスワードで有効ユーザーは認証成功")
        void correctCredentials_returnsUser() {
            User user = activeUser(2L, "$stored$");
            user.setEmail("user@example.com");
            when(userMapper.selectByEmail("user@example.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("pass", "$stored$")).thenReturn(true);
            when(userMapper.selectById(2L)).thenReturn(Optional.of(user));

            Optional<User> result = service.authenticate("user@example.com", "pass");

            assertThat(result).isPresent();
            assertThat(result.get().getUserId()).isEqualTo(2L);
        }

        @Test
        @DisplayName("存在しないメールは empty を返す")
        void unknownEmail_returnsEmpty() {
            when(userMapper.selectByEmail("unknown@example.com")).thenReturn(Optional.empty());

            assertThat(service.authenticate("unknown@example.com", "pass")).isEmpty();
        }

        @Test
        @DisplayName("パスワード不一致は empty を返す")
        void wrongPassword_returnsEmpty() {
            User user = activeUser(2L, "$stored$");
            when(userMapper.selectByEmail("user@example.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("wrongpass", "$stored$")).thenReturn(false);

            assertThat(service.authenticate("user@example.com", "wrongpass")).isEmpty();
        }

        @Test
        @DisplayName("無効ユーザー（isActive=false）は empty を返す")
        void inactiveUser_returnsEmpty() {
            User user = User.builder()
                    .userId(2L).password("$stored$").isActive(false).build();
            when(userMapper.selectByEmail("user@example.com")).thenReturn(Optional.of(user));

            assertThat(service.authenticate("user@example.com", "pass")).isEmpty();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ヘルパー
    // ─────────────────────────────────────────────────────────────────────────

    private User activeUser(Long id, String encodedPassword) {
        return User.builder()
                .userId(id)
                .email("user" + id + "@example.com")
                .password(encodedPassword)
                .userRole(UserRole.USER)
                .isActive(true)
                .canApproveAttendance(false)
                .build();
    }
}
