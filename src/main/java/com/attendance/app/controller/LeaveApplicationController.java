package com.attendance.app.controller;

import com.attendance.app.entity.AttendanceSubmission;
import com.attendance.app.entity.LeaveApplication;
import com.attendance.app.entity.LeaveStatus;
import com.attendance.app.entity.LeaveType;
import com.attendance.app.security.SecurityUtil;
import com.attendance.app.service.AttendanceSubmissionService;
import com.attendance.app.service.LeaveApplicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Leave Application Controller - 休暇申請機能
 *
 * 主な責務:
 * - ユーザーの休暇申請一覧表示
 * - 休暇申請の作成
 * - 休暇申請の削除
 */
@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/leave")
@PreAuthorize("isAuthenticated()")
public class LeaveApplicationController {

    private static final String LEAVE_CREATE_VIEW = "user/leave-create";
    private static final String LEAVE_LIST_VIEW = "user/leave-list";
    private static final String ATTENDANCE_REDIRECT = "redirect:/attendance";
    private static final String LEAVE_REDIRECT = "redirect:/leave";

    private static final DateTimeFormatter YEAR_MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    private final LeaveApplicationService leaveApplicationService;
    private final AttendanceSubmissionService attendanceSubmissionService;
    private final SecurityUtil securityUtil;

    /**
     * 休暇申請一覧を表示します。
     * 
     * ステータスパラメータが指定されていた場合、フィルタリングを行います。
     *
     * @param status 休暇申請のステータス（省略可）
     * @param model Spring MVC のモデル
     * @return テンプレート名 (user/leave-list)
     */
    @GetMapping
    public String showLeaveApplicationList(
            @RequestParam(required = false) String status,
            Model model) {
        try {
            Long userId = securityUtil.getCurrentUserId();

            List<LeaveApplication> applications;
            if (status != null && !status.isEmpty()) {
                try {
                    applications = leaveApplicationService.getApplicationsByUserIdAndStatus(userId, LeaveStatus.valueOf(status));
                } catch (IllegalArgumentException e) {
                    applications = leaveApplicationService.getApplicationsByUserId(userId);
                }
            } else {
                applications = leaveApplicationService.getApplicationsByUserId(userId);
            }

            // ロック中の月に属する申請IDのセットを計算（PENDING/APPROVED月はロック）
            Set<Long> lockedApplicationIds = resolveLockedApplicationIds(userId, applications);

            model.addAttribute("applications", applications);
            model.addAttribute("statuses", LeaveStatus.values());
            model.addAttribute("leaveTypes", LeaveType.values());
            model.addAttribute("lockedApplicationIds", lockedApplicationIds);
            log.info("休暇申請一覧を表示: userId={}, count={}", userId, applications.size());
        } catch (Exception e) {
            logActionError(e, "休暇申請一覧表示に失敗");
            model.addAttribute("error", "休暇申請一覧の表示に失敗しました");
        }
        return LEAVE_LIST_VIEW;
    }

