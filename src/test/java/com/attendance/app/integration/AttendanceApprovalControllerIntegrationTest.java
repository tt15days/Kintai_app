package com.attendance.app.integration;

import com.attendance.app.entity.AttendanceSubmission;
import com.attendance.app.mapper.AttendanceSubmissionMapper;
import com.attendance.app.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest(classes = com.attendance.app.AttendanceApplication.class)
@ActiveProfiles("integration")
@Transactional
@DisplayName("AttendanceApprovalController Integration")
class AttendanceApprovalControllerIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @Autowired
    private AttendanceSubmissionMapper attendanceSubmissionMapper;

    @Autowired
    private UserMapper userMapper;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context)
                .apply(springSecurity())
                .build();
    }

    @Test
    @WithMockUser(username = "test@example.com", roles = "USER")
    @DisplayName("GET /attendance/approval: 承認一覧画面が表示される")
    void getPendingApprovals_returnsView() throws Exception {
        mockMvc.perform(get("/attendance/approval"))
                .andExpect(status().isOk())
                .andExpect(view().name("user/attendance-approval"))
                .andExpect(model().attributeExists("pendingSubmissions"));
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("POST /attendance/approval/{id}/approve: 承認で状態更新される")
    void postApprove_updatesSubmissionStatus() throws Exception {
        Long applicantUserId = userMapper.selectByEmail("user@example.com").orElseThrow().getUserId();
        Long adminUserId = userMapper.selectByEmail("admin@example.com").orElseThrow().getUserId();

        AttendanceSubmission submission = AttendanceSubmission.builder()
                .userId(applicantUserId)
                .targetYearMonth("2099-11")
                .status("PENDING")
                .submittedAt(Instant.now())
                .build();
        attendanceSubmissionMapper.insert(submission);

        mockMvc.perform(post("/attendance/approval/{submissionId}/approve", submission.getSubmissionId())
                        .with(csrf())
                        .param("comment", "integration-approve"))
                .andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:/attendance/approval"))
                .andExpect(flash().attribute("message", "勤怠申請を承認しました"));

        AttendanceSubmission approved = attendanceSubmissionMapper.selectById(submission.getSubmissionId()).orElseThrow();
        assertThat(approved.getStatus()).isEqualTo("APPROVED");
        assertThat(approved.getActionBy()).isEqualTo(adminUserId);
        assertThat(approved.getActionComment()).isEqualTo("integration-approve");
    }
}
