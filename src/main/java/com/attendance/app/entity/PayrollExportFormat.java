package com.attendance.app.entity;

/**
 * 給与計算システムへの連携用CSVフォーマット定義
 */
public enum PayrollExportFormat {
    MONEYFORWARD("マネーフォワード クラウド給与"),
    FREEE("freee人事労務"),
    YAYOI("弥生給与");

    private final String displayName;

    PayrollExportFormat(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
