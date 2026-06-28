package com.attendance.app.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * AttendanceCorrectionRequest Entity - 勤怠修正申請
 *
 * 月次勤怠が承認済み（APPROVED）または申請中（PENDING）で直接編集できない場合に、
 * 個別日の勤怠修正を申請するワークフロー用エンティティ。
 *
 * ステータス遷移:
 *   PENDING → APPROVED : 承認者が承認 → attendance_records に反映
 *   PENDING → REJECTED : 承認者が却下 → attendance_records は変更なし
 *   PENDING → WITHDRAWN: 申請者が取り下げ
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceCorrectionRequest {
    /** 修正申請ID（PK） */
    private Long requestId;
    /** 申請対象のユーザーID */
    private Long userId;
    /** 勤怠対象日 */
    private LocalDate attendanceDate;
    /** 対象年月（例: "2026-06"） */
    private String targetYearMonth;

    // 修正後の希望値
    /** 希望する出勤時刻 */
    private LocalTime requestedStartTime;
    /** 希望する退勤時刻 */
    private LocalTime requestedEndTime;
    /** 希望する休憩時間（分） */
    private Integer requestedBreakTimeMinutes;
    /** 希望する深夜労働時間（時間） */
    private Double requestedNightShiftHours;
    /** 修正後の備考 */
    private String requestedRemarks;

    // 修正理由（必須）
    /** 修正理由（必須） */
    private String reason;

    // 申請時点のスナップショット（承認画面での差分表示用）
    /** 申請時点の出勤時刻 */
    private LocalTime currentStartTime;
    /** 申請時点の退勤時刻 */
    private LocalTime currentEndTime;
    /** 申請時点の休憩時間（分） */
    private Integer currentBreakTimeMinutes;
    /** 申請時点の深夜労働時間（時間） */
    private Double currentNightShiftHours;
    /** 申請時点の備考 */
    private String currentRemarks;

    // PENDING / APPROVED / REJECTED / WITHDRAWN
    /** 申請ステータス（PENDING / APPROVED / REJECTED / WITHDRAWN） */
    private String status;

    /** 申請日時 */
    private Instant submittedAt;
    /** 承認/却下等のアクションを行ったユーザーID */
    private Long actionBy;
    /** 承認者によるコメント */
    private String actionComment;
    /** アクション日時 */
    private Instant actionAt;
    /** レコード作成日時（UTC） */
    private Instant createdAt;
    /** レコード更新日時（UTC） */
    private Instant updatedAt;
}
