package com.attendance.app.integration;

import com.attendance.app.mapper.SystemSettingMapper;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest(classes = com.attendance.app.AttendanceApplication.class)
@ActiveProfiles("integration")
@Transactional
@DisplayName("SettingsController Integration Tests")
class SettingsControllerIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @Autowired
    private SystemSettingMapper systemSettingMapper;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context)
                .apply(springSecurity())
                .build();
    }

    @Test
    @DisplayName("ゲスト（未ログイン）が設定画面へアクセスした際、ログイン画面にリダイレクトされること")
    void guestAccessToSettings_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/admin/settings"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @WithMockUser(username = "user@example.com", roles = "USER")
    @DisplayName("USER権限のみを持つユーザーが設定画面へアクセスした際、403 Forbidden になること")
    void userAccessToSettings_isForbidden() throws Exception {
        mockMvc.perform(get("/admin/settings"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("ADMIN権限を持つユーザーが設定画面へ正常にアクセスできること")
    void adminAccessToSettings_isOk() throws Exception {
        mockMvc.perform(get("/admin/settings"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/settings"))
                .andExpect(model().attributeExists("paidLeaveGrantDate"))
                .andExpect(model().attributeExists("copyrightText"))
                .andExpect(model().attributeExists("systemName"))
                .andExpect(model().attributeDoesNotExist("attendancePeriodStartDay"))
                .andExpect(model().attributeExists("attendancePeriodEndDay"))
                .andExpect(model().attributeExists("existingHolidays"))
                .andExpect(model().attributeExists("holidayCount"));
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("ADMIN権限を持つユーザーが設定を正常に更新できること")
    void adminSaveSettings_updatesSettingsAndRedirects() throws Exception {
        // 設定更新前の値を退避（ロールバックされるが、検証用に初期状態を考慮）
        mockMvc.perform(post("/admin/settings")
                        .with(csrf())
                        .param("paidLeaveGrantDate", "08-15"))
                .andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:/admin/settings"))
                .andExpect(flash().attribute("message", "システム設定を更新しました。"));

        // 更新した設定値がDBに反映されているか検証
        String date = systemSettingMapper.selectValueByKey("PAID_LEAVE_GRANT_DATE");

        assertThat(date).isEqualTo("08-15");
    }
}
