package com.attendance.app.controller;

import com.attendance.app.entity.PaidLeaveBalance;
import com.attendance.app.entity.User;
import com.attendance.app.service.PaidLeaveBalanceService;
import com.attendance.app.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin/leave-usage")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminLeaveUsageController {

    private final UserService userService;
    private final PaidLeaveBalanceService paidLeaveBalanceService;

    @GetMapping
    public String showLeaveUsage(Model model) {
        List<User> users = userService.getActiveUsers();
        
        int currentYear = LocalDate.now().getYear();
        
        Map<Long, PaidLeaveBalance> balanceMap = new HashMap<>();
        Map<Long, Boolean> obligationMetMap = new HashMap<>();
        Map<Long, BigDecimal> remainingTotalMap = new HashMap<>();

        for (User user : users) {
            remainingTotalMap.put(user.getUserId(), paidLeaveBalanceService.getTotalRemainingDays(user.getUserId()));
            // 現在の年の有給休暇残高を取得
            paidLeaveBalanceService.getByUserAndYear(user.getUserId(), currentYear)
                .ifPresentOrElse(balance -> {
                    balanceMap.put(user.getUserId(), balance);
                    // 年5日取得義務を満たしているかチェック（取得済み日数が5以上）
                    boolean obligationMet = balance.getUsedDays().compareTo(new BigDecimal("5.0")) >= 0;
                    obligationMetMap.put(user.getUserId(), obligationMet);
                }, () -> {
                    // まだ付与されていない場合はダミーデータ（あるいは null のまま表示で対応）
                    PaidLeaveBalance emptyBalance = new PaidLeaveBalance();
                    emptyBalance.setGrantedDays(BigDecimal.ZERO);
                    emptyBalance.setUsedDays(BigDecimal.ZERO);
                    balanceMap.put(user.getUserId(), emptyBalance);
                    obligationMetMap.put(user.getUserId(), false);
                });
        }

        model.addAttribute("users", users);
        model.addAttribute("balanceMap", balanceMap);
        model.addAttribute("obligationMetMap", obligationMetMap);
        model.addAttribute("remainingTotalMap", remainingTotalMap);
        model.addAttribute("currentYear", currentYear);
        
        return "admin/leave-usage";
    }
}
