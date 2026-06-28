package com.attendance.app.service;

import com.attendance.app.mapper.SystemSettingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

/**
 * BatchSettingService - バッチ処理スケジュール設定の管理サービス。
 *
 * 管理者がシステム設定画面から変更できるバッチ実行タイミングを
 * system_settings テーブルで一元管理します。
 *
 * <ul>
 *   <li>{@code monthly_summary_days_after_end} : 月次集計を勤怠期間終了日の何日後に実行するか（デフォルト 5）</li>
 *   <li>{@code paid_leave_grant_month}         : 年次有給付与月（1-12、デフォルト 4月）</li>
 *   <li>{@code paid_leave_grant_day}           : 年次有給付与日（1-28、デフォルト 1日）</li>
 *   <li>{@code reminder_day}                   : 勤怠提出リマインド送信日（1-28、デフォルト 20日）</li>
 *   <li>{@code reminder_hour}                  : 勤怠提出リマインド送信時刻（0-23、デフォルト 9時）</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class BatchSettingService {

    public static final String KEY_MONTHLY_SUMMARY_DAYS_AFTER_END = "monthly_summary_days_after_end";
    public static final String KEY_PAID_LEAVE_GRANT_MONTH         = "paid_leave_grant_month";
    public static final String KEY_PAID_LEAVE_GRANT_DAY           = "paid_leave_grant_day";
    public static final String KEY_REMINDER_DAY                   = "reminder_day";
    public static final String KEY_REMINDER_HOUR                  = "reminder_hour";

    public static final String KEY_ALERT_ARTICLE36_LIMIT1         = "alert_article36_limit1";
    public static final String KEY_ALERT_ARTICLE36_LIMIT2         = "alert_article36_limit2";
    public static final String KEY_ALERT_PAID_LEAVE_MONTHS        = "alert_paid_leave_months";
    public static final String KEY_ALERT_PAID_LEAVE_DAYS          = "alert_paid_leave_days";

    public static final String KEY_LAST_MONTHLY_SUMMARY_EXECUTED_AT    = "last_monthly_summary_executed_at";
    public static final String KEY_LAST_ANNUAL_LEAVE_GRANT_EXECUTED_AT = "last_annual_leave_grant_executed_at";
    public static final String KEY_LAST_REMINDER_EXECUTED_AT           = "last_reminder_executed_at";

    private static final int DEFAULT_DAYS_AFTER_END  = 5;
    private static final int DEFAULT_GRANT_MONTH     = 4;
    private static final int DEFAULT_GRANT_DAY       = 1;
    private static final int DEFAULT_REMINDER_DAY    = 20;
    private static final int DEFAULT_REMINDER_HOUR   = 9;

    private static final int DEFAULT_ALERT_ARTICLE36_LIMIT1  = 30;
    private static final int DEFAULT_ALERT_ARTICLE36_LIMIT2  = 45;
    private static final int DEFAULT_ALERT_PAID_LEAVE_MONTHS = 9;
    private static final int DEFAULT_ALERT_PAID_LEAVE_DAYS   = 3;

    private final SystemSettingMapper systemSettingMapper;

    /** 月次集計実行日（勤怠期間終了日の何日後か）を返します。 */
    public int getMonthlySummaryDaysAfterEnd() {
        return parseIntValue(
                systemSettingMapper.selectValueByKey(KEY_MONTHLY_SUMMARY_DAYS_AFTER_END),
                DEFAULT_DAYS_AFTER_END);
    }

    /** 年次有給付与月（1-12）を返します。 */
    public int getPaidLeaveGrantMonth() {
        return parseIntValue(
                systemSettingMapper.selectValueByKey(KEY_PAID_LEAVE_GRANT_MONTH),
                DEFAULT_GRANT_MONTH);
    }

    /** 年次有給付与日（1-28）を返します。 */
    public int getPaidLeaveGrantDay() {
        return parseIntValue(
                systemSettingMapper.selectValueByKey(KEY_PAID_LEAVE_GRANT_DAY),
                DEFAULT_GRANT_DAY);
    }

    /** 勤怠提出リマインド送信日（1-28）を返します。 */
    public int getReminderDay() {
        return parseIntValue(
                systemSettingMapper.selectValueByKey(KEY_REMINDER_DAY),
                DEFAULT_REMINDER_DAY);
    }

    /** 勤怠提出リマインド送信時刻（0-23）を返します。 */
    public int getReminderHour() {
        return parseIntValue(
                systemSettingMapper.selectValueByKey(KEY_REMINDER_HOUR),
                DEFAULT_REMINDER_HOUR);
    }

    /** 36協定 第1警告閾値（時間）を返します。 */
    public int getAlertArticle36Limit1() {
        return parseIntValue(systemSettingMapper.selectValueByKey(KEY_ALERT_ARTICLE36_LIMIT1), DEFAULT_ALERT_ARTICLE36_LIMIT1);
    }

    /** 36協定 第2警告閾値（時間）を返します。 */
    public int getAlertArticle36Limit2() {
        return parseIntValue(systemSettingMapper.selectValueByKey(KEY_ALERT_ARTICLE36_LIMIT2), DEFAULT_ALERT_ARTICLE36_LIMIT2);
    }

    /** 有給消化警告 付与後経過月数 を返します。 */
    public int getAlertPaidLeaveMonths() {
        return parseIntValue(systemSettingMapper.selectValueByKey(KEY_ALERT_PAID_LEAVE_MONTHS), DEFAULT_ALERT_PAID_LEAVE_MONTHS);
    }

    /** 有給消化警告 消化基準日数 を返します。 */
    public int getAlertPaidLeaveDays() {
        return parseIntValue(systemSettingMapper.selectValueByKey(KEY_ALERT_PAID_LEAVE_DAYS), DEFAULT_ALERT_PAID_LEAVE_DAYS);
    }

    /**
     * バッチスケジュール設定を一括更新します。
     *
     * @param daysAfterEnd  月次集計: 期間終了日の何日後か（1-14）
     * @param grantMonth    有給付与月（1-12）
     * @param grantDay      有給付与日（1-28）
     * @param reminderDay   リマインド送信日（1-28）
     * @param reminderHour  リマインド送信時刻（0-23）
     */
    @Transactional
    public void updateSettings(int daysAfterEnd, int grantMonth, int grantDay,
                               int reminderDay, int reminderHour) {
        if (daysAfterEnd < 1 || daysAfterEnd > 14) {
            throw new IllegalArgumentException("月次集計実行日は1〜14の範囲で設定してください");
        }
        if (grantMonth < 1 || grantMonth > 12) {
            throw new IllegalArgumentException("有給付与月は1〜12の範囲で設定してください");
        }
        if (grantDay < 1 || grantDay > 28) {
            throw new IllegalArgumentException("有給付与日は1〜28の範囲で設定してください");
        }
        if (reminderDay < 1 || reminderDay > 28) {
            throw new IllegalArgumentException("リマインド送信日は1〜28の範囲で設定してください");
        }
        if (reminderHour < 0 || reminderHour > 23) {
            throw new IllegalArgumentException("リマインド送信時刻は0〜23の範囲で設定してください");
        }

        systemSettingMapper.upsertValue(KEY_MONTHLY_SUMMARY_DAYS_AFTER_END, String.valueOf(daysAfterEnd));
        systemSettingMapper.upsertValue(KEY_PAID_LEAVE_GRANT_MONTH,         String.valueOf(grantMonth));
        systemSettingMapper.upsertValue(KEY_PAID_LEAVE_GRANT_DAY,           String.valueOf(grantDay));
        systemSettingMapper.upsertValue(KEY_REMINDER_DAY,                   String.valueOf(reminderDay));
        systemSettingMapper.upsertValue(KEY_REMINDER_HOUR,                  String.valueOf(reminderHour));
    }

    /**
     * アラート閾値設定を一括更新します。
     */
    @Transactional
    public void updateAlertSettings(int limit1, int limit2, int months, int days) {
        if (limit1 < 0 || limit2 < 0 || limit1 >= limit2) {
            throw new IllegalArgumentException("36協定の警告閾値が不正です（第1警告 < 第2警告となるように設定してください）");
        }
        if (months < 1 || months > 24) {
            throw new IllegalArgumentException("有給消化警告月数は1〜24の範囲で設定してください");
        }
        if (days < 0 || days > 40) {
            throw new IllegalArgumentException("有給消化基準日数は0〜40の範囲で設定してください");
        }

        systemSettingMapper.upsertValue(KEY_ALERT_ARTICLE36_LIMIT1,  String.valueOf(limit1));
        systemSettingMapper.upsertValue(KEY_ALERT_ARTICLE36_LIMIT2,  String.valueOf(limit2));
        systemSettingMapper.upsertValue(KEY_ALERT_PAID_LEAVE_MONTHS, String.valueOf(months));
        systemSettingMapper.upsertValue(KEY_ALERT_PAID_LEAVE_DAYS,   String.valueOf(days));
    }

    /** 月次集計の最終実行日時を返します。未実行の場合は {@code null} を返します。 */
    public LocalDateTime getLastMonthlySummaryExecutedAt() {
        return parseLocalDateTime(systemSettingMapper.selectValueByKey(KEY_LAST_MONTHLY_SUMMARY_EXECUTED_AT));
    }

    /** 年次有給付与の最終実行日時を返します。未実行の場合は {@code null} を返します。 */
    public LocalDateTime getLastAnnualLeaveGrantExecutedAt() {
        return parseLocalDateTime(systemSettingMapper.selectValueByKey(KEY_LAST_ANNUAL_LEAVE_GRANT_EXECUTED_AT));
    }

    /** 勤怠提出リマインドの最終実行日時を返します。未実行の場合は {@code null} を返します。 */
    public LocalDateTime getLastReminderExecutedAt() {
        return parseLocalDateTime(systemSettingMapper.selectValueByKey(KEY_LAST_REMINDER_EXECUTED_AT));
    }

    /** 月次集計の実行日時を記録します。 */
    @Transactional
    public void recordMonthlySummaryExecutedAt(LocalDateTime executedAt) {
        systemSettingMapper.upsertValue(KEY_LAST_MONTHLY_SUMMARY_EXECUTED_AT, executedAt.toString());
    }

    /** 年次有給付与の実行日時を記録します。 */
    @Transactional
    public void recordAnnualLeaveGrantExecutedAt(LocalDateTime executedAt) {
        systemSettingMapper.upsertValue(KEY_LAST_ANNUAL_LEAVE_GRANT_EXECUTED_AT, executedAt.toString());
    }

    /** 勤怠提出リマインドの実行日時を記録します。 */
    @Transactional
    public void recordReminderExecutedAt(LocalDateTime executedAt) {
        systemSettingMapper.upsertValue(KEY_LAST_REMINDER_EXECUTED_AT, executedAt.toString());
    }

    private static int parseIntValue(String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static LocalDateTime parseLocalDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
