package com.attendance.app.entity;

/**
 * 監査ログのイベント種別を表す列挙型。
 *
 * <p>各定数は {@code audit_logs.event_type} カラムに文字列として保存される。
 * 名称変更時は既存 DB レコードとの整合に注意すること。
 *
 * <p>グループ別の分類:
 * <ul>
 *   <li>SUBMISSION_* — 月次勤怠申請の状態変更イベント</li>
 *   <li>USER_* — ユーザー管理イベント</li>
 * </ul>
 */
public enum AuditEventType {

    // ── 月次勤怠申請 ──────────────────────────────────────────────────────────

    /** 月次勤怠を申請（初回・再申請どちらも対象）。 */
    SUBMISSION_SUBMITTED,

    /** 月次勤怠申請を承認した。 */
    SUBMISSION_APPROVED,

    /** 月次勤怠申請を差し戻した。 */
    SUBMISSION_RETURNED,

    /** 月次勤怠申請を取り下げた（申請者本人による取消）。 */
    SUBMISSION_WITHDRAWN,

    /** 管理者が承認済み申請を差し戻し状態へ取り消した。 */
    APPROVAL_REVOKED,

    // ── ユーザー管理 ────────────────────────────────────────────────────────

    /** ユーザーを新規作成した。 */
    USER_CREATED,

    /** ユーザー情報を更新した。 */
    USER_UPDATED,

    /** ユーザーをソフトデリートした（論理削除）。 */
    USER_DELETED,

    /** 管理者がユーザーのパスワードを初期化した。 */
    USER_PASSWORD_RESET
}
