package com.attendance.app.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * UserNotification Entity - ユーザーダッシュボード通知
 *
 * 勤怠提出リマインドなど、ユーザーのダッシュボードに表示する通知を管理します。
 * 管理者によるリマインド送信またはバッチ処理により生成されます。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserNotification {

    /** 通知ID（PK） */
    private Long notificationId;
    /** 通知対象のユーザーID */
    private Long userId;
    /** 通知メッセージ本文 */
    private String message;
    /** 既読フラグ（true: 既読、false: 未読） */
    private Boolean isRead;
    /** 通知の種類（例: SYSTEM, REMINDER） */
    private String notificationType;
    /** 作成日時（UTC） */
    private Instant createdAt;
}
