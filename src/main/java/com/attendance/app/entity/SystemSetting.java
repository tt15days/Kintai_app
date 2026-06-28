package com.attendance.app.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * SystemSetting Entity - システム設定
 *
 * アプリケーションの動作に必要な動的設定（有給付与日など）を保持します。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemSetting {
    /** 設定キー（PK） */
    private String settingKey;
    /** 設定値 */
    private String settingValue;
    /** 更新日時（UTC） */
    private Instant updatedAt;
}
