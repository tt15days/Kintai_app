package com.attendance.app.service;

import com.attendance.app.mapper.SystemSettingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 勤怠表示期間（開始日・終了日）の設定を管理するサービスです。
 * 
 * システム全体で共通の勤怠締め日・開始日等の設定値を取得・更新する機能を提供します。
 * 設定値は system_settings テーブルに保存されます。
 */
@Service
@RequiredArgsConstructor
public class AttendancePeriodSettingService {

    public static final String SETTING_KEY_START_DAY = "attendance_period_start_day";
    public static final String SETTING_KEY_END_DAY = "attendance_period_end_day";
    public static final int DEFAULT_START_DAY = 21;
    public static final int DEFAULT_END_DAY = 20;

    private final SystemSettingMapper systemSettingMapper;

    /**
     * 勤怠期間の開始日（前月の何日から開始するか）を取得します。
     * 設定が存在しない、または不正な値の場合はデフォルト値（21）を返します。
     *
     * @return 開始日（1〜28の整数）
     */
    public int getStartDay() {
        return parseDayValue(systemSettingMapper.selectValueByKey(SETTING_KEY_START_DAY), DEFAULT_START_DAY);
    }

    /**
     * 勤怠期間の終了日（当月の何日までとするか）を取得します。
     * 設定が存在しない、または不正な値の場合はデフォルト値（20）を返します。
     *
     * @return 終了日（1〜28の整数）
     */
    public int getEndDay() {
        return parseDayValue(systemSettingMapper.selectValueByKey(SETTING_KEY_END_DAY), DEFAULT_END_DAY);
    }

    /**
     * 勤怠期間の開始日・終了日の設定を更新します。
     *
     * @param startDay 更新する開始日（1〜28の整数）
     * @param endDay   更新する終了日（1〜28の整数）
     * @throws IllegalArgumentException 指定された日付が1〜28の範囲外の場合
     */
    public void updatePeriod(int startDay, int endDay) {
        validateDay(startDay, "開始日");
        validateDay(endDay, "終了日");
        systemSettingMapper.upsertValue(SETTING_KEY_START_DAY, String.valueOf(startDay));
        systemSettingMapper.upsertValue(SETTING_KEY_END_DAY, String.valueOf(endDay));
    }

    /**
     * 文字列の日付設定値を整数に変換します。
     * 空文字や数値変換できない場合、または1〜28の範囲外の場合はデフォルト値を返します。
     *
     * @param value 変換対象の文字列値
     * @param defaultValue 変換失敗時に返すデフォルト値
     * @return 変換後の整数値、またはデフォルト値
     */
    private int parseDayValue(String value, int defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            int day = Integer.parseInt(value.trim());
            return (day >= 1 && day <= 28) ? day : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 指定された日付が1〜28の範囲内であるかを検証します。
     *
     * @param day 検証対象の日付
     * @param label エラーメッセージに含める項目名（"開始日"、"終了日"など）
     * @throws IllegalArgumentException 日付が範囲外の場合
     */
    private void validateDay(int day, String label) {
        if (day < 1 || day > 28) {
            throw new IllegalArgumentException(label + "は1〜28の範囲で指定してください");
        }
    }
}
