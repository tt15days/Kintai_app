package com.attendance.app.integration;

import com.attendance.app.entity.LeaveApplication;
import com.attendance.app.entity.LeaveStatus;
import com.attendance.app.entity.LeaveType;
import com.attendance.app.mapper.LeaveApplicationMapper;
import com.attendance.app.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;

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
@DisplayName("LeaveApplicationController Integration")
class LeaveApplicationControllerIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private LeaveApplicationMapper leaveApplicationMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context)
                .apply(springSecurity())
                .build();
    }

    @Test
    @WithMockUser(username = "user@example.com", roles = "USER")
    @DisplayName("GET /leave: 一覧画面とモデルが返る")
    void getLeaveList_returnsViewAndModel() throws Exception {
        mockMvc.perform(get("/leave"))
                .andExpect(status().isOk())
                .andExpect(view().name("user/leave-list"))
                .andExpect(model().attributeExists("applications"))
                .andExpect(model().attributeExists("lockedApplicationIds"));
    }

    @Test
    @WithMockUser(username = "user@example.com", roles = "USER")
    @DisplayName("POST /leave/apply: 申請成功で即時承認され、勤怠画面へリダイレクト")
    void postApply_createsApprovedLeaveAndRedirects() throws Exception {
        Long userId = userMapper.selectByEmail("user@example.com").orElseThrow().getUserId();
        LocalDate date = LocalDate.of(2099, 12, 10);

        mockMvc.perform(post("/leave/apply")
                        .with(csrf())
                        .param("date", date.toString())
                        .param("leaveType", LeaveType.SPECIAL_LEAVE.name())
                        .param("yearMonth", "2099-12")
                        .param("remarks", "integration-test"))
                .andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:/attendance?yearMonth=2099-12"))
                .andExpect(flash().attribute("message", "特別休暇を申請しました"));

        List<LeaveApplication> apps = leaveApplicationMapper.selectByUserAndDateRange(userId, date, date);
        LeaveApplication created = apps.stream()
                .filter(a -> a.getLeaveType() == LeaveType.SPECIAL_LEAVE)
                .findFirst()
                .orElseThrow();

        assertThat(created.getStatus()).isEqualTo(LeaveStatus.APPROVED);
    }

    @Test
    @WithMockUser(username = "user@example.com", roles = "USER")
    @DisplayName("POST /leave/delete/{id}: 休暇申請の論理削除の検証")
    void postDelete_softDeletesApplication() throws Exception {
        Long userId = userMapper.selectByEmail("user@example.com").orElseThrow().getUserId();
        LocalDate date = LocalDate.of(2099, 12, 20);

        // 1. テストデータのインサート
        LeaveApplication app = LeaveApplication.builder()
                .userId(userId)
                .leaveStartDate(date)
                .leaveEndDate(date)
                .leaveDurationType("FULL_DAY")
                .leaveType(LeaveType.SPECIAL_LEAVE)
                .reason("integration-delete-test")
                .status(LeaveStatus.PENDING)
                .build();
        leaveApplicationMapper.insert(app);
        Long appId = app.getApplicationId();
        assertThat(appId).isNotNull();

        // 2. 削除エンドポイントの呼び出し
        mockMvc.perform(post("/leave/delete/" + appId)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:/leave"))
                .andExpect(flash().attribute("message", "休暇申請を削除しました"));

        // 3. 論理削除されたため selectById では取得できないことを検証
        assertThat(leaveApplicationMapper.selectById(appId)).isEmpty();

        // 4. DBにはデータが残っており is_deleted = true になっていることを検証
        Boolean isDeleted = jdbcTemplate.queryForObject(
                "SELECT is_deleted FROM leave_applications WHERE application_id = ?",
                Boolean.class,
                appId
        );
        assertThat(isDeleted).isTrue();
    }
}
