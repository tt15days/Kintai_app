package com.attendance.app.controller;

import com.attendance.app.entity.AttendanceRecord;
import com.attendance.app.security.SecurityUtil;
import com.attendance.app.service.AdminAnnouncementService;
import com.attendance.app.service.AttendanceCorrectionRequestService;
import com.attendance.app.service.AttendanceRecordService;
import com.attendance.app.service.AttendanceSubmissionService;
import com.attendance.app.service.PaidLeaveBalanceService;
import com.attendance.app.service.UserNotificationService;
import com.attendance.app.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

import com.attendance.app.entity.PaidLeaveBalance;
import java.time.LocalDate;
import java.time.Duration;
import java.time.YearMonth;
import java.util.List;

/**
 * User Dashboard Controller - ユーザーダッシュボード
 *
 * 主な責務:
 * - ログイン中のユーザーのダッシュボード表示
 * - パスワード変更画面・処理
 */
@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/dashboard")
@PreAuthorize("isAuthenticated()")
public class DashboardController {

    private static final String DASHBOARD_VIEW = "dashboard";
    private static final String CHANGE_PASSWORD_VIEW = "user/change-password";
    private static final String DASHBOARD_PASSWORD_CHANGED_REDIRECT = "redirect:/dashboard?passwordChanged=true";

    private final UserService userService;
    private final AttendanceRecordService attendanceRecordService;
    private final AttendanceSubmissionService attendanceSubmissionService;
    private final AttendanceCorrectionRequestService correctionRequestService;
    private final SecurityUtil securityUtil;
    private final UserNotificationService userNotificationService;
    private final AdminAnnouncementService adminAnnouncementService;
    private final PaidLeaveBalanceService paidLeaveBalanceService;

