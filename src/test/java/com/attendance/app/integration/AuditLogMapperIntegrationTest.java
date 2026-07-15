package com.attendance.app.integration;

import com.attendance.app.entity.AuditEventType;
import com.attendance.app.entity.AuditLog;
import com.attendance.app.mapper.AuditLogMapper;
import com.attendance.app.mapper.UserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = com.attendance.app.AttendanceApplication.class)
@ActiveProfiles("integration")
@Transactional
@DisplayName("AuditLogMapper Integration")
class AuditLogMapperIntegrationTest {

    @Autowired
    private AuditLogMapper auditLogMapper;

    @Autowired
    private UserMapper userMapper;

    @Test
    @DisplayName("監査イベントを追記して同じ内容を取得できる")
    void insertAndFind_roundTrip() {
        Long userId = userMapper.selectByEmail("admin@example.com").orElseThrow().getUserId();
        Instant createdAt = Instant.now();
        AuditLog auditLog = AuditLog.builder()
                .eventType(AuditEventType.USER_UPDATED)
                .actorUserId(userId)
                .targetUserId(userId)
                .targetType("USER")
                .targetId(userId)
                .description("integration-test")
                .createdAt(createdAt)
                .build();

        assertThat(auditLogMapper.insert(auditLog)).isEqualTo(1);
        assertThat(auditLog.getLogId()).isNotNull();

        AuditLog selected = auditLogMapper.findById(auditLog.getLogId());
        assertThat(selected).isNotNull();
        assertThat(selected.getEventType()).isEqualTo(AuditEventType.USER_UPDATED);
        assertThat(selected.getActorUserId()).isEqualTo(userId);
        assertThat(selected.getDescription()).isEqualTo("integration-test");
    }
}
