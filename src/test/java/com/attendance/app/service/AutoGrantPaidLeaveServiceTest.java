package com.attendance.app.service;

import com.attendance.app.entity.User;
import com.attendance.app.mapper.SystemSettingMapper;
import com.attendance.app.util.DateTimeUtil;
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
        when(paidLeaveBalanceService.getByUsersAndYear(anyList(), anyInt())).thenReturn(List.of());

        autoGrantPaidLeaveService.grantPaidLeaveBatch();

        verify(userService).grantAnnualPaidLeave(101L, 20);
    }

    @Test
    void testGrantPaidLeaveBatch_NotGrantDate() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Tokyo"));
        LocalDate notToday = today.plusDays(1);
        String notTodayStr = String.format("%02d-%02d", notToday.getMonthValue(), notToday.getDayOfMonth());

        when(systemSettingMapper.selectValueByKey("PAID_LEAVE_GRANT_DATE")).thenReturn(notTodayStr);
        when(systemSettingMapper.selectValueByKey("PAID_LEAVE_GRANT_DAYS")).thenReturn("20");

        autoGrantPaidLeaveService.grantPaidLeaveBatch();

        verify(userService, never()).getActiveUsers();
        verify(userService, never()).grantAnnualPaidLeave(anyLong(), anyInt());
    }

    @Test
    void testGrantPaidLeaveBatch_SettingsNull_UsesDefaults() {
        LocalDate defaultGrantDate = LocalDate.of(2026, 4, 1);
        when(systemSettingMapper.selectValueByKey(SystemSettingService.PAID_LEAVE_GRANT_DATE_KEY)).thenReturn(null);
        when(systemSettingMapper.selectValueByKey(SystemSettingService.PAID_LEAVE_GRANT_DAYS_KEY)).thenReturn(null);
        User user = new User();
        user.setUserId(101L);
        when(userService.getActiveUsers()).thenReturn(List.of(user));
        when(paidLeaveBalanceService.getByUsersAndYear(anyList(), anyInt())).thenReturn(List.of());

        try (MockedStatic<DateTimeUtil> mockedDateTimeUtil = mockStatic(DateTimeUtil.class, CALLS_REAL_METHODS)) {
            mockedDateTimeUtil.when(DateTimeUtil::todayJapan).thenReturn(defaultGrantDate);
            autoGrantPaidLeaveService.grantPaidLeaveBatch();
        }

        verify(userService).grantAnnualPaidLeave(101L, 10);
    }

    @Test
    void testGrantPaidLeaveBatch_ExceptionHandledSafely() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Tokyo"));
        String todayStr = String.format("%02d-%02d", today.getMonthValue(), today.getDayOfMonth());

        when(systemSettingMapper.selectValueByKey("PAID_LEAVE_GRANT_DATE")).thenReturn(todayStr);
        when(systemSettingMapper.selectValueByKey("PAID_LEAVE_GRANT_DAYS")).thenReturn("20");
        
        when(userService.getActiveUsers()).thenThrow(new RuntimeException("DB Error"));

        // Should not throw exception out of scheduled method
        autoGrantPaidLeaveService.grantPaidLeaveBatch();

        verify(userService, never()).grantAnnualPaidLeave(anyLong(), anyInt());
    }

    @Test
    void testGrantPaidLeaveBatch_LeapDayGrantDate_GrantsOnLeapDay() {
        // 付与日設定が "02-29" の場合、うるう年の2/29には実際に付与される
        LocalDate leapDay = LocalDate.of(2024, 2, 29);
        String grantDateStr = "02-29";

        try (MockedStatic<DateTimeUtil> mockedDateTimeUtil = mockStatic(DateTimeUtil.class, CALLS_REAL_METHODS)) {
            mockedDateTimeUtil.when(DateTimeUtil::todayJapan).thenReturn(leapDay);

            when(systemSettingMapper.selectValueByKey("PAID_LEAVE_GRANT_DATE")).thenReturn(grantDateStr);
            when(systemSettingMapper.selectValueByKey("PAID_LEAVE_GRANT_DAYS")).thenReturn("20");

            User user1 = new User();
            user1.setUserId(101L);
            when(userService.getActiveUsers()).thenReturn(List.of(user1));
            when(paidLeaveBalanceService.getByUsersAndYear(anyList(), anyInt())).thenReturn(List.of());

            autoGrantPaidLeaveService.grantPaidLeaveBatch();

            verify(userService).grantAnnualPaidLeave(101L, 20);
        }
    }

    @Test
    void testGrantPaidLeaveBatch_LeapDayGrantDate_NotGrantedInNonLeapYear() {
        // 付与日設定が "02-29" でも、非うるう年には2/29という日が存在しないため
        // 付与日は一度も到来せず、その年は付与が行われない（3/1になっても付与されない）
        LocalDate dayAfterFeb28NonLeapYear = LocalDate.of(2025, 3, 1);
        String grantDateStr = "02-29";

        try (MockedStatic<DateTimeUtil> mockedDateTimeUtil = mockStatic(DateTimeUtil.class, CALLS_REAL_METHODS)) {
            mockedDateTimeUtil.when(DateTimeUtil::todayJapan).thenReturn(dayAfterFeb28NonLeapYear);

            when(systemSettingMapper.selectValueByKey("PAID_LEAVE_GRANT_DATE")).thenReturn(grantDateStr);
            when(systemSettingMapper.selectValueByKey("PAID_LEAVE_GRANT_DAYS")).thenReturn("20");

            autoGrantPaidLeaveService.grantPaidLeaveBatch();

            verify(userService, never()).getActiveUsers();
            verify(userService, never()).grantAnnualPaidLeave(anyLong(), anyInt());
        }
    }
}
