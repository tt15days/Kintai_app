package com.attendance.app.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 操作監査ログエンティティ。{@code audit_logs} テーブルに対応する。
 *
 * <p>勤怠申請の承認・差戻し・ユーザー更新など、業務上重要な更新操作を
 * 不変ログとして記録する。一度挿入されたレコードは更新・削除しない設計とする。
 *
 * <p>設計上の注意:
 * <ul>
 *   <li>{@code actorUserId} および {@code targetUserId} に外部キー制約はない。
 *       ユーザー削除後もログを保全するため、参照整合性より記録保全を優先している。</li>
 *   <li>{@code description} にはコメントや対象月など操作の補足情報のみを格納し、
 *       メールアドレス等の個人情報を含めないこと。</li>
 * </ul>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    /** 監査ログ ID（PK、自動採番）。 */
    private Long logId;

    /** イベント種別。{@link AuditEventType} の値を VARCHAR として保存する。 */
    private AuditEventType eventType;

    /** 操作を実行したユーザー ID（システム処理の場合は {@code null}）。 */
    private Long actorUserId;

    /** 操作の影響を受けたユーザー ID。 */
    private Long targetUserId;

    /**
     * 操作対象のエンティティ種別。
     * 定数値: {@code "ATTENDANCE_SUBMISSION"} または {@code "USER"}。
     */
    private String targetType;

    /** 操作対象エンティティの PK。 */
    private Long targetId;

    /** 操作の補足情報（承認コメント、対象年月など）。個人情報は含めない。 */
    private String description;

    /** ログ記録日時（UTC）。 */
    private Instant createdAt;
}
