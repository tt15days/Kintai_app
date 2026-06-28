package com.attendance.app.controller;

import com.attendance.app.entity.User;
import com.attendance.app.entity.WorkScheduleClass;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProfileController Unit Tests")
class ProfileControllerTest {

    @Mock
    private UserService userService;

    @Mock
    private WorkScheduleClassService workScheduleClassService;

    @Mock
    private SecurityUtil securityUtil;

    @Mock
    private LeaveApplicationService leaveApplicationService;

    @Mock
    private PaidLeaveBalanceService paidLeaveBalanceService;

    @InjectMocks
    private ProfileController controller;

    @Test
    @DisplayName("showProfile: 有給付与日数がnullの場合、正常にプロフィール画面を表示し、残日数を0として計算すること")
    void showProfile_withNullPaidLeaveDays_success() {
        // Arrange
        User user = new User();
        user.setUserId(1L);
        user.setPaidLeaveDays(null);

        when(securityUtil.getCurrentUser()).thenReturn(user);
        when(workScheduleClassService.getAllClasses()).thenReturn(Collections.emptyList());
        when(leaveApplicationService.calculateYearlyUsedPaidLeaveDays(eq(1L), anyInt())).thenReturn(2L);
        when(paidLeaveBalanceService.getBalancesByUserId(1L)).thenReturn(Collections.emptyList());
        when(paidLeaveBalanceService.getTotalRemainingDays(1L)).thenReturn(BigDecimal.ZERO);

        ExtendedModelMap model = new ExtendedModelMap();

        // Act
        String view = controller.showProfile(model);

        // Assert
        assertThat(view).isEqualTo("user/profile");
        assertThat(model.get("currentUser")).isEqualTo(user);
        assertThat(model.get("yearlyUsedPaidLeaveDays")).isEqualTo(2L);
        assertThat(model.get("remainingPaidLeaveDays")).isEqualTo(new BigDecimal("-2")); // 0 - 2 = -2
        assertThat(model.get("workScheduleClasses")).isEqualTo(Collections.emptyList());
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
        when(workScheduleClassService.getAllClasses()).thenReturn(List.of(new WorkScheduleClass()));
        when(leaveApplicationService.calculateYearlyUsedPaidLeaveDays(eq(1L), anyInt())).thenReturn(3L);
        when(paidLeaveBalanceService.getBalancesByUserId(1L)).thenReturn(Collections.emptyList());
        when(paidLeaveBalanceService.getTotalRemainingDays(1L)).thenReturn(new BigDecimal("7.5"));

        ExtendedModelMap model = new ExtendedModelMap();

        // Act
        String view = controller.showProfile(model);

        // Assert
        assertThat(view).isEqualTo("user/profile");
        assertThat(model.get("currentUser")).isEqualTo(user);
        assertThat(model.get("yearlyUsedPaidLeaveDays")).isEqualTo(3L);
        assertThat(model.get("remainingPaidLeaveDays")).isEqualTo(new BigDecimal("7.5")); // 10.5 - 3 = 7.5
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

    @Test
    @DisplayName("updateWorkScheduleClass: 所属クラスを正常に更新できること")
    void updateWorkScheduleClass_success() {
        // Arrange
        when(securityUtil.getCurrentUserId()).thenReturn(1L);
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // Act
        String view = controller.updateWorkScheduleClass("A_CLASS", redirectAttributes);

        // Assert
        assertThat(view).isEqualTo("redirect:/profile");
        assertThat(redirectAttributes.getFlashAttributes().get("successMessage")).isEqualTo("所属クラスを更新しました");
        verify(userService).updateWorkScheduleClass(1L, "A_CLASS");
    }

    @Test
    @DisplayName("updateWorkScheduleClass: IllegalArgumentException 発生時のハンドリング")
    void updateWorkScheduleClass_invalidArg_returnsError() {
        // Arrange
        when(securityUtil.getCurrentUserId()).thenReturn(1L);
        doThrow(new IllegalArgumentException("無効なクラス名です")).when(userService).updateWorkScheduleClass(anyLong(), any());
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // Act
        String view = controller.updateWorkScheduleClass("INVALID", redirectAttributes);

        // Assert
        assertThat(view).isEqualTo("redirect:/profile");
        assertThat(redirectAttributes.getFlashAttributes().get("errorMessage")).isEqualTo("無効なクラス名です");
    }

    @Test
    @DisplayName("updateWorkScheduleClass: その他例外発生時のハンドリング")
    void updateWorkScheduleClass_exception_returnsGeneralError() {
        // Arrange
        when(securityUtil.getCurrentUserId()).thenReturn(1L);
        doThrow(new RuntimeException("DB error")).when(userService).updateWorkScheduleClass(anyLong(), any());
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        // Act
        String view = controller.updateWorkScheduleClass("A_CLASS", redirectAttributes);

        // Assert
        assertThat(view).isEqualTo("redirect:/profile");
        assertThat(redirectAttributes.getFlashAttributes().get("errorMessage")).isEqualTo("所属クラスの更新に失敗しました");
    }
}
