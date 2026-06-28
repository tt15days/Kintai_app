package com.attendance.app.service;

import com.attendance.app.mapper.SystemSettingMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AttendancePeriodSettingServiceTest {

    @Mock
    private SystemSettingMapper systemSettingMapper;

    @InjectMocks
    private AttendancePeriodSettingService attendancePeriodSettingService;

    @Test
    void testGetStartDay_Valid() {
        when(systemSettingMapper.selectValueByKey(AttendancePeriodSettingService.SETTING_KEY_START_DAY))
                .thenReturn("15");
        assertEquals(15, attendancePeriodSettingService.getStartDay());
    }

    @Test
    void testGetStartDay_NullReturnsDefault() {
        when(systemSettingMapper.selectValueByKey(AttendancePeriodSettingService.SETTING_KEY_START_DAY))
                .thenReturn(null);
        assertEquals(21, attendancePeriodSettingService.getStartDay());
    }

    @Test
    void testGetStartDay_EmptyReturnsDefault() {
        when(systemSettingMapper.selectValueByKey(AttendancePeriodSettingService.SETTING_KEY_START_DAY))
                .thenReturn("  ");
        assertEquals(21, attendancePeriodSettingService.getStartDay());
    }

    @Test
    void testGetStartDay_InvalidFormatReturnsDefault() {
        when(systemSettingMapper.selectValueByKey(AttendancePeriodSettingService.SETTING_KEY_START_DAY))
                .thenReturn("abc");
        assertEquals(21, attendancePeriodSettingService.getStartDay());
    }

    @Test
    void testGetStartDay_OutOfRangeReturnsDefault() {
        when(systemSettingMapper.selectValueByKey(AttendancePeriodSettingService.SETTING_KEY_START_DAY))
                .thenReturn("29");
        assertEquals(21, attendancePeriodSettingService.getStartDay());

        when(systemSettingMapper.selectValueByKey(AttendancePeriodSettingService.SETTING_KEY_START_DAY))
                .thenReturn("0");
        assertEquals(21, attendancePeriodSettingService.getStartDay());
    }

    @Test
    void testGetEndDay_Valid() {
        when(systemSettingMapper.selectValueByKey(AttendancePeriodSettingService.SETTING_KEY_END_DAY))
                .thenReturn("14");
        assertEquals(14, attendancePeriodSettingService.getEndDay());
    }

    @Test
    void testUpdatePeriod_Success() {
        attendancePeriodSettingService.updatePeriod(15, 14);

        verify(systemSettingMapper).upsertValue(AttendancePeriodSettingService.SETTING_KEY_START_DAY, "15");
        verify(systemSettingMapper).upsertValue(AttendancePeriodSettingService.SETTING_KEY_END_DAY, "14");
    }

    @Test
    void testUpdatePeriod_InvalidStartDay() {
        IllegalArgumentException ex1 = assertThrows(IllegalArgumentException.class, () -> {
            attendancePeriodSettingService.updatePeriod(0, 14);
        });
        assertTrue(ex1.getMessage().contains("開始日"));

        IllegalArgumentException ex2 = assertThrows(IllegalArgumentException.class, () -> {
            attendancePeriodSettingService.updatePeriod(29, 14);
        });
        assertTrue(ex2.getMessage().contains("開始日"));

        verify(systemSettingMapper, never()).upsertValue(anyString(), anyString());
    }

    @Test
    void testUpdatePeriod_InvalidEndDay() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            attendancePeriodSettingService.updatePeriod(15, 30);
        });
        assertTrue(ex.getMessage().contains("終了日"));

        verify(systemSettingMapper, never()).upsertValue(anyString(), anyString());
    }
}
