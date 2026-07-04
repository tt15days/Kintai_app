package com.attendance.app.service;

import com.attendance.app.entity.PaidLeaveBalance;
import com.attendance.app.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

/**
 * BatchSchedulerService - 定期バッチ処理
 *
 * ポーリング方式で動作します。Spring の cron は固定値のため、
 * 毎日/毎時起動してシステム設定と照合し、実行タイミングを動的に判定します。
 *
 * <ol>
 *   <li><b>月次集計</b>: 毎日 01:00（JST）に、今日が「勤怠期間終了日 + daysAfterEnd」かをチェックして実行</li>
 *   <li><b>年次有給付与</b>: 毎日 02:00（JST）に、今日が設定した付与月日かをチェックして実行</li>
 *   <li><b>勤怠提出リマインド</b>: 毎時 00分（JST）に、今日の日付・時刻が設定と一致するかをチェックして実行</li>
 * </ol>
 *
 * タイムゾーン: すべての cron 式は Asia/Tokyo で評価されます。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BatchSchedulerService {

    private static final ZoneId JAPAN_ZONE = ZoneId.of("Asia/Tokyo");
    private static final String BATCH_LOG_START = "バッチ開始: {}";
    private static final String BATCH_LOG_DONE  = "バッチ完了: {}";
    private static final String BATCH_LOG_ERROR = "バッチ異常終了: job={}, error={}";
    private static final int DEFAULT_ANNUAL_LEAVE_GRANT_DAYS = 10;

    private final AttendanceRecordService attendanceRecordService;
    private final AttendancePeriodSettingService attendancePeriodSettingService;
    private final BatchSettingService batchSettingService;
    private final UserNotificationService userNotificationService;
    private final UserService userService;
    private final PaidLeaveBalanceService paidLeaveBalanceService;

    // -------------------------------------------------------
    // 1. 月次集計バッチ
    //    毎日 01:00（JST）にポーリング実行
    // -------------------------------------------------------

    /**
     * 月次勤怠集計ジョブのポーリングエントリポイント。
     * 「今日 - daysAfterEnd」の日付が勤怠期間終了日と一致する場合にのみ実行します。
     */
    @Scheduled(cron = "0 0 1 * * ?", zone = "Asia/Tokyo")
    public void runMonthlySummaryCheck() {
        LocalDate today = LocalDate.now(JAPAN_ZONE);
        int endDay    = attendancePeriodSettingService.getEndDay();
        int daysAfter = batchSettingService.getMonthlySummaryDaysAfterEnd();
        LocalDate targetDay = today.minusDays(daysAfter);
        if (targetDay.getDayOfMonth() == endDay) {
            // targetDay が属する月を集計対象とする
            YearMonth targetMonth = YearMonth.from(targetDay);
            executeMonthlySummary(targetMonth);
        }
    }

    /**
     * 月次勤怠集計を指定月に対して実行します。
     * 管理者画面からの手動起動でも利用されます。
     *
     * @param targetMonth 集計対象年月
     */
    public void executeMonthlySummary(YearMonth targetMonth) {
        log.info(BATCH_LOG_START, "月次集計: targetMonth=" + targetMonth);
        try {
            List<AttendanceRecordService.MonthlyUserSummary> summaries =
                    attendanceRecordService.getMonthlyAggregateForAllUsers(targetMonth);

            for (AttendanceRecordService.MonthlyUserSummary s : summaries) {
                if (s.overtimeHours() >= AttendanceRecordService.ARTICLE36_MONTHLY_LIMIT_HOURS) {
                    log.warn("36協定超過: userId={}, yearMonth={}, overtimeHours={}",
                            s.userId(), targetMonth, s.overtimeHours());
                } else if (s.overtimeHours() >= AttendanceRecordService.ARTICLE36_MONTHLY_WARNING_HOURS) {
                    log.warn("36協定注意: userId={}, yearMonth={}, overtimeHours={}",
                            s.userId(), targetMonth, s.overtimeHours());
                } else {
                    log.info("月次集計: userId={}, yearMonth={}, workingHours={}, overtimeHours={}, recordCount={}",
                            s.userId(), targetMonth, s.workingHours(), s.overtimeHours(), s.recordCount());
                }
            }
            log.info(BATCH_LOG_DONE, "月次集計: " + summaries.size() + "名処理");
            batchSettingService.recordMonthlySummaryExecutedAt(LocalDateTime.now(JAPAN_ZONE));
        } catch (Exception e) {
            log.error(BATCH_LOG_ERROR, "月次集計", e.getMessage(), e);
        }
    }


    // -------------------------------------------------------
    // 3. 勤怠提出リマインドバッチ
    //    毎時 00分（JST）にポーリング実行
    // -------------------------------------------------------

    /**
     * 勤怠提出リマインドジョブのポーリングエントリポイント。
     * 今日の日付・時刻が設定と一致する場合にのみ実行します。
     */
    @Scheduled(cron = "0 0 * * * ?", zone = "Asia/Tokyo")
    public void runSubmissionReminderCheck() {
        LocalDate today = LocalDate.now(JAPAN_ZONE);
        int reminderDay  = batchSettingService.getReminderDay();
        int reminderHour = batchSettingService.getReminderHour();
        int nowHour = LocalTime.now(JAPAN_ZONE).getHour();
        if (today.getDayOfMonth() == reminderDay && nowHour == reminderHour) {
            executeSubmissionReminder();
        }
    }

    /**
     * 勤怠提出リマインドを送信します。
     * 当月の勤怠を未提出（または差し戻し・取り下げ状態）のユーザーにダッシュボード通知を作成します。
     * 管理者画面からの手動送信でも利用されます。
     */
    public void executeSubmissionReminder() {
        YearMonth currentMonth = YearMonth.now();
        log.info(BATCH_LOG_START, "勤怠提出リマインド: targetMonth=" + currentMonth);
        try {
            int count = userNotificationService.createRemindersForUnsubmittedUsers(currentMonth);
            log.info(BATCH_LOG_DONE, "勤怠提出リマインド: " + count + "名に通知作成");
            batchSettingService.recordReminderExecutedAt(LocalDateTime.now(JAPAN_ZONE));
        } catch (Exception e) {
            log.error(BATCH_LOG_ERROR, "勤怠提出リマインド", e.getMessage(), e);
        }
    }

    // -------------------------------------------------------
    // 2. 年次有給付与バッチ
    //    管理者ダッシュボードからの手動実行のみ（自動スケジュール実行は
    //    AutoGrantPaidLeaveService が担当）
    // -------------------------------------------------------

    /**
     * 年次有給付与バッチの実行結果。
     *
     * @param grantedCount 付与人数
     * @param skippedCount 当年付与済みのためスキップした人数
     */
    public record AnnualLeaveGrantResult(int grantedCount, int skippedCount) {
    }

    /**
     * 全アクティブユーザーに対して年次有給休暇を一括付与します。
     * ユーザーごとの次回付与日数（{@code annualLeaveGrantDays}）を有給残高として付与し、
     * {@link UserService#grantAnnualPaidLeave(Long)} により残日数・次回付与日数を更新します。
     * 当年分がすでに付与済みのユーザーはスキップします（{@code paid_leave_balance} の
     * {@code UNIQUE(user_id, grant_year)} 制約により二重付与できないため）。
     * 管理者画面からの手動実行専用です。例外はハンドリングせず呼び出し元に伝播します。
     *
     * @return 付与人数・スキップ人数
     */
    public AnnualLeaveGrantResult executeAnnualLeaveGrant() {
        LocalDate today = LocalDate.now(JAPAN_ZONE);
        log.info(BATCH_LOG_START, "年次有給付与: executedDate=" + today);

        List<User> users = userService.getActiveUsers();
        int grantedCount = 0;
        int skippedCount = 0;
        for (User user : users) {
            if (paidLeaveBalanceService.getByUserAndYear(user.getUserId(), today.getYear()).isPresent()) {
                log.info("年次有給付与: userId={} は{}年分を付与済みのためスキップ", user.getUserId(), today.getYear());
                skippedCount++;
                continue;
            }

            BigDecimal grantDays = user.getAnnualLeaveGrantDays() != null
                    ? BigDecimal.valueOf(user.getAnnualLeaveGrantDays())
                    : BigDecimal.valueOf(DEFAULT_ANNUAL_LEAVE_GRANT_DAYS);

            PaidLeaveBalance balance = new PaidLeaveBalance();
            balance.setUserId(user.getUserId());
            balance.setGrantYear(today.getYear());
            balance.setGrantedDays(grantDays);
            balance.setUsedDays(BigDecimal.ZERO);
            balance.setGrantDate(today);
            balance.setExpiryDate(today.plusYears(2).minusDays(1));
            paidLeaveBalanceService.insert(balance);

            // ユーザーテーブルの有給残日数の加算更新と次回付与日数のインクリメントを同期
            userService.grantAnnualPaidLeave(user.getUserId());
            grantedCount++;
        }
        log.info(BATCH_LOG_DONE, "年次有給付与: 付与" + grantedCount + "名, スキップ" + skippedCount + "名");
        batchSettingService.recordAnnualLeaveGrantExecutedAt(LocalDateTime.now(JAPAN_ZONE));
        return new AnnualLeaveGrantResult(grantedCount, skippedCount);
    }
}
