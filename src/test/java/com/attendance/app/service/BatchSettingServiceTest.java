package com.attendance.app.service;

import com.attendance.app.mapper.SystemSettingMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class BatchSettingServiceTest {

    @Mock
    private SystemSettingMapper systemSettingMapper;

    @InjectMocks
    private BatchSettingService batchSettingService;

    @Test
    void testGettersWithValidValues() {
        when(systemSettingMapper.selectValueByKey(BatchSettingService.KEY_MONTHLY_SUMMARY_DAYS_AFTER_END)).thenReturn("10");
        when(systemSettingMapper.selectValueByKey(BatchSettingService.KEY_REMINDER_DAY)).thenReturn("25");
        when(systemSettingMapper.selectValueByKey(BatchSettingService.KEY_REMINDER_HOUR)).thenReturn("18");

        assertEquals(10, batchSettingService.getMonthlySummaryDaysAfterEnd());
        assertEquals(25, batchSettingService.getReminderDay());
        assertEquals(18, batchSettingService.getReminderHour());
    }

    @Test
    void testGettersWithNullValuesShouldReturnDefaults() {
        when(systemSettingMapper.selectValueByKey(anyString())).thenReturn(null);

        assertEquals(5, batchSettingService.getMonthlySummaryDaysAfterEnd());
        assertEquals(20, batchSettingService.getReminderDay());
        assertEquals(9, batchSettingService.getReminderHour());
    }

    @Test
    void testGettersWithInvalidNumberFormatShouldReturnDefaults() {
        when(systemSettingMapper.selectValueByKey(anyString())).thenReturn("abc");

        assertEquals(5, batchSettingService.getMonthlySummaryDaysAfterEnd());
    }

    @Test
    void testUpdateSettings_Success() {
        batchSettingService.updateSettings(10, 25, 18);

        verify(systemSettingMapper).upsertValue(BatchSettingService.KEY_MONTHLY_SUMMARY_DAYS_AFTER_END, "10");
        verify(systemSettingMapper).upsertValue(BatchSettingService.KEY_REMINDER_DAY, "25");
        verify(systemSettingMapper).upsertValue(BatchSettingService.KEY_REMINDER_HOUR, "18");
    }

    @Test
    void testUpdateSettings_ValidationFailures() {
        assertThrows(IllegalArgumentException.class, () -> batchSettingService.updateSettings(0, 25, 18));
        assertThrows(IllegalArgumentException.class, () -> batchSettingService.updateSettings(15, 25, 18));
        assertThrows(IllegalArgumentException.class, () -> batchSettingService.updateSettings(10, 0, 18));
        assertThrows(IllegalArgumentException.class, () -> batchSettingService.updateSettings(10, 29, 18));
        assertThrows(IllegalArgumentException.class, () -> batchSettingService.updateSettings(10, 25, -1));
        assertThrows(IllegalArgumentException.class, () -> batchSettingService.updateSettings(10, 25, 24));
        
        verify(systemSettingMapper, never()).upsertValue(anyString(), anyString());
    }

    @Test
    void testUpdateAlertSettings_RejectsUiLimitBypass() {
        assertThrows(IllegalArgumentException.class,
                () -> batchSettingService.updateAlertSettings(101, 120, 9, 3, 11));
        assertThrows(IllegalArgumentException.class,
                () -> batchSettingService.updateAlertSettings(30, 151, 9, 3, 11));

        verify(systemSettingMapper, never()).upsertValue(anyString(), anyString());
    }

    @Test
    void testGetLastExecutedAt() {
        when(systemSettingMapper.selectValueByKey(BatchSettingService.KEY_LAST_MONTHLY_SUMMARY_EXECUTED_AT))
                .thenReturn("2026-05-10T15:30:00");
        
        LocalDateTime time = batchSettingService.getLastMonthlySummaryExecutedAt();
        assertNotNull(time);
        assertEquals(2026, time.getYear());
        assertEquals(5, time.getMonthValue());
        
        // Null test
        when(systemSettingMapper.selectValueByKey(BatchSettingService.KEY_LAST_ANNUAL_LEAVE_GRANT_EXECUTED_AT))
                .thenReturn(null);
        assertNull(batchSettingService.getLastAnnualLeaveGrantExecutedAt());
        
        // Invalid format test
        when(systemSettingMapper.selectValueByKey(BatchSettingService.KEY_LAST_REMINDER_EXECUTED_AT))
                .thenReturn("invalid date");
        assertNull(batchSettingService.getLastReminderExecutedAt());
    }

    @Test
    void testRecordExecutedAt() {
        LocalDateTime now = LocalDateTime.now();
        batchSettingService.recordMonthlySummaryExecutedAt(now);
        batchSettingService.recordAnnualLeaveGrantExecutedAt(now);
        batchSettingService.recordReminderExecutedAt(now);

        verify(systemSettingMapper).upsertValue(eq(BatchSettingService.KEY_LAST_MONTHLY_SUMMARY_EXECUTED_AT), eq(now.toString()));
        verify(systemSettingMapper).upsertValue(eq(BatchSettingService.KEY_LAST_ANNUAL_LEAVE_GRANT_EXECUTED_AT), eq(now.toString()));
        verify(systemSettingMapper).upsertValue(eq(BatchSettingService.KEY_LAST_REMINDER_EXECUTED_AT), eq(now.toString()));
    }
}
