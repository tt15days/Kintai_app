package com.attendance.app.entity;

/**
 * LeaveType Enum - 休暇タイプ
 */
public enum LeaveType {
    /** 有給休暇 */
    PAID_LEAVE("有給休暇"),
    /** 無給休暇 */
    UNPAID_LEAVE("無給休暇"),
    /** 病気休暇 */
    SICK_LEAVE("病気休暇"),
    /** 特別休暇 */
    SPECIAL_LEAVE("特別休暇"),
    /** 欠勤 */
    ABSENCE("欠勤");

    private final String displayName;

    LeaveType(String displayName) {
        this.displayName = displayName;
    }

    /**
     * 画面表示用の休暇種別名を返します。
     */
    public String getDisplayName() {
        return displayName;
    }
}
