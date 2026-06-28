package com.attendance.app.service;

import com.attendance.app.entity.User;
import com.attendance.app.mapper.SystemSettingMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CsvFilenamePatternServiceTest {

    @Mock
    private SystemSettingMapper systemSettingMapper;

    @InjectMocks
    private CsvFilenamePatternService csvFilenamePatternService;

    @Test
    void testGetPattern_ReturnsSavedPattern() {
        when(systemSettingMapper.selectValueByKey(CsvFilenamePatternService.SETTING_KEY))
                .thenReturn("{yyyy}-{MM}_{userId}_{name}_{downloadAt}");
        assertEquals("{yyyy}-{MM}_{userId}_{name}_{downloadAt}", csvFilenamePatternService.getPattern());
    }

    @Test
    void testGetPattern_ReturnsDefaultWhenNullOrEmpty() {
        when(systemSettingMapper.selectValueByKey(CsvFilenamePatternService.SETTING_KEY))
                .thenReturn(null);
        assertEquals(CsvFilenamePatternService.DEFAULT_PATTERN, csvFilenamePatternService.getPattern());

        when(systemSettingMapper.selectValueByKey(CsvFilenamePatternService.SETTING_KEY))
                .thenReturn("   ");
        assertEquals(CsvFilenamePatternService.DEFAULT_PATTERN, csvFilenamePatternService.getPattern());
    }

    @Test
    void testUpdatePattern_Success() {
        String validPattern = "{yyyy}-{MM}_{userId}_{name}_{downloadAt}_test";
        csvFilenamePatternService.updatePattern(validPattern);
        verify(systemSettingMapper).upsertValue(CsvFilenamePatternService.SETTING_KEY, validPattern);
    }

    @Test
    void testUpdatePattern_EmptyThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> csvFilenamePatternService.updatePattern(""));
        assertThrows(IllegalArgumentException.class, () -> csvFilenamePatternService.updatePattern("   "));
    }

    @Test
    void testUpdatePattern_TooLongThrowsException() {
        String longPattern = "{yyyy}-{MM}_{userId}_{name}_{downloadAt}_" + "a".repeat(200);
        assertThrows(IllegalArgumentException.class, () -> csvFilenamePatternService.updatePattern(longPattern));
    }

    @Test
    void testUpdatePattern_UnbalancedBracesThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> csvFilenamePatternService.updatePattern("{yyyy}-{MM_{userId}_{name}_{downloadAt}"));
        assertThrows(IllegalArgumentException.class, () -> csvFilenamePatternService.updatePattern("{yyyy}}-{MM}_{userId}_{name}_{downloadAt}"));
    }

    @Test
    void testUpdatePattern_UnsupportedTokenThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> csvFilenamePatternService.updatePattern("{yyyy}-{MM}_{userId}_{name}_{downloadAt}_{unknown}"));
    }

    @Test
    void testUpdatePattern_MissingRequiredTokensThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> csvFilenamePatternService.updatePattern("{MM}_{userId}_{name}_{downloadAt}"));
        assertThrows(IllegalArgumentException.class, () -> csvFilenamePatternService.updatePattern("{yyyy}_{userId}_{name}_{downloadAt}"));
        assertThrows(IllegalArgumentException.class, () -> csvFilenamePatternService.updatePattern("{yyyy}-{MM}_{name}_{downloadAt}"));
        assertThrows(IllegalArgumentException.class, () -> csvFilenamePatternService.updatePattern("{yyyy}-{MM}_{userId}_{downloadAt}"));
        assertThrows(IllegalArgumentException.class, () -> csvFilenamePatternService.updatePattern("{yyyy}-{MM}_{userId}_{name}"));
    }

    @Test
    void testBuildCsvFilename() {
        when(systemSettingMapper.selectValueByKey(CsvFilenamePatternService.SETTING_KEY))
                .thenReturn("{yyyy}-{MM}_{userId}_{name}_{downloadAt}");

        User user = new User();
        user.setUserId(42L);
        user.setFullName("山田 太郎");

        YearMonth ym = YearMonth.of(2026, 5);
        OffsetDateTime downloadAt = OffsetDateTime.parse("2026-05-15T12:34:56Z");

        String filename = csvFilenamePatternService.buildCsvFilename(user, ym, downloadAt);
        assertEquals("2026-05_042_山田_太郎_20260515123456.csv", filename);
    }

    @Test
    void testBuildCsvFilename_SanitizeFilename() {
        when(systemSettingMapper.selectValueByKey(CsvFilenamePatternService.SETTING_KEY))
                .thenReturn("{yyyy}-{MM}_{userId}_{name}_{downloadAt}");

        User user = new User();
        user.setUserId(42L);
        user.setFullName("山田/太郎\\:*?\"<>|"); // invalid characters

        YearMonth ym = YearMonth.of(2026, 5);
        OffsetDateTime downloadAt = OffsetDateTime.parse("2026-05-15T12:34:56Z");

        String filename = csvFilenamePatternService.buildCsvFilename(user, ym, downloadAt);
        // Special characters should be replaced with underscores
        assertEquals("2026-05_042_山田_太郎_________20260515123456.csv", filename);
    }
}
