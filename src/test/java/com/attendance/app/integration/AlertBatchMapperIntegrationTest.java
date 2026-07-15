package com.attendance.app.integration;

import com.attendance.app.dto.Article36AlertDto;
import com.attendance.app.dto.PaidLeaveAlertDto;
import com.attendance.app.mapper.AlertBatchMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = com.attendance.app.AttendanceApplication.class)
@ActiveProfiles("integration")
@Transactional
@DisplayName("AlertBatchMapper Integration")
class AlertBatchMapperIntegrationTest {

    @Autowired
    private AlertBatchMapper alertBatchMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("36協定・有休消化アラートは有効かつ未削除のユーザーだけを抽出する")
    void alertQueries_onlyReturnActiveAndNotDeletedUsers() {
        Long activeUserId = Objects.requireNonNull(insertUser(true, null));
        Long inactiveUserId = insertUser(false, null);
        Long deletedUserId = insertUser(true, OffsetDateTime.now(ZoneOffset.UTC));
        List<Long> excludedUserIds = List.of(inactiveUserId, deletedUserId);

        LocalDate attendanceDate = LocalDate.of(2099, 9, 1);
        for (Long userId : List.of(activeUserId, inactiveUserId, deletedUserId)) {
            jdbcTemplate.update("""
                    INSERT INTO attendance_records (user_id, attendance_date, overtime_hours)
                    VALUES (?, ?, 40.0)
                    """, userId, attendanceDate.atStartOfDay(ZoneId.of("Asia/Tokyo")).toOffsetDateTime());
            jdbcTemplate.update("""
                    INSERT INTO paid_leave_balance
                        (user_id, grant_year, granted_days, grant_date, expiry_date, carried_over_days, used_days)
                    VALUES (?, 2099, 10.0, DATE '2099-01-01', DATE '2101-01-01', 0.0, 0.0)
                    """, userId);
        }

        List<Article36AlertDto> article36Alerts = alertBatchMapper.findUsersExceedingOvertimeLimit(
                attendanceDate, attendanceDate, 30, 0, 100);
        List<PaidLeaveAlertDto> paidLeaveAlerts = alertBatchMapper.findUsersWithInsufficientPaidLeave(
                9, 3, LocalDate.of(2099, 10, 1), 0, 100);

        assertThat(article36Alerts).extracting(dto -> dto == null ? null : dto.getUserId())
                .contains(activeUserId)
                .doesNotContainAnyElementsOf(excludedUserIds);
        assertThat(paidLeaveAlerts).extracting(dto -> dto == null ? null : dto.getUserId())
                .contains(activeUserId)
                .doesNotContainAnyElementsOf(excludedUserIds);
    }

    private Long insertUser(boolean active, OffsetDateTime deletedAt) {
        String suffix = UUID.randomUUID().toString();
        return jdbcTemplate.queryForObject("""
                INSERT INTO users (email, password, full_name, user_role, is_active, emp_no, deleted_at)
                VALUES (?, 'test-password', 'Alert Mapper Test', 'USER', ?, ?, ?)
                RETURNING user_id
                """, Long.class, "alert-" + suffix + "@example.com", active, "ALERT-" + suffix, deletedAt);
    }
}
