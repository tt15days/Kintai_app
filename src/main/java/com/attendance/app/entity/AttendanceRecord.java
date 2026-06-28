package com.attendance.app.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

/**
 * AttendanceRecord Entity - 勤怠記録
 *
 * タイムゾーン対応: タイムスタンプは Instant 型（UTC）で管理
 * - attendanceDate: 勤務日付（UTC、TIMESTAMP WITH TIME ZONE、実質的には日付）
 * - startTime: 出勤時刻（UTC、TIMESTAMP WITH TIME ZONE）
 * - endTime: 退勤時刻（UTC、TIMESTAMP WITH TIME ZONE）
 * - createdAt: 記録作成日時（UTC、TIMESTAMP WITH TIME ZONE）
 * - updatedAt: 記録更新日時（UTC、TIMESTAMP WITH TIME ZONE）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceRecord {
    /** 勤怠記録ID（PK） */
    private Long recordId;
    /** ユーザーID */
    private Long userId;
    /** 勤務日付（UTC、実質的な日付情報を保持） */
    private Instant attendanceDate;
    /** 出勤時刻（UTC） */
    private Instant startTime;
    /** 退勤時刻（UTC） */
    private Instant endTime;
    /** 総実労働時間（時間） */
    private Double workingHours;
    /** 休憩時間（分） */
    @Builder.Default
    private Integer breakTimeMinutes = 0;
    /** 残業時間（時間） */
    @Builder.Default
    private Double overtimeHours = 0.0;
    /** 深夜労働時間（時間） */
    @Builder.Default
    private Double nightShiftHours = 0.0;
    /** 休日労働時間（時間） */
    @Builder.Default
    private Double holidayWorkHours = 0.0;
    /** 勤怠事由ID（event_typesテーブル参照） */
    private Integer eventTypeId;
    /** 適用勤務クラスID */
    private Long classId;
    /** 備考・特記事項 */
    private String remarks;
    /** 記録作成日時（UTC） */
    private Instant createdAt;
    /** 記録更新日時（UTC） */
    private Instant updatedAt;
    /** 論理削除フラグ */
    @Builder.Default
    private Boolean isDeleted = false;
}