    /**
     * ダッシュボード画面を表示します。
     * 
     * ログイン中のユーザーの当月の勤怠情報を表示します。
     *
     * @param model Spring MVC のモデル
     * @return テンプレート名 (dashboard)
     */
    @GetMapping
    public String showDashboard(Model model) {
        try {
            // ログイン中のユーザー情報を取得
            var currentUser = securityUtil.getCurrentUser();
            if (Boolean.TRUE.equals(currentUser.getPasswordResetRequired())) {
                return "redirect:/dashboard/change-password?required=true";
            }

            Long userId = currentUser.getUserId();
            YearMonth currentMonth = YearMonth.now();

            // 勤怠記録取得
            List<AttendanceRecord> records = attendanceRecordService.getRecordsByUserAndMonth(userId, currentMonth);

            // スタッツ集計
            double totalHours = records.stream()
                    .mapToDouble(r -> this.resolveWorkingHours(r))
                    .sum();
            double totalOvertime = records.stream()
                    .mapToDouble(r -> attendanceRecordService.resolveOvertimeHours(r))
                    .sum();
            double totalNightShift = records.stream()
                    .filter(r -> r.getNightShiftHours() != null)
                    .mapToDouble(r -> r.getNightShiftHours())
                    .sum();

            // モデルに属性を追加
            model.addAttribute("currentUser", currentUser);
            model.addAttribute("currentMonth", currentMonth);
            model.addAttribute("records", records);
            model.addAttribute("totalHoursThisMonth", String.format("%.1f", totalHours));
            model.addAttribute("totalHoursThisMonthStr", com.attendance.app.util.DateTimeUtil.formatHoursToHHmm(totalHours));
            model.addAttribute("overtimeHours", String.format("%.1f", totalOvertime));
            model.addAttribute("overtimeHoursStr", com.attendance.app.util.DateTimeUtil.formatHoursToHHmm(totalOvertime));
            model.addAttribute("nightShiftHours", String.format("%.1f", totalNightShift));
            model.addAttribute("nightShiftHoursStr", com.attendance.app.util.DateTimeUtil.formatHoursToHHmm(totalNightShift));
            // 有給休暇日数の取得
            double remainingPaidLeave = paidLeaveBalanceService.getTotalRemainingDays(userId).doubleValue();
            model.addAttribute("remainingPaidLeave", String.format("%.1f", remainingPaidLeave));

            // 出勤実績日数の集計
            long attendanceDays = records.stream()
                    .filter(r -> r.getStartTime() != null)
                    .count();
            model.addAttribute("attendanceDays", attendanceDays);

            // 有給休暇の付与総数と消化日数の集計
            List<PaidLeaveBalance> balances = paidLeaveBalanceService.getActiveBalances(userId);
            double totalGranted = balances.stream()
                    .mapToDouble(b -> b.getGrantedDays().doubleValue()
                            + (b.getCarriedOverDays() != null ? b.getCarriedOverDays().doubleValue() : 0))
                    .sum();
            double totalUsed = balances.stream()
                    .mapToDouble(b -> b.getUsedDays() != null ? b.getUsedDays().doubleValue() : 0)
                    .sum();
            model.addAttribute("grantedPaidLeave", String.format("%.0f", totalGranted));
            model.addAttribute("usedPaidLeave", String.format("%.0f", totalUsed));

            // ログイン時の日付を取得
            LocalDate today = currentUser.getLastLoginAt() != null
                    ? currentUser.getLastLoginAt().atZone(java.time.ZoneId.of("Asia/Tokyo")).toLocalDate()
                    : LocalDate.now(java.time.ZoneId.of("Asia/Tokyo"));

            // 本日の打刻レコードを取得
            AttendanceRecord todayRecord = records.stream()
                    .filter(r -> r.getAttendanceDate() != null && com.attendance.app.util.DateTimeUtil.toLocalDate(r.getAttendanceDate()).equals(today))
                    .findFirst()
                    .orElse(null);
            model.addAttribute("todayRecord", todayRecord);

            // ログイン時の日付をフォーマット（曜日込み）
            String todayFormatted = today
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy年M月d日EEEE", java.util.Locale.JAPANESE));
            model.addAttribute("todayFormatted", todayFormatted);

            boolean isApprover = userService.isAttendanceApprover(currentUser);
            model.addAttribute("canApproveAttendance", isApprover);
            model.addAttribute("notifications",
                    userNotificationService.getUnreadByUserId(userId));
            model.addAttribute("announcements_board",
                    adminAnnouncementService.getActiveAnnouncements());

            // 承認権限を持つユーザーには承認待ち件数を表示
            if (isApprover) {
                int pendingSubmissionsCount = attendanceSubmissionService.getPendingSubmissions(currentUser).size();
                int pendingCorrectionsCount = correctionRequestService.getPendingRequests(currentUser).size();
                model.addAttribute("pendingSubmissionsCount", pendingSubmissionsCount);
                model.addAttribute("pendingCorrectionsCount", pendingCorrectionsCount);
            }

            log.info("ダッシュボードを表示: userId={}, userName={}, role={}",
                    userId, currentUser.getFullName(), currentUser.getUserRole());

        } catch (Exception e) {
            addDashboardViewError(model, e, "ダッシュボードの表示に失敗しました");
        }
        return DASHBOARD_VIEW;
    }

    /**
     * パスワード変更画面を表示します。
     *
     * @return テンプレート名 (user/change-password)
     */
    @GetMapping("/change-password")
    public String showChangePasswordForm() {
        return CHANGE_PASSWORD_VIEW;
    }

    /**
     * パスワード変更を処理します。
     * 
     * 新しいパスワードと確認用パスワードが一致することを確認した上で、
     * UserService に委譲します。
     *
     * @param oldPassword     現在のパスワード
     * @param newPassword     新しいパスワード
     * @param confirmPassword パスワード確認用入力
     * @param model           Spring MVC のモデル
     * @return 成功時はリダイレクト、失敗時はフォーム再表示
     */
    @PostMapping("/change-password")
    public String changePassword(
            @RequestParam String oldPassword,
            @RequestParam String newPassword,
            @RequestParam String confirmPassword,
            Model model) {
        if (!newPassword.equals(confirmPassword)) {
            model.addAttribute("error", "新しいパスワードと確認用パスワードが一致しません");
            return CHANGE_PASSWORD_VIEW;
        }

        try {
            Long userId = securityUtil.getCurrentUserId();
            userService.changePassword(userId, oldPassword, newPassword);
            log.info("パスワードを変更: userId={}", userId);
            return DASHBOARD_PASSWORD_CHANGED_REDIRECT;
        } catch (IllegalArgumentException e) {
            log.warn("パスワード変更に失敗: {}", e.getMessage());
            return changePasswordViewWithError(model, e.getMessage());
        } catch (Exception e) {
            logActionError(e, "パスワード変更に失敗");
            return changePasswordViewWithError(model, "予期しないエラーが発生しました");
        }
    }

