package com.attendance.app.service;

import com.attendance.app.entity.User;
import com.attendance.app.mapper.SystemSettingMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.CALLS_REAL_METHODS;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AutoGrantPaidLeaveServiceTest {

    @Mock
    private SystemSettingMapper systemSettingMapper;



    @Mock
    private UserService userService;

    @Mock
    private PaidLeaveBalanceService paidLeaveBalanceService;

    @InjectMocks
    private AutoGrantPaidLeaveService autoGrantPaidLeaveService;

    @Test
    void testGrantPaidLeaveBatch_Success() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Tokyo"));
        String todayStr = String.format("%02d-%02d", today.getMonthValue(), today.getDayOfMonth());

        when(systemSettingMapper.selectValueByKey("PAID_LEAVE_GRANT_DATE")).thenReturn(todayStr);
        when(systemSettingMapper.selectValueByKey("PAID_LEAVE_GRANT_DAYS")).thenReturn("20");

        User user1 = new User();
        user1.setUserId(101L);

        when(userService.getActiveUsers()).thenReturn(List.of(user1));
        when(paidLeaveBalanceService.getByUserAndYear(eq(101L), anyInt())).thenReturn(Optional.empty());

        autoGrantPaidLeaveService.grantPaidLeaveBatch();

        verify(userService).grantAnnualPaidLeave(101L, 20);
    }

    @Test
    void testGrantPaidLeaveBatch_NotGrantDate() {
        LocalDate today = LocalDate.now();
        LocalDate notToday = today.plusDays(1);
        String notTodayStr = String.format("%02d-%02d", notToday.getMonthValue(), notToday.getDayOfMonth());

        when(systemSettingMapper.selectValueByKey("PAID_LEAVE_GRANT_DATE")).thenReturn(notTodayStr);
        when(systemSettingMapper.selectValueByKey("PAID_LEAVE_GRANT_DAYS")).thenReturn("20");

        autoGrantPaidLeaveService.grantPaidLeaveBatch();

        verify(userService, never()).getActiveUsers();
        verify(userService, never()).grantAnnualPaidLeave(anyLong(), anyInt());
    }

    @Test
    void testGrantPaidLeaveBatch_SettingsNull() {
        when(systemSettingMapper.selectValueByKey("PAID_LEAVE_GRANT_DATE")).thenReturn(null);

        autoGrantPaidLeaveService.grantPaidLeaveBatch();

        verify(userService, never()).getActiveUsers();
        verify(userService, never()).grantAnnualPaidLeave(anyLong(), anyInt());
    }

    @Test
    void testGrantPaidLeaveBatch_ExceptionHandledSafely() {
        LocalDate today = LocalDate.now();
        String todayStr = String.format("%02d-%02d", today.getMonthValue(), today.getDayOfMonth());

        when(systemSettingMapper.selectValueByKey("PAID_LEAVE_GRANT_DATE")).thenReturn(todayStr);
        when(systemSettingMapper.selectValueByKey("PAID_LEAVE_GRANT_DAYS")).thenReturn("20");
        
        when(userService.getActiveUsers()).thenThrow(new RuntimeException("DB Error"));

        // Should not throw exception out of scheduled method
        autoGrantPaidLeaveService.grantPaidLeaveBatch();

        verify(userService, never()).grantAnnualPaidLeave(anyLong(), anyInt());
    }

    @Test
    void testGrantPaidLeaveBatch_LeapYear() {
        LocalDate leapDay = LocalDate.of(2024, 2, 29);
        String grantDateStr = "02-29";

        try (MockedStatic<LocalDate> mockedLocalDate = mockStatic(LocalDate.class, CALLS_REAL_METHODS)) {
            mockedLocalDate.when(() -> LocalDate.now(ZoneId.of("Asia/Tokyo"))).thenReturn(leapDay);

            when(systemSettingMapper.selectValueByKey("PAID_LEAVE_GRANT_DATE")).thenReturn(grantDateStr);
            when(systemSettingMapper.selectValueByKey("PAID_LEAVE_GRANT_DAYS")).thenReturn("20");

            User user1 = new User();
            user1.setUserId(101L);
            when(userService.getActiveUsers()).thenReturn(List.of(user1));
            when(paidLeaveBalanceService.getByUserAndYear(eq(101L), anyInt())).thenReturn(Optional.empty());

            autoGrantPaidLeaveService.grantPaidLeaveBatch();

            verify(userService).grantAnnualPaidLeave(101L, 20);
        }
    }
}
