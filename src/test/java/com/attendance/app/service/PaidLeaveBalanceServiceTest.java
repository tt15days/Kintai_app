package com.attendance.app.service;

import com.attendance.app.entity.PaidLeaveBalance;
import com.attendance.app.mapper.PaidLeaveBalanceMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaidLeaveBalanceService")
class PaidLeaveBalanceServiceTest {

    @Mock
    private PaidLeaveBalanceMapper paidLeaveBalanceMapper;

    @InjectMocks
    private PaidLeaveBalanceService service;

    @Test
    @DisplayName("残高減算は失効日の早い順（FIFO）で消費する")
    void deductBalance_consumesEarliestExpiryFirst() {
        PaidLeaveBalance first = balance(1L, 2025, LocalDate.of(2026, 3, 31), "5.0", "0.0");
        PaidLeaveBalance second = balance(1L, 2026, LocalDate.of(2027, 3, 31), "10.0", "0.0");

        LocalDate today = LocalDate.now();
        when(paidLeaveBalanceMapper.selectActiveByUserIdForUpdate(1L, today))
                .thenReturn(List.of(first, second));

        service.deductBalance(1L, new BigDecimal("6.0"), today);

        ArgumentCaptor<PaidLeaveBalance> captor = ArgumentCaptor.forClass(PaidLeaveBalance.class);
        verify(paidLeaveBalanceMapper, times(2)).update(captor.capture());
        List<PaidLeaveBalance> updated = captor.getAllValues();

        assertThat(updated.get(0).getGrantYear()).isEqualTo(2025);
        assertThat(updated.get(0).getUsedDays()).isEqualByComparingTo("5.0");
        assertThat(updated.get(1).getGrantYear()).isEqualTo(2026);
        assertThat(updated.get(1).getUsedDays()).isEqualByComparingTo("1.0");
    }

    @Test
    @DisplayName("残高不足時は利用可能分だけ減算して終了する")
    void deductBalance_withInsufficientBalance_deductsAvailableOnly() {
        PaidLeaveBalance only = balance(2L, 2026, LocalDate.of(2027, 3, 31), "2.0", "0.0");

        LocalDate today = LocalDate.now();
        when(paidLeaveBalanceMapper.selectActiveByUserIdForUpdate(2L, today))
                .thenReturn(List.of(only));

        service.deductBalance(2L, new BigDecimal("3.0"), today);

        verify(paidLeaveBalanceMapper).update(only);
        assertThat(only.getUsedDays()).isEqualByComparingTo("2.0");
    }

    @Test
    @DisplayName("有効残高の合計日数を返す")
    void getTotalRemainingDays_sumsAllActiveBalances() {
        PaidLeaveBalance a = balance(3L, 2025, LocalDate.of(2026, 3, 31), "5.0", "1.0");
        PaidLeaveBalance b = balance(3L, 2026, LocalDate.of(2027, 3, 31), "10.0", "2.5");

        when(paidLeaveBalanceMapper.selectActiveByUserId(3L, LocalDate.now()))
                .thenReturn(List.of(a, b));

        BigDecimal total = service.getTotalRemainingDays(3L);

        assertThat(total).isEqualByComparingTo("11.5");
    }

    @Test
    @DisplayName("残高返還は失効日の遅い順（LIFO）で used_days を減算する")
    void refundBalance_refundsLatestExpiryFirst() {
        PaidLeaveBalance first = balance(1L, 2025, LocalDate.of(2026, 3, 31), "5.0", "5.0");
        PaidLeaveBalance second = balance(1L, 2026, LocalDate.of(2027, 3, 31), "10.0", "1.0");

        LocalDate targetDate = LocalDate.of(2025, 6, 1);
        when(paidLeaveBalanceMapper.selectActiveByUserIdForUpdate(1L, targetDate))
                .thenReturn(List.of(first, second));

        service.refundBalance(1L, new BigDecimal("2.0"), targetDate);

        ArgumentCaptor<PaidLeaveBalance> captor = ArgumentCaptor.forClass(PaidLeaveBalance.class);
        verify(paidLeaveBalanceMapper, times(2)).update(captor.capture());
        List<PaidLeaveBalance> updated = captor.getAllValues();

        assertThat(updated.get(0).getGrantYear()).isEqualTo(2026);
        assertThat(updated.get(0).getUsedDays()).isEqualByComparingTo("0.0");
        assertThat(updated.get(1).getGrantYear()).isEqualTo(2025);
        assertThat(updated.get(1).getUsedDays()).isEqualByComparingTo("4.0");
    }

    private PaidLeaveBalance balance(Long userId, int grantYear, LocalDate expiry, String granted, String used) {
        return PaidLeaveBalance.builder()
                .userId(userId)
                .grantYear(grantYear)
                .grantDate(LocalDate.of(grantYear, 4, 1))
                .expiryDate(expiry)
                .grantedDays(new BigDecimal(granted))
                .carriedOverDays(BigDecimal.ZERO)
                .usedDays(new BigDecimal(used))
                .build();
    }
}
