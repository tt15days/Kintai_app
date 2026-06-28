package com.attendance.app.entity;

/**
 * LeaveStatus Enum - 休暇申請ステータス
 */
public enum LeaveStatus {
    /** 申請中 */
    PENDING("申請中"),
    /** 承認済み */
    APPROVED("承認済み"),
    /** 却下 */
    REJECTED("却下");

    private final String displayName;

    LeaveStatus(String displayName) {
        this.displayName = displayName;
    }

    /**
     * 画面表示用の申請ステータス名を返します。
     */
    public String getDisplayName() {
        return displayName;
    }
}
