package com.attendance.app.security;

import com.attendance.app.entity.User;
import com.attendance.app.entity.UserRole;
import com.attendance.app.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CustomUserDetailsService")
class CustomUserDetailsServiceTest {

    @Mock
    private UserService userService;

    private CustomUserDetailsService service;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        service = new CustomUserDetailsService(userService);
    }

    @Test
    @DisplayName("存在しないメールアドレスの場合はUsernameNotFoundExceptionを投げる")
    void loadUserByUsername_userNotFound_throwsException() {
        when(userService.getUserByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("nobody@example.com"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    @DisplayName("無効化されたユーザーの場合はUsernameNotFoundExceptionを投げる")
    void loadUserByUsername_inactiveUser_throwsException() {
        User user = User.builder()
                .userId(1L)
                .email("inactive@example.com")
                .password("hashed")
                .userRole(UserRole.USER)
                .isActive(false)
                .build();
        when(userService.getUserByEmail("inactive@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.loadUserByUsername("inactive@example.com"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    @DisplayName("永久ロック中の一般ユーザーはaccountLocked=trueとなる")
    void loadUserByUsername_permanentlyLockedUser_isLocked() {
        User user = User.builder()
                .userId(2L)
                .email("locked@example.com")
                .password("hashed")
                .userRole(UserRole.USER)
                .isActive(true)
                .accountLocked(true)
                .build();
        when(userService.getUserByEmail("locked@example.com")).thenReturn(Optional.of(user));

        UserDetails details = service.loadUserByUsername("locked@example.com");

        assertThat(details.isAccountNonLocked()).isFalse();
    }

    @Test
    @DisplayName("一時ロック期限内の一般ユーザーはaccountLocked=trueとなる")
    void loadUserByUsername_tempLockedUser_isLocked() {
        User user = User.builder()
                .userId(3L)
                .email("templocked@example.com")
                .password("hashed")
                .userRole(UserRole.USER)
                .isActive(true)
                .accountLocked(false)
                .lockedUntil(Instant.now().plusSeconds(600))
                .build();
        when(userService.getUserByEmail("templocked@example.com")).thenReturn(Optional.of(user));

        UserDetails details = service.loadUserByUsername("templocked@example.com");

        assertThat(details.isAccountNonLocked()).isFalse();
    }

    @Test
    @DisplayName("一時ロック期限が過ぎている一般ユーザーはaccountLocked=falseとなる")
    void loadUserByUsername_expiredTempLock_isNotLocked() {
        User user = User.builder()
                .userId(4L)
                .email("expired@example.com")
                .password("hashed")
                .userRole(UserRole.USER)
                .isActive(true)
                .accountLocked(false)
                .lockedUntil(Instant.now().minusSeconds(600))
                .build();
        when(userService.getUserByEmail("expired@example.com")).thenReturn(Optional.of(user));

        UserDetails details = service.loadUserByUsername("expired@example.com");

        assertThat(details.isAccountNonLocked()).isTrue();
    }

    @Test
    @DisplayName("管理者も一時ロック期間中はロック扱いとなる")
    void loadUserByUsername_adminUser_tempLockEnforced() {
        User user = User.builder()
                .userId(5L)
                .email("admin@example.com")
                .password("hashed")
                .userRole(UserRole.ADMIN)
                .isActive(true)
                .accountLocked(false)
                .lockedUntil(Instant.now().plusSeconds(600))
                .build();
        when(userService.getUserByEmail("admin@example.com")).thenReturn(Optional.of(user));

        UserDetails details = service.loadUserByUsername("admin@example.com");

        assertThat(details.isAccountNonLocked()).isFalse();
    }

    @Test
    @DisplayName("正常な一般ユーザーはROLE_USER権限を持ちロックされない")
    void loadUserByUsername_normalUser_returnsExpectedAuthorities() {
        User user = User.builder()
                .userId(6L)
                .email("user@example.com")
                .password("hashed")
                .userRole(UserRole.USER)
                .isActive(true)
                .accountLocked(false)
                .build();
        when(userService.getUserByEmail("user@example.com")).thenReturn(Optional.of(user));

        UserDetails details = service.loadUserByUsername("user@example.com");

        assertThat(details.getUsername()).isEqualTo("user@example.com");
        assertThat(details.getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_USER");
        assertThat(details.isAccountNonLocked()).isTrue();
    }
}
