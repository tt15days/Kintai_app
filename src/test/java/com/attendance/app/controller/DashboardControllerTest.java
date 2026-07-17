package com.attendance.app.controller;

import com.attendance.app.entity.AttendanceRecord;
import com.attendance.app.entity.PaidLeaveBalance;
import com.attendance.app.entity.User;
import com.attendance.app.entity.UserNotification;
import com.attendance.app.entity.UserRole;
import com.attendance.app.security.SecurityUtil;
import com.attendance.app.service.AdminAnnouncementService;
import com.attendance.app.service.AttendanceCorrectionRequestService;
import com.attendance.app.service.AttendanceRecordService;
import com.attendance.app.service.AttendanceSubmissionService;
import com.attendance.app.service.PaidLeaveBalanceService;
import com.attendance.app.service.UserNotificationService;
import com.attendance.app.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DashboardController Unit Tests")
class DashboardControllerTest {

    @Mock private UserService userService;
    @Mock private AttendanceRecordService attendanceRecordService;
    @Mock private AttendanceSubmissionService attendanceSubmissionService;
    @Mock private AttendanceCorrectionRequestService correctionRequestService;
    @Mock private SecurityUtil securityUtil;
    @Mock private UserNotificationService userNotificationService;
    @Mock private AdminAnnouncementService adminAnnouncementService;
    @Mock private PaidLeaveBalanceService paidLeaveBalanceService;

    @InjectMocks
    private DashboardController controller;

    @Test
    @DisplayName("showDashboard: パスワードリセット要求中の場合、パスワード変更画面へリダイレクトされること")
    void showDashboard_passwordResetRequired_redirects() {
        // Arrange
        User user = new User();
        user.setUserId(1L);
        user.setPasswordResetRequired(true);
        when(securityUtil.getCurrentUser()).thenReturn(user);

        ExtendedModelMap model = new ExtendedModelMap();

        // Act
        String view = controller.showDashboard(model);

        // Assert
        assertThat(view).isEqualTo("redirect:/dashboard/change-password?required=true");
    }

    @Test
    @DisplayName("showDashboard: 一般ユーザーの場合、正常にダッシュボード情報をモデルに設定して表示すること")
    void showDashboard_regularUser_success() {
        // Arrange
        User user = new User();
        user.setUserId(1L);
        user.setFullName("テスト太郎");
        user.setUserRole(UserRole.USER);
        user.setLastLoginAt(Instant.parse("2026-06-24T00:00:00Z"));
        when(securityUtil.getCurrentUser()).thenReturn(user);

        AttendanceRecord record = new AttendanceRecord();
        record.setWorkingHours(8.0);
        record.setOvertimeHours(1.5);
        record.setNightShiftHours(0.0);
        record.setStartTime(Instant.now());
        record.setAttendanceDate(Instant.now());

        when(attendanceRecordService.getRecordsByUserAndMonth(eq(1L), any(YearMonth.class)))
                .thenReturn(List.of(record));
        when(attendanceRecordService.resolveOvertimeHours(record)).thenReturn(1.5);
        when(paidLeaveBalanceService.getTotalRemainingDays(1L)).thenReturn(new BigDecimal("15.5"));

        PaidLeaveBalance balance = new PaidLeaveBalance();
        balance.setGrantedDays(new BigDecimal("10"));
        balance.setCarriedOverDays(new BigDecimal("5"));
        balance.setUsedDays(new BigDecimal("2"));
        when(paidLeaveBalanceService.getActiveBalances(1L)).thenReturn(List.of(balance));

        when(userService.isAttendanceApprover(user)).thenReturn(false);
        when(userNotificationService.getUnreadByUserId(1L)).thenReturn(Collections.emptyList());
        when(adminAnnouncementService.getActiveAnnouncements()).thenReturn(Collections.emptyList());

        ExtendedModelMap model = new ExtendedModelMap();

        // Act
        String view = controller.showDashboard(model);

        // Assert
        assertThat(view).isEqualTo("dashboard");
        assertThat(model.get("currentUser")).isEqualTo(user);
        assertThat(model.get("totalHoursThisMonth")).isEqualTo("8.0");
        assertThat(model.get("overtimeHours")).isEqualTo("1.5");
        assertThat(model.get("nightShiftHours")).isEqualTo("0.0");
        assertThat(model.get("remainingPaidLeave")).isEqualTo("15.5");
        assertThat(model.get("attendanceDays")).isEqualTo(1L);
        assertThat(model.get("grantedPaidLeave")).isEqualTo("15");
        assertThat(model.get("usedPaidLeave")).isEqualTo("2");
        assertThat(model.get("canApproveAttendance")).isEqualTo(false);
        assertThat(model.get("pendingSubmissionsCount")).isNull(); // 一般ユーザーには表示されない
    }

