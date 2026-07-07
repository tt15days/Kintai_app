package com.attendance.app.service;

import com.attendance.app.entity.PaidLeaveBalance;
import com.attendance.app.mapper.PaidLeaveBalanceMapper;
import com.attendance.app.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 有給休暇年次残高の業務ロジックを提供するサービスです。
 *
 * 有給休暇の付与日・失効日・繰越を年次単位で管理します。
 * 有給休暇申請が承認される際に {@link #deductBalance} を呼び出して使用日数を更新します。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PaidLeaveBalanceService {

    private final PaidLeaveBalanceMapper paidLeaveBalanceMapper;
    private final UserMapper userMapper;

    private void syncUserPaidLeaveDays(Long userId) {
        BigDecimal remainingDays = getTotalRemainingDays(userId);
        userMapper.updatePaidLeaveDays(userId, remainingDays);
    }

    /**
     * ユーザーの全有給残高を付与年度降順で取得します。
     *
     * @param userId ユーザーID
     * @return 全有給残高リスト
     */
    @Transactional(readOnly = true)
    public List<PaidLeaveBalance> getBalancesByUserId(Long userId) {
        return paidLeaveBalanceMapper.selectByUserId(userId);
    }

    /**
     * ユーザーの有効な有給残高（失効日が今日以降かつ残日数あり）を失効日昇順で取得します。
     *
     * @param userId ユーザーID
     * @return 有効な有給残高リスト（失効日の早い順）
     */
    @Transactional(readOnly = true)
    public List<PaidLeaveBalance> getActiveBalances(Long userId) {
        return paidLeaveBalanceMapper.selectActiveByUserId(userId, LocalDate.now());
    }

    /**
     * 指定ユーザーの全有効残日数を返します。
     *
     * @param userId ユーザーID
     * @return 合計残日数
     */
    @Transactional(readOnly = true)
    public BigDecimal getTotalRemainingDays(Long userId) {
        return getActiveBalances(userId).stream()
                .map(p -> p.getRemainingDays())
                .reduce(BigDecimal.ZERO, (a, b) -> a.add(b));
    }

    /**
     * 指定ユーザーと付与年度の有給残高を取得します。
     *
     * @param userId    ユーザーID
     * @param grantYear 付与年度
     * @return 有給残高
     */
    @Transactional(readOnly = true)
    public Optional<PaidLeaveBalance> getByUserAndYear(Long userId, Integer grantYear) {
        return paidLeaveBalanceMapper.selectByUserAndYear(userId, grantYear);
    }

    /**
     * 有給休暇の使用日数を残高から減算します。
     * 失効日が最も早い有効残高から順に消化します（先入れ先出し方式）。
     * 残高が不足している場合は例外を送出し、呼び出し元のトランザクションをロールバックさせます。
     *
     * @param userId        ユーザーID
     * @param daysToDeduct  減算する日数
     * @throws IllegalArgumentException 有効残高が不足している場合
     */
    public void deductBalance(Long userId, BigDecimal daysToDeduct, LocalDate targetDate) {
        if (daysToDeduct == null || daysToDeduct.compareTo(BigDecimal.ZERO) <= 0 || targetDate == null) {
            return;
        }

        List<PaidLeaveBalance> activeBalances = paidLeaveBalanceMapper.selectActiveByUserIdForUpdate(userId, targetDate);
        BigDecimal remaining = daysToDeduct;

        for (PaidLeaveBalance balance : activeBalances) {
            BigDecimal available = balance.getRemainingDays();
            if (available.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            BigDecimal deduct = remaining.min(available);
            balance.setUsedDays(balance.getUsedDays().add(deduct));
            paidLeaveBalanceMapper.update(balance);
            remaining = remaining.subtract(deduct);

            log.info("有給残高を減算: userId={}, grantYear={}, deducted={}, remainingBalance={}",
                    userId, balance.getGrantYear(), deduct, balance.getRemainingDays());

            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
        }

        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            log.warn("有給残高が不足: userId={}, 不足日数={}", userId, remaining);
            throw new IllegalArgumentException(
                    "有給休暇の残日数が不足しています（不足: " + remaining.stripTrailingZeros().toPlainString() + "日）");
        }

        syncUserPaidLeaveDays(userId);
    }

    /**
     * 有給休暇の返還（巻き戻し）処理を行います。
     * 休暇取得日時点で有効だった有給残高（失効日が休暇日以降）から、
     * 直近に使用されたものから順に used_days を減算（返還）します。
     *
     * @param userId        ユーザーID
     * @param daysToRefund  返還する日数
     * @param targetDate    休暇の開始日（基準日）
     */
    public void refundBalance(Long userId, BigDecimal daysToRefund, LocalDate targetDate) {
        if (daysToRefund == null || daysToRefund.compareTo(BigDecimal.ZERO) <= 0 || targetDate == null) {
            return;
        }

        // 休暇取得日時点で有効だった有給残高（失効日が休暇取得日以降）を取得し、排他ロックを獲得
        List<PaidLeaveBalance> activeBalances = new java.util.ArrayList<>(paidLeaveBalanceMapper.selectActiveByUserIdForUpdate(userId, targetDate));
        // 直近に消化されたものから返還するため、失効日が遅い順（新しい有休）にソート
        activeBalances.sort((b1, b2) -> b2.getExpiryDate().compareTo(b1.getExpiryDate()));

        BigDecimal remaining = daysToRefund;
        for (PaidLeaveBalance balance : activeBalances) {
            BigDecimal used = balance.getUsedDays();
            if (used.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            BigDecimal refund = remaining.min(used);
            balance.setUsedDays(used.subtract(refund));
            paidLeaveBalanceMapper.update(balance);
            remaining = remaining.subtract(refund);

            log.info("有給残高を返還しました: userId={}, grantYear={}, refunded={}, remainingBalance={}",
                    userId, balance.getGrantYear(), refund, balance.getRemainingDays());

            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
        }

        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            log.warn("返還する有給使用履歴が不足しています: userId={}, 未返還日数={}", userId, remaining);
        }

        syncUserPaidLeaveDays(userId);
    }

    /**
     * 有給残高を挿入します（管理者によるバッチ付与処理などで使用）。
     *
     *
     * @param balance 挿入する有給残高
     * @return 挿入された有給残高
     */
    public PaidLeaveBalance insert(PaidLeaveBalance balance) {
        paidLeaveBalanceMapper.insert(balance);
        log.info("有給残高を付与: userId={}, grantYear={}, grantedDays={}",
                balance.getUserId(), balance.getGrantYear(), balance.getGrantedDays());
        syncUserPaidLeaveDays(balance.getUserId());
        return balance;
    }
}
