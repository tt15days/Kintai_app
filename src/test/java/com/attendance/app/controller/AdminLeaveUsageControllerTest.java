package com.attendance.app.controller;

import com.attendance.app.entity.PaidLeaveBalance;
import com.attendance.app.entity.User;
import com.attendance.app.service.PaidLeaveBalanceService;
import com.attendance.app.service.UserService;
import com.attendance.app.service.WorkScheduleClassService;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminLeaveUsageControllerTest")
class AdminLeaveUsageControllerTest {

    @Mock
    private UserService userService;

    @Mock
    private PaidLeaveBalanceService paidLeaveBalanceService;

    @Mock
    private WorkScheduleClassService workScheduleClassService;

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
        when(userService.countUsers(null, null, true, false)).thenReturn(3L);
        when(userService.getUsersPage(null, null, true, false, 0, 20)).thenReturn(List.of(user1, user2, user3));

        int currentYear = LocalDate.now().getYear();

        // 累計残日数 (#17) - ユーザーオブジェクトの paidLeaveDays フィールドを使用
        user1.setPaidLeaveDays(new BigDecimal("4.0"));
        user2.setPaidLeaveDays(new BigDecimal("6.5"));
        user3.setPaidLeaveDays(BigDecimal.ZERO);

        // ユーザー1：5日以上の取得義務を達成している (6.0日取得)
        PaidLeaveBalance balance1 = new PaidLeaveBalance();
        balance1.setUserId(1L);
        balance1.setGrantYear(currentYear);
        balance1.setGrantedDays(new BigDecimal("10.0"));
        balance1.setUsedDays(new BigDecimal("6.0"));

        // ユーザー2：取得日数が5日未満 (3.5日取得)
        PaidLeaveBalance balance2 = new PaidLeaveBalance();
        balance2.setUserId(2L);
        balance2.setGrantYear(currentYear);
        balance2.setGrantedDays(new BigDecimal("10.0"));
        balance2.setUsedDays(new BigDecimal("3.5"));

        // 有給残高を一括取得するモックを定義
        when(paidLeaveBalanceService.getByUsersAndYear(List.of(1L, 2L, 3L), currentYear))
                .thenReturn(List.of(balance1, balance2));

        ExtendedModelMap model = new ExtendedModelMap();
        String view = controller.showLeaveUsage(null, null, model);

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

        // remainingTotalMapの検証 (#17: 累計残日数)
        @SuppressWarnings("unchecked")
        Map<Long, BigDecimal> remainingTotalMap = (Map<Long, BigDecimal>) model.getAttribute("remainingTotalMap");
        assertNotNull(remainingTotalMap);
        assertEquals(new BigDecimal("4.0"), remainingTotalMap.get(1L));
        assertEquals(new BigDecimal("6.5"), remainingTotalMap.get(2L));
    }

    @Test
    @DisplayName("showLeaveUsage - 例外発生時はerror属性を設定して同一画面を返す")
    void testShowLeaveUsage_exception_setsErrorAttribute() {
        when(userService.countUsers(null, null, true, false)).thenThrow(new RuntimeException("DB Error"));

        ExtendedModelMap model = new ExtendedModelMap();
        String view = controller.showLeaveUsage(null, null, model);

        assertEquals("admin/leave-usage", view);
        assertEquals("有給休暇取得状況画面の表示に失敗しました", model.getAttribute("error"));
    }

    @Test
    @DisplayName("showLeaveUsage - 上限を超えるページサイズと範囲外ページを補正する")
    void testShowLeaveUsage_clampsPageAndSize() {
        when(userService.countUsers("開発", "田中", true, false)).thenReturn(21L);
        when(userService.getUsersPage("開発", "田中", true, false, 0, 100)).thenReturn(List.of(user1));

        ExtendedModelMap model = new ExtendedModelMap();
        controller.showLeaveUsage("開発", "田中", "99", "999", model);

        assertEquals(0, model.getAttribute("page"));
        assertEquals(100, model.getAttribute("pageSize"));
        assertEquals(1, model.getAttribute("totalPages"));
        verify(userService).getUsersPage("開発", "田中", true, false, 0, 100);
    }
}
