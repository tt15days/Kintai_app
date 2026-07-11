package com.attendance.app.integration;

import com.attendance.app.entity.PaidLeaveBalance;
import com.attendance.app.entity.User;
import com.attendance.app.entity.UserRole;
import com.attendance.app.mapper.PaidLeaveBalanceMapper;
import com.attendance.app.mapper.UserMapper;
import com.attendance.app.service.PaidLeaveBalanceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = com.attendance.app.AttendanceApplication.class)
@ActiveProfiles("integration")
@Transactional
@DisplayName("PaidLeaveBalanceService Integration")
class PaidLeaveBalanceServiceIntegrationTest {

    @Autowired
    private PaidLeaveBalanceService paidLeaveBalanceService;

    @Autowired
    private PaidLeaveBalanceMapper paidLeaveBalanceMapper;

    @Autowired
    private UserMapper userMapper;

    @Test
    @DisplayName("失効日の早い残高からFIFOで減算される")
    void deductBalance_fifo_updatesRowsInOrder() {
        Long userId = createIntegrationUser();

        paidLeaveBalanceMapper.insert(PaidLeaveBalance.builder()
                .userId(userId)
                .grantYear(2097)
                .grantedDays(new BigDecimal("5.0"))
                .grantDate(LocalDate.of(2097, 4, 1))
                .expiryDate(LocalDate.of(2098, 3, 31))
                .carriedOverDays(BigDecimal.ZERO)
                .usedDays(BigDecimal.ZERO)
                .build());
        paidLeaveBalanceMapper.insert(PaidLeaveBalance.builder()
                .userId(userId)
                .grantYear(2098)
                .grantedDays(new BigDecimal("10.0"))
                .grantDate(LocalDate.of(2098, 4, 1))
                .expiryDate(LocalDate.of(2099, 3, 31))
                .carriedOverDays(BigDecimal.ZERO)
                .usedDays(BigDecimal.ZERO)
                .build());

        paidLeaveBalanceService.deductBalance(userId, new BigDecimal("6.0"), LocalDate.now());

        PaidLeaveBalance first = paidLeaveBalanceService.getByUserAndYear(userId, 2097).orElseThrow();
        PaidLeaveBalance second = paidLeaveBalanceService.getByUserAndYear(userId, 2098).orElseThrow();

        assertThat(first.getUsedDays()).isEqualByComparingTo("5.0");
        assertThat(second.getUsedDays()).isEqualByComparingTo("1.0");
    }

    @Test
    @DisplayName("有効残高合計はremainingDaysの合算になる")
    void getTotalRemainingDays_sumsActiveBalances() {
        Long userId = createIntegrationUser();

        paidLeaveBalanceMapper.insert(PaidLeaveBalance.builder()
                .userId(userId)
                .grantYear(2099)
                .grantedDays(new BigDecimal("8.0"))
                .grantDate(LocalDate.of(2099, 4, 1))
                .expiryDate(LocalDate.of(2100, 3, 31))
                .carriedOverDays(new BigDecimal("2.0"))
                .usedDays(new BigDecimal("3.5"))
                .build());

        assertThat(paidLeaveBalanceService.getTotalRemainingDays(userId)).isEqualByComparingTo("6.5");
    }

    @Test
    @DisplayName("返還時は新しい有給から順に used_days が差し引かれる")
    void refundBalance_refundsRecentBalances() {
        Long userId = createIntegrationUser();
        paidLeaveBalanceMapper.insert(PaidLeaveBalance.builder()
                .userId(userId)
                .grantYear(2097)
                .grantedDays(new BigDecimal("10.0"))
                .grantDate(LocalDate.of(2097, 4, 1))
                .expiryDate(LocalDate.of(2098, 3, 31))
                .carriedOverDays(BigDecimal.ZERO)
                .usedDays(new BigDecimal("5.0"))
                .build());
        paidLeaveBalanceMapper.insert(PaidLeaveBalance.builder()
                .userId(userId)
                .grantYear(2098)
                .grantedDays(new BigDecimal("10.0"))
                .grantDate(LocalDate.of(2098, 4, 1))
                .expiryDate(LocalDate.of(2099, 3, 31))
                .carriedOverDays(BigDecimal.ZERO)
                .usedDays(new BigDecimal("1.0"))
                .build());

        paidLeaveBalanceService.refundBalance(userId, new BigDecimal("2.0"), LocalDate.of(2097, 6, 1));

        PaidLeaveBalance first = paidLeaveBalanceService.getByUserAndYear(userId, 2097).orElseThrow();
        PaidLeaveBalance second = paidLeaveBalanceService.getByUserAndYear(userId, 2098).orElseThrow();

        assertThat(first.getUsedDays()).isEqualByComparingTo("4.0");
        assertThat(second.getUsedDays()).isEqualByComparingTo("0.0");
    }

    private Long createIntegrationUser() {
        String email = "it-leave-" + UUID.randomUUID() + "@example.com";
        User user = User.builder()
                .empNo("IT-" + UUID.randomUUID().toString().substring(0, 8))
                .email(email)
                .password("$2a$10$testtesttesttesttesttesttesttesttesttesttesttesttest")
                .passwordResetRequired(false)
                .fullName("IT Leave User")
                .userRole(UserRole.USER)
                .canApproveAttendance(false)
                .isActive(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        userMapper.insert(user);
        return user.getUserId();
    }
}
