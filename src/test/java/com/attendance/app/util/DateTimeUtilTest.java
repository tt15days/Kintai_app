package com.attendance.app.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DateTimeUtilTest")
class DateTimeUtilTest {

    private static final ZoneId JAPAN = ZoneId.of("Asia/Tokyo");

    @Test
    @DisplayName("toInstant(LocalDate) - LocalDateがnullの場合はnullを返し、それ以外は東京タイムゾーンの開始時刻(00:00:00)のInstantを返すこと")
    void testToInstantLocalDate() {
        assertNull(DateTimeUtil.toInstant((LocalDate) null));

        LocalDate date = LocalDate.of(2026, 6, 27);
        Instant expected = date.atStartOfDay(JAPAN).toInstant();
        assertEquals(expected, DateTimeUtil.toInstant(date));
    }

    @Test
    @DisplayName("toInstant(LocalDate, LocalTime) - いずれかがnullの場合はnullを返し、それ以外は東京タイムゾーンの指定日時のInstantを返すこと")
    void testToInstantLocalDateLocalTime() {
        assertNull(DateTimeUtil.toInstant(null, LocalTime.of(9, 0)));
        assertNull(DateTimeUtil.toInstant(LocalDate.of(2026, 6, 27), null));
        assertNull(DateTimeUtil.toInstant(null, null));

        LocalDate date = LocalDate.of(2026, 6, 27);
        LocalTime time = LocalTime.of(9, 15);
        Instant expected = date.atTime(time).atZone(JAPAN).toInstant();
        assertEquals(expected, DateTimeUtil.toInstant(date, time));
    }

    @Test
    @DisplayName("toInstant(LocalTime) - LocalTimeがnullの場合はnullを返し、それ以外は本日日付の指定時刻のInstantを返すこと")
    void testToInstantLocalTime() {
        assertNull(DateTimeUtil.toInstant((LocalTime) null));

        LocalTime time = LocalTime.of(18, 30);
        LocalDate today = LocalDate.now(JAPAN);
        Instant expected = today.atTime(time).atZone(JAPAN).toInstant();
        assertEquals(expected, DateTimeUtil.toInstant(time));
    }

    @Test
    @DisplayName("toOffsetDateTime(Instant) - Instantがnullの場合はnullを返し、それ以外は東京タイムゾーン of OffsetDateTimeを返すこと")
    void testToOffsetDateTime() {
        assertNull(DateTimeUtil.toOffsetDateTime(null));

        Instant instant = Instant.parse("2026-06-27T02:00:00Z");
        OffsetDateTime expected = instant.atZone(JAPAN).toOffsetDateTime();
        assertEquals(expected, DateTimeUtil.toOffsetDateTime(instant));
    }

    @Test
    @DisplayName("toLocalDate(Instant) - Instantがnullの場合はnullを返し、それ以外は東京タイムゾーンのLocalDateを返すこと")
    void testToLocalDate() {
        assertNull(DateTimeUtil.toLocalDate(null));

        Instant instant = Instant.parse("2026-06-27T02:00:00Z"); // 日本時間では 2026-06-27 11:00:00
        LocalDate expected = instant.atZone(JAPAN).toLocalDate();
        assertEquals(expected, DateTimeUtil.toLocalDate(instant));
        assertEquals(LocalDate.of(2026, 6, 27), DateTimeUtil.toLocalDate(instant));
    }

    @Test
    @DisplayName("toLocalTime(Instant) - Instantがnullの場合はnullを返し、それ以外は東京タイムゾーンのLocalTimeを返すこと")
    void testToLocalTime() {
        assertNull(DateTimeUtil.toLocalTime(null));

        Instant instant = Instant.parse("2026-06-27T02:30:00Z"); // 日本時間では 11:30:00
        LocalTime expected = instant.atZone(JAPAN).toLocalTime();
        assertEquals(expected, DateTimeUtil.toLocalTime(instant));
        assertEquals(LocalTime.of(11, 30), DateTimeUtil.toLocalTime(instant));
    }

    @Test
    @DisplayName("calculateWorkingHours(LocalTime, LocalTime) - 開始・終了時刻から稼働時間（時間単位）を正しく計算すること")
    void testCalculateWorkingHoursLocalTime() {
        // 9:00 - 18:00 = 9.0時間
        assertEquals(9.0, DateTimeUtil.calculateWorkingHours(LocalTime.of(9, 0), LocalTime.of(18, 0)), 0.001);
        // 22:00 - 05:00 = 7.0時間（翌日またぎ）
        assertEquals(7.0, DateTimeUtil.calculateWorkingHours(LocalTime.of(22, 0), LocalTime.of(5, 0)), 0.001);
    }

    @Test
    @DisplayName("calculateDurationMinutes(LocalTime, LocalTime) - 開始・終了時刻から経過時間（分単位）を計算すること")
    void testCalculateDurationMinutes() {
        assertEquals(0L, DateTimeUtil.calculateDurationMinutes(null, LocalTime.of(18, 0)));
        assertEquals(0L, DateTimeUtil.calculateDurationMinutes(LocalTime.of(9, 0), null));

        // 9:00 - 10:30 = 90分
        assertEquals(90L, DateTimeUtil.calculateDurationMinutes(LocalTime.of(9, 0), LocalTime.of(10, 30)));
        // 23:00 - 01:00 = 120分（翌日またぎ）
        assertEquals(120L, DateTimeUtil.calculateDurationMinutes(LocalTime.of(23, 0), LocalTime.of(1, 0)));
    }

    @Test
    @DisplayName("calculateWorkingHours(Instant, Instant) - 2つのInstantから稼働時間（時間単位）を計算すること")
    void testCalculateWorkingHoursInstant() {
        Instant start = Instant.parse("2026-06-27T09:00:00Z");
        Instant end = Instant.parse("2026-06-27T17:30:00Z"); // 8.5時間
        assertEquals(8.5, DateTimeUtil.calculateWorkingHours(start, end), 0.001);
    }

    @Test
    @DisplayName("calculateWorkingHours(Instant, Instant, breakMinutes) - 休憩時間を控除して稼働時間を計算すること")
    void testCalculateWorkingHoursWithBreak() {
        assertEquals(0.0, DateTimeUtil.calculateWorkingHours(null, Instant.now(), 60), 0.001);
        assertEquals(0.0, DateTimeUtil.calculateWorkingHours(Instant.now(), null, 60), 0.001);

        Instant start = Instant.parse("2026-06-27T09:00:00Z");
        Instant end = Instant.parse("2026-06-27T18:00:00Z"); // 9.0時間

        // 休憩60分（1.0時間）控除 -> 8.0時間
        assertEquals(8.0, DateTimeUtil.calculateWorkingHours(start, end, 60), 0.001);

        // 休憩なし（0分） -> 9.0時間
        assertEquals(9.0, DateTimeUtil.calculateWorkingHours(start, end, 0), 0.001);

        // 終了が開始より前の場合は0.0時間
        assertEquals(0.0, DateTimeUtil.calculateWorkingHours(end, start, 60), 0.001);
    }

    @Test
    @DisplayName("now / todayJapan / currentTimeJapan - 現在日時を取得できること")
    void testCurrentDateTimeMethods() {
        assertNotNull(DateTimeUtil.now());
        assertNotNull(DateTimeUtil.todayJapan());
        assertNotNull(DateTimeUtil.currentTimeJapan());
    }
}