    /**
     * ダッシュボード表示時の例外ログ出力とエラーメッセージ設定を行います。
     *
     * @param model       モデル
     * @param e           発生した例外
     * @param userMessage 画面表示用メッセージ
     */
    private void addDashboardViewError(Model model, Exception e, String userMessage) {
        logActionError(e, "ダッシュボード表示に失敗");
        model.addAttribute("error", userMessage);
    }

    /**
     * パスワード変更画面へエラーメッセージ付きで戻します。
     *
     * @param model        モデル
     * @param errorMessage 画面表示用メッセージ
     * @return パスワード変更画面ビュー名
     */
    private String changePasswordViewWithError(Model model, String errorMessage) {
        model.addAttribute("error", errorMessage);
        return CHANGE_PASSWORD_VIEW;
    }

    /**
     * 例外メッセージ付きのエラーログを出力します。
     *
     * @param e          発生した例外
     * @param logMessage ログ用メッセージ
     */
    private void logActionError(Exception e, String logMessage) {
        log.error("{}: {}", logMessage, e.getMessage());
    }

    /**
     * 未読通知を全て既読にします。
     */
    @PostMapping("/notifications/read-all")
    public String readAllNotifications(RedirectAttributes redirectAttributes) {
        try {
            Long userId = securityUtil.getCurrentUserId();
            userNotificationService.markAllAsRead(userId);
        } catch (Exception e) {
            logActionError(e, "通知既読処理に失敗");
        }
        return "redirect:/dashboard";
    }

    /**
     * 未読通知一覧をJSONで取得します。
     */
    @GetMapping("/notifications/unread")
    @ResponseBody
    public List<com.attendance.app.entity.UserNotification> getUnreadNotifications() {
        try {
            Long userId = securityUtil.getCurrentUserId();
            return userNotificationService.getUnreadByUserId(userId);
        } catch (Exception e) {
            logActionError(e, "未読通知一覧の取得に失敗しました");
            return java.util.Collections.emptyList();
        }
    }

