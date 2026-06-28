package com.attendance.app.entity;

import java.time.Instant;
import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OvertimeRecord Entity - 残業管理記録
 *
 * タイムゾーン対応:
 * - overtimeDate: 残業日付（LocalDate）
 * - overtimeStart: 残業開始時刻（Instant、UTC、TIMESTAMP WITH TIME ZONE）
 * - overtimeEnd: 残業終了時刻（Instant、UTC、TIMESTAMP WITH TIME ZONE）
 * - createdAt: 記録作成日時（UTC、TIMESTAMP WITH TIME ZONE）
 * - updatedAt: 記録更新日時（UTC、TIMESTAMP WITH TIME ZONE）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OvertimeRecord {
    /** 残業記録ID（PK） */
    private Long overtimeId;
    /** ユーザーID */
    private Long userId;
    /** 残業日付 */
    private LocalDate overtimeDate;
    /** 残業開始時刻（UTC） */
    private Instant overtimeStart;
    /** 残業終了時刻（UTC） */
    private Instant overtimeEnd;
    /** 残業時間（時間） */
    private Double overtimeHours;
    /** 残業理由 */
    private String reason;
    /** 備考・特記事項 */
    private String remarks;
    /** 作成日時（UTC） */
    private Instant createdAt;
    /** 更新日時（UTC） */
    private Instant updatedAt;
    /** 論理削除フラグ */
    @Builder.Default
    private Boolean isDeleted = false;
}