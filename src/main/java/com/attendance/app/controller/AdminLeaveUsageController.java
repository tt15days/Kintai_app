package com.attendance.app.controller;

import com.attendance.app.entity.PaidLeaveBalance;
import com.attendance.app.entity.User;
import com.attendance.app.service.PaidLeaveBalanceService;
import com.attendance.app.service.UserService;
import com.attendance.app.service.WorkScheduleClassService;
import com.attendance.app.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Controller
@RequestMapping("/admin/leave-usage")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminLeaveUsageController {

    private final UserService userService;
    private final PaidLeaveBalanceService paidLeaveBalanceService;
    private final WorkScheduleClassService workScheduleClassService;

    @GetMapping
    public String showLeaveUsage(
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String keyword,
            Model model) {
        try {
            List<User> users = filterUsersByDepartmentAndKeyword(userService.getActiveUsers(), department, keyword);
            model.addAttribute("department", department);
            model.addAttribute("keyword", keyword);
            List<String> departments = workScheduleClassService.getAllActiveClasses().stream()
                    .map(c -> c.getName())
                    .collect(Collectors.toList());
            model.addAttribute("departments", departments);

            int currentYear = DateTimeUtil.todayJapan().getYear();

            Map<Long, PaidLeaveBalance> balanceMap = new HashMap<>();
            Map<Long, Boolean> obligationMetMap = new HashMap<>();
            Map<Long, BigDecimal> remainingTotalMap = new HashMap<>();

            // N+1対策として一括取得
            List<Long> userIds = users.stream().map(u -> u.getUserId()).toList();
            List<PaidLeaveBalance> currentYearBalances = paidLeaveBalanceService.getByUsersAndYear(userIds, currentYear);
            Map<Long, PaidLeaveBalance> currentYearBalanceMap = currentYearBalances.stream()
                .collect(Collectors.toMap(b -> b.getUserId(), b -> b));

            for (User user : users) {
                // UserエンティティのpaidLeaveDays（同期済み）を使用
                remainingTotalMap.put(user.getUserId(), user.getPaidLeaveDays() != null ? user.getPaidLeaveDays() : BigDecimal.ZERO);

                // 現在の年の有給休暇残高を取得
                PaidLeaveBalance balance = currentYearBalanceMap.get(user.getUserId());
                if (balance != null) {
                    balanceMap.put(user.getUserId(), balance);
                    // 年5日取得義務を満たしているかチェック（取得済み日数が5以上）
                    boolean obligationMet = balance.getUsedDays().compareTo(new BigDecimal("5.0")) >= 0;
                    obligationMetMap.put(user.getUserId(), obligationMet);
                } else {
                    // まだ付与されていない場合はダミーデータ（あるいは null のまま表示で対応）
                    PaidLeaveBalance emptyBalance = new PaidLeaveBalance();
                    emptyBalance.setGrantedDays(BigDecimal.ZERO);
                    emptyBalance.setUsedDays(BigDecimal.ZERO);
                    balanceMap.put(user.getUserId(), emptyBalance);
                    obligationMetMap.put(user.getUserId(), false);
                }
            }

            model.addAttribute("users", users);
            model.addAttribute("balanceMap", balanceMap);
            model.addAttribute("obligationMetMap", obligationMetMap);
            model.addAttribute("remainingTotalMap", remainingTotalMap);
            model.addAttribute("currentYear", currentYear);
        } catch (Exception e) {
            log.error("有給休暇取得状況画面の表示に失敗しました", e);
            model.addAttribute("error", "有給休暇取得状況画面の表示に失敗しました");
        }

        return "admin/leave-usage";
    }

    private List<User> filterUsersByDepartmentAndKeyword(List<User> users, String department, String keyword) {
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
        return users;
    }
}
