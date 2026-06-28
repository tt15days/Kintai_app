package com.attendance.app.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * PaidLeaveBalance Entity - 有給休暇年次残高
 *
 * 有給休暇の付与日・失効日・繰越を年次単位で管理します。
 * 有給休暇取得承認時に used_days を加算します。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaidLeaveBalance {

    /** 残高ID（PK） */
    private Long balanceId;
    /** ユーザーID */
    private Long userId;
    /** 付与年度（例: 2026） */
    private Integer grantYear;
    /** 当年付与日数 */
    private BigDecimal grantedDays;
    /** 付与日 */
    private LocalDate grantDate;
    /** 失効日 */
    private LocalDate expiryDate;
    /** 前年度繰越日数 */
    private BigDecimal carriedOverDays;
    /** 使用済み日数 */
    private BigDecimal usedDays;
    /** 作成日時（UTC） */
    private Instant createdAt;
    /** 更新日時（UTC） */
    private Instant updatedAt;

    /**
     * 有効残日数（付与 + 繰越 - 使用済み）を返します。
     *
     * @return 残日数
     */
    public BigDecimal getRemainingDays() {
        BigDecimal total = (grantedDays != null ? grantedDays : BigDecimal.ZERO)
                .add(carriedOverDays != null ? carriedOverDays : BigDecimal.ZERO);
        BigDecimal used = usedDays != null ? usedDays : BigDecimal.ZERO;
        return total.subtract(used);
    }
}
