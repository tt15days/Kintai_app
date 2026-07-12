package com.attendance.app.controller;

import com.attendance.app.entity.AttendanceSubmission;
import com.attendance.app.entity.User;
import com.attendance.app.entity.UserRole;
import com.attendance.app.security.SecurityUtil;
import com.attendance.app.service.AlertBatchService;
import com.attendance.app.service.AttendanceApproverAssignmentService;
import com.attendance.app.service.BatchSchedulerService;
import com.attendance.app.service.BatchSettingService;
import com.attendance.app.service.AttendanceRecordService;
import com.attendance.app.service.AttendanceSubmissionService;
import com.attendance.app.service.CsvFilenamePatternService;
import com.attendance.app.service.ReportService;
import com.attendance.app.service.UserService;
import com.attendance.app.service.WorkScheduleClassService;
import com.attendance.app.service.UserNotificationService;
import com.attendance.app.util.DateTimeUtil;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Admin Controller - 管理者機能
 *
 * 主な責務:
 * - 管理者ダッシュボード表示
 * - ユーザーの作成・更新・削除
 * - ユーザーの勤怠情報管理
 */
@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private static final String INVALID_YEAR_MONTH_LOG = "yearMonth形式が不正: {}";
    private static final String ADMIN_DASHBOARD_VIEW = "admin/dashboard";
    private static final String USER_CREATE_VIEW = "admin/user-create";
    private static final String USER_LIST_VIEW = "admin/user-list";
    private static final String USER_DETAIL_VIEW = "admin/user-detail";
    private static final String ATTENDANCE_DASHBOARD_VIEW = "admin/attendance-manage";
    private static final String ADMIN_USERS_REDIRECT = "redirect:/admin/users";
    private static final String ADMIN_DASHBOARD_SUCCESS_REDIRECT = "redirect:/admin/dashboard?success=true";
    private static final String ADMIN_USER_DETAIL_REDIRECT_PREFIX = "redirect:/admin/users/";
    private static final String WORK_SCHEDULES_VIEW = "admin/work-schedules";
    private static final String ADMIN_WORK_SCHEDULES_REDIRECT = "redirect:/admin/work-schedules";
    private static final String ADMIN_NOTIFICATIONS_VIEW = "admin/notifications";
    private static final String ADMIN_NOTIFICATIONS_REDIRECT = "redirect:/admin/notifications";
    private static final String ADMIN_ARTICLE36_VIEW = "admin/article36-dashboard";

    private final UserService userService;
    private final AttendanceRecordService attendanceRecordService;
    private final AttendanceApproverAssignmentService approverAssignmentService;
    private final AttendanceSubmissionService attendanceSubmissionService;
    private final SecurityUtil securityUtil;
    private final WorkScheduleClassService workScheduleClassService;
    private final ReportService reportService;
    private final CsvFilenamePatternService csvFilenamePatternService;
    private final BatchSettingService batchSettingService;
    private final BatchSchedulerService batchSchedulerService;
    private final AlertBatchService alertBatchService;
    private final UserNotificationService userNotificationService;

    /**
     * 管理者ダッシュボードを表示します。
     * 
     * システム内の全ユーザー情報を一覧で表示します。
     *
     * @param model Spring MVC のモデル
     * @return テンプレート名 (admin/dashboard)
     */
    @GetMapping("/dashboard")
    public String showAdminDashboard(Model model) {
        try {
            List<User> users = userService.getAllUsers();
            model.addAttribute("users", users);
            model.addAttribute("userCount", users.size());
            model.addAttribute("lastMonthlySummaryExecutedAt", batchSettingService.getLastMonthlySummaryExecutedAt());
            model.addAttribute("lastAnnualLeaveGrantExecutedAt",
                    batchSettingService.getLastAnnualLeaveGrantExecutedAt());
            model.addAttribute("lastReminderExecutedAt", batchSettingService.getLastReminderExecutedAt());
            model.addAttribute("lastAlertBatchExecutedAt", batchSettingService.getLastAlertBatchExecutedAt());
            log.info("管理者ダッシュボードを表示: userCount={}", users.size());
        } catch (Exception e) {
            handleAdminViewError(model, e, "管理者ダッシュボード表示に失敗", "ダッシュボード表示に失敗しました");
        }
        return ADMIN_DASHBOARD_VIEW;
    }

    /**
     * ユーザー作成フォームを表示します。
     *
     * @param model Spring MVC のモデル
     * @return テンプレート名 (admin/user-create)
     */
    @GetMapping("/users/create")
    public String showCreateUserForm(Model model) {
        model.addAttribute("roles", UserRole.values());
        return USER_CREATE_VIEW;
    }

    /**
     * 新しいユーザーを作成します。
     * 
     * メールアドレスの重複チェック、パスワードのエンコード等を含めて処理します。
     *
     * @param email    ユーザーのメールアドレス
     * @param password ユーザーのパスワード
     * @param fullName ユーザーのフルネーム
     * @param userRole ユーザーのロール
     * @param model    Spring MVC のモデル
     * @return 成功時はリダイレクト、失敗時はフォーム再表示
     */
    @PostMapping("/users/create")
    public String createUser(
            @RequestParam String email,
            @RequestParam String password,
            @RequestParam String fullName,
            @RequestParam UserRole userRole,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(pattern = "yyyy-MM-dd") java.time.LocalDate hireDate,
            Model model) {
        try {
            userService.createUser(email, password, fullName, userRole, hireDate, securityUtil.getCurrentUserId());
            log.info("ユーザーを追加: role={}, hireDate={}", userRole, hireDate);
            return ADMIN_DASHBOARD_SUCCESS_REDIRECT;
        } catch (IllegalArgumentException e) {
            log.warn("ユーザー追加に失敗", e);
            model.addAttribute("error", e.getMessage());
            model.addAttribute("roles", UserRole.values());
            model.addAttribute("email", email);
            model.addAttribute("fullName", fullName);
            model.addAttribute("userRole", userRole);
            model.addAttribute("hireDate", hireDate);
            return USER_CREATE_VIEW;
        }
    }

    /**
     * ユーザー一覧を表示します。
     *
     * @param model Spring MVC のモデル
     * @return テンプレート名 (admin/user-list)
     */
    @GetMapping("/users")
    public String showUserList(Model model) {
        try {
            List<User> users = userService.getAllUsers();
            model.addAttribute("users", users);
            model.addAttribute("userCount", users.size());
            log.info("ユーザー一覧を表示: count={}", users.size());
        } catch (Exception e) {
            handleAdminViewError(model, e, "ユーザー一覧表示に失敗", "ユーザー一覧の表示に失敗しました");
        }
        return USER_LIST_VIEW;
    }

    /**
     * ユーザーの詳細情報を表示します。
     *
     * @param userId 表示対象のユーザーID
     * @param model  Spring MVC のモデル
     * @return テンプレート名 (admin/user-detail)
     */
    @GetMapping("/users/{userId}")
    public String showUserDetail(@PathVariable Long userId, Model model) {
        try {
            User user = userService.getUserById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("ユーザーが見つかりません"));
            model.addAttribute("user", user);
            model.addAttribute("roles", UserRole.values());
            model.addAttribute("workScheduleClasses", workScheduleClassService.getAllClasses());
            List<User> approverCandidates = userService.getAttendanceApproverCandidates();
            Set<Long> assignedUserApproverIds = Set.copyOf(approverAssignmentService.getUserApproverIds(userId));
            Set<Long> assignedDepartmentApproverIds = user.getClassName() == null
                    || user.getClassName().trim().isEmpty()
                            ? Collections.emptySet()
                            : Set.copyOf(approverAssignmentService.getDepartmentApproverIds(user.getClassName()));
            model.addAttribute("approverCandidates", approverCandidates);
            model.addAttribute("assignedUserApproverIds", assignedUserApproverIds);
            model.addAttribute("assignedDepartmentApproverIds", assignedDepartmentApproverIds);
            log.info("ユーザー詳細を表示: userId={}", userId);
            return USER_DETAIL_VIEW;
        } catch (Exception e) {
            logActionError(e, "ユーザー詳細表示に失敗");
            return ADMIN_USERS_REDIRECT + "?error=true";
        }
    }

    /**
     * ユーザーに対する承認者（申請者ごと）を更新します。
     *
     * @param userId             対象ユーザーID
     * @param approverUserIds    承認者となるユーザーのIDリスト
     * @param redirectAttributes リダイレクト先メッセージ
     * @return ユーザー詳細画面へリダイレクト
     */
    @PostMapping("/users/{userId}/approvers/user")
    public String updateUserApprovers(
            @PathVariable Long userId,
            @RequestParam(value = "approverUserIds", required = false) List<Long> approverUserIds,
            RedirectAttributes redirectAttributes) {
        try {
            approverAssignmentService.assignUserApprovers(userId, approverUserIds);
            redirectAttributes.addFlashAttribute("successMessage", "申請者ごとの承認者割当を更新しました");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            log.warn("申請者ごとの承認者割当更新に失敗", e);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "申請者ごとの承認者割当更新に失敗しました");
            logActionError(e, "申請者ごとの承認者割当更新に失敗");
        }
        return ADMIN_USER_DETAIL_REDIRECT_PREFIX + userId;
    }

    /**
     * 部署に対する承認者（部署ごと）を更新します。
     *
     * @param userId             対象ユーザーID（リダイレクト用）
     * @param departmentName     対象の部署名
     * @param approverUserIds    承認者となるユーザーのIDリスト
     * @param redirectAttributes リダイレクト先メッセージ
     * @return ユーザー詳細画面へリダイレクト
     */
    @PostMapping("/users/{userId}/approvers/department")
    public String updateDepartmentApprovers(
            @PathVariable Long userId,
            @RequestParam String departmentName,
            @RequestParam(value = "approverUserIds", required = false) List<Long> approverUserIds,
            RedirectAttributes redirectAttributes) {
        try {
            approverAssignmentService.assignDepartmentApprovers(departmentName, approverUserIds);
            redirectAttributes.addFlashAttribute("successMessage", "部署ごとの承認者割当を更新しました");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            log.warn("部署ごとの承認者割当更新に失敗", e);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "部署ごとの承認者割当更新に失敗しました");
            logActionError(e, "部署ごとの承認者割当更新に失敗");
        }
        return ADMIN_USER_DETAIL_REDIRECT_PREFIX + userId;
    }

    /**
     * ユーザー情報を更新します。
     *
     * @param userId   更新対象のユーザーID
     * @param email    新しいメールアドレス
     * @param fullName 新しいフルネーム
     * @param userRole 新しいロール
     * @return ユーザー一覧へリダイレクト
     */
    @PostMapping("/users/{userId}/update")
    public String updateUser(
            @PathVariable Long userId,
            @RequestParam String email,
            @RequestParam String fullName,
            @RequestParam UserRole userRole,
            @RequestParam(required = false) String positionTitle,
            @RequestParam(required = false) String phoneNumber,
            @RequestParam(required = false) String className,
            @RequestParam(required = false) BigDecimal paidLeaveDays,
            @RequestParam(required = false) String notes,
            @RequestParam(defaultValue = "false") boolean canApproveAttendance,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(pattern = "yyyy-MM-dd") java.time.LocalDate hireDate,
            @RequestParam(defaultValue = "false") boolean isActive,
            RedirectAttributes redirectAttributes) {
        try {
            userService.updateUser(
                    userId,
                    email,
                    fullName,
                    userRole,
                    positionTitle,
                    phoneNumber,
                    className,
                    paidLeaveDays,
                    notes,
                    canApproveAttendance,
                    hireDate,
                    isActive,
                    securityUtil.getCurrentUserId());
            log.info("ユーザー情報を更新: userId={}", userId);
            return ADMIN_USERS_REDIRECT;
        } catch (IllegalArgumentException e) {
            log.warn("ユーザー情報更新に失敗", e);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return ADMIN_USER_DETAIL_REDIRECT_PREFIX + userId;
        }
    }

    /**
     * ユーザーを削除します。
     * 
     * ソフトデリート（論理削除）を行います。
     *
     * @param userId 削除対象のユーザーID
     * @return ユーザー一覧へリダイレクト
     */
    @PostMapping("/users/{userId}/delete")
    public String deleteUser(@PathVariable Long userId, RedirectAttributes redirectAttributes) {
        try {
            userService.deleteUser(userId, securityUtil.getCurrentUserId());
            redirectAttributes.addFlashAttribute("successMessage", "ユーザーを削除しました");
            log.info("ユーザーを削除: userId={}", userId);
        } catch (Exception e) {
            logActionError(e, "ユーザー削除に失敗");
            redirectAttributes.addFlashAttribute("errorMessage", "ユーザーの削除に失敗しました");
        }
        return ADMIN_USERS_REDIRECT;
    }

    /**
     * 管理者がユーザーパスワードを初期化します。
     *
     * @param userId             対象ユーザーID
     * @param redirectAttributes リダイレクト先へ表示するメッセージ
     * @return ユーザー詳細画面へリダイレクト
     */
    @PostMapping("/users/{userId}/reset-password")
    public String resetUserPassword(@PathVariable Long userId, RedirectAttributes redirectAttributes) {
        try {
            String initialPassword = userService.resetPasswordByAdmin(userId, securityUtil.getCurrentUserId());
            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "パスワードを初期化しました。\n初期パスワード: " + initialPassword + "（次回ログイン時に変更が必要です）");
            log.info("管理者がユーザーのパスワードを初期化: userId={}", userId);
        } catch (Exception e) {
            logActionError(e, "パスワード初期化に失敗");
            redirectAttributes.addFlashAttribute("errorMessage", "パスワード初期化に失敗しました");
        }
        return ADMIN_USER_DETAIL_REDIRECT_PREFIX + userId;
    }

    /**
     * 勤怠管理ダッシュボード（管理者用）を表示します。
     * 
     * 全ユーザーの指定年月の申請状況一覧を表示します。
     *
     * @param yearMonth 表示する年月（YYYY-MM形式、省略可）
     * @param model     Spring MVC のモデル
     * @return テンプレート名 (admin/attendance-manage)
     */
    @GetMapping("/attendance")
    public String showAttendanceDashboard(
            @RequestParam(required = false) String yearMonth,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String keyword,
            Model model) {
        try {
            List<User> users = userService.getActiveUsers();
            YearMonth currentMonth = parseYearMonthOrNow(yearMonth);

            // Filter users based on department and keyword
            if (department != null && !department.isEmpty() && !"All Departments".equals(department)) {
                users = users.stream()
                        .filter(u -> department.equals(u.getClassName()))
                        .collect(Collectors.toList());
            }
            if (keyword != null && !keyword.isEmpty()) {
                String lowerKeyword = keyword.toLowerCase();
                users = users.stream()
                        .filter(u -> (u.getFullName() != null && u.getFullName().toLowerCase().contains(lowerKeyword))
                                ||
                                (u.getEmpNo() != null && u.getEmpNo().toLowerCase().contains(lowerKeyword)))
                        .collect(Collectors.toList());
            }

            List<AttendanceSubmission> submissions = attendanceSubmissionService
                    .getSubmissionsByTargetYearMonth(currentMonth);
            Map<Long, AttendanceSubmission> submissionByUserMap = submissions.stream()
                    .collect(Collectors.toMap(s -> s.getUserId(), s -> s));
            Map<Long, User> userMap = users.stream()
                    .collect(Collectors.toMap(u -> u.getUserId(), u -> u));

            model.addAttribute("users", users);
            model.addAttribute("yearMonth", currentMonth);
            model.addAttribute("submissionByUserMap", submissionByUserMap);
            model.addAttribute("submissionCount", submissions.size());
            model.addAttribute("userMap", userMap);
            model.addAttribute("department", department);
            model.addAttribute("keyword", keyword);

            // 部署一覧の取得（フィルタ用）
            List<String> departments = workScheduleClassService.getAllClasses().stream()
                    .map(c -> c.getName())
                    .collect(Collectors.toList());
            model.addAttribute("departments", departments);

            // 36協定チェックおよび各時間のサマリー取得（working/overtime/nightShiftを1回のフェッチで集計）
            List<AttendanceRecordService.MonthlyUserSummary> monthlySummaries =
                    attendanceRecordService.getMonthlyAggregateForAllUsers(currentMonth);
            Map<Long, Double> overtimeSumByUserMap = monthlySummaries.stream()
                    .collect(Collectors.toMap(AttendanceRecordService.MonthlyUserSummary::userId,
                            AttendanceRecordService.MonthlyUserSummary::overtimeHours));
            Map<Long, Double> workingSumByUserMap = monthlySummaries.stream()
                    .collect(Collectors.toMap(AttendanceRecordService.MonthlyUserSummary::userId,
                            AttendanceRecordService.MonthlyUserSummary::workingHours));
            Map<Long, Double> nightShiftSumByUserMap = monthlySummaries.stream()
                    .collect(Collectors.toMap(AttendanceRecordService.MonthlyUserSummary::userId,
                            AttendanceRecordService.MonthlyUserSummary::nightShiftHours));
            Map<Long, String> article36ByUserMap = new HashMap<>();

            for (User u : users) {
                double overtime = overtimeSumByUserMap.getOrDefault(u.getUserId(), 0.0);
                article36ByUserMap.put(u.getUserId(), attendanceRecordService.checkArticle36(overtime));
            }
            model.addAttribute("overtimeSumByUserMap", overtimeSumByUserMap);
            model.addAttribute("workingSumByUserMap", workingSumByUserMap);
            model.addAttribute("nightShiftSumByUserMap", nightShiftSumByUserMap);
            model.addAttribute("article36ByUserMap", article36ByUserMap);
            model.addAttribute("article36MonthlyLimit", batchSettingService.getAlertArticle36Limit2());
            model.addAttribute("article36MonthlyWarning", batchSettingService.getAlertArticle36Limit1());

            model.addAttribute("submissionStatusPending", AttendanceSubmissionService.STATUS_PENDING);
            model.addAttribute("submissionStatusApproved", AttendanceSubmissionService.STATUS_APPROVED);
            model.addAttribute("submissionStatusReturned", AttendanceSubmissionService.STATUS_RETURNED);
            model.addAttribute("submissionStatusWithdrawn", AttendanceSubmissionService.STATUS_WITHDRAWN);
            log.info("勤怠管理ダッシュボード（管理者用）を表示: yearMonth={}", currentMonth);
        } catch (Exception e) {
            handleAdminViewError(model, e, "勤怠管理ダッシュボード表示に失敗", "勤怠管理ダッシュボードの表示に失敗しました");
        }

        return ATTENDANCE_DASHBOARD_VIEW;
    }

    /**
     * 手動で月次集計を実行します。
     *
     * @param yearMonth 集計対象年月（YYYY-MM形式、省略時は前月）
     */
    @PostMapping("/batch/monthly-summary")
    public String runManualMonthlySummary(
            @RequestParam(required = false) String yearMonth,
            RedirectAttributes redirectAttributes) {
        try {
            YearMonth targetMonth = parseYearMonthOrPrevious(yearMonth);
            batchSchedulerService.executeMonthlySummary(targetMonth);
            redirectAttributes.addFlashAttribute("successMessage",
                    targetMonth.getYear() + "年" + targetMonth.getMonthValue() + "月の月次集計を実行しました");
            log.info("手動月次集計を実行: targetMonth={}", targetMonth);
        } catch (Exception e) {
            logActionError(e, "手動月次集計に失敗");
            redirectAttributes.addFlashAttribute("errorMessage", "月次集計の実行に失敗しました");
        }
        return "redirect:/admin/dashboard";
    }

    /**
     * 手動で勤怠提出リマインドを送信します。
     */
    @PostMapping("/batch/reminder")
    public String sendManualReminder(RedirectAttributes redirectAttributes) {
        try {
            batchSchedulerService.executeSubmissionReminder();
            redirectAttributes.addFlashAttribute("successMessage", "勤怠提出リマインドを送信しました");
            log.info("手動リマインド送信を実行");
        } catch (Exception e) {
            logActionError(e, "手動リマインド送信に失敗");
            redirectAttributes.addFlashAttribute("errorMessage", "リマインドの送信に失敗しました");
        }
        return "redirect:/admin/dashboard";
    }

    /**
     * 手動で全従業員への年次有給一括付与を実行します。
     */
    @PostMapping("/batch/annual-leave-grant")
    public String runManualAnnualLeaveGrant(RedirectAttributes redirectAttributes) {
        try {
            BatchSchedulerService.AnnualLeaveGrantResult result = batchSchedulerService.executeAnnualLeaveGrant();
            redirectAttributes.addFlashAttribute("successMessage",
                    "有給一括付与を実行しました（付与: " + result.grantedCount() + "名、"
                            + "スキップ（当年付与済み）: " + result.skippedCount() + "名）");
            log.info("手動有給一括付与を実行: granted={}, skipped={}", result.grantedCount(), result.skippedCount());
        } catch (Exception e) {
            logActionError(e, "手動有給一括付与に失敗");
            redirectAttributes.addFlashAttribute("errorMessage", "有給一括付与の実行に失敗しました");
        }
        return "redirect:/admin/dashboard";
    }

    /**
     * 手動で36協定・有給消化アラートバッチを実行します。
     *
     * @param yearMonth 有給消化アラートの基準年月（YYYY-MM形式、省略時は当月）
     */
    @PostMapping("/batch/alert")
    public String runManualAlertBatch(
            @RequestParam(required = false) String yearMonth,
            RedirectAttributes redirectAttributes) {
        try {
            YearMonth targetMonth = parseYearMonthOrPrevious(yearMonth);
            alertBatchService.runAlertBatchManually(targetMonth);
            redirectAttributes.addFlashAttribute("successMessage", "36協定・有給消化アラートバッチを実行しました");
            log.info("手動アラートバッチを実行: targetMonth={}", targetMonth);
        } catch (Exception e) {
            logActionError(e, "手動アラートバッチに失敗");
            redirectAttributes.addFlashAttribute("errorMessage", "アラートバッチの実行に失敗しました");
        }
        return "redirect:/admin/dashboard";
    }

    /**
     * ユーザー別年次有給設定を更新します。
     */
    @PostMapping("/users/{userId}/leave-settings")
    public String updateLeaveSettings(
            @PathVariable Long userId,
            @RequestParam int annualLeaveGrantDays,
            @RequestParam BigDecimal annualLeaveIncrement,
            @RequestParam int maxPaidLeaveDays,
            RedirectAttributes redirectAttributes) {
        try {
            userService.updatePaidLeaveSettings(userId, annualLeaveGrantDays, annualLeaveIncrement, maxPaidLeaveDays);
            redirectAttributes.addFlashAttribute("successMessage", "年次有給設定を更新しました");
            log.info("ユーザー別有給設定を更新: userId={}, grantDays={}, increment={}, maxDays={}",
                    userId, annualLeaveGrantDays, annualLeaveIncrement, maxPaidLeaveDays);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            log.warn("有給設定の更新に失敗: userId={}, reason={}", userId, e.getMessage(), e);
        } catch (Exception e) {
            logActionError(e, "有給設定の更新に失敗");
            redirectAttributes.addFlashAttribute("errorMessage", "有給設定の更新に失敗しました");
        }
        return ADMIN_USER_DETAIL_REDIRECT_PREFIX + userId;
    }

    /**
     * ユーザーの勤怠記録詳細を表示します。
     * 
     * 新しい共通詳細画面（AttendanceApprovalController）へリダイレクトします。
     *
     * @param userId    対象ユーザーのID
     * @param yearMonth 表示する年月（YYYY-MM形式、省略可）
     * @return 新しい詳細画面へのリダイレクト
     */
    @GetMapping("/attendance/{userId}")
    public String showUserAttendanceRecords(
            @PathVariable Long userId,
            @RequestParam(required = false) String yearMonth) {
        String redirect = "redirect:/attendance/approval/" + userId + "/detail?from=admin";
        if (yearMonth != null && !yearMonth.isEmpty()) {
            redirect += "&yearMonth=" + yearMonth;
        }
        return redirect;
    }

    /**
     * 管理者による承認取り消しを行います。
     *
     * @param submissionId       対象申請 ID
     * @param yearMonth          戻り先年月（YYYY-MM）
     * @param redirectAttributes リダイレクト先メッセージ
     * @return 勤怠ダッシュボードへリダイレクト
     */
    @PostMapping("/attendance/submissions/{submissionId}/revoke")
    public String revokeApproval(
            @PathVariable Long submissionId,
            @RequestParam(required = false) String yearMonth,
            RedirectAttributes redirectAttributes) {
        try {
            Long adminUserId = securityUtil.getCurrentUserId();
            attendanceSubmissionService.revokeApproval(submissionId, adminUserId);
            redirectAttributes.addFlashAttribute("successMessage", "承認を取り消しました");
            log.info("管理者が承認を取り消し: submissionId={}, adminUserId={}", submissionId, adminUserId);
        } catch (IllegalArgumentException e) {
            log.warn("承認取り消しに失敗", e);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            logActionError(e, "承認取り消しに失敗");
            redirectAttributes.addFlashAttribute("errorMessage", "承認取り消しに失敗しました");
        }
        String redirect = "redirect:/admin/attendance";
        if (yearMonth != null && !yearMonth.isEmpty()) {
            redirect += "?yearMonth=" + yearMonth;
        }
        return redirect;
    }

    /**
     * 管理者画面表示時の例外ログ出力とユーザー向けエラーメッセージ設定を行います。
     *
     * @param model       モデル
     * @param e           発生した例外
     * @param logMessage  ログ用メッセージ
     * @param userMessage 画面表示用メッセージ
     */
    private void handleAdminViewError(Model model, Exception e, String logMessage, String userMessage) {
        logActionError(e, logMessage);
        model.addAttribute("error", userMessage);
    }

    /**
     * 例外メッセージ付きのエラーログを出力します。
     *
     * @param e          発生した例外
     * @param logMessage ログ用メッセージ
     */
    private void logActionError(Exception e, String logMessage) {
        log.error("{}", logMessage, e);
    }

    /**
     * 年月文字列を YearMonth に変換し、不正値の場合は現在年月を返します。
     *
     * @param yearMonth 年月文字列（yyyy-MM）
     * @return 解析成功時は指定年月、失敗時は現在年月
     */
    private YearMonth parseYearMonthOrNow(String yearMonth) {
        if (yearMonth == null || yearMonth.isEmpty()) {
            return attendanceSubmissionService.resolvePayrollMonth(DateTimeUtil.todayJapan());
        }
        try {
            return YearMonth.parse(yearMonth);
        } catch (Exception e) {
            log.warn(INVALID_YEAR_MONTH_LOG, yearMonth);
            return attendanceSubmissionService.resolvePayrollMonth(DateTimeUtil.todayJapan());
        }
    }

    /**
     * yearMonth 文字列を解析し、不正・未指定の場合は前月を返します。
     *
     * @param yearMonth 年月文字列（yyyy-MM）
     * @return 解析成功時は指定年月、失敗時は前月
     */
    private YearMonth parseYearMonthOrPrevious(String yearMonth) {
        if (yearMonth == null || yearMonth.isEmpty()) {
            return YearMonth.now().minusMonths(1);
        }
        try {
            return YearMonth.parse(yearMonth);
        } catch (Exception e) {
            log.warn(INVALID_YEAR_MONTH_LOG, yearMonth);
            return YearMonth.now().minusMonths(1);
        }
    }

    // ============================================================
    // 勤務クラス管理（所定時間マスタ）
    // ============================================================

    /**
     * 勤務クラス一覧を表示します。
     */
    @GetMapping("/work-schedules")
    public String showWorkSchedules(Model model) {
        try {
            model.addAttribute("workScheduleClasses", workScheduleClassService.getAllClasses());
            log.info("勤務クラス一覧を表示");
        } catch (Exception e) {
            handleAdminViewError(model, e, "勤務クラス一覧表示に失敗", "勤務クラス一覧の表示に失敗しました");
        }
        return WORK_SCHEDULES_VIEW;
    }

    /**
     * 勤務クラスを新規作成します。
     */
    @PostMapping("/work-schedules/create")
    public String createWorkSchedule(
            @RequestParam String name,
            @RequestParam(required = false) String workLocation,
            @RequestParam(required = false) String address,
            @RequestParam(required = false) String station,
            @RequestParam(required = false) String telephone,
            @RequestParam(required = false) String sectionName,
            @RequestParam(required = false) String folderName,
            @RequestParam(required = false) String tags,
            @RequestParam(required = false, defaultValue = "true") Boolean isActive,
            @RequestParam(required = false) Short maxHours,
            @RequestParam(required = false) Short minHours,
            @RequestParam String startTime,
            @RequestParam String endTime,
            @RequestParam(required = false) List<String> breakStartTimes,
            @RequestParam(required = false) List<String> breakEndTimes,
            RedirectAttributes redirectAttributes) {
        try {
            List<com.attendance.app.entity.WorkScheduleClassBreak> breaks = new java.util.ArrayList<>();
            if (breakStartTimes != null && breakEndTimes != null) {
                int size = Math.min(breakStartTimes.size(), breakEndTimes.size());
                for (int i = 0; i < size; i++) {
                    String start = breakStartTimes.get(i);
                    String end = breakEndTimes.get(i);
                    boolean startPresent = start != null && !start.isBlank();
                    boolean endPresent = end != null && !end.isBlank();
                    if (startPresent && endPresent) {
                        breaks.add(com.attendance.app.entity.WorkScheduleClassBreak.builder()
                                .breakStartTime(LocalTime.parse(start))
                                .breakEndTime(LocalTime.parse(end))
                                .build());
                    } else if (startPresent || endPresent) {
                        throw new IllegalArgumentException("休憩の開始時刻と終了時刻は両方入力してください");
                    }
                }
            }

            workScheduleClassService.createClass(
                    name,
                    workLocation,
                    address,
                    station,
                    telephone,
                    sectionName,
                    folderName,
                    tags,
                    isActive,
                    maxHours,
                    minHours,
                    LocalTime.parse(startTime),
                    LocalTime.parse(endTime),
                    breaks);
            redirectAttributes.addFlashAttribute("successMessage", "勤務クラスを作成しました: " + name);
            log.info("勤務クラスを作成: name={}", name);
        } catch (IllegalArgumentException e) {
            log.warn("勤務クラス作成に失敗", e);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            logActionError(e, "勤務クラス作成に失敗");
            redirectAttributes.addFlashAttribute("errorMessage", "勤務クラスの作成に失敗しました");
        }
        return ADMIN_WORK_SCHEDULES_REDIRECT;
    }

    /**
     * 勤務クラスを更新します。
     */
    @PostMapping("/work-schedules/{classId}/update")
    public String updateWorkSchedule(
            @PathVariable Long classId,
            @RequestParam String name,
            @RequestParam(required = false) String workLocation,
            @RequestParam(required = false) String address,
            @RequestParam(required = false) String station,
            @RequestParam(required = false) String telephone,
            @RequestParam(required = false) String sectionName,
            @RequestParam(required = false) String folderName,
            @RequestParam(required = false) String tags,
            @RequestParam(required = false, defaultValue = "true") Boolean isActive,
            @RequestParam(required = false) Short maxHours,
            @RequestParam(required = false) Short minHours,
            @RequestParam String startTime,
            @RequestParam String endTime,
            @RequestParam(required = false) List<String> breakStartTimes,
            @RequestParam(required = false) List<String> breakEndTimes,
            RedirectAttributes redirectAttributes) {
        try {
            List<com.attendance.app.entity.WorkScheduleClassBreak> breaks = new java.util.ArrayList<>();
            if (breakStartTimes != null && breakEndTimes != null) {
                int size = Math.min(breakStartTimes.size(), breakEndTimes.size());
                for (int i = 0; i < size; i++) {
                    String start = breakStartTimes.get(i);
                    String end = breakEndTimes.get(i);
                    boolean startPresent = start != null && !start.isBlank();
                    boolean endPresent = end != null && !end.isBlank();
                    if (startPresent && endPresent) {
                        breaks.add(com.attendance.app.entity.WorkScheduleClassBreak.builder()
                                .breakStartTime(LocalTime.parse(start))
                                .breakEndTime(LocalTime.parse(end))
                                .build());
                    } else if (startPresent || endPresent) {
                        throw new IllegalArgumentException("休憩の開始時刻と終了時刻は両方入力してください");
                    }
                }
            }

            workScheduleClassService.updateClass(
                    classId,
                    name,
                    workLocation,
                    address,
                    station,
                    telephone,
                    sectionName,
                    folderName,
                    tags,
                    isActive,
                    maxHours,
                    minHours,
                    LocalTime.parse(startTime),
                    LocalTime.parse(endTime),
                    breaks);
            redirectAttributes.addFlashAttribute("successMessage", "勤務クラスを更新しました: " + name);
            log.info("勤務クラスを更新: classId={}, name={}", classId, name);
        } catch (IllegalArgumentException e) {
            log.warn("勤務クラス更新に失敗", e);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            logActionError(e, "勤務クラス更新に失敗");
            redirectAttributes.addFlashAttribute("errorMessage", "勤務クラスの更新に失敗しました");
        }
        return ADMIN_WORK_SCHEDULES_REDIRECT;
    }


    /**
     * 勤務クラスを論理削除（無効化）します。
     * 紐付くユーザーの勤務クラス情報は維持され、新規の割り当て対象から外れます。
     */
    @PostMapping("/work-schedules/{classId}/delete")
    public String deleteWorkSchedule(
            @PathVariable Long classId,
            RedirectAttributes redirectAttributes) {
        try {
            workScheduleClassService.deleteClass(classId);
            redirectAttributes.addFlashAttribute("successMessage", "勤務クラスを無効化（論理削除）しました");
            log.info("勤務クラスを論理削除: classId={}", classId);
        } catch (IllegalArgumentException e) {
            log.warn("勤務クラス削除に失敗", e);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            logActionError(e, "勤務クラス削除に失敗");
            redirectAttributes.addFlashAttribute("errorMessage", "勤務クラスの削除に失敗しました");
        }
        return ADMIN_WORK_SCHEDULES_REDIRECT;
    }

    // ============================================================
    // 通知管理
    // ============================================================

    /**
     * 通知管理画面を表示します。
     */
    @GetMapping("/notifications")
    public String showNotifications(Model model) {
        try {
            List<User> activeUsers = userService.getActiveUsers().stream()
                    .filter(u -> u.getUserRole() != UserRole.ADMIN)
                    .collect(Collectors.toList());
            model.addAttribute("activeUsers", activeUsers);
            model.addAttribute("classes", workScheduleClassService.getAllClasses());
            log.info("通知管理画面を表示: activeUserCount={}", activeUsers.size());
        } catch (Exception e) {
            handleAdminViewError(model, e, "通知管理画面表示に失敗", "通知管理画面の表示に失敗しました");
        }
        return ADMIN_NOTIFICATIONS_VIEW;
    }

    /**
     * 指定ユーザー、指定勤務クラス、または全ユーザーにカスタム通知を送信します。
     *
     * @param userId  null の場合は全ユーザーに送信
     * @param classId null の場合は全ユーザーに送信（userIdもnullの場合）
     * @param message 通知メッセージ
     */
    @PostMapping("/notifications/send")
    public String sendNotification(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long classId,
            @RequestParam String message,
            RedirectAttributes redirectAttributes) {
        try {
            Long senderUserId = securityUtil.getCurrentUserId();
            if (userId != null) {
                User target = userService.getUserById(userId)
                        .orElseThrow(() -> new IllegalArgumentException("ユーザーが見つかりません: id=" + userId));
                userNotificationService.sendCustomNotification(userId, message, senderUserId);
                redirectAttributes.addFlashAttribute("successMessage",
                        target.getFullName() + " さんに通知を送信しました");
                log.info("管理者カスタム通知を送信: targetUserId={}, senderUserId={}", userId, senderUserId);
            } else if (classId != null) {
                com.attendance.app.entity.WorkScheduleClass wsc = workScheduleClassService.getClassById(classId)
                        .orElseThrow(() -> new IllegalArgumentException("勤務クラスが見つかりません: id=" + classId));
                int count = userNotificationService.sendCustomNotificationToClass(classId, message, senderUserId);
                redirectAttributes.addFlashAttribute("successMessage",
                        "勤務クラス「" + wsc.getName() + "」のユーザー（" + count + "名）に通知を送信しました");
                log.info("管理者クラス一括通知を送信: classId={}, count={}, senderUserId={}", classId, count, senderUserId);
            } else {
                int count = userNotificationService.sendCustomNotificationToAll(message, senderUserId);
                redirectAttributes.addFlashAttribute("successMessage",
                        "全ユーザー（" + count + "名）に通知を送信しました");
                log.info("管理者一括通知を送信: count={}, senderUserId={}", count, senderUserId);
            }
        } catch (IllegalArgumentException e) {
            log.warn("通知送信に失敗", e);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            logActionError(e, "通知送信に失敗");
            redirectAttributes.addFlashAttribute("errorMessage", "通知の送信に失敗しました");
        }
        return ADMIN_NOTIFICATIONS_REDIRECT;
    }

    // ============================================================
    // 36協定チェック管理
    // ============================================================

    /**
     * 36協定チェックダッシュボードを表示します。
     * 指定年月を含む直近3ヶ月の全ユーザー残業状況を一覧表示します。
     *
     * @param yearMonth 基準年月（省略時: 当月）
     */
    @GetMapping("/article36")
    public String showArticle36Dashboard(
            @RequestParam(required = false) String yearMonth,
            Model model) {
        YearMonth baseMonth = parseYearMonthOrNow(yearMonth);
        List<User> users = Collections.emptyList();
        List<Map<String, String>> monthEntries = Collections.emptyList();
        Map<String, Double> overtimeCellMap = Collections.emptyMap();
        Map<String, String> article36StatusCellMap = Collections.emptyMap();
        List<Map<String, Object>> article36MonthlyTrend = Collections.emptyList();
        long alertCount = 0;
        long warningCount = 0;
        long normalCount = 0;

        try {
            users = userService.getActiveUsers().stream()
                    .filter(u -> u.getUserRole() != UserRole.ADMIN)
                    .collect(Collectors.toList());

            // 直近3ヶ月分の残業時間と36協定ステータスを収集
            List<YearMonth> months = List.of(baseMonth.minusMonths(2), baseMonth.minusMonths(1), baseMonth);
            monthEntries = months.stream()
                    .map(ym -> Map.of(
                            "key", ym.toString(),
                            "label", ym.getYear() + "年" + ym.getMonthValue() + "月"))
                    .toList();

            // 3ヶ月分の月次集計を1回のフェッチ（範囲取得＋YearMonthグルーピング）にまとめて取得
            Map<YearMonth, Map<Long, Double>> overtimeByMonth = attendanceRecordService.getOvertimeSumByUserForMonthRange(months);
            overtimeCellMap = new HashMap<>();
            article36StatusCellMap = new HashMap<>();
            for (YearMonth ym : months) {
                Map<Long, Double> overtimeMap = overtimeByMonth.getOrDefault(ym, Map.of());
                for (User u : users) {
                    double ot = overtimeMap.getOrDefault(u.getUserId(), 0.0);
                    String cellKey = ym + "_" + u.getUserId();
                    overtimeCellMap.put(cellKey, ot);
                    article36StatusCellMap.put(cellKey, attendanceRecordService.checkArticle36(ot));
                }
            }

            // 月別時間外労働の平均推移（直近3ヶ月）
            double article36Warning = batchSettingService.getAlertArticle36Limit1();
            article36MonthlyTrend = new ArrayList<>();
            for (YearMonth ym : months) {
                double sum = 0.0;
                for (User u : users) {
                    sum += overtimeCellMap.getOrDefault(ym + "_" + u.getUserId(), 0.0);
                }
                double average = users.isEmpty() ? 0.0 : Math.round(sum / users.size() * 10.0) / 10.0;
                double barPercent = Math.min(100.0, average / 45.0 * 100.0);
                Map<String, Object> trendEntry = new HashMap<>();
                trendEntry.put("label", ym.getMonthValue() + "月");
                trendEntry.put("average", average);
                trendEntry.put("barPercent", barPercent);
                trendEntry.put("isWarning", average >= article36Warning);
                article36MonthlyTrend.add(trendEntry);
            }

            final String baseKey = baseMonth.toString();
            final Map<String, String> statusMap = article36StatusCellMap;
            alertCount = users.stream()
                    .filter(u -> "ALERT".equals(statusMap.get(baseKey + "_" + u.getUserId())))
                    .count();
            warningCount = users.stream()
                    .filter(u -> "WARNING".equals(statusMap.get(baseKey + "_" + u.getUserId())))
                    .count();
            normalCount = users.stream()
                    .filter(u -> "NORMAL".equals(statusMap.get(baseKey + "_" + u.getUserId())))
                    .count();

            log.info("36協定チェックダッシュボードを表示: baseMonth={}, userCount={}", baseMonth, users.size());
        } catch (Exception e) {
            handleAdminViewError(model, e, "36協定ダッシュボード表示に失敗", "36協定ダッシュボードの表示に失敗しました");
        }

        model.addAttribute("users", users);
        model.addAttribute("baseMonth", baseMonth);
        model.addAttribute("baseMonthKey", baseMonth.toString());
        model.addAttribute("monthEntries", monthEntries);
        model.addAttribute("overtimeCellMap", overtimeCellMap);
        model.addAttribute("article36StatusCellMap", article36StatusCellMap);
        model.addAttribute("article36MonthlyTrend", article36MonthlyTrend);
        model.addAttribute("article36MonthlyLimit", batchSettingService.getAlertArticle36Limit2());
        model.addAttribute("article36MonthlyWarning", batchSettingService.getAlertArticle36Limit1());
        model.addAttribute("alertCount", alertCount);
        model.addAttribute("warningCount", warningCount);
        model.addAttribute("normalCount", normalCount);

        return ADMIN_ARTICLE36_VIEW;
    }

    /**
     * 指定ユーザーに36協定アラート通知を送信します。
     *
     * @param userId    対象ユーザーID
     * @param yearMonth 対象年月
     */
    @PostMapping("/article36/notify/{userId}")
    public String notifyArticle36(
            @PathVariable Long userId,
            @RequestParam(required = false) String yearMonth,
            RedirectAttributes redirectAttributes) {
        try {
            User target = userService.getUserById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("ユーザーが見つかりません: id=" + userId));
            YearMonth ym = parseYearMonthOrNow(yearMonth);
            Map<Long, Double> overtimeMap = attendanceRecordService.getOvertimeSumByUserForMonth(ym);
            double ot = overtimeMap.getOrDefault(userId, 0.0);
            String status = attendanceRecordService.checkArticle36(ot);
            if ("NORMAL".equals(status)) {
                throw new IllegalArgumentException(
                        target.getFullName() + " さんの " + ym.getYear() + "年" + ym.getMonthValue()
                                + "月の残業時間（" + String.format("%.1f", ot) + "時間）は注意水準に達していないため通知できません（NORMAL）");
            }
            String statusLabel = "ALERT".equals(status)
                    ? "36協定上限超過（月" + batchSettingService.getAlertArticle36Limit2() + "時間超）"
                    : "36協定注意レベル（月" + batchSettingService.getAlertArticle36Limit1() + "時間超）";
            String msg = ym.getYear() + "年" + ym.getMonthValue() + "月の残業時間が"
                    + String.format("%.1f", ot) + "時間に達し、"
                    + statusLabel + "に該当しています。残業時間の管理にご注意ください。";
            userNotificationService.notifyArticle36Alert(userId, msg);
            redirectAttributes.addFlashAttribute("successMessage",
                    target.getFullName() + " さんに36協定アラートを送信しました");
            log.info("36協定アラートを送信: userId={}, yearMonth={}, overtime={}", userId, ym, ot);
        } catch (IllegalArgumentException e) {
            log.warn("36協定アラート送信に失敗", e);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            logActionError(e, "36協定アラート送信に失敗");
            redirectAttributes.addFlashAttribute("errorMessage", "36協定アラートの送信に失敗しました");
        }
        String redirectBase = "redirect:/admin/article36";
        if (yearMonth != null && !yearMonth.isEmpty()) {
            return redirectBase + "?yearMonth=" + yearMonth;
        }
        return redirectBase;
    }

    // ============================================================
    // レポート出力（CSV / ZIP）
    // ============================================================

    /**
     * 指定ユーザーの月次勤怠CSVをダウンロードします。
     *
     * @param userId    対象ユーザーID
     * @param yearMonth 対象年月（YYYY-MM形式、省略可）
     * @return CSVファイルレスポンス
     */
    @GetMapping("/attendance/export/csv")
    public ResponseEntity<byte[]> downloadUserAttendanceCsv(
            @RequestParam Long userId,
            @RequestParam(required = false) String yearMonth) {
        try {
            User user = userService.getUserById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("ユーザーが見つかりません: userId=" + userId));
            YearMonth targetMonth = parseYearMonthOrNow(yearMonth);
            byte[] csvBytes = reportService.generateUserAttendanceCsv(user, targetMonth);
            OffsetDateTime downloadedAt = DateTimeUtil.toOffsetDateTime(DateTimeUtil.now());
            String filename = csvFilenamePatternService.buildCsvFilename(user, targetMonth, downloadedAt);
            log.info("月次勤怠CSVをダウンロード: userId={}, yearMonth={}, filename={}", userId, targetMonth, filename);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodeFilename(filename))
                    .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                    .body(csvBytes);
        } catch (IllegalArgumentException e) {
            log.warn("月次勤怠CSVダウンロードに失敗", e);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            logActionError(e, "月次勤怠CSVダウンロードに失敗");
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 全アクティブユーザーの月次勤怠CSVをまとめたZIPをダウンロードします。
     *
     * @param yearMonth 対象年月（YYYY-MM形式、省略可）
     * @return ZIPファイルレスポンス
     */
    @GetMapping("/attendance/export/zip")
    public ResponseEntity<byte[]> downloadAllAttendanceZip(
            @RequestParam(required = false) String yearMonth) {
        try {
            YearMonth targetMonth = parseYearMonthOrNow(yearMonth);
            OffsetDateTime downloadedAt = DateTimeUtil.toOffsetDateTime(DateTimeUtil.now());
            byte[] zipBytes = reportService.generateAllUsersAttendanceZip(targetMonth, downloadedAt);
            String filename = targetMonth + "_attendance.zip";
            log.info("全ユーザー月次勤怠ZIPをダウンロード: yearMonth={}, filename={}", targetMonth, filename);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodeFilename(filename))
                    .contentType(MediaType.parseMediaType("application/zip"))
                    .body(zipBytes);
        } catch (Exception e) {
            logActionError(e, "全ユーザー月次勤怠ZIPダウンロードに失敗");
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * RFC 5987 に準拠した Content-Disposition filename* 用のパーセントエンコードを行います。
     */
    private String encodeFilename(String filename) {
        return URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
