package com.attendance.app.service;

import com.attendance.app.entity.AuditEventType;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * 操作監査ログサービス。
 *
 * <p>勤怠申請の承認・差戻し・取下げ、ユーザーの作成・更新・削除・パスワード初期化など
 * 業務上重要な更新操作を監査ログファイルへ記録する共通サービス。
 *
 * <p>設計上の注意:
 * <ul>
 *   <li>{@code description} には操作の補足情報のみを格納し、メールアドレス等の
 *       個人情報を含めないこと。</li>
 *   <li>このサービスは {@link UserService} に依存しないよう設計する
 *       （循環依存回避のため）。</li>
 * </ul>
 */
@Slf4j
@Service
public class AuditLogService {

    /** audit_logs.target_type: 月次勤怠申請 */
    public static final String TARGET_TYPE_SUBMISSION = "ATTENDANCE_SUBMISSION";

    /** audit_logs.target_type: 勤怠修正申請 */
    public static final String TARGET_TYPE_CORRECTION_REQUEST = "ATTENDANCE_CORRECTION_REQUEST";

    /** audit_logs.target_type: 休暇申請 */
    public static final String TARGET_TYPE_LEAVE_APPLICATION = "LEAVE_APPLICATION";

    /** audit_logs.target_type: ユーザー */
    public static final String TARGET_TYPE_USER = "USER";

    private static final Logger AUDIT_LOGGER = LoggerFactory.getLogger("com.attendance.app.audit");

    /**
     * 月次勤怠申請に関する監査イベントを記録する。
     *
     * @param eventType    イベント種別（SUBMISSION_SUBMITTED / APPROVED / RETURNED / WITHDRAWN / APPROVAL_REVOKED）
     * @param actorUserId  操作を実行したユーザー ID
     * @param targetUserId 影響を受けたユーザー ID（申請者）
     * @param submissionId 操作対象の申請 ID
     * @param description  補足情報（対象年月、コメント等）。個人情報は含めない
     */
    public void recordSubmissionEvent(
            AuditEventType eventType,
            Long actorUserId,
            Long targetUserId,
            Long submissionId,
            String description) {
        record(eventType, actorUserId, targetUserId, TARGET_TYPE_SUBMISSION, submissionId, description);
    }

    /**
     * 勤怠修正申請に関する監査イベントを記録する。
     *
     * @param eventType    イベント種別（CORRECTION_APPROVED / CORRECTION_REJECTED）
     * @param actorUserId  操作を実行したユーザー ID（承認者）
     * @param targetUserId 影響を受けたユーザー ID（申請者）
     * @param requestId    操作対象の修正申請 ID
     * @param description  補足情報（対象日、コメント等）。個人情報は含めない
     */
    public void recordCorrectionRequestEvent(
            AuditEventType eventType,
            Long actorUserId,
            Long targetUserId,
            Long requestId,
            String description) {
        record(eventType, actorUserId, targetUserId, TARGET_TYPE_CORRECTION_REQUEST, requestId, description);
    }

    /**
     * 休暇申請に関する監査イベントを記録する。
     *
     * @param eventType     イベント種別（LEAVE_APPROVED / LEAVE_REJECTED）
     * @param actorUserId   操作を実行したユーザー ID（承認者）
     * @param targetUserId  影響を受けたユーザー ID（申請者）
     * @param applicationId 操作対象の休暇申請 ID
     * @param description   補足情報（休暇期間等）。個人情報は含めない
     */
    public void recordLeaveApplicationEvent(
            AuditEventType eventType,
            Long actorUserId,
            Long targetUserId,
            Long applicationId,
            String description) {
        record(eventType, actorUserId, targetUserId, TARGET_TYPE_LEAVE_APPLICATION, applicationId, description);
    }

    /**
     * ユーザー管理に関する監査イベントを記録する。
     *
     * @param eventType    イベント種別（USER_CREATED / UPDATED / DELETED / PASSWORD_RESET）
     * @param actorUserId  操作を実行したユーザー ID（管理者など）
     * @param targetUserId 操作対象のユーザー ID
     * @param description  補足情報（ロール変更内容等）。個人情報は含めない
     */
    public void recordUserEvent(
            AuditEventType eventType,
            Long actorUserId,
            Long targetUserId,
            String description) {
        record(eventType, actorUserId, targetUserId, TARGET_TYPE_USER, targetUserId, description);
    }

    // ── 内部ヘルパー ──────────────────────────────────────────────────────────

    /**
    * 監査ログの共通記録処理。監査専用ロガーへ CSV 形式で出力する。
     *
     * @param eventType    イベント種別
     * @param actorUserId  操作者 ID
     * @param targetUserId 影響ユーザー ID
     * @param targetType   対象エンティティ種別
     * @param targetId     対象エンティティの PK
     * @param description  補足情報
     */
    private void record(
            AuditEventType eventType,
            Long actorUserId,
            Long targetUserId,
            String targetType,
            Long targetId,
            String description) {

        String createdAt = Instant.now().toString();
        String message = String.join(",",
                quoteCsv(eventType == null ? null : eventType.name()),
                quoteCsv(String.valueOf(actorUserId)),
                quoteCsv(String.valueOf(targetUserId)),
                quoteCsv(targetType),
                quoteCsv(String.valueOf(targetId)),
                quoteCsv(description),
                quoteCsv(createdAt));

        AUDIT_LOGGER.info(message);

        log.info("監査ログを記録しました: eventType={}, actor={}, targetType={}, targetId={}",
            eventType, actorUserId, targetType, targetId);
    }

    private String quoteCsv(String value) {
        String normalized = value == null ? "" : value;
        return "\"" + normalized.replace("\"", "\"\"") + "\"";
    }
}
