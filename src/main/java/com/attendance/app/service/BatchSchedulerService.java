package com.attendance.app.service;

import com.attendance.app.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import com.attendance.app.util.DateTimeUtil;

/**
 * BatchSchedulerService - 定期バッチ処理
 *
 * ポーリング方式で動作します。Spring の cron は固定値のため、
 * 毎日/毎時起動してシステム設定と照合し、実行タイミングを動的に判定します。
 *
 * <ol>
 *   <li><b>月次集計</b>: 毎日 01:00（JST）に、今日が「勤怠期間終了日 + daysAfterEnd」かをチェックして実行</li>
 *   <li><b>利用終了日到達ユーザーの無効化</b>: 毎日 00:30（JST）に実行</li>
 *   <li><b>勤怠提出リマインド</b>: 毎時 00分（JST）に、今日の日付・時刻が設定と一致するかをチェックして実行</li>
 * </ol>
 * 年次有給の自動付与は {@link AutoGrantPaidLeaveService} が毎日 00:00（JST）に実行します。
 *
 * タイムゾーン: すべての cron 式は Asia/Tokyo で評価されます。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BatchSchedulerService {

    private static final int BATCH_PAGE_SIZE = 100;

    private static final String BATCH_LOG_START = "バッチ開始: {}";
    private static final String BATCH_LOG_DONE  = "バッチ完了: {}";
    private static final String BATCH_LOG_ERROR = "バッチ異常終了: job={}, error={}";

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
        LocalDate today = DateTimeUtil.todayJapan();
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
            int warningHours = batchSettingService.getAlertArticle36Limit1();
            int limitHours = batchSettingService.getAlertArticle36Limit2();
            int alertCount = 0;
            int warningCount = 0;
            int processedCount = 0;
            long afterUserId = 0;
            while (true) {
                List<User> users = userService.getUsersAfterId(
                        null, null, true, false, afterUserId, BATCH_PAGE_SIZE);
                if (users.isEmpty()) {
                    break;
                }
                List<AttendanceRecordService.MonthlyUserSummary> summaries = attendanceRecordService
                        .getMonthlyAggregateForUsers(targetMonth, users.stream().map(User::getUserId).toList());
                for (AttendanceRecordService.MonthlyUserSummary s : summaries) {
                if (s.overtimeHours() >= limitHours) {
                    alertCount++;
                    log.debug("36協定超過: userId={}, yearMonth={}, overtimeHours={}",
                            s.userId(), targetMonth, s.overtimeHours());
                } else if (s.overtimeHours() >= warningHours) {
                    warningCount++;
                    log.debug("36協定注意: userId={}, yearMonth={}, overtimeHours={}",
                            s.userId(), targetMonth, s.overtimeHours());
                } else {
                    log.debug("月次集計: userId={}, yearMonth={}, workingHours={}, overtimeHours={}, recordCount={}",
                            s.userId(), targetMonth, s.workingHours(), s.overtimeHours(), s.recordCount());
                }
                }
                processedCount += users.size();
                afterUserId = users.get(users.size() - 1).getUserId();
            }
            log.info(BATCH_LOG_DONE, "月次集計: targetMonth=" + targetMonth + ", processed=" + processedCount
                    + ", warning=" + warningCount + ", alert=" + alertCount);
            batchSettingService.recordMonthlySummaryExecutedAt(DateTimeUtil.nowJapan());
        } catch (Exception e) {
            log.error(BATCH_LOG_ERROR, "月次集計", e.getMessage(), e);
        }
    }


    // -------------------------------------------------------
    // 2. 利用終了日到達ユーザーの自動無効化バッチ
    //    毎日 00:30（JST）に実行
    // -------------------------------------------------------

    /**
     * 利用終了日（scheduled_end_date）を過ぎた有効ユーザーを自動無効化します。
     */
    @Scheduled(cron = "0 30 0 * * ?", zone = "Asia/Tokyo")
    public void runExpiredUserDeactivationCheck() {
        try {
            executeExpiredUserDeactivation();
        } catch (Exception e) {
            log.error(BATCH_LOG_ERROR, "利用終了日自動無効化", e.getMessage(), e);
        }
    }

    /**
     * 利用終了日到達ユーザーの無効化を実行します。
     * 管理者画面からの手動実行でも利用されます。
     *
     * @return 無効化したユーザー数
     */
    public int executeExpiredUserDeactivation() {
        log.info(BATCH_LOG_START, "利用終了日自動無効化");
        int count = userService.deactivateExpiredUsers();
        log.info(BATCH_LOG_DONE, "利用終了日自動無効化: " + count + "名を無効化");
        return count;
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
        LocalDate today = DateTimeUtil.todayJapan();
        int reminderDay  = batchSettingService.getReminderDay();
        int reminderHour = batchSettingService.getReminderHour();
        int nowHour = DateTimeUtil.currentTimeJapan().getHour();
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
        YearMonth currentMonth = YearMonth.from(DateTimeUtil.todayJapan());
        log.info(BATCH_LOG_START, "勤怠提出リマインド: targetMonth=" + currentMonth);
        try {
            int count = userNotificationService.createRemindersForUnsubmittedUsers(currentMonth);
            log.info(BATCH_LOG_DONE, "勤怠提出リマインド: " + count + "名に通知作成");
            batchSettingService.recordReminderExecutedAt(DateTimeUtil.nowJapan());
        } catch (Exception e) {
            log.error(BATCH_LOG_ERROR, "勤怠提出リマインド", e.getMessage(), e);
        }
    }

    // -------------------------------------------------------
    // 4. 年次有給付与バッチ
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
     * {@link UserService#grantAnnualPaidLeave(Long)} により実効付与日数を解決し、残日数・次回付与日数を更新します。
     * 当年分がすでに付与済みのユーザーはスキップします（{@code paid_leave_balance} の
     * {@code UNIQUE(user_id, grant_year)} 制約により二重付与できないため）。
     * 管理者画面からの手動実行専用です。例外はハンドリングせず呼び出し元に伝播します。
     *
     * @return 付与人数・スキップ人数
     */
    public AnnualLeaveGrantResult executeAnnualLeaveGrant() {
        LocalDate today = DateTimeUtil.todayJapan();
        log.info(BATCH_LOG_START, "年次有給付与: executedDate=" + today);

        List<User> users = userService.getActiveUsers();

        // N+1対策として当年付与済みユーザーIDを一括取得してからループでフィルタする
        List<Long> userIds = users.stream().map(User::getUserId).toList();
        java.util.Set<Long> alreadyGrantedUserIds = paidLeaveBalanceService.getByUsersAndYear(userIds, today.getYear()).stream()
                .map(com.attendance.app.entity.PaidLeaveBalance::getUserId)
                .collect(java.util.stream.Collectors.toSet());

        int grantedCount = 0;
        int skippedCount = 0;
        for (User user : users) {
            if (alreadyGrantedUserIds.contains(user.getUserId())) {
                log.debug("年次有給付与スキップ: userId={}, grantYear={}", user.getUserId(), today.getYear());
                skippedCount++;
                continue;
            }

            try {
                // ユーザーテーブルの有給残日数の加算更新と次回付与日数のインクリメントを同期
                // （内部で paid_leave_balance テーブルへのインサートも実行されます）
                userService.grantAnnualPaidLeave(user.getUserId());
                grantedCount++;
            } catch (Exception e) {
                log.error("年次有給付与: userId={} への付与に失敗しました。", user.getUserId(), e);
            }
        }
        log.info(BATCH_LOG_DONE, "年次有給付与: granted=" + grantedCount + ", skipped=" + skippedCount
                + ", failed=" + (users.size() - grantedCount - skippedCount));
        batchSettingService.recordAnnualLeaveGrantExecutedAt(DateTimeUtil.nowJapan());
        return new AnnualLeaveGrantResult(grantedCount, skippedCount);
    }
}
