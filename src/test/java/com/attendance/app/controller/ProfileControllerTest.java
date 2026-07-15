package com.attendance.app.controller;

import com.attendance.app.entity.User;
import com.attendance.app.security.SecurityUtil;
import com.attendance.app.service.LeaveApplicationService;
import com.attendance.app.service.PaidLeaveBalanceService;
import com.attendance.app.service.UserService;
import com.attendance.app.service.WorkScheduleClassService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.math.BigDecimal;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProfileController Unit Tests")
class ProfileControllerTest {

    @Mock
    private WorkScheduleClassService workScheduleClassService;

    @Mock
    private SecurityUtil securityUtil;

    @Mock
    private LeaveApplicationService leaveApplicationService;

    @Mock
    private PaidLeaveBalanceService paidLeaveBalanceService;

    @Mock
    private UserService userService;

    @InjectMocks
    private ProfileController controller;

    @Test
    @DisplayName("プロフィールに勤務クラス自己更新POSTを公開する")
    void profile_exposesWorkScheduleUpdatePost() {
        boolean exposed = java.util.Arrays.stream(ProfileController.class.getDeclaredMethods())
                .map(method -> method.getAnnotation(PostMapping.class))
                .filter(java.util.Objects::nonNull)
                .flatMap(mapping -> java.util.Arrays.stream(mapping.value()))
                .anyMatch("/work-schedule"::equals);

        assertThat(exposed).isTrue();
    }

    @Test
    @DisplayName("本人は承認なしで勤務クラスを変更できる")
    void updateWorkSchedule_updatesCurrentUserClass() {
        User user = User.builder().userId(1L).build();
        when(securityUtil.getCurrentUser()).thenReturn(user);
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        String view = controller.updateWorkSchedule("標準勤務", redirectAttributes);

        assertThat(view).isEqualTo("redirect:/profile");
        verify(userService).updateWorkScheduleClass(1L, "標準勤務");
        assertThat(redirectAttributes.getFlashAttributes().get("successMessage"))
                .isEqualTo("勤務クラスを変更しました");
    }

    @Test
    @DisplayName("showProfile: 有給付与日数がnullの場合、正常にプロフィール画面を表示し、残日数を0として計算すること")
    void showProfile_withNullPaidLeaveDays_success() {
        // Arrange
        User user = new User();
        user.setUserId(1L);
        user.setPaidLeaveDays(null);

        when(securityUtil.getCurrentUser()).thenReturn(user);
        when(leaveApplicationService.calculateYearlyUsedPaidLeaveDays(eq(1L), anyInt())).thenReturn(new BigDecimal("2"));
        when(paidLeaveBalanceService.getBalancesByUserId(1L)).thenReturn(Collections.emptyList());
        when(paidLeaveBalanceService.getTotalRemainingDays(1L)).thenReturn(BigDecimal.ZERO);

        ExtendedModelMap model = new ExtendedModelMap();

        // Act
        String view = controller.showProfile(model);

        // Assert
        assertThat(view).isEqualTo("user/profile");
        assertThat(model.get("currentUser")).isEqualTo(user);
        assertThat(model.get("yearlyUsedPaidLeaveDays")).isEqualTo(new BigDecimal("2"));
        // 残日数は paid_leave_balance ベース（getTotalRemainingDays）に統一
        assertThat((BigDecimal) model.get("remainingPaidLeaveDays")).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(model.get("totalRemainingPaidLeaveDays")).isEqualTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("showProfile: 有給付与日数が存在する場合、正常に残日数が計算されること")
    void showProfile_withExistingPaidLeaveDays_success() {
        // Arrange
        User user = new User();
        user.setUserId(1L);
        user.setPaidLeaveDays(new BigDecimal("10.5"));

        when(securityUtil.getCurrentUser()).thenReturn(user);
        when(leaveApplicationService.calculateYearlyUsedPaidLeaveDays(eq(1L), anyInt())).thenReturn(new BigDecimal("3"));
        when(paidLeaveBalanceService.getBalancesByUserId(1L)).thenReturn(Collections.emptyList());
        when(paidLeaveBalanceService.getTotalRemainingDays(1L)).thenReturn(new BigDecimal("7.5"));

        ExtendedModelMap model = new ExtendedModelMap();

        // Act
        String view = controller.showProfile(model);

        // Assert
        assertThat(view).isEqualTo("user/profile");
        assertThat(model.get("currentUser")).isEqualTo(user);
        assertThat(model.get("yearlyUsedPaidLeaveDays")).isEqualTo(new BigDecimal("3"));
        // 残日数は paid_leave_balance ベース（getTotalRemainingDays）に統一
        assertThat((BigDecimal) model.get("remainingPaidLeaveDays")).isEqualByComparingTo(new BigDecimal("7.5"));
        assertThat(model.get("totalRemainingPaidLeaveDays")).isEqualTo(new BigDecimal("7.5"));
    }

    @Test
    @DisplayName("showProfile: 例外発生時にエラーメッセージが設定されること")
    void showProfile_exception_returnsErrorInModel() {
        // Arrange
        when(securityUtil.getCurrentUser()).thenThrow(new RuntimeException("Database error"));

        ExtendedModelMap model = new ExtendedModelMap();

        // Act
        String view = controller.showProfile(model);

        // Assert
        assertThat(view).isEqualTo("user/profile");
        assertThat(model.get("error")).isEqualTo("プロフィール画面の表示に失敗しました");
    }

}
