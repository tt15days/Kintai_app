package com.attendance.app.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * AdminAnnouncement Entity - 管理者お知らせ
 *
 * 管理者がダッシュボードに掲示するお知らせメッセージを管理します。
 * 表示期間を設定でき、有効なお知らせのみがダッシュボードに表示されます。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminAnnouncement {

    /** お知らせID（PK） */
    private Long announcementId;
    /** お知らせのタイトル */
    private String title;
    /** お知らせの本文 */
    private String message;
    /** 有効フラグ（true: 有効、false: 無効） */
    private Boolean isActive;
    /** 表示開始日時 */
    private Instant displayStartDate;
    /** 表示終了日時 */
    private Instant displayEndDate;
    /** 作成者のユーザーID */
    private Long createdBy;
    /** 作成日時（UTC） */
    private Instant createdAt;
    /** 更新日時（UTC） */
    private Instant updatedAt;
    /** 論理削除フラグ */
    @Builder.Default
    private Boolean isDeleted = false;
}
