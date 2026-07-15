package com.attendance.app.service;

import com.attendance.app.mapper.SystemSettingMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.YearMonth;

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
    void testUpdateEndDay_Success() {
        attendancePeriodSettingService.updateEndDay(20);

        verify(systemSettingMapper).upsertValue(AttendancePeriodSettingService.SETTING_KEY_END_DAY, "20");
        verify(systemSettingMapper, never()).upsertValue(eq(AttendancePeriodSettingService.SETTING_KEY_START_DAY), anyString());
    }

    @Test
    void testUpdateEndDay_Invalid() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> attendancePeriodSettingService.updateEndDay(29));

        assertTrue(ex.getMessage().contains("締め日"));
        verify(systemSettingMapper, never()).upsertValue(anyString(), anyString());
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

    @Test
    void closingDay20_isContinuousAcrossYearBoundary() {
        var december = AttendancePeriodSettingService.calculatePeriod(YearMonth.of(2025, 12), 20);
        var january = AttendancePeriodSettingService.calculatePeriod(YearMonth.of(2026, 1), 20);

        assertEquals(LocalDate.of(2025, 11, 21), december.startDate());
        assertEquals(LocalDate.of(2025, 12, 20), december.endDate());
        assertEquals(december.endDate().plusDays(1), january.startDate());
        assertEquals(LocalDate.of(2026, 1, 20), january.endDate());
    }

    @Test
    void resolvePeriod_readsOnlyClosingDaySetting() {
        when(systemSettingMapper.selectValueByKey(AttendancePeriodSettingService.SETTING_KEY_END_DAY))
                .thenReturn("20");

        var period = attendancePeriodSettingService.resolvePeriod(YearMonth.of(2026, 5));

        assertEquals(LocalDate.of(2026, 4, 21), period.startDate());
        assertEquals(LocalDate.of(2026, 5, 20), period.endDate());
        verify(systemSettingMapper, never())
                .selectValueByKey(AttendancePeriodSettingService.SETTING_KEY_START_DAY);
    }

    @Test
    void closingDay28_isContinuousAcrossNonLeapFebruary() {
        var february = AttendancePeriodSettingService.calculatePeriod(YearMonth.of(2025, 2), 28);
        var march = AttendancePeriodSettingService.calculatePeriod(YearMonth.of(2025, 3), 28);

        assertEquals(LocalDate.of(2025, 1, 29), february.startDate());
        assertEquals(LocalDate.of(2025, 2, 28), february.endDate());
        assertEquals(LocalDate.of(2025, 3, 1), march.startDate());
        assertEquals(february.endDate().plusDays(1), march.startDate());
    }

    @Test
    void closingDay28_assignsLeapDayToMarchWithoutGap() {
        var february = AttendancePeriodSettingService.calculatePeriod(YearMonth.of(2024, 2), 28);
        var march = AttendancePeriodSettingService.calculatePeriod(YearMonth.of(2024, 3), 28);

        assertEquals(LocalDate.of(2024, 2, 28), february.endDate());
        assertEquals(LocalDate.of(2024, 2, 29), march.startDate());
        assertEquals(february.endDate().plusDays(1), march.startDate());
        assertEquals(LocalDate.of(2024, 3, 28), march.endDate());
    }

    @Test
    void resolvePayrollMonth_usesClosingDayBoundary() {
        when(systemSettingMapper.selectValueByKey(AttendancePeriodSettingService.SETTING_KEY_END_DAY))
                .thenReturn("28");

        assertEquals(YearMonth.of(2024, 2),
                attendancePeriodSettingService.resolvePayrollMonth(LocalDate.of(2024, 2, 28)));
        assertEquals(YearMonth.of(2024, 3),
                attendancePeriodSettingService.resolvePayrollMonth(LocalDate.of(2024, 2, 29)));
    }
}
