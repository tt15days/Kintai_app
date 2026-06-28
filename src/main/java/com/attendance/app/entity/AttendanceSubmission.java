package com.attendance.app.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

/**
 * AttendanceSubmission Entity - 月次勤怠申請情報
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceSubmission {
    /** 申請ID（PK） */
    private Long submissionId;
    /** ユーザーID */
    private Long userId;
    /** 対象年月（例: "2026-06"） */
    private String targetYearMonth;
    /** 申請ステータス（PENDING / APPROVED / REJECTED など） */
    private String status;
    /** 申請日時 */
    private Instant submittedAt;
    /** 承認・却下等のアクションを行ったユーザーID */
    private Long actionBy;
    /** 承認者によるコメント */
    private String actionComment;
    /** アクション日時 */
    private Instant actionAt;
    /** 締め対象期間の開始日 */
    private LocalDate startDate;
    /** 締め対象期間の終了日 */
    private LocalDate endDate;
    /** レコード作成日時（UTC） */
    private Instant createdAt;
    /** レコード更新日時（UTC） */
    private Instant updatedAt;
}
