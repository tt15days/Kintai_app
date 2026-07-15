package com.attendance.app.service;


import com.attendance.app.entity.PaidLeaveBalance;
import com.attendance.app.entity.User;
import com.attendance.app.mapper.SystemSettingMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.attendance.app.util.DateTimeUtil;

import java.time.LocalDate;
import java.time.MonthDay;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AutoGrantPaidLeaveService {

    private static final DateTimeFormatter GRANT_DATE_FORMATTER = DateTimeFormatter.ofPattern("MM-dd");

    private final SystemSettingMapper systemSettingMapper;

    private final UserService userService;
    private final BatchSettingService batchSettingService;
    private final PaidLeaveBalanceService paidLeaveBalanceService;

    /**
     * 毎日深夜0時に実行される有給休暇自動付与バッチ
     */
    @Scheduled(cron = "0 0 0 * * ?", zone = "Asia/Tokyo")
    public void grantPaidLeaveBatch() {
        log.info("有給休暇自動付与バッチを開始します");

        try {
            String grantDateStr = systemSettingMapper.selectValueByKey(SystemSettingService.PAID_LEAVE_GRANT_DATE_KEY);
            if (grantDateStr == null || grantDateStr.isBlank()) {
                grantDateStr = SystemSettingService.DEFAULT_PAID_LEAVE_GRANT_DATE;
            }

            LocalDate today = DateTimeUtil.todayJapan();
            LocalDate grantDate = resolveGrantDate(grantDateStr, today.getYear());

            if (!grantDate.equals(today)) {
                log.debug("有給休暇自動付与対象外: grantDate={}, today={}", grantDate, today);
                return;
            }

            List<User> users = userService.getActiveUsers();

            // N+1対策として当年付与済みユーザーIDを一括取得してからループでフィルタする
            List<Long> userIds = users.stream().map(User::getUserId).toList();
            Set<Long> alreadyGrantedUserIds = paidLeaveBalanceService.getByUsersAndYear(userIds, today.getYear()).stream()
                    .map(PaidLeaveBalance::getUserId)
                    .collect(Collectors.toSet());

            int grantedCount = 0;
            int skippedCount = 0;
            int failedCount = 0;
            for (User user : users) {
                if (alreadyGrantedUserIds.contains(user.getUserId())) {
                    skippedCount++;
                    log.debug("有給休暇自動付与スキップ: userId={}, grantYear={}", user.getUserId(), today.getYear());
                    continue;
                }

                try {
                    // ユーザーテーブルの有給残日数の加算更新と次回付与日数のインクリメントを同期
                    // （内部で paid_leave_balance テーブルへのインサートおよび同期処理も実行されます）
                    userService.grantAnnualPaidLeave(user.getUserId());

                    grantedCount++;
                } catch (Exception e) {
                    failedCount++;
                    log.error("ユーザー {} への有給休暇付与に失敗しました。", user.getUserId(), e);
                }
            }

            log.info("有給休暇自動付与バッチ完了: granted={}, skipped={}, failed={}",
                    grantedCount, skippedCount, failedCount);
            batchSettingService.recordAnnualLeaveGrantExecutedAt(DateTimeUtil.nowJapan());
        } catch (Exception e) {
            log.error("有給休暇自動付与バッチ中にエラーが発生しました。", e);
        }
    }

    private LocalDate resolveGrantDate(String grantDate, int year) {
        if ("02-29".equals(grantDate)) {
            return LocalDate.of(year, 2, 28);
        }
        try {
            return MonthDay.parse(grantDate, GRANT_DATE_FORMATTER).atYear(year);
        } catch (DateTimeParseException e) {
            log.warn("有給付与日の設定が不正なため既定値を使用: value={}", grantDate);
            return MonthDay.parse(SystemSettingService.DEFAULT_PAID_LEAVE_GRANT_DATE, GRANT_DATE_FORMATTER)
                    .atYear(year);
        }
    }
}
