package com.attendance.app.integration;

import com.attendance.app.entity.AttendanceSubmission;
import com.attendance.app.mapper.AttendanceSubmissionMapper;
import com.attendance.app.mapper.UserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = com.attendance.app.AttendanceApplication.class)
@ActiveProfiles("integration")
@Transactional
@DisplayName("AttendanceSubmissionMapper Integration")
class AttendanceSubmissionMapperIntegrationTest {

    @Autowired
    private AttendanceSubmissionMapper attendanceSubmissionMapper;

    @Autowired
    private UserMapper userMapper;

    @Test
    @DisplayName("insert -> select -> update -> delete の往復ができる")
    void crudRoundTrip_worksAsExpected() {
        Long userId = userMapper.selectByEmail("user@example.com").orElseThrow().getUserId();
        String ym = "2099-12";

        AttendanceSubmission submission = AttendanceSubmission.builder()
                .userId(userId)
                .targetYearMonth(ym)
                .status("PENDING")
                .submittedAt(Instant.now())
                .build();

        int inserted = attendanceSubmissionMapper.insert(submission);
        assertThat(inserted).isEqualTo(1);
        assertThat(submission.getSubmissionId()).isNotNull();

        AttendanceSubmission selected = attendanceSubmissionMapper.selectByUserAndMonth(userId, ym).orElseThrow();
        assertThat(selected.getStatus()).isEqualTo("PENDING");

        selected.setStatus("APPROVED");
        selected.setActionBy(userId);
        selected.setActionComment("integration-test");
        selected.setActionAt(Instant.now());
        int updated = attendanceSubmissionMapper.update(selected);
        assertThat(updated).isEqualTo(1);

        List<AttendanceSubmission> approved = attendanceSubmissionMapper.selectByStatus("APPROVED");
        assertThat(approved).extracting(s -> s.getSubmissionId()).contains(selected.getSubmissionId());

        int deleted = attendanceSubmissionMapper.deleteByUserAndMonth(userId, ym);
        assertThat(deleted).isEqualTo(1);
        assertThat(attendanceSubmissionMapper.selectByUserAndMonth(userId, ym)).isEmpty();
    }
}