    /**
     * 個別通知を既読にします。
     */
    @PostMapping("/notifications/{id}/read")
    @ResponseBody
    public ResponseEntity<Void> readNotification(@PathVariable("id") Long notificationId) {
        try {
            Long userId = securityUtil.getCurrentUserId();
            userNotificationService.markAsRead(notificationId, userId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("通知の既読処理に失敗しました: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 当月の勤務状況を分析し、Gemini風の健康アドバイスを生成してJSONで返します。
     */
    @PostMapping("/analyze")
    @ResponseBody
    public ResponseEntity<java.util.Map<String, String>> analyzeAttendance() {
        try {
            var currentUser = securityUtil.getCurrentUser();
            Long userId = currentUser.getUserId();
            YearMonth currentMonth = YearMonth.now();
            List<AttendanceRecord> records = attendanceRecordService.getRecordsByUserAndMonth(userId, currentMonth);

            double totalHours = records.stream()
                    .mapToDouble(r -> this.resolveWorkingHours(r))
                    .sum();
            double totalOvertime = records.stream()
                    .mapToDouble(r -> attendanceRecordService.resolveOvertimeHours(r))
                    .sum();
            double totalNightShift = records.stream()
                    .filter(r -> r.getNightShiftHours() != null)
                    .mapToDouble(r -> r.getNightShiftHours())
                    .sum();
            double remainingPaidLeave = paidLeaveBalanceService.getTotalRemainingDays(userId).doubleValue();

            StringBuilder advice = new StringBuilder();
            advice.append("<div class='leading-relaxed text-txt-primary'>");
            advice.append(
                    "<p class='text-lg mb-4'><strong>📊 今月の勤務傾向とAIヘルスアドバイス</strong></p>");

            // 残業時間に関する診断
            if (totalOvertime >= 30) {
                advice.append("<p class='mb-3'>⚠️ <strong>残業過多のリスクがあります:</strong> 時間外労働が現在 <strong>")
                        .append(String.format("%.1f", totalOvertime))
                        .append("時間</strong> に達しています。36協定の上限（45時間）を意識し、業務量の調整や他メンバーへのタスク共有を強く推奨します。</p>");
            } else if (totalOvertime > 10) {
                advice.append("<p class='mb-3'>ℹ️ <strong>時間外勤務について:</strong> 現在の時間外労働は <strong>")
                        .append(String.format("%.1f", totalOvertime))
                        .append("時間</strong> です。比較的安定していますが、週後半にかけての疲労蓄積にご注意ください。</p>");
            } else {
                advice.append("<p class='mb-3'>✅ <strong>時間外勤務について:</strong> 残業時間は非常に少なく抑えられています（")
                        .append(String.format("%.1f", totalOvertime)).append("時間）。適切なワークライフバランスが保たれています。</p>");
            }

            // 深夜労働に関する診断
            if (totalNightShift > 0) {
                advice.append("<p class='mb-3'>🌙 <strong>深夜勤務に関して:</strong> 深夜労働が <strong>")
                        .append(String.format("%.1f", totalNightShift))
                        .append("時間</strong> 発生しています。体内時計の乱れや睡眠不足になりやすいため、翌日の朝は緩やかに始動するなどセルフケアを十分に行いましょう。</p>");
            }

            // 有給取得に関する診断
            if (remainingPaidLeave >= 15) {
                advice.append("<p class='mb-3'>📅 <strong>有給休暇の取得推奨:</strong> 有給休暇が <strong>")
                        .append(String.format("%.1f", remainingPaidLeave))
                        .append("日</strong> 残っています。今月〜来月にかけて1日か半日のリフレッシュ休暇を取得し、心身の健康を維持することをお勧めします。</p>");
            } else if (remainingPaidLeave > 0) {
                advice.append("<p class='mb-3'>📅 <strong>有給休暇について:</strong> 残日数は <strong>")
                        .append(String.format("%.1f", remainingPaidLeave))
                        .append("日</strong> です。適度に計画的な取得を続けましょう。</p>");
            }

            // 総労働時間
            if (totalHours > 150) {
                advice.append("<p class='mb-3'>🏃 <strong>総稼働時間について:</strong> 当月の実労働時間は <strong>")
                        .append(String.format("%.1f", totalHours))
                        .append("時間</strong> です。高水準での勤務が続いていますので、週末の休息を大切にし、無理のないセルフペースを保ってください。</p>");
            }

            if (totalOvertime == 0 && totalNightShift == 0 && totalHours <= 150) {
                advice.append(
                        "<p class='mb-3'>✨ <strong>素晴らしい勤務傾向です:</strong> 非常に健康的かつ自律的なタイムマネジメントが行われています。引き続きこのペースを維持し、心身ともに快適な状態を保ちながら安全第一で進めていきましょう！</p>");
            }

            advice.append("</div>");

            java.util.Map<String, String> response = new java.util.HashMap<>();
            response.put("advice", advice.toString());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("AIアドバイス分析に失敗しました", e);
            java.util.Map<String, String> errorResponse = new java.util.HashMap<>();
            errorResponse.put("error", "診断アドバイスの生成中にエラーが発生しました。");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * 既存データ互換のため、workingHours が未設定の場合は開始/終了時刻から算出する。
     */
    private double resolveWorkingHours(AttendanceRecord record) {
        if (record == null) {
            return 0.0;
        }
        if (record.getWorkingHours() != null) {
            return record.getWorkingHours();
        }
        if (record.getStartTime() == null || record.getEndTime() == null) {
            return 0.0;
        }

        long minutes = Duration.between(record.getStartTime(), record.getEndTime()).toMinutes();
        if (minutes <= 0) {
            return 0.0;
        }
        return minutes / 60.0;
    }
}