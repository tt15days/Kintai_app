package com.attendance.app.integration;

import com.attendance.app.entity.User;
import com.attendance.app.entity.UserRole;
import com.attendance.app.mapper.UserMapper;
import com.attendance.app.service.LoginAttemptResult;
import com.attendance.app.service.LoginAttemptService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = com.attendance.app.AttendanceApplication.class)
@ActiveProfiles("integration")
@Transactional
@DisplayName("LoginAttemptService Integration")
class LoginAttemptServiceIntegrationTest {

    @Autowired
    private LoginAttemptService loginAttemptService;

    @Autowired
    private UserMapper userMapper;

    @Test
    @DisplayName("3回失敗でTEMP_LOCKED、成功で試行情報をリセットする")
    void failureAndSuccessFlow_updatesDatabaseState() {
        String email = "it-login-" + UUID.randomUUID() + "@example.com";
        User user = User.builder()
                .empNo("IT-" + UUID.randomUUID().toString().substring(0, 8))
                .email(email)
                .password("$2a$10$testtesttesttesttesttesttesttesttesttesttesttesttest")
                .passwordResetRequired(false)
                .fullName("IT Login User")
                .userRole(UserRole.USER)
                .canApproveAttendance(false)
                .isActive(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        userMapper.insert(user);

        assertThat(loginAttemptService.handleFailure(email)).isEqualTo(LoginAttemptResult.NORMAL);
        assertThat(loginAttemptService.handleFailure(email)).isEqualTo(LoginAttemptResult.NORMAL);
        assertThat(loginAttemptService.handleFailure(email)).isEqualTo(LoginAttemptResult.TEMP_LOCKED);

        User locked = userMapper.selectByEmail(email).orElseThrow();
        assertThat(locked.getFailedLoginCount()).isEqualTo(3);
        assertThat(locked.getLockedUntil()).isNotNull();
        assertThat(locked.getAccountLocked()).isFalse();

        loginAttemptService.handleSuccess(email);

        User reset = userMapper.selectByEmail(email).orElseThrow();
        assertThat(reset.getFailedLoginCount()).isEqualTo(0);
        assertThat(reset.getLockedUntil()).isNull();
        assertThat(reset.getAccountLocked()).isFalse();
    }
}
