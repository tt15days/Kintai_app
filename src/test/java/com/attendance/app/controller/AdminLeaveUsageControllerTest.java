package com.attendance.app.controller;

import com.attendance.app.entity.PaidLeaveBalance;
import com.attendance.app.entity.User;
import com.attendance.app.service.PaidLeaveBalanceService;
import com.attendance.app.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ExtendedModelMap;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminLeaveUsageControllerTest")
class AdminLeaveUsageControllerTest {

    @Mock
    private UserService userService;

    @Mock
    private PaidLeaveBalanceService paidLeaveBalanceService;

    @InjectMocks
    private AdminLeaveUsageController controller;

    private User user1;
    private User user2;
    private User user3;

    @BeforeEach
    void setUp() {
        user1 = User.builder().userId(1L).fullName("ユーザー1").build();
        user2 = User.builder().userId(2L).fullName("ユーザー2").build();
        user3 = User.builder().userId(3L).fullName("ユーザー3").build();
    }

    @Test
    @DisplayName("showLeaveUsage - 有給取得状況画面の表示において、ユーザー全員の残高および義務達成状況が正しくモデルに格納されること")
    void testShowLeaveUsage() {
        // 全ユーザーを返す
        when(userService.getAllUsers()).thenReturn(List.of(user1, user2, user3));

        int currentYear = LocalDate.now().getYear();

        // ユーザー1：5日以上の取得義務を達成している (6.0日取得)
        PaidLeaveBalance balance1 = new PaidLeaveBalance();
        balance1.setUserId(1L);
        balance1.setGrantYear(currentYear);
        balance1.setGrantedDays(new BigDecimal("10.0"));
        balance1.setUsedDays(new BigDecimal("6.0"));
        when(paidLeaveBalanceService.getByUserAndYear(1L, currentYear)).thenReturn(Optional.of(balance1));

        // ユーザー2：取得日数が5日未満 (3.5日取得)
        PaidLeaveBalance balance2 = new PaidLeaveBalance();
        balance2.setUserId(2L);
        balance2.setGrantYear(currentYear);
        balance2.setGrantedDays(new BigDecimal("10.0"));
        balance2.setUsedDays(new BigDecimal("3.5"));
        when(paidLeaveBalanceService.getByUserAndYear(2L, currentYear)).thenReturn(Optional.of(balance2));

        // ユーザー3：有給休暇残高のレコードが存在しない
        when(paidLeaveBalanceService.getByUserAndYear(3L, currentYear)).thenReturn(Optional.empty());

        ExtendedModelMap model = new ExtendedModelMap();
        String view = controller.showLeaveUsage(model);

        assertEquals("admin/leave-usage", view);
        assertEquals(currentYear, model.getAttribute("currentYear"));

        // usersリストの検証
        @SuppressWarnings("unchecked")
        List<User> usersInModel = (List<User>) model.getAttribute("users");
        assertNotNull(usersInModel);
        assertEquals(3, usersInModel.size());

        // balanceMapの検証
        @SuppressWarnings("unchecked")
        Map<Long, PaidLeaveBalance> balanceMap = (Map<Long, PaidLeaveBalance>) model.getAttribute("balanceMap");
        assertNotNull(balanceMap);
        assertEquals(balance1, balanceMap.get(1L));
        assertEquals(balance2, balanceMap.get(2L));
        // レコードがないユーザー3にはダミーデータ(0日)がセットされること
        PaidLeaveBalance emptyBalance = balanceMap.get(3L);
        assertNotNull(emptyBalance);
        assertEquals(BigDecimal.ZERO, emptyBalance.getGrantedDays());
        assertEquals(BigDecimal.ZERO, emptyBalance.getUsedDays());

        // obligationMetMapの検証
        @SuppressWarnings("unchecked")
        Map<Long, Boolean> obligationMetMap = (Map<Long, Boolean>) model.getAttribute("obligationMetMap");
        assertNotNull(obligationMetMap);
        assertTrue(obligationMetMap.get(1L));  // 6.0 >= 5.0
        assertFalse(obligationMetMap.get(2L)); // 3.5 < 5.0
        assertFalse(obligationMetMap.get(3L)); // レコードなし -> false
    }
}
