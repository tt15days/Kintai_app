package com.attendance.app.controller;

import com.attendance.app.entity.User;
import com.attendance.app.entity.WorkScheduleClass;
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

import java.util.List;

/**
 * Profile Controller - ユーザープロフィール
 *
 * 主な責務:
 * - ログイン中ユーザーのプロフィール表示
 * - 所属クラス（勤務クラス）の変更
 */
@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/profile")
@PreAuthorize("isAuthenticated()")
public class ProfileController {

    private static final String PROFILE_VIEW = "user/profile";
    private static final String PROFILE_REDIRECT = "redirect:/profile";

    private final UserService userService;
    private final WorkScheduleClassService workScheduleClassService;
    private final SecurityUtil securityUtil;
    private final LeaveApplicationService leaveApplicationService;
    private final PaidLeaveBalanceService paidLeaveBalanceService;

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
            List<WorkScheduleClass> workScheduleClasses = workScheduleClassService.getAllClasses();

            int currentYear = java.time.LocalDate.now().getYear();
            long yearlyUsedPaidLeaveDays = leaveApplicationService.calculateYearlyUsedPaidLeaveDays(
                    currentUser.getUserId(), currentYear);
            java.math.BigDecimal paidLeaveDays = currentUser.getPaidLeaveDays() != null
                    ? currentUser.getPaidLeaveDays() : java.math.BigDecimal.ZERO;
            java.math.BigDecimal remainingPaidLeaveDays = paidLeaveDays.subtract(
                    new java.math.BigDecimal(yearlyUsedPaidLeaveDays));

            model.addAttribute("currentUser", currentUser);
            model.addAttribute("workScheduleClasses", workScheduleClasses);
            model.addAttribute("yearlyUsedPaidLeaveDays", yearlyUsedPaidLeaveDays);
            model.addAttribute("remainingPaidLeaveDays", remainingPaidLeaveDays);
            // 有給残高年次一覧
            model.addAttribute("paidLeaveBalances", paidLeaveBalanceService.getBalancesByUserId(currentUser.getUserId()));
            model.addAttribute("totalRemainingPaidLeaveDays", paidLeaveBalanceService.getTotalRemainingDays(currentUser.getUserId()));
            log.info("プロフィール画面を表示: userId={}", currentUser.getUserId());
        } catch (Exception e) {
            log.error("プロフィール画面表示に失敗: {}", e.getMessage());
            model.addAttribute("error", "プロフィール画面の表示に失敗しました");
        }
        return PROFILE_VIEW;
    }

    /**
     * 所属クラス（勤務クラス）を更新します。
     *
     * @param className 選択した勤務クラス名（未選択の場合は null）
     * @param redirectAttributes リダイレクト先メッセージ
     * @return プロフィール画面へリダイレクト
     */
    @PostMapping("/work-schedule")
    public String updateWorkScheduleClass(
            @RequestParam(required = false) String className,
            RedirectAttributes redirectAttributes) {
        try {
            Long userId = securityUtil.getCurrentUserId();
            userService.updateWorkScheduleClass(userId, className);
            redirectAttributes.addFlashAttribute("successMessage", "所属クラスを更新しました");
            log.info("所属クラスを更新: userId={}, className={}", userId, className);
        } catch (IllegalArgumentException e) {
            log.warn("所属クラス更新に失敗: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            log.error("所属クラス更新に失敗: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "所属クラスの更新に失敗しました");
        }
        return PROFILE_REDIRECT;
    }
}