    @Test
    @DisplayName("showDashboard: 最終ログインが過去でも日本時間の本日を表示して当日レコードを選択すること")
    void showDashboard_pastLastLogin_usesTodayInJapan() {
        LocalDate today = com.attendance.app.util.DateTimeUtil.todayJapan();
        User user = new User();
        user.setUserId(1L);
        user.setUserRole(UserRole.USER);
        user.setLastLoginAt(com.attendance.app.util.DateTimeUtil.toInstant(today.minusDays(1)));
        when(securityUtil.getCurrentUser()).thenReturn(user);

        AttendanceRecord previousRecord = new AttendanceRecord();
        previousRecord.setAttendanceDate(com.attendance.app.util.DateTimeUtil.toInstant(today.minusDays(1)));
        AttendanceRecord todayRecord = new AttendanceRecord();
        todayRecord.setAttendanceDate(com.attendance.app.util.DateTimeUtil.toInstant(today));

        when(attendanceRecordService.getRecordsByUserAndMonth(1L, YearMonth.from(today)))
                .thenReturn(List.of(previousRecord, todayRecord));
        when(paidLeaveBalanceService.getTotalRemainingDays(1L)).thenReturn(BigDecimal.ZERO);
        when(paidLeaveBalanceService.getActiveBalances(1L)).thenReturn(Collections.emptyList());
        when(userService.isAttendanceApprover(user)).thenReturn(false);
        when(userNotificationService.getUnreadByUserId(1L)).thenReturn(Collections.emptyList());
        when(adminAnnouncementService.getActiveAnnouncements()).thenReturn(Collections.emptyList());

        ExtendedModelMap model = new ExtendedModelMap();

        assertThat(controller.showDashboard(model)).isEqualTo("dashboard");
        assertThat(model.get("currentMonth")).isEqualTo(YearMonth.from(today));
        assertThat(model.get("todayRecord")).isSameAs(todayRecord);
        assertThat(model.get("todayFormatted")).isEqualTo(today.format(
                java.time.format.DateTimeFormatter.ofPattern("yyyy年M月d日EEEE", java.util.Locale.JAPANESE)));
    }

    @Test
    @DisplayName("showDashboard: 承認者の場合、承認待ち件数をモデルに設定して表示すること")
    void showDashboard_approverUser_success() {
        // Arrange
        User user = new User();
        user.setUserId(2L);
        user.setUserRole(UserRole.ADMIN);
        when(securityUtil.getCurrentUser()).thenReturn(user);

        when(attendanceRecordService.getRecordsByUserAndMonth(eq(2L), any(YearMonth.class)))
                .thenReturn(Collections.emptyList());
        when(paidLeaveBalanceService.getTotalRemainingDays(2L)).thenReturn(BigDecimal.ZERO);
        when(paidLeaveBalanceService.getActiveBalances(2L)).thenReturn(Collections.emptyList());

        when(userService.isAttendanceApprover(user)).thenReturn(true);
        when(attendanceSubmissionService.getPendingSubmissions(user)).thenReturn(List.of(new com.attendance.app.entity.AttendanceSubmission(), new com.attendance.app.entity.AttendanceSubmission()));
        when(correctionRequestService.getPendingRequests(user)).thenReturn(List.of(new com.attendance.app.entity.AttendanceCorrectionRequest()));

        ExtendedModelMap model = new ExtendedModelMap();

        // Act
        String view = controller.showDashboard(model);

        // Assert
        assertThat(view).isEqualTo("dashboard");
        assertThat(model.get("canApproveAttendance")).isEqualTo(true);
        assertThat(model.get("pendingSubmissionsCount")).isEqualTo(2);
        assertThat(model.get("pendingCorrectionsCount")).isEqualTo(1);
    }

    @Test
    @DisplayName("showDashboard: 例外発生時のエラーハンドリング")
    void showDashboard_exception_returnsErrorInModel() {
        // Arrange
        when(securityUtil.getCurrentUser()).thenThrow(new RuntimeException("DB error"));
        ExtendedModelMap model = new ExtendedModelMap();

        // Act
        String view = controller.showDashboard(model);

        // Assert
        assertThat(view).isEqualTo("dashboard");
        assertThat(model.get("error")).isEqualTo("ダッシュボードの表示に失敗しました");
    }

    @Test
    @DisplayName("showChangePasswordForm: パスワード変更画面ビューが返されること")
    void showChangePasswordForm_success() {
        String view = controller.showChangePasswordForm();
        assertThat(view).isEqualTo("user/change-password");
    }

