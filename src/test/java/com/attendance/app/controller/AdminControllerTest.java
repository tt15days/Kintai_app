package com.attendance.app.controller;

import com.attendance.app.entity.User;
import com.attendance.app.entity.UserRole;
import com.attendance.app.security.SecurityUtil;
import com.attendance.app.service.AttendanceApproverAssignmentService;
import com.attendance.app.service.AttendancePeriodSettingService;
import com.attendance.app.service.AttendanceRecordService;
import com.attendance.app.service.AttendanceSubmissionService;
import com.attendance.app.service.BatchSchedulerService;
import com.attendance.app.service.BatchSettingService;
import com.attendance.app.service.CsvFilenamePatternService;
import com.attendance.app.service.LeaveApplicationService;
import com.attendance.app.service.ReportService;
import com.attendance.app.service.UserNotificationService;
import com.attendance.app.service.UserService;
import com.attendance.app.service.WorkScheduleClassService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminController")
class AdminControllerTest {

    @Mock
    private UserService userService;
    @Mock
    private AttendanceRecordService attendanceRecordService;
    @Mock
    private AttendanceApproverAssignmentService approverAssignmentService;
    @Mock
    private AttendanceSubmissionService attendanceSubmissionService;
    @Mock
    private LeaveApplicationService leaveApplicationService;
    @Mock
    private SecurityUtil securityUtil;
    @Mock
    private WorkScheduleClassService workScheduleClassService;
    @Mock
    private ReportService reportService;
    @Mock
    private CsvFilenamePatternService csvFilenamePatternService;
    @Mock
    private AttendancePeriodSettingService attendancePeriodSettingService;
    @Mock
    private BatchSettingService batchSettingService;
    @Mock
    private BatchSchedulerService batchSchedulerService;
    @Mock
    private UserNotificationService userNotificationService;

    @InjectMocks
    private AdminController controller;

    @Nested
    @DisplayName("showAdminDashboard")
    class ShowAdminDashboard {

