package com.attendance.app.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * LeaveApplication Entity - 休暇申請
 *
 * タイムゾーン対応:
 * - 日付のみ必要な項目は LocalDate 型を使用（leaveStartDate, leaveEndDate）
 * - タイムスタンプ項目は Instant 型（UTC、TIMESTAMP WITH TIME ZONE）で管理
 * - createdAt: 申請作成日時（UTC）
 * - updatedAt: 申請更新日時（UTC）
 * - approvedAt: 承認日時（UTC）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaveApplication {
    /** 申請ID（PK） */
    private Long applicationId;
    /** ユーザーID */
    private Long userId;
    /** 休暇開始日 */
    private LocalDate leaveStartDate;
    /** 休暇終了日 */
    private LocalDate leaveEndDate;
    /** 休暇期間種別（FULL_DAY, AM_HALF, PM_HALF等） */
    private String leaveDurationType;
    /** 実消化日数（承認時に計算・保存。NULLは旧データ） */
    private BigDecimal consumedDays;
    /** 休暇種別（PAID_LEAVE, SICK_LEAVEなど） */
    private LeaveType leaveType;
    /** 休暇理由 */
    private String reason;
    /** 申請ステータス（PENDING, APPROVED, REJECTED） */
    private LeaveStatus status;
    /** 作成日時（UTC） */
    private Instant createdAt;
    /** 更新日時（UTC） */
    private Instant updatedAt;
    /** 承認日時（UTC） */
    private Instant approvedAt;
    /** 承認者のユーザーID */
    private Long approvedBy;
    /** 論理削除フラグ */
    @Builder.Default
    private Boolean isDeleted = false;
}