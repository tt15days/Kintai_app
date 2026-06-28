package com.attendance.app.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * EventType Entity - 勤怠事由マスタ
 *
 * 出退勤記録に紐づく勤怠事由（通常、遅刻、有休、テレワーク等）を管理します。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventType {
    /** 事由ID（PK） */
    private Integer eventTypeId;
    /** 事由コード（日本語短縮名: '通常', '遅刻' 等） */
    private String code;
    /** 表示名 */
    private String displayName;
    /** 表示順 */
    private Integer sortOrder;
    /** 有効フラグ */
    private Boolean isActive;
    /** 作成日時（UTC） */
    private Instant createdAt;
    /** 更新日時（UTC） */
    private Instant updatedAt;
}