    /**
     * 休暇申請フォームを表示します。
     *
     * @param model Spring MVC のモデル
     * @return テンプレート名 (user/leave-create)
     */
    @GetMapping("/create")
    public String showCreateForm(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) java.time.LocalDate leaveStartDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) java.time.LocalDate leaveEndDate,
            Model model) {
        model.addAttribute("leaveTypes", LeaveType.values());
        model.addAttribute("leaveStartDate", leaveStartDate);
        model.addAttribute("leaveEndDate", leaveEndDate);
        return LEAVE_CREATE_VIEW;
    }

    /**
     * 勤怠画面のワンクリック有給申請（当日分1日）
     */
    @PostMapping("/applyPaid")
    public String applyPaidLeave(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) java.time.LocalDate date,
                                 @RequestParam(required = false) String yearMonth,
                                 @RequestParam(required = false) String remarks,
                                 RedirectAttributes redirectAttributes) {
        try {
            Long userId = securityUtil.getCurrentUserId();
            assertLeaveMonthEditable(userId, date);
            String reason = (remarks != null && !remarks.trim().isEmpty()) ? remarks.trim() : "勤怠からの有給申請";
            LeaveApplication application = leaveApplicationService.createApplication(userId, date, date, LeaveType.PAID_LEAVE, reason);
            // 承認フロー不要のため即時承認する
            leaveApplicationService.approveApplication(application.getApplicationId(), userId);
            redirectAttributes.addFlashAttribute("message", "有給休暇を申請しました");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            logActionError(e, "有給休暇申請に失敗");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            logActionError(e, "有給休暇申請に失敗");
        }
        if (yearMonth != null && !yearMonth.isEmpty()) {
            return ATTENDANCE_REDIRECT + "?yearMonth=" + yearMonth;
        }
        return ATTENDANCE_REDIRECT;
    }

    /**
     * 勤怠画面のワンクリック休暇申請（特休・欠勤）
     */
    @PostMapping("/apply")
    public String applyLeave(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) java.time.LocalDate date,
                             @RequestParam LeaveType leaveType,
                             @RequestParam(required = false) String yearMonth,
                             @RequestParam(required = false) String remarks,
                             RedirectAttributes redirectAttributes) {
        try {
            Long userId = securityUtil.getCurrentUserId();
            assertLeaveMonthEditable(userId, date);
            String defaultReason = leaveType.getDisplayName() + "申請";
            String reason = (remarks != null && !remarks.trim().isEmpty()) ? remarks.trim() : defaultReason;
            LeaveApplication application = leaveApplicationService.createApplication(userId, date, date, leaveType, reason);
            leaveApplicationService.approveApplication(application.getApplicationId(), userId);
            redirectAttributes.addFlashAttribute("message", leaveType.getDisplayName() + "を申請しました");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            logActionError(e, leaveType.getDisplayName() + "申請に失敗");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            logActionError(e, leaveType.getDisplayName() + "申請に失敗");
        }
        if (yearMonth != null && !yearMonth.isEmpty()) {
            return ATTENDANCE_REDIRECT + "?yearMonth=" + yearMonth;
        }
        return ATTENDANCE_REDIRECT;
    }

    /**
     * 新しい休暇申請を作成します。
     * 
     * 入力値を検証した上で LeaveApplicationService に委譲します。
     *
     * @param startDate 休暇開始日
     * @param endDate 休暇終了日
     * @param leaveType 休暇タイプ
     * @param reason 申請理由
     * @param model Spring MVC のモデル
     * @return 成功時はリダイレクト、失敗時はフォーム再表示
     */
    @PostMapping("/create")
    public String createLeaveApplication(
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate,
            @RequestParam LeaveType leaveType,
            @RequestParam(defaultValue = "FULL_DAY") String leaveDurationType,
            @RequestParam String reason,
            RedirectAttributes redirectAttributes) {
        try {
            Long userId = securityUtil.getCurrentUserId();
            assertLeaveMonthEditableForRange(userId, startDate, endDate);
            LeaveApplication application = leaveApplicationService.createApplication(userId, startDate, endDate, leaveDurationType, leaveType, reason);
            // 承認フロー不要なので即時承認する
            leaveApplicationService.approveApplication(application.getApplicationId(), userId);
            long days = leaveApplicationService.calculateLeaveDays(startDate, endDate);
            log.info("休暇申請を作成して承認: applicationId={}, userId={}, days={}", application.getApplicationId(), userId, days);
            redirectAttributes.addFlashAttribute("message", "休暇申請を送信しました。");
            return LEAVE_REDIRECT;
        } catch (IllegalArgumentException e) {
            log.warn("休暇申請作成に失敗: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            redirectAttributes.addFlashAttribute("leaveStartDate", startDate);
            redirectAttributes.addFlashAttribute("leaveEndDate", endDate);
            redirectAttributes.addFlashAttribute("selectedLeaveType", leaveType);
            redirectAttributes.addFlashAttribute("selectedLeaveDurationType", leaveDurationType);
            redirectAttributes.addFlashAttribute("reason", reason);
            redirectAttributes.addFlashAttribute("showForm", true);
            return LEAVE_REDIRECT;
        } catch (Exception e) {
            logActionError(e, "休暇申請作成に失敗");
            redirectAttributes.addFlashAttribute("error", "予期しないエラーが発生しました");
            redirectAttributes.addFlashAttribute("showForm", true);
            return LEAVE_REDIRECT;
        }
    }

    /**
     * 休暇申請を削除します。
     *
     * @param applicationId 削除対象の休暇申請ID
     * @return 休暇一覧へリダイレクト
     */
    @PostMapping("/delete/{applicationId}")
    public String deleteLeaveApplication(@PathVariable Long applicationId, RedirectAttributes redirectAttributes) {
        try {
            Long userId = securityUtil.getCurrentUserId();
            Optional<LeaveApplication> appOpt = leaveApplicationService.getApplicationById(applicationId);
            if (appOpt.isPresent()) {
                LeaveApplication app = appOpt.get();
                if (!app.getUserId().equals(userId)) {
                    throw new IllegalArgumentException("他人の休暇申請は削除できません。");
                }
                assertLeaveMonthEditable(userId, app.getLeaveStartDate());
                leaveApplicationService.deleteApplication(applicationId);
                log.info("休暇申請を削除: applicationId={}, userId={}", applicationId, userId);
                redirectAttributes.addFlashAttribute("message", "休暇申請を削除しました");
            } else {
                redirectAttributes.addFlashAttribute("error", "指定された休暇申請が見つかりません。");
            }
        } catch (IllegalArgumentException e) {
            log.warn("休暇申請削除に失敗: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            logActionError(e, "休暇申請削除に失敗");
            redirectAttributes.addFlashAttribute("error", "休暇申請の削除に失敗しました");
        }
        return LEAVE_REDIRECT;
    }

    /**
     * 例外メッセージ付きのエラーログを出力します。
     *
     * @param e 発生した例外
     * @param logMessage ログ用メッセージ
     */
    /**
     * 対象日の給与月が編集可能かを検証し、ロック中の場合は例外を送出します。
     *
     * @param userId ユーザーID
     * @param date   対象日
     * @throws IllegalArgumentException 月が申請中または承認済みの場合
     */
    private void assertLeaveMonthEditable(Long userId, LocalDate date) {
        YearMonth payrollMonth = attendanceSubmissionService.resolvePayrollMonth(date);
        if (!attendanceSubmissionService.isEditableMonth(userId, payrollMonth)) {
            throw new IllegalArgumentException(
                    payrollMonth.format(YEAR_MONTH_FORMATTER) + " の月次勤怠は申請中または承認済みのため操作できません");
        }
    }

    /**
     * 申請期間（startDate〜endDate）に含まれる全ての給与月が編集可能かを検証し、
     * ロック中の月が含まれる場合は例外を送出します。
     *
     * @param userId    ユーザーID
     * @param startDate 休暇開始日
     * @param endDate   休暇終了日
     * @throws IllegalArgumentException 期間内にロック中の月が含まれる場合
     */
    private void assertLeaveMonthEditableForRange(Long userId, LocalDate startDate, LocalDate endDate) {
        Set<YearMonth> payrollMonths = new java.util.LinkedHashSet<>();
        LocalDate d = startDate;
        while (!d.isAfter(endDate)) {
            payrollMonths.add(attendanceSubmissionService.resolvePayrollMonth(d));
            d = d.plusDays(1);
        }
        for (YearMonth payrollMonth : payrollMonths) {
            if (!attendanceSubmissionService.isEditableMonth(userId, payrollMonth)) {
                throw new IllegalArgumentException(
                        payrollMonth.format(YEAR_MONTH_FORMATTER) + " の月次勤怠は申請中または承認済みのため操作できません");
            }
        }
    }

    /**
     * ロック中の月（PENDING/APPROVED）に属する申請IDのセットを返します。
     *
     * @param userId       ユーザーID
     * @param applications 申請一覧
     * @return ロック中申請IDのセット
     */
    private Set<Long> resolveLockedApplicationIds(Long userId, List<LeaveApplication> applications) {
        List<AttendanceSubmission> submissions = attendanceSubmissionService.getSubmissionsByUserId(userId);
        Set<String> lockedMonths = new HashSet<>();
        for (AttendanceSubmission s : submissions) {
            if (AttendanceSubmissionService.STATUS_PENDING.equals(s.getStatus())
                    || AttendanceSubmissionService.STATUS_APPROVED.equals(s.getStatus())) {
                lockedMonths.add(s.getTargetYearMonth());
            }
        }
        Set<Long> lockedIds = new HashSet<>();
        for (LeaveApplication app : applications) {
            YearMonth pm = attendanceSubmissionService.resolvePayrollMonth(app.getLeaveStartDate());
            if (lockedMonths.contains(pm.format(YEAR_MONTH_FORMATTER))) {
                lockedIds.add(app.getApplicationId());
            }
        }
        return lockedIds;
    }

    private void logActionError(Exception e, String logMessage) {
        log.error("{}: {}", logMessage, e.getMessage());
    }
}