package com.attendance.app.service;


import com.attendance.app.entity.PaidLeaveBalance;
import com.attendance.app.entity.User;
import com.attendance.app.mapper.SystemSettingMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.attendance.app.util.DateTimeUtil;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AutoGrantPaidLeaveService {

    private final SystemSettingMapper systemSettingMapper;

    private final UserService userService;
    private final BatchSettingService batchSettingService;
    private final PaidLeaveBalanceService paidLeaveBalanceService;

    /**
     * 毎日深夜0時に実行される有給休暇自動付与バッチ
     */
    @Scheduled(cron = "0 0 0 * * ?", zone = "Asia/Tokyo")
    public void grantPaidLeaveBatch() {
        log.info("有給休暇自動付与バッチを開始します。");

        try {
            String grantDateStr = systemSettingMapper.selectValueByKey(SystemSettingService.PAID_LEAVE_GRANT_DATE_KEY);
            String grantDaysStr = systemSettingMapper.selectValueByKey(SystemSettingService.PAID_LEAVE_GRANT_DAYS_KEY);
            if (grantDateStr == null || grantDateStr.isBlank()) {
                grantDateStr = SystemSettingService.DEFAULT_PAID_LEAVE_GRANT_DATE;
            }
            if (grantDaysStr == null || grantDaysStr.isBlank()) {
                grantDaysStr = SystemSettingService.DEFAULT_PAID_LEAVE_GRANT_DAYS;
            }

            LocalDate today = DateTimeUtil.todayJapan();
            String todayStr = String.format("%02d-%02d", today.getMonthValue(), today.getDayOfMonth());

            if (!grantDateStr.equals(todayStr)) {
                log.info("本日は有給休暇付与日 ({}) ではありません。本日: {}", grantDateStr, todayStr);
                return;
            }

            BigDecimal defaultGrantDays = new BigDecimal(grantDaysStr);
            List<User> users = userService.getActiveUsers();

            // N+1対策として当年付与済みユーザーIDを一括取得してからループでフィルタする
            List<Long> userIds = users.stream().map(User::getUserId).toList();
            Set<Long> alreadyGrantedUserIds = paidLeaveBalanceService.getByUsersAndYear(userIds, today.getYear()).stream()
                    .map(PaidLeaveBalance::getUserId)
                    .collect(Collectors.toSet());

            for (User user : users) {
                if (alreadyGrantedUserIds.contains(user.getUserId())) {
                    log.info("ユーザー {} は{}年分を付与済みのためスキップします。", user.getUserId(), today.getYear());
                    continue;
                }

                BigDecimal grantDays = user.getAnnualLeaveGrantDays() != null
                        ? BigDecimal.valueOf(user.getAnnualLeaveGrantDays())
                        : defaultGrantDays;

                try {
                    // ユーザーテーブルの有給残日数の加算更新と次回付与日数のインクリメントを同期
                    // （内部で paid_leave_balance テーブルへのインサートおよび同期処理も実行されます）
                    userService.grantAnnualPaidLeave(user.getUserId(), grantDays.intValue());

                    log.info("ユーザー {} に有給休暇 {} 日を付与しました。", user.getUserId(), grantDays);
                } catch (Exception e) {
                    log.error("ユーザー {} への有給休暇付与に失敗しました。", user.getUserId(), e);
                }
            }

            log.info("有給休暇自動付与バッチが正常に終了しました。");
            batchSettingService.recordAnnualLeaveGrantExecutedAt(DateTimeUtil.nowJapan());
        } catch (Exception e) {
            log.error("有給休暇自動付与バッチ中にエラーが発生しました。", e);
        }
    }
}
