package com.attendance.app.service;

import com.attendance.app.entity.PaidLeaveBalance;
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
    private final PaidLeaveBalanceService paidLeaveBalanceService;
    private final UserService userService;
    private final BatchSettingService batchSettingService;

    /**
     * 毎日深夜0時に実行される有給休暇自動付与バッチ
     */
    @Scheduled(cron = "0 0 0 * * ?")
    public void grantPaidLeaveBatch() {
        log.info("有給休暇自動付与バッチを開始します。");

        try {
            String grantDateStr = systemSettingMapper.selectValueByKey("PAID_LEAVE_GRANT_DATE");
            String grantDaysStr = systemSettingMapper.selectValueByKey("PAID_LEAVE_GRANT_DAYS");

            if (grantDateStr == null || grantDaysStr == null) {
                log.info("自動付与設定が存在しないため、バッチを終了します。");
                return;
            }

            LocalDate today = LocalDate.now();
            String todayStr = String.format("%02d-%02d", today.getMonthValue(), today.getDayOfMonth());

            if (!grantDateStr.equals(todayStr)) {
                log.info("本日は有給休暇付与日 ({}) ではありません。本日: {}", grantDateStr, todayStr);
                return;
            }

            BigDecimal defaultGrantDays = new BigDecimal(grantDaysStr);
            List<User> users = userService.getActiveUsers();
            
            for (User user : users) {
                BigDecimal grantDays = user.getAnnualLeaveGrantDays() != null
                        ? BigDecimal.valueOf(user.getAnnualLeaveGrantDays())
                        : defaultGrantDays;

                PaidLeaveBalance balance = new PaidLeaveBalance();
                balance.setUserId(user.getUserId());
                balance.setGrantYear(today.getYear());
                balance.setGrantedDays(grantDays);
                balance.setUsedDays(BigDecimal.ZERO);
                // 付与日と有効期限(2年後)をセット
                balance.setGrantDate(today);
                balance.setExpiryDate(today.plusYears(2).minusDays(1));
                
                paidLeaveBalanceService.insert(balance);
                
                // ユーザーテーブルの有給残日数の加算更新と次回付与日数のインクリメントを同期
                userService.grantAnnualPaidLeave(user.getUserId());
                
                log.info("ユーザー {} に有給休暇 {} 日を付与しました。", user.getUserId(), grantDays);
            }

            log.info("有給休暇自動付与バッチが正常に終了しました。");
            batchSettingService.recordAnnualLeaveGrantExecutedAt(LocalDateTime.now(ZoneId.of("Asia/Tokyo")));
        } catch (Exception e) {
            log.error("有給休暇自動付与バッチ中にエラーが発生しました。", e);
        }
    }
}
