package com.attendance.app.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * TimeZoneUtil - タイムゾーン変換ユーティリティ
 *
 * LocalDate/LocalTime/OffsetDateTime と Instant（UTC）の相互変換を提供します。
 * データベースの TIMESTAMPTZ（UTC）と Java の時間型を扱う際に使用します。
 */
public class TimeZoneUtil {

    private static final ZoneId JAPAN_ZONE = ZoneId.of("Asia/Tokyo");

    /**
     * Instant（UTC）を日本時刻の ZonedDateTime に変換します。
     *
     * @param instant 変換対象の Instant
     * @return 変換後の ZonedDateTime オブジェクト（null の場合は null）
     */
    public static ZonedDateTime toJst(Instant instant) {
        if (instant == null) {
            return null;
        }
        return  instant.atZone(JAPAN_ZONE);
    }

    /**
     * Instant（UTC）を日本時刻の LocalDate に変換します。
     *
     * @param instant 変換対象の Instant
     * @return 変換後の LocalDate オブジェクト（null の場合は null）
     */
    public static LocalDate toLocalDate(Instant instant) {
        if (instant == null) {
            return null;
        }
        return toJst(instant).toLocalDate();
    }

    /**
     * Instant（UTC）を日本時刻の LocalTime に変換します。
     *
     * @param instant 変換対象の Instant
     * @return 変換後の LocalTime オブジェクト（null の場合は null）
     */
    public static LocalTime toLocalTime(Instant instant) {
        if (instant == null) {
            return null;
        }
        return toJst(instant).toLocalTime();
    }

    /**
     * 現在時刻を Instant（UTC）で取得します。
     *
     * @return 現在の Instant オブジェクト
     */
    public static Instant now() {
        return Instant.now();
    }
}
