package com.attendance.app.integration;

import com.attendance.app.entity.User;
import com.attendance.app.entity.UserRole;
import com.attendance.app.mapper.UserMapper;
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
@DisplayName("UserMapper Integration")
class UserMapperIntegrationTest {

    @Autowired
    private UserMapper userMapper;

    @Test
    @DisplayName("insert -> selectByEmail -> softDelete の往復ができる")
    void insertSelectAndSoftDelete_roundTrip() {
        String email = "it-user-" + UUID.randomUUID() + "@example.com";
        User user = User.builder()
                .empNo("IT-" + UUID.randomUUID().toString().substring(0, 8))
                .email(email)
                .password("$2a$10$testtesttesttesttesttesttesttesttesttesttesttesttest")
                .passwordResetRequired(false)
                .fullName("IT User")
                .userRole(UserRole.USER)
                .canApproveAttendance(false)
                .isActive(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        int inserted = userMapper.insert(user);
        assertThat(inserted).isEqualTo(1);
        assertThat(user.getUserId()).isNotNull();

        User selected = userMapper.selectByEmail(email).orElseThrow();
        assertThat(selected.getEmail()).isEqualTo(email);
        assertThat(userMapper.existsByEmail(email)).isTrue();

        int updated = userMapper.softDeleteById(user.getUserId());
        assertThat(updated).isEqualTo(1);

        User softDeleted = userMapper.selectById(user.getUserId()).orElseThrow();
        assertThat(softDeleted.getIsActive()).isFalse();
    }
}
