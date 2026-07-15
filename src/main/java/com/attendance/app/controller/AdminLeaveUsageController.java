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

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final UserService userService;
    private final PaidLeaveBalanceService paidLeaveBalanceService;
    private final WorkScheduleClassService workScheduleClassService;

    @GetMapping
    public String showLeaveUsage(
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size,
            Model model) {
        try {
            int pageSize = parsePageSize(size);
            long totalCount = userService.countUsers(department, keyword, true, false);
            int totalPages = Math.max(1, (int) Math.ceil((double) totalCount / pageSize));
            int currentPage = Math.min(parsePage(page), totalPages - 1);
            List<User> users = userService.getUsersPage(department, keyword, true, false,
                    (long) currentPage * pageSize, pageSize);
            model.addAttribute("department", department);
            model.addAttribute("keyword", keyword);
            model.addAttribute("page", currentPage);
            model.addAttribute("pageSize", pageSize);
            model.addAttribute("totalPages", totalPages);
            model.addAttribute("totalCount", totalCount);
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

    public String showLeaveUsage(String department, String keyword, Model model) {
        return showLeaveUsage(department, keyword, null, null, model);
    }

    private int parsePage(String page) {
        try {
            return Math.max(0, Integer.parseInt(page));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private int parsePageSize(String size) {
        try {
            return Math.min(MAX_PAGE_SIZE, Math.max(1, Integer.parseInt(size)));
        } catch (NumberFormatException e) {
            return DEFAULT_PAGE_SIZE;
        }
    }
}