    @Test
    @DisplayName("changePassword: パスワードと確認パスワードが一致しない場合のエラーハンドリング")
    void changePassword_passwordsDoNotMatch_returnsError() {
        ExtendedModelMap model = new ExtendedModelMap();
        String view = controller.changePassword("old", "new", "different", new org.springframework.mock.web.MockHttpSession(), model);

        assertThat(view).isEqualTo("user/change-password");
        assertThat(model.get("error")).isEqualTo("新しいパスワードと確認用パスワードが一致しません");
        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("changePassword: パスワード変更成功でダッシュボードにリダイレクトされること")
    void changePassword_success() {
        when(securityUtil.getCurrentUserId()).thenReturn(1L);
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.changePassword("old", "new", "new", new org.springframework.mock.web.MockHttpSession(), model);

        assertThat(view).isEqualTo("redirect:/dashboard?passwordChanged=true");
        verify(userService).changePassword(eq(1L), eq("old"), eq("new"), any());
    }

    @Test
    @DisplayName("changePassword: IllegalArgumentException 発生時のハンドリング")
    void changePassword_invalidArgs_returnsError() {
        when(securityUtil.getCurrentUserId()).thenReturn(1L);
        doThrow(new IllegalArgumentException("現在のパスワードが正しくありません"))
                .when(userService).changePassword(anyLong(), any(), any(), any());

        ExtendedModelMap model = new ExtendedModelMap();
        String view = controller.changePassword("old", "new", "new", new org.springframework.mock.web.MockHttpSession(), model);

        assertThat(view).isEqualTo("user/change-password");
        assertThat(model.get("error")).isEqualTo("現在のパスワードが正しくありません");
    }

    @Test
    @DisplayName("changePassword: その他例外発生時のハンドリング")
    void changePassword_otherException_returnsGeneralError() {
        when(securityUtil.getCurrentUserId()).thenReturn(1L);
        doThrow(new RuntimeException("Server error"))
                .when(userService).changePassword(anyLong(), any(), any(), any());

        ExtendedModelMap model = new ExtendedModelMap();
        String view = controller.changePassword("old", "new", "new", new org.springframework.mock.web.MockHttpSession(), model);

        assertThat(view).isEqualTo("user/change-password");
        assertThat(model.get("error")).isEqualTo("予期しないエラーが発生しました");
    }

    @Test
    @DisplayName("readAllNotifications: すべての通知を既読にし、ダッシュボードへリダイレクトすること")
    void readAllNotifications_success() {
        when(securityUtil.getCurrentUserId()).thenReturn(1L);
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        String view = controller.readAllNotifications(redirectAttributes);

        assertThat(view).isEqualTo("redirect:/dashboard");
        verify(userNotificationService).markAllAsRead(1L);
    }

    @Test
    @DisplayName("getUnreadNotifications: 未読通知一覧をJSONで取得できること")
    void getUnreadNotifications_success() {
        when(securityUtil.getCurrentUserId()).thenReturn(1L);
        UserNotification notification = new UserNotification();
        when(userNotificationService.getUnreadByUserId(1L)).thenReturn(List.of(notification));

        List<UserNotification> result = controller.getUnreadNotifications();

        assertThat(result).hasSize(1).contains(notification);
    }

    @Test
    @DisplayName("getUnreadNotifications: 例外発生時に空のリストを返すこと")
    void getUnreadNotifications_exception_returnsEmptyList() {
        when(securityUtil.getCurrentUserId()).thenThrow(new RuntimeException("DB error"));

        List<UserNotification> result = controller.getUnreadNotifications();

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("readNotification: 個別通知を既読にし、200 OKを返すこと")
    void readNotification_success() {
        when(securityUtil.getCurrentUserId()).thenReturn(1L);

        ResponseEntity<Void> response = controller.readNotification(100L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(userNotificationService).markAsRead(100L, 1L);
    }

    @Test
    @DisplayName("readNotification: 例外発生時に500エラーを返すこと")
    void readNotification_exception_returns500() {
        when(securityUtil.getCurrentUserId()).thenThrow(new RuntimeException("DB error"));

        ResponseEntity<Void> response = controller.readNotification(100L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    @DisplayName("analyzeAttendance: 勤務時間・残業時間に応じたAI健康アドバイスが生成されること")
    void analyzeAttendance_success() {
        // Arrange
        User user = new User();
        user.setUserId(1L);
        when(securityUtil.getCurrentUser()).thenReturn(user);

        AttendanceRecord record = new AttendanceRecord();
        record.setWorkingHours(160.0);
        record.setOvertimeHours(35.0); // 残業過多
        record.setNightShiftHours(2.0); // 深夜あり

        when(attendanceRecordService.getRecordsByUserAndMonth(eq(1L), any(YearMonth.class)))
                .thenReturn(List.of(record));
        when(attendanceRecordService.resolveOvertimeHours(record)).thenReturn(35.0);
        when(paidLeaveBalanceService.getTotalRemainingDays(1L)).thenReturn(new BigDecimal("18.0")); // 有休あり

        // Act
        ResponseEntity<Map<String, String>> response = controller.analyzeAttendance();

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, String> body = response.getBody();
        assertThat(body).containsKey("advice");
        String advice = body.get("advice");
        assertThat(advice).contains("残業過多のリスクがあります");
        assertThat(advice).contains("深夜勤務に関して");
        assertThat(advice).contains("有給休暇の取得推奨");
    }

    @Test
    @DisplayName("analyzeAttendance: 健康的な勤務傾向のAIアドバイスが生成されること")
    void analyzeAttendance_healthy_success() {
        // Arrange
        User user = new User();
        user.setUserId(1L);
        when(securityUtil.getCurrentUser()).thenReturn(user);

        AttendanceRecord record = new AttendanceRecord();
        record.setWorkingHours(140.0);
        record.setOvertimeHours(0.0);
        record.setNightShiftHours(0.0);

        when(attendanceRecordService.getRecordsByUserAndMonth(eq(1L), any(YearMonth.class)))
                .thenReturn(List.of(record));
        when(paidLeaveBalanceService.getTotalRemainingDays(1L)).thenReturn(BigDecimal.ZERO);

        // Act
        ResponseEntity<Map<String, String>> response = controller.analyzeAttendance();

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, String> body = response.getBody();
        String advice = body.get("advice");
        assertThat(advice).contains("素晴らしい勤務傾向です");
    }

    @Test
    @DisplayName("analyzeAttendance: 例外発生時に500エラーを返すこと")
    void analyzeAttendance_exception_returns500() {
        when(securityUtil.getCurrentUser()).thenThrow(new RuntimeException("DB error"));

        ResponseEntity<Map<String, String>> response = controller.analyzeAttendance();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().get("error")).isEqualTo("診断アドバイスの生成中にエラーが発生しました。");
    }

    @Test
    @DisplayName("showDashboard: workingHoursとovertimeHoursがnullの場合、開始/終了時刻から計算してモデルに設定すること")
    void showDashboard_nullWorkingHours_fallbackCalculation() {
        // Arrange
        User user = new User();
        user.setUserId(1L);
        user.setUserRole(UserRole.USER);
        when(securityUtil.getCurrentUser()).thenReturn(user);

        AttendanceRecord record = new AttendanceRecord();
        record.setUserId(1L);
        LocalDate date = LocalDate.of(2026, 6, 15);
        record.setAttendanceDate(com.attendance.app.util.DateTimeUtil.toInstant(date));
        record.setStartTime(com.attendance.app.util.DateTimeUtil.toInstant(date, LocalTime.of(9, 0)));
        record.setEndTime(com.attendance.app.util.DateTimeUtil.toInstant(date, LocalTime.of(19, 30)));
        record.setWorkingHours(null);
        record.setOvertimeHours(null);
        record.setNightShiftHours(null);

        when(attendanceRecordService.getRecordsByUserAndMonth(eq(1L), any(YearMonth.class)))
                .thenReturn(List.of(record));
        when(attendanceRecordService.resolveOvertimeHours(record)).thenReturn(1.5);
        when(paidLeaveBalanceService.getTotalRemainingDays(1L)).thenReturn(new BigDecimal("15.5"));

        PaidLeaveBalance balance = new PaidLeaveBalance();
        balance.setGrantedDays(new BigDecimal("10"));
        balance.setCarriedOverDays(new BigDecimal("5"));
        balance.setUsedDays(new BigDecimal("2"));
        when(paidLeaveBalanceService.getActiveBalances(1L)).thenReturn(List.of(balance));

        when(userService.isAttendanceApprover(user)).thenReturn(false);
        when(userNotificationService.getUnreadByUserId(1L)).thenReturn(Collections.emptyList());
        when(adminAnnouncementService.getActiveAnnouncements()).thenReturn(Collections.emptyList());

        ExtendedModelMap model = new ExtendedModelMap();

        // Act
        String view = controller.showDashboard(model);

        // Assert
        assertThat(view).isEqualTo("dashboard");
        assertThat(model.get("totalHoursThisMonth")).isEqualTo("10.5");
        assertThat(model.get("overtimeHours")).isEqualTo("1.5");
        assertThat(model.get("nightShiftHours")).isEqualTo("0.0");
    }
}
