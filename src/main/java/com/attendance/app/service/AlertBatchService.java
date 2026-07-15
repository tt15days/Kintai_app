package com.attendance.app.service;

import com.attendance.app.dto.Article36AlertDto;
import com.attendance.app.dto.PaidLeaveAlertDto;
import com.attendance.app.entity.UserNotification;
import com.attendance.app.mapper.AlertBatchMapper;
import com.attendance.app.mapper.UserNotificationMapper;
import com.attendance.app.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertBatchService {

    private static final int BATCH_PAGE_SIZE = 100;

    private static final String BATCH_LOG_START = "バッチ開始: {}";
    private static final String BATCH_LOG_DONE  = "バッチ完了: {}";
    private static final String BATCH_LOG_ERROR = "バッチ異常終了: job={}, error={}";

    private final AlertBatchMapper alertBatchMapper;
    private final BatchSettingService batchSettingService;
    private final UserNotificationMapper userNotificationMapper;
    private final AttendancePeriodSettingService attendancePeriodSettingService;

    /**
     * 毎月1日の深夜3時に実行されるアラートバッチ（前月の36協定・有給消化をチェック）
     * （実際の運用に合わせてcron式は変更可能）
     *
     * 36協定チェックと有給消化チェックは互いに独立した通知作成処理であり、
     * 通知INSERTは1文で原子的に完結するため、チェック単位で個別にtry/catchし、
     * 他バッチ（BatchSchedulerService 等）と同じ構造化ログで失敗を記録する。
     * 一方のチェックが失敗しても、もう一方の結果はロールバックされない。
     */
    @Scheduled(cron = "0 0 3 1 * ?", zone = "Asia/Tokyo")
    public void runAlertBatch() {
        LocalDate currentDate = DateTimeUtil.todayJapan();
        // 36協定は直近で締まった勤怠期間（設定された締め日基準）を対象とする
        YearMonth lastMonth = YearMonth.from(currentDate).minusMonths(1);
        AttendancePeriodSettingService.AttendancePeriod period =
                attendancePeriodSettingService.resolvePeriod(lastMonth);
        LocalDate startDate = period.startDate();
        LocalDate endDate = period.endDate();

        log.info(BATCH_LOG_START, "36協定アラートバッチ");
        try {
            checkArticle36Alerts(startDate, endDate);
            log.info(BATCH_LOG_DONE, "36協定アラートバッチ");
        } catch (Exception e) {
            log.error(BATCH_LOG_ERROR, "36協定アラートバッチ", e.getMessage(), e);
        }

        log.info(BATCH_LOG_START, "有給消化アラートバッチ");
        try {
            checkPaidLeaveAlerts(currentDate);
            log.info(BATCH_LOG_DONE, "有給消化アラートバッチ");
        } catch (Exception e) {
            log.error(BATCH_LOG_ERROR, "有給消化アラートバッチ", e.getMessage(), e);
        }

        batchSettingService.recordAlertBatchExecutedAt(DateTimeUtil.nowJapan());
    }

    /**
     * 手動でアラートバッチを実行するためのメソッド
     */
    @Transactional
    public void runAlertBatchManually(YearMonth targetMonth) {
        log.info("手動アラートバッチ処理を開始します。対象年月: {}", targetMonth);

        AttendancePeriodSettingService.AttendancePeriod period =
                attendancePeriodSettingService.resolvePeriod(targetMonth);
        LocalDate startDate = period.startDate();
        LocalDate endDate = period.endDate();
        LocalDate currentDate = DateTimeUtil.todayJapan();

        checkArticle36Alerts(startDate, endDate);
        checkPaidLeaveAlerts(currentDate);
        batchSettingService.recordAlertBatchExecutedAt(DateTimeUtil.nowJapan());

        log.info("手動アラートバッチ処理が完了しました。");
    }

    private void checkArticle36Alerts(LocalDate startDate, LocalDate endDate) {
        int limit1 = batchSettingService.getAlertArticle36Limit1();
        int limit2 = batchSettingService.getAlertArticle36Limit2();

        log.info("36協定アラートチェック開始 (期間: {} - {}, 第1閾値: {}, 第2閾値: {})", startDate, endDate, limit1, limit2);

        String idempotencyKey = startDate + "/" + endDate;

        int count = 0;
        long afterUserId = 0;
        while (true) {
            List<Article36AlertDto> alertUsers = alertBatchMapper.findUsersExceedingOvertimeLimit(
                    startDate, endDate, limit1, afterUserId, BATCH_PAGE_SIZE);
            if (alertUsers.isEmpty()) {
                break;
            }
            for (Article36AlertDto dto : alertUsers) {
            int hours = dto.getTotalOvertimeHours().intValue();
            BigDecimal displayHours = dto.getTotalOvertimeHours().setScale(1, RoundingMode.HALF_UP);
            String message;
            String type;

            if (hours >= limit2) {
                // 第2警告
                type = "ALERT_ARTICLE_36_LIMIT2";
                message = String.format("【警告】集計期間 (%s〜%s) の残業時間が %s 時間に達しました。第2警告閾値(%d時間)を超過しています。直ちに労務管理者に報告し、労働時間の是正を行ってください。",
                        startDate, endDate, displayHours, limit2);
            } else {
                // 第1警告
                type = "ALERT_ARTICLE_36_LIMIT1";
                message = String.format("【注意】集計期間 (%s〜%s) の残業時間が %s 時間に達しました。第1警告閾値(%d時間)を超過しています。労働時間の調整を行ってください。",
                        startDate, endDate, displayHours, limit1);
            }

            try {
                count += createBatchNotification(dto.getUserId(), message, type, idempotencyKey);
            } catch (Exception e) {
                log.error("ユーザー {} への36協定アラート通知作成に失敗しました。", dto.getUserId(), e);
            }
            }
            afterUserId = alertUsers.get(alertUsers.size() - 1).getUserId();
        }
        log.info("36協定アラート: {} 件の通知を作成しました。", count);
    }

    private void checkPaidLeaveAlerts(LocalDate currentDate) {
        int months = batchSettingService.getAlertPaidLeaveMonths();
        int days = batchSettingService.getAlertPaidLeaveDays();

        log.info("有給消化アラートチェック開始 (基準日: {}, 経過月数: {}, 基準日数: {})", currentDate, months, days);

        String type = "ALERT_PAID_LEAVE";
        String idempotencyKey = YearMonth.from(currentDate).toString();

        int count = 0;
        long afterUserId = 0;
        while (true) {
            List<PaidLeaveAlertDto> alertUsers = alertBatchMapper.findUsersWithInsufficientPaidLeave(
                    months, days, currentDate, afterUserId, BATCH_PAGE_SIZE);
            if (alertUsers.isEmpty()) {
                break;
            }
            for (PaidLeaveAlertDto dto : alertUsers) {
            String message = String.format("【重要: 有給消化】有給休暇が付与された日（%s）から %d ヶ月が経過しましたが、消化日数が %s 日となっており、基準である %d 日を下回っています。計画的な有給取得をお願いします。",
                    dto.getGrantDate(), months, dto.getUsedDays().toString(), days);

            try {
                count += createBatchNotification(dto.getUserId(), message, type, idempotencyKey);
            } catch (Exception e) {
                log.error("ユーザー {} への有給消化アラート通知作成に失敗しました。", dto.getUserId(), e);
            }
            }
            afterUserId = alertUsers.get(alertUsers.size() - 1).getUserId();
        }
        log.info("有給消化アラート: {} 件の通知を作成しました。", count);
    }

    private int createBatchNotification(Long userId, String message, String type, String idempotencyKey) {
        UserNotification notification = new UserNotification();
        notification.setUserId(userId);
        notification.setMessage(message);
        notification.setIsRead(false);
        notification.setNotificationType(type);
        notification.setIdempotencyKey(idempotencyKey);

        return userNotificationMapper.insertBatchIfAbsent(notification);
    }
}