        @Test
        @DisplayName("ダッシュボード表示: 全ユーザー情報を取得してモデルへ設定する")
        void showDashboard_success() {
            User user = User.builder().userId(1L).fullName("管理者").userRole(UserRole.ADMIN).build();
            when(userService.getAllUsers()).thenReturn(List.of(user));
            when(batchSettingService.getLastMonthlySummaryExecutedAt()).thenReturn(null);

            ExtendedModelMap model = new ExtendedModelMap();
            String viewName = controller.showAdminDashboard(model);

            assertThat(viewName).isEqualTo("admin/dashboard");
            assertThat(model.getAttribute("users")).isEqualTo(List.of(user));
            assertThat(model.getAttribute("userCount")).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("createUser")
    class CreateUser {

        @Test
        @DisplayName("ユーザー作成: 正常系 (成功時にダッシュボードへリダイレクト)")
        void createUser_success() {
            when(securityUtil.getCurrentUserId()).thenReturn(1L);

            ExtendedModelMap model = new ExtendedModelMap();
            String viewName = controller.createUser("new@example.com", "pass9999", "新規ユーザー", UserRole.USER, null, model);

            assertThat(viewName).isEqualTo("redirect:/admin/dashboard?success=true");
            verify(userService).createUser("new@example.com", "pass9999", "新規ユーザー", UserRole.USER, null, 1L);
        }

        @Test
        @DisplayName("ユーザー作成: バリデーションエラー時は再表示しエラーを設定")
        void createUser_validationError_returnsCreateForm() {
            when(securityUtil.getCurrentUserId()).thenReturn(1L);
            doThrow(new IllegalArgumentException("重複エラー"))
                    .when(userService)
                    .createUser(anyString(), anyString(), anyString(), any(UserRole.class), any(), anyLong());

            ExtendedModelMap model = new ExtendedModelMap();
            String viewName = controller.createUser("dup@example.com", "pass9999", "重複", UserRole.USER, null, model);

            assertThat(viewName).isEqualTo("admin/user-create");
            assertThat(model.getAttribute("error")).isEqualTo("重複エラー");
        }
    }

    @Nested
    @DisplayName("36協定")
    class Article36 {

        @Test
        @DisplayName("showArticle36Dashboard: 月別セルデータをモデルへ設定する")
        void showArticle36Dashboard_populatesCellMaps() {
            User user = User.builder().userId(2L).fullName("一般ユーザー").userRole(UserRole.USER).build();
            User tester = User.builder().userId(3L).fullName("テストユーザー").userRole(UserRole.USER).build();
            when(userService.getActiveUsers()).thenReturn(List.of(user, tester));

            when(attendanceRecordService.getOvertimeSumByUserForMonth(YearMonth.of(2026, 4)))
                    .thenReturn(Map.of(2L, 0.0, 3L, 1.5));
            when(attendanceRecordService.getOvertimeSumByUserForMonth(YearMonth.of(2026, 5)))
                    .thenReturn(Map.of(2L, 2.0, 3L, 0.0));
            when(attendanceRecordService.getOvertimeSumByUserForMonth(YearMonth.of(2026, 6)))
                    .thenReturn(Map.of(2L, 40.5, 3L, 0.0));
            when(attendanceRecordService.checkArticle36(0.0)).thenReturn("NORMAL");
            when(attendanceRecordService.checkArticle36(1.5)).thenReturn("NORMAL");
            when(attendanceRecordService.checkArticle36(2.0)).thenReturn("NORMAL");
            when(attendanceRecordService.checkArticle36(40.5)).thenReturn("WARNING");

            ExtendedModelMap model = new ExtendedModelMap();

            String viewName = controller.showArticle36Dashboard("2026-06", model);

            assertThat(viewName).isEqualTo("admin/article36-dashboard");
            assertThat(model.getAttribute("baseMonthKey")).isEqualTo("2026-06");
            assertThat((List<?>) model.getAttribute("monthEntries")).hasSize(3);

            @SuppressWarnings("unchecked")
            Map<String, Double> overtimeCellMap = (Map<String, Double>) model.getAttribute("overtimeCellMap");
            @SuppressWarnings("unchecked")
            Map<String, String> statusCellMap = (Map<String, String>) model.getAttribute("article36StatusCellMap");

            assertThat(overtimeCellMap.get("2026-06_2")).isEqualTo(40.5);
            assertThat(statusCellMap.get("2026-06_2")).isEqualTo("WARNING");
            assertThat(statusCellMap.get("2026-05_3")).isEqualTo("NORMAL");
        }

        @Test
        @DisplayName("notifyArticle36: WARNING状態のユーザーへ通知し対象年月に戻る")
        void notifyArticle36_sendsAlertAndRedirectsBack() {
            User user = User.builder().userId(2L).fullName("一般ユーザー").userRole(UserRole.USER).build();
            when(userService.getUserById(2L)).thenReturn(Optional.of(user));
            when(attendanceRecordService.getOvertimeSumByUserForMonth(YearMonth.of(2026, 6)))
                    .thenReturn(Map.of(2L, 40.5));
            when(attendanceRecordService.checkArticle36(40.5)).thenReturn("WARNING");

            RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

            String viewName = controller.notifyArticle36(2L, "2026-06", redirectAttributes);

            assertThat(viewName).isEqualTo("redirect:/admin/article36?yearMonth=2026-06");
            verify(userNotificationService).notifyArticle36Alert(eq(2L),
                    org.mockito.ArgumentMatchers.contains("40.5時間"));
            assertThat(redirectAttributes.getFlashAttributes().get("successMessage"))
                    .isEqualTo("一般ユーザー さんに36協定アラートを送信しました");
        }

        @Test
        @DisplayName("notifyArticle36: ALERT状態のユーザーへ通知を送信する")
        void notifyArticle36_sendsAlertForAlertStatus() {
            User user = User.builder().userId(2L).fullName("一般ユーザー").userRole(UserRole.USER).build();
            when(userService.getUserById(2L)).thenReturn(Optional.of(user));
            when(attendanceRecordService.getOvertimeSumByUserForMonth(YearMonth.of(2026, 6)))
                    .thenReturn(Map.of(2L, 50.0));
            when(attendanceRecordService.checkArticle36(50.0)).thenReturn("ALERT");

            RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

            String viewName = controller.notifyArticle36(2L, "2026-06", redirectAttributes);

            assertThat(viewName).isEqualTo("redirect:/admin/article36?yearMonth=2026-06");
            verify(userNotificationService).notifyArticle36Alert(eq(2L),
                    org.mockito.ArgumentMatchers.contains("50.0時間"));
            assertThat(redirectAttributes.getFlashAttributes().get("successMessage"))
                    .isEqualTo("一般ユーザー さんに36協定アラートを送信しました");
        }

        @Test
        @DisplayName("notifyArticle36: NORMAL状態（0.0時間）は通知を送らずエラーメッセージを返す")
        void notifyArticle36_rejectsNormalStatus() {
            User user = User.builder().userId(2L).fullName("一般ユーザー").userRole(UserRole.USER).build();
            when(userService.getUserById(2L)).thenReturn(Optional.of(user));
            when(attendanceRecordService.getOvertimeSumByUserForMonth(YearMonth.of(2026, 6)))
                    .thenReturn(Map.of(2L, 0.0));
            when(attendanceRecordService.checkArticle36(0.0)).thenReturn("NORMAL");

            RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

            String viewName = controller.notifyArticle36(2L, "2026-06", redirectAttributes);

            assertThat(viewName).isEqualTo("redirect:/admin/article36?yearMonth=2026-06");
            verify(userNotificationService, never()).notifyArticle36Alert(eq(2L), org.mockito.ArgumentMatchers.any());
            assertThat((String) redirectAttributes.getFlashAttributes().get("errorMessage"))
                    .contains("NORMAL")
                    .contains("0.0時間");
        }
    }
}
