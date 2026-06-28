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
    /** 休憩1 開始時刻 */
    private LocalTime breakStartTime;
    /** 休憩1 終了時刻 */
    private LocalTime breakEndTime;
    /** 休憩2 開始時刻 */
    private LocalTime breakStartTime2;
    /** 休憩2 終了時刻 */
    private LocalTime breakEndTime2;
    /** 休憩3 開始時刻 */
    private LocalTime breakStartTime3;
    /** 休憩3 終了時刻 */
    private LocalTime breakEndTime3;
    /** 休憩4 開始時刻 */
    private LocalTime breakStartTime4;
    /** 休憩4 終了時刻 */
    private LocalTime breakEndTime4;
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
        total += (int) DateTimeUtil.calculateDurationMinutes(breakStartTime, breakEndTime);
        total += (int) DateTimeUtil.calculateDurationMinutes(breakStartTime2, breakEndTime2);
        total += (int) DateTimeUtil.calculateDurationMinutes(breakStartTime3, breakEndTime3);
        total += (int) DateTimeUtil.calculateDurationMinutes(breakStartTime4, breakEndTime4);
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
        StringBuilder summary = new StringBuilder();
        appendTimeWindow(summary, breakStartTime, breakEndTime);
        appendTimeWindow(summary, breakStartTime2, breakEndTime2);
        appendTimeWindow(summary, breakStartTime3, breakEndTime3);
        appendTimeWindow(summary, breakStartTime4, breakEndTime4);
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
