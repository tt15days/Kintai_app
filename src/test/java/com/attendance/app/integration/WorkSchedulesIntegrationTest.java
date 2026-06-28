package com.attendance.app.integration;

import com.attendance.app.entity.WorkScheduleClass;
import com.attendance.app.mapper.WorkScheduleClassMapper;
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

import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = com.attendance.app.AttendanceApplication.class)
@ActiveProfiles("integration")
@Transactional
@DisplayName("勤務クラス設定画面 統合テスト")
class WorkSchedulesIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @Autowired
    private WorkScheduleClassMapper workScheduleClassMapper;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context)
                .apply(springSecurity())
                .build();
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("POST /admin/work-schedules/create: 全ての休憩時間、フォルダ、タグを設定して新規登録できること")
    void createWorkSchedule_withAllParams_success() throws Exception {
        mockMvc.perform(post("/admin/work-schedules/create")
                        .with(csrf())
                        .param("name", "統合テストフルシフト")
                        .param("workLocation", "在宅")
                        .param("address", "東京都千代田区")
                        .param("station", "東京駅")
                        .param("telephone", "03-0000-0000")
                        .param("sectionName", "システム部")
                        .param("folderName", "開発")
                        .param("tags", "テレワーク,夜勤")
                        .param("isActive", "true")
                        .param("maxHours", "12")
                        .param("minHours", "4")
                        .param("startTime", "09:00")
                        .param("endTime", "18:00")
                        .param("breakStartTime", "12:00")
                        .param("breakEndTime", "13:00")
                        .param("breakStartTime2", "15:00")
                        .param("breakEndTime2", "15:15")
                        .param("breakStartTime3", "19:00")
                        .param("breakEndTime3", "19:30")
                        .param("breakStartTime4", "22:00")
                        .param("breakEndTime4", "22:30"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("successMessage", "勤務クラスを作成しました: 統合テストフルシフト"));

        Optional<WorkScheduleClass> opt = workScheduleClassMapper.selectByName("統合テストフルシフト");
        assertThat(opt).isPresent();
        WorkScheduleClass created = opt.get();
        assertThat(created.getWorkLocation()).isEqualTo("在宅");
        assertThat(created.getFolderName()).isEqualTo("開発");
        assertThat(created.getTags()).isEqualTo("テレワーク,夜勤");
        assertThat(created.getBreakStartTime()).isEqualTo(LocalTime.of(12, 0));
        assertThat(created.getBreakStartTime2()).isEqualTo(LocalTime.of(15, 0));
        assertThat(created.getBreakStartTime3()).isEqualTo(LocalTime.of(19, 0));
        assertThat(created.getBreakStartTime4()).isEqualTo(LocalTime.of(22, 0));
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("POST /admin/work-schedules/create: 休憩時間1のみで、2〜4が空欄(NULL)の場合も登録できること")
    void createWorkSchedule_withOnlyBreak1_success() throws Exception {
        mockMvc.perform(post("/admin/work-schedules/create")
                        .with(csrf())
                        .param("name", "休憩1のみシフト")
                        .param("folderName", "一般")
                        .param("tags", "")
                        .param("isActive", "true")
                        .param("startTime", "09:00")
                        .param("endTime", "18:00")
                        .param("breakStartTime", "12:00")
                        .param("breakEndTime", "13:00")
                        .param("breakStartTime2", "")
                        .param("breakEndTime2", "")
                        .param("breakStartTime3", "")
                        .param("breakEndTime3", "")
                        .param("breakStartTime4", "")
                        .param("breakEndTime4", ""))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("successMessage", "勤務クラスを作成しました: 休憩1のみシフト"));

        Optional<WorkScheduleClass> opt = workScheduleClassMapper.selectByName("休憩1のみシフト");
        assertThat(opt).isPresent();
        WorkScheduleClass created = opt.get();
        assertThat(created.getFolderName()).isEqualTo("一般");
        assertThat(created.getTags()).isNull(); // 空文字列はサービス側でNULLに正規化される
        assertThat(created.getBreakStartTime()).isEqualTo(LocalTime.of(12, 0));
        assertThat(created.getBreakStartTime2()).isNull();
        assertThat(created.getBreakStartTime3()).isNull();
        assertThat(created.getBreakStartTime4()).isNull();
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("POST /admin/work-schedules/create: 休憩1が未入力の場合にエラーになること")
    void createWorkSchedule_missingBreak1_fails() throws Exception {
        mockMvc.perform(post("/admin/work-schedules/create")
                        .with(csrf())
                        .param("name", "休憩1欠落シフト")
                        .param("isActive", "true")
                        .param("startTime", "09:00")
                        .param("endTime", "18:00")
                        .param("breakStartTime", "")
                        .param("breakEndTime", ""))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("errorMessage", "休憩1の開始時刻と終了時刻は両方入力してください"));

        Optional<WorkScheduleClass> opt = workScheduleClassMapper.selectByName("休憩1欠落シフト");
        assertThat(opt).isNotPresent();
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("POST /admin/work-schedules/create: 休憩2〜4で片方のみ入力された場合にエラーになること")
    void createWorkSchedule_partialBreakTime_fails() throws Exception {
        mockMvc.perform(post("/admin/work-schedules/create")
                        .with(csrf())
                        .param("name", "休憩不正シフト")
                        .param("isActive", "true")
                        .param("startTime", "09:00")
                        .param("endTime", "18:00")
                        .param("breakStartTime", "12:00")
                        .param("breakEndTime", "13:00")
                        .param("breakStartTime2", "15:00")
                        .param("breakEndTime2", "")) // 終了が空
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("errorMessage", "休憩2の開始時刻と終了時刻は両方入力してください"));

        Optional<WorkScheduleClass> opt = workScheduleClassMapper.selectByName("休憩不正シフト");
        assertThat(opt).isNotPresent();
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    @DisplayName("POST /admin/work-schedules/{classId}/update: 既存クラスの更新が正常に行われること")
    void updateWorkSchedule_success() throws Exception {
        // テスト用のクラスをあらかじめ登録
        WorkScheduleClass target = WorkScheduleClass.builder()
                .name("更新前シフト")
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(18, 0))
                .breakStartTime(LocalTime.of(12, 0))
                .breakEndTime(LocalTime.of(13, 0))
                .isActive(true)
                .build();
        workScheduleClassMapper.insert(target);
        Long classId = target.getClassId();

        mockMvc.perform(post("/admin/work-schedules/" + classId + "/update")
                        .with(csrf())
                        .param("name", "更新後シフト")
                        .param("folderName", "新フォルダ")
                        .param("tags", "新タグ")
                        .param("isActive", "false")
                        .param("startTime", "10:00")
                        .param("endTime", "19:00")
                        .param("breakStartTime", "13:00")
                        .param("breakEndTime", "14:00")
                        .param("breakStartTime2", "")
                        .param("breakEndTime2", "")
                        .param("breakStartTime3", "")
                        .param("breakEndTime3", "")
                        .param("breakStartTime4", "")
                        .param("breakEndTime4", ""))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("successMessage", "勤務クラスを更新しました: 更新後シフト"));

        Optional<WorkScheduleClass> opt = workScheduleClassMapper.selectById(classId);
        assertThat(opt).isPresent();
        WorkScheduleClass updated = opt.get();
        assertThat(updated.getName()).isEqualTo("更新後シフト");
        assertThat(updated.getFolderName()).isEqualTo("新フォルダ");
        assertThat(updated.getTags()).isEqualTo("新タグ");
        assertThat(updated.getIsActive()).isFalse();
        assertThat(updated.getStartTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(updated.getBreakStartTime()).isEqualTo(LocalTime.of(13, 0));
    }
}
