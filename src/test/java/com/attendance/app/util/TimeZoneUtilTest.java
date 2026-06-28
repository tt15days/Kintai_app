package com.attendance.app.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TimeZoneUtilTest")
class TimeZoneUtilTest {

    private static final ZoneId JAPAN = ZoneId.of("Asia/Tokyo");

    @Test
    @DisplayName("toJst(Instant) - Instantがnullの場合はnullを返し、それ以外は東京タイムゾーンのZonedDateTimeを返すこと")
    void testToJst() {
        assertNull(TimeZoneUtil.toJst(null));

        Instant instant = Instant.parse("2026-06-27T02:00:00Z");
        ZonedDateTime expected = instant.atZone(JAPAN);
        assertEquals(expected, TimeZoneUtil.toJst(instant));
        assertEquals(11, TimeZoneUtil.toJst(instant).getHour()); // 2 + 9 = 11
    }

    @Test
    @DisplayName("toLocalDate(Instant) - Instantがnullの場合はnullを返し、それ以外は東京タイムゾーンのLocalDateを返すこと")
    void testToLocalDate() {
        assertNull(TimeZoneUtil.toLocalDate(null));

        Instant instant = Instant.parse("2026-06-27T17:00:00Z"); // 日本時間 2026-06-28 02:00:00
        LocalDate expected = instant.atZone(JAPAN).toLocalDate();
        assertEquals(expected, TimeZoneUtil.toLocalDate(instant));
        assertEquals(LocalDate.of(2026, 6, 28), TimeZoneUtil.toLocalDate(instant));
    }

    @Test
    @DisplayName("toLocalTime(Instant) - Instantがnullの場合はnullを返し、それ以外は東京タイムゾーンのLocalTimeを返すこと")
    void testToLocalTime() {
        assertNull(TimeZoneUtil.toLocalTime(null));

        Instant instant = Instant.parse("2026-06-27T17:00:00Z"); // 日本時間 2026-06-28 02:00:00
        LocalTime expected = instant.atZone(JAPAN).toLocalTime();
        assertEquals(expected, TimeZoneUtil.toLocalTime(instant));
        assertEquals(LocalTime.of(2, 0), TimeZoneUtil.toLocalTime(instant));
    }

    @Test
    @DisplayName("now() - 現在時刻のInstantを返すこと")
    void testNow() {
        assertNotNull(TimeZoneUtil.now());
    }
}
