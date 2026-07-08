package com.attendance.app.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.attendance.app.util.DateTimeUtil;

import java.time.LocalTime;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * WorkScheduleClass Entity - 勤務クラス（所定時間マスタ）
 *
 * ユーザーが所属するクラスを定義し、所定の勤務開始・終了・休憩時間帯を管理します。
 * 残業計算の基準時刻はこのクラスの end_time を使用します。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkScheduleClass {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    /** クラスID（PK） */
    private Long classId;
    /** クラスコード（一意なビジネスID） */
    private String classCode;
    /** クラス名（例: "A班"、"通常勤務"） */
    private String name;
    /** 勤務地・拠点名 */
    private String workLocation;
    /** 住所 */
    private String address;
    /** 最寄り駅 */
    private String station;
    /** 電話番号 */
    private String telephone;
    /** 所属部署名 */
    private String sectionName;
    /** フォルダ分類 */
    private String folderName;
    /** タグ（カンマ区切り） */
    private String tags;
    /** 有効フラグ */
    private Boolean isActive;
    /** 最大勤務時間 */
    private Short maxHours;
    /** 最小勤務時間 */
    private Short minHours;
    /** 所定勤務開始時刻 */
    private LocalTime startTime;
    /** 所定勤務終了時刻 */
    private LocalTime endTime;
    /** 休憩時間リスト（縦持ち） */
    private java.util.List<WorkScheduleClassBreak> breaks;
    /** 作成日時（UTC） */
    private Instant createdAt;
    /** 更新日時（UTC） */
    private Instant updatedAt;

    /**
     * 全スロットの休憩時間合計（分）を返します。
     * 終了時刻が開始時刻より前の場合は翌日扱いで計算します。
     */
    public int getTotalBreakMinutes() {
        int total = 0;
        if (breaks != null) {
            for (WorkScheduleClassBreak b : breaks) {
                total += (int) DateTimeUtil.calculateDurationMinutes(b.getBreakStartTime(), b.getBreakEndTime());
            }
        }
        return total;
    }

    /**
     * 指定した時刻帯を HH:mm〜HH:mm 形式で返します。
     */
    public String formatTimeWindow(LocalTime startTime, LocalTime endTime) {
        if (startTime == null || endTime == null) {
            return "-";
        }
        return startTime.format(TIME_FORMATTER) + "〜" + endTime.format(TIME_FORMATTER);
    }

    /**
     * 休憩時間帯の要約を返します。
     */
    public String getBreakScheduleSummary() {
        if (breaks == null || breaks.isEmpty()) {
            return "-";
        }
        StringBuilder summary = new StringBuilder();
        for (WorkScheduleClassBreak b : breaks) {
            appendTimeWindow(summary, b.getBreakStartTime(), b.getBreakEndTime());
        }
        return summary.length() == 0 ? "-" : summary.toString();
    }

    private void appendTimeWindow(StringBuilder summary, LocalTime startTime, LocalTime endTime) {
        String window = formatTimeWindow(startTime, endTime);
        if ("-".equals(window)) {
            return;
        }
        if (summary.length() > 0) {
            summary.append(" / ");
        }
        summary.append(window);
    }
}
