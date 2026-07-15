package com.attendance.app.service;

/**
 * 更新・削除対象のお知らせが存在しない場合に送出する業務例外です。
 */
public class AdminAnnouncementNotFoundException extends RuntimeException {

    public AdminAnnouncementNotFoundException(Long announcementId) {
        super("対象のお知らせが見つかりません。ID: " + announcementId);
    }
}
