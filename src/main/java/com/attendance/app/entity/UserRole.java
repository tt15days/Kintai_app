package com.attendance.app.entity;

/**
 * UserRole Enum - ユーザーロール
 */
public enum UserRole {
    /** 管理者 */
    ADMIN("管理者"),
    /** 一般管理者 */
    MANAGER("一般管理者"),
    /** 一般ユーザー */
    USER("一般ユーザー");

    private final String displayName;

    UserRole(String displayName) {
        this.displayName = displayName;
    }

    /**
     * 画面表示用のロール名を返します。
     */
    public String getDisplayName() {
        return displayName;
    }
}
