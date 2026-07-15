package com.attendance.app.controller;

import com.attendance.app.entity.User;
import com.attendance.app.security.SecurityUtil;
import com.attendance.app.service.LeaveApplicationService;
import com.attendance.app.service.PaidLeaveBalanceService;
import com.attendance.app.service.UserService;
import com.attendance.app.service.WorkScheduleClassService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Profile Controller - ユーザープロフィール
 *
 * 主な責務:
 * - ログイン中ユーザーのプロフィール表示
 * - 所属クラス（勤務クラス）の表示
 */
@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/profile")
@PreAuthorize("isAuthenticated()")
public class ProfileController {

    private static final String PROFILE_VIEW = "user/profile";
    private final WorkScheduleClassService workScheduleClassService;
    private final SecurityUtil securityUtil;
    private final LeaveApplicationService leaveApplicationService;
    private final PaidLeaveBalanceService paidLeaveBalanceService;
    private final UserService userService;

    /**
     * プロフィール画面を表示します。
     *
     * @param model Spring MVC のモデル
     * @return テンプレート名 (user/profile)
     */
    @GetMapping
    public String showProfile(Model model) {
        try {
            User currentUser = securityUtil.getCurrentUser();
            int currentYear = com.attendance.app.util.DateTimeUtil.todayJapan().getYear();
            java.math.BigDecimal yearlyUsedPaidLeaveDays = leaveApplicationService.calculateYearlyUsedPaidLeaveDays(
                    currentUser.getUserId(), currentYear);
            // 有給残日数は paid_leave_balance テーブル（年度別残高）を正とする
            java.math.BigDecimal remainingPaidLeaveDays =
                    paidLeaveBalanceService.getTotalRemainingDays(currentUser.getUserId());

            model.addAttribute("currentUser", currentUser);
            // 無効化されたクラスに割当中でも「現在の所属クラス」表示が壊れないよう別途取得する
            model.addAttribute("currentWorkScheduleClass",
                    workScheduleClassService.getClassByName(currentUser.getClassName()).orElse(null));
            model.addAttribute("workScheduleClasses", workScheduleClassService.getAllActiveClasses());
            model.addAttribute("yearlyUsedPaidLeaveDays", yearlyUsedPaidLeaveDays);
            model.addAttribute("remainingPaidLeaveDays", remainingPaidLeaveDays);
            // 有給残高年次一覧
            model.addAttribute("paidLeaveBalances", paidLeaveBalanceService.getBalancesByUserId(currentUser.getUserId()));
            model.addAttribute("totalRemainingPaidLeaveDays", remainingPaidLeaveDays);
            log.info("プロフィール画面を表示: userId={}", currentUser.getUserId());
        } catch (Exception e) {
            log.error("プロフィール画面表示に失敗", e);
            model.addAttribute("error", "プロフィール画面の表示に失敗しました");
        }
        return PROFILE_VIEW;
    }

    @PostMapping("/work-schedule")
    public String updateWorkSchedule(@RequestParam(required = false) String className,
                                     RedirectAttributes redirectAttributes) {
        try {
            User currentUser = securityUtil.getCurrentUser();
            userService.updateWorkScheduleClass(currentUser.getUserId(), className);
            redirectAttributes.addFlashAttribute("successMessage", "勤務クラスを変更しました");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            log.error("勤務クラスの変更に失敗", e);
            redirectAttributes.addFlashAttribute("errorMessage", "勤務クラスの変更に失敗しました");
        }
        return "redirect:/profile";
    }

}
