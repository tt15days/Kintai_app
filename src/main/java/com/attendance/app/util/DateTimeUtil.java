package com.attendance.app.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * DateTimeUtil - タイムゾーン変換ユーティリティ
 *
 * LocalDate/LocalTime/OffsetDateTime と Instant（UTC）の相互変換を提供します。
 * タイムゾーン付きデータベースカラムと Java の時間型を扱う際に使用します。
 */
public class DateTimeUtil {

    private static final ZoneId JAPAN = ZoneId.of("Asia/Tokyo");

    // ========== Instant への変換 ==========

    /**
     * LocalDate（日本の日付と想定）を Instant（UTC、00:00:00）に変換します。
     *
     * @param date 変換対象の LocalDate
     * @return 変換後の Instant オブジェクト（null の場合は null）
     */
    public static Instant toInstant(LocalDate date) {
        if (date == null) return null;
        return date.atStartOfDay(JAPAN).toInstant();
    }

    /**
     * LocalDate と LocalTime（日本時刻と想定）を Instant（UTC）に変換します。
     *
     * @param date 変換対象の LocalDate
     * @param time 変換対象の LocalTime
     * @return 変換後の Instant オブジェクト（引数のいずれかが null の場合は null）
     */
    public static Instant toInstant(LocalDate date, LocalTime time) {
        if (date == null || time == null) return null;
        return date.atTime(time).atZone(JAPAN).toInstant();
    }

    /**
     * LocalTime（日本時刻と想定）を Instant に変換します（基準日付は本日）。
     *
     * @param time 変換対象の LocalTime
     * @return 変換後の Instant オブジェクト（null の場合は null）
     */
    public static Instant toInstant(LocalTime time) {
        if (time == null) return null;
        LocalDate today = LocalDate.now(JAPAN);
        return toInstant(today, time);
    }

    // ========== OffsetDateTime への変換 ==========

    /**
     * Instant（UTC）を OffsetDateTime（日本時刻）に変換します。
     *
     * @param instant 変換対象の Instant
     * @return 変換後の OffsetDateTime オブジェクト（null の場合は null）
     */
    public static OffsetDateTime toOffsetDateTime(Instant instant) {
        if (instant == null) return null;
        return instant.atZone(JAPAN).toOffsetDateTime();
    }

    // ========== LocalDate への変換 ==========

    /**
     * Instant（UTC）を LocalDate（日本の日付）に変換します。
     *
     * @param instant 変換対象の Instant
     * @return 変換後の LocalDate オブジェクト（null の場合は null）
     */
    public static LocalDate toLocalDate(Instant instant) {
        if (instant == null) return null;
        return instant.atZone(JAPAN).toLocalDate();
    }

    // ========== LocalTime への変換 ==========

    /**
     * Instant（UTC）を LocalTime（日本時刻）に変換します。
     *
     * @param instant 変換対象の Instant
     * @return 変換後の LocalTime オブジェクト（null の場合は null）
     */
    public static LocalTime toLocalTime(Instant instant) {
        if (instant == null) return null;
        return instant.atZone(JAPAN).toLocalTime();
    }

    // ========== 時間差計算 ==========

    /**
     * 2つの時刻（LocalTime）から稼働時間（時間）を計算します。
     * 24時間を超える場合は、翌日にまたがるものとして計算します。
     *
     * @param startTime 開始時刻
     * @param endTime 終了時刻
     * @return 稼働時間（時間単位）
     */
    public static double calculateWorkingHours(LocalTime startTime, LocalTime endTime) {
        return calculateDurationMinutes(startTime, endTime) / 60.0;
    }

    /**
     * 2つの時刻（LocalTime）から経過時間（分）を計算します。
     * 終了時刻が開始時刻より前の場合は翌日にまたがるものとして扱います。
     *
     * @param startTime 開始時刻
     * @param endTime 終了時刻
     * @return 経過時間（分単位）
     */
    public static long calculateDurationMinutes(LocalTime startTime, LocalTime endTime) {
        if (startTime == null || endTime == null) return 0L;

        long startMinutes = startTime.toSecondOfDay() / 60;
        long endMinutes = endTime.toSecondOfDay() / 60;

        if (endMinutes < startMinutes) {
            endMinutes += 24 * 60;
        }

        return endMinutes - startMinutes;
    }

    /**
     * 2つの Instant（時刻）から稼働時間（時間）を計算します。
     *
     * @param startInstant 開始時刻
     * @param endInstant 終了時刻
     * @return 稼働時間（時間単位）
     */
    public static double calculateWorkingHours(Instant startInstant, Instant endInstant) {
        return calculateWorkingHours(startInstant, endInstant, 0);
    }

    /**
     * 2つの Instant（時刻）から休憩時間を控除した稼働時間（時間）を計算します。
     *
     * @param startInstant 開始時刻
     * @param endInstant 終了時刻
     * @param breakMinutes 控除する休憩時間（分）
     * @return 休憩時間を控除した稼働時間（時間単位）
     */
    public static double calculateWorkingHours(Instant startInstant, Instant endInstant, int breakMinutes) {
        if (startInstant == null || endInstant == null) return 0.0;

        long durationMinutes = Duration.between(startInstant, endInstant).toMinutes();
        if (durationMinutes <= 0) {
            return 0.0;
        }

        long adjustedMinutes = Math.max(0, durationMinutes - Math.max(breakMinutes, 0));
        return adjustedMinutes / 60.0;
    }

    // ========== 現在時刻 ==========

    /**
     * 現在時刻を Instant（UTC）で取得します。
     *
     * @return 現在の Instant オブジェクト
     */
    public static Instant now() {
        return Instant.now();
    }

    /**
     * 現在日付を LocalDate（日本の日付）で取得します。
     *
     * @return 現在の日本の LocalDate オブジェクト
     */
    public static LocalDate todayJapan() {
        return LocalDate.now(JAPAN);
    }

    /**
     * 現在時刻を LocalTime（日本時間）で取得します。
     *
     * @return 現在の日本の LocalTime オブジェクト
     */
    public static LocalTime currentTimeJapan() {
        return LocalTime.now(JAPAN);
    }

    /**
     * 時間（Double）を「〇時間〇分」形式の文字列に変換します。
     *
     * @param hours 変換対象の時間（例: 8.5）
     * @return 「〇時間〇分」の形式の文字列。null または 0 以下の場合は「0時間0分」
     */
    public static String formatHoursToHHmm(Double hours) {
        if (hours == null || hours <= 0) {
            return "0時間0分";
        }
        long totalMinutes = Math.round(hours * 60);
        long h = totalMinutes / 60;
        long m = totalMinutes % 60;
        return h + "時間" + m + "分";
    }

    /**
     * 分（Integer）を「〇時間〇分」形式の文字列に変換します。
     *
     * @param minutes 変換対象の分（例: 510）
     * @return 「〇時間〇分」の形式の文字列。null または 0 以下の場合は「0時間0分」
     */
    public static String formatMinutesToHHmm(Integer minutes) {
        if (minutes == null || minutes <= 0) {
            return "0時間0分";
        }
        int h = minutes / 60;
        int m = minutes % 60;
        return h + "時間" + m + "分";
    }
}
