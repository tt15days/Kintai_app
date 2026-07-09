package com.attendance.app.service;


import com.attendance.app.entity.User;
import com.attendance.app.mapper.SystemSettingMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

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
            String grantDateStr = systemSettingMapper.selectValueByKey("PAID_LEAVE_GRANT_DATE");
            String grantDaysStr = systemSettingMapper.selectValueByKey("PAID_LEAVE_GRANT_DAYS");

            if (grantDateStr == null || grantDaysStr == null) {
                log.info("自動付与設定が存在しないため、バッチを終了します。");
                return;
            }

            LocalDate today = LocalDate.now(ZoneId.of("Asia/Tokyo"));
            String todayStr = String.format("%02d-%02d", today.getMonthValue(), today.getDayOfMonth());

            if (!grantDateStr.equals(todayStr)) {
                log.info("本日は有給休暇付与日 ({}) ではありません。本日: {}", grantDateStr, todayStr);
                return;
            }

            BigDecimal defaultGrantDays = new BigDecimal(grantDaysStr);
            List<User> users = userService.getActiveUsers();

            for (User user : users) {
                if (paidLeaveBalanceService.getByUserAndYear(user.getUserId(), today.getYear()).isPresent()) {
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
            batchSettingService.recordAnnualLeaveGrantExecutedAt(LocalDateTime.now(ZoneId.of("Asia/Tokyo")));
        } catch (Exception e) {
            log.error("有給休暇自動付与バッチ中にエラーが発生しました。", e);
        }
    }
}
