-- ============================================================
-- Attendance Management System - Initial Schema (Consolidated)
-- マイグレーション V1: DDLのみ（テーブル・インデックス・パーミッション）
-- 統合対象: 旧 V1〜V7 + V3(有給残高) + V4(管理者お知らせ・監査ログ) + V5(休憩時間拡張) のスキーマ変更をすべて反映済み
--   - leave_type に ABSENCE を追加 (旧 V2)
--   - attendance_submissions.status に WITHDRAWN を追加 (旧 V3)
--   - system_settings テーブルを追加 (旧 V3)
--   - work_schedule_classes テーブルを追加 (旧 V5)
--   - attendance_correction_requests テーブルを追加 (旧 V6)
--   - users に有給設定カラムを追加 (旧 V7)
--   - user_notifications テーブルを追加 (旧 V7)
--   - paid_leave_balance テーブルを追加 (旧 V3マイグレーション)
--   - admin_announcements テーブルを追加 (旧 V4マイグレーション)
--   - audit_logs テーブルを追加 (旧 V4マイグレーション)
--   - work_schedule_classes に休憩時間スロット2〜4を追加 (旧 V5マイグレーション)
--   - users にログイン試行制限カラムを追加 (failed_login_count, locked_until, account_locked)
-- サンプルデータは db/sample/V2__Sample_Data.sql に分離
-- ============================================================

-- ============================================================
-- Users テーブル
-- ============================================================
CREATE TABLE IF NOT EXISTS users (
    user_id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    user_role VARCHAR(50) NOT NULL DEFAULT 'USER',
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    last_login_at TIMESTAMP WITH TIME ZONE,
    position_title VARCHAR(100),
    phone_number VARCHAR(30),
    class_name VARCHAR(100),
    paid_leave_days NUMERIC(5, 1) NOT NULL DEFAULT 10.0,
    notes TEXT,
    password_reset_required BOOLEAN NOT NULL DEFAULT false,
    can_approve_attendance BOOLEAN NOT NULL DEFAULT false,
    annual_leave_grant_days INTEGER        NOT NULL DEFAULT 10,
    annual_leave_increment  NUMERIC(3, 1) NOT NULL DEFAULT 1.0,
    max_paid_leave_days     INTEGER        NOT NULL DEFAULT 40,
    failed_login_count      INTEGER        NOT NULL DEFAULT 0,
    locked_until            TIMESTAMP WITH TIME ZONE,
    account_locked          BOOLEAN        NOT NULL DEFAULT false,
    emp_no                  VARCHAR(50)    NOT NULL UNIQUE,
    department              VARCHAR(100),
    employment_type         VARCHAR(50),
    hire_date               DATE,
    CONSTRAINT check_user_role CHECK (user_role IN ('ADMIN', 'USER'))
);

CREATE INDEX IF NOT EXISTS idx_users_user_role ON users(user_role);
CREATE INDEX IF NOT EXISTS idx_users_is_active ON users(is_active);

-- ============================================================
-- work_schedule_classes テーブル（所定時間マスタ）
-- 変更理由: シフト管理機能の追加。ユーザーが所属するクラスごとに
--           勤務開始・終了・休憩時間を管理し、残業計算の基準時刻を
--           ユーザーごとに可変化するため。
-- users.class_name を勤務クラス名のキーとして利用
-- ============================================================
CREATE TABLE IF NOT EXISTS work_schedule_classes (
    class_id        BIGSERIAL PRIMARY KEY,
    name            VARCHAR(100) NOT NULL UNIQUE,        -- クラス名称
    work_location   VARCHAR(255),                        -- 勤務地
    address         VARCHAR(200),                        -- 住所
    station         VARCHAR(50),                         -- 最寄り駅
    telephone       VARCHAR(30),                         -- 電話番号
    section_name    VARCHAR(100),                        -- 所属部署名
    folder_name     VARCHAR(100) DEFAULT NULL,           -- フォルダ分類
    tags            VARCHAR(255) DEFAULT NULL,           -- タグ（カンマ区切り）
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,       -- 有効フラグ
    max_hours       SMALLINT,                            -- 最大勤務時間
    min_hours       SMALLINT,                            -- 最小勤務時間
    start_time      TIME NOT NULL DEFAULT '09:00:00',    -- 勤務開始時刻
    end_time        TIME NOT NULL DEFAULT '18:00:00',    -- 勤務終了時刻
    break_start_time   TIME NOT NULL DEFAULT '12:00:00', -- 休憩1開始時刻
    break_end_time     TIME NOT NULL DEFAULT '13:00:00', -- 休憩1終了時刻
    break_start_time_2 TIME DEFAULT NULL,                -- 休憩2開始時刻（任意）
    break_end_time_2   TIME DEFAULT NULL,                -- 休憩2終了時刻（任意）
    break_start_time_3 TIME DEFAULT NULL,                -- 休憩3開始時刻（任意）
    break_end_time_3   TIME DEFAULT NULL,                -- 休憩3終了時刻（任意）
    break_start_time_4 TIME DEFAULT NULL,                -- 休憩4開始時刻（任意）
    break_end_time_4   TIME DEFAULT NULL,                -- 休憩4終了時刻（任意）
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_break_time_window_1
        CHECK (
            (break_start_time IS NULL AND break_end_time IS NULL)
            OR (break_start_time IS NOT NULL AND break_end_time IS NOT NULL AND break_start_time <> break_end_time)
        ),
    CONSTRAINT chk_break_time_window_2
        CHECK (
            (break_start_time_2 IS NULL AND break_end_time_2 IS NULL)
            OR (break_start_time_2 IS NOT NULL AND break_end_time_2 IS NOT NULL AND break_start_time_2 <> break_end_time_2)
        ),
    CONSTRAINT chk_break_time_window_3
        CHECK (
            (break_start_time_3 IS NULL AND break_end_time_3 IS NULL)
            OR (break_start_time_3 IS NOT NULL AND break_end_time_3 IS NOT NULL AND break_start_time_3 <> break_end_time_3)
        ),
    CONSTRAINT chk_break_time_window_4
        CHECK (
            (break_start_time_4 IS NULL AND break_end_time_4 IS NULL)
            OR (break_start_time_4 IS NOT NULL AND break_end_time_4 IS NOT NULL AND break_start_time_4 <> break_end_time_4)
        )
);


-- ============================================================
-- Event Types テーブル（勤怠事由マスタ）
-- 旧 time_record.event ENUM 14種 + テレワーク = 15種をマスタ管理
-- ============================================================
CREATE TABLE IF NOT EXISTS event_types (
    event_type_id SERIAL PRIMARY KEY,
    code          VARCHAR(10)  NOT NULL UNIQUE,
    display_name  VARCHAR(50)  NOT NULL,
    sort_order    INTEGER      NOT NULL DEFAULT 0,
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_event_types_sort_order ON event_types(sort_order);

-- 初期データ: 15種の勤怠事由
INSERT INTO event_types (code, display_name, sort_order) VALUES
    ('通常',     '通常',         1),
    ('遅刻',     '遅刻',         2),
    ('早退',     '早退',         3),
    ('有休',     '有休',         4),
    ('特休',     '特別休暇',     5),
    ('欠勤',     '欠勤',         6),
    ('土出',     '土曜出勤',     7),
    ('休出',     '休日出勤',     8),
    ('振出',     '振替出勤',     9),
    ('振休',     '振替休日',     10),
    ('自宅待',   '自宅待機',     11),
    ('テレワーク', 'テレワーク',  12),
    ('その他',   'その他',       13),
    ('週休',     '週休',         14),
    ('休職',     '休職',         15);

-- ============================================================
-- Attendance Records テーブル
-- ============================================================
CREATE TABLE IF NOT EXISTS attendance_records (
    record_id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    class_id BIGINT,
    attendance_date TIMESTAMP WITH TIME ZONE NOT NULL,
    start_time TIMESTAMP WITH TIME ZONE,
    end_time TIMESTAMP WITH TIME ZONE,
    event_type_id INTEGER REFERENCES event_types(event_type_id) ON DELETE SET NULL,
    remarks TEXT,
    working_hours NUMERIC(5, 2),
    overtime_hours NUMERIC(5, 2) DEFAULT 0.0,
    holiday_work_hours NUMERIC(5, 2) DEFAULT 0.0,
    break_time_minutes INTEGER NOT NULL DEFAULT 0,
    night_shift_hours NUMERIC(5, 2) NOT NULL DEFAULT 0.0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    is_deleted BOOLEAN NOT NULL DEFAULT false,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    FOREIGN KEY (class_id) REFERENCES work_schedule_classes(class_id) ON DELETE SET NULL,
    CONSTRAINT unique_user_attendance UNIQUE(user_id, attendance_date)
);

CREATE INDEX IF NOT EXISTS idx_attendance_records_user_id ON attendance_records(user_id);
CREATE INDEX IF NOT EXISTS idx_attendance_records_class_id ON attendance_records(class_id);
CREATE INDEX IF NOT EXISTS idx_attendance_records_attendance_date ON attendance_records(attendance_date);
CREATE INDEX IF NOT EXISTS idx_attendance_records_working_hours ON attendance_records(user_id, working_hours);
CREATE INDEX IF NOT EXISTS idx_attendance_records_overtime ON attendance_records(user_id, overtime_hours);
CREATE INDEX IF NOT EXISTS idx_attendance_records_event_type ON attendance_records(event_type_id);
CREATE INDEX IF NOT EXISTS idx_attendance_records_is_deleted ON attendance_records(is_deleted);

-- ============================================================
-- Leave Applications テーブル
-- ============================================================
CREATE TABLE IF NOT EXISTS leave_applications (
    application_id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    leave_start_date DATE NOT NULL,
    leave_end_date DATE NOT NULL,
    leave_type VARCHAR(50) NOT NULL,
    leave_duration_type VARCHAR(20) NOT NULL DEFAULT 'FULL_DAY',
    reason TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    is_deleted BOOLEAN NOT NULL DEFAULT false,
    approved_at TIMESTAMP WITH TIME ZONE,
    approved_by BIGINT,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    FOREIGN KEY (approved_by) REFERENCES users(user_id) ON DELETE SET NULL,
    CONSTRAINT check_leave_type CHECK (leave_type IN ('PAID_LEAVE', 'UNPAID_LEAVE', 'SICK_LEAVE', 'SPECIAL_LEAVE', 'ABSENCE')),
    CONSTRAINT check_leave_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
);

CREATE INDEX IF NOT EXISTS idx_leave_applications_user_id ON leave_applications(user_id);
CREATE INDEX IF NOT EXISTS idx_leave_applications_status ON leave_applications(status);
CREATE INDEX IF NOT EXISTS idx_leave_applications_dates ON leave_applications(leave_start_date, leave_end_date);
CREATE INDEX IF NOT EXISTS idx_leave_applications_is_deleted ON leave_applications(is_deleted);

-- ============================================================
-- Overtime Records テーブル
-- ============================================================
CREATE TABLE IF NOT EXISTS overtime_records (
    overtime_id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    overtime_date DATE NOT NULL,
    overtime_start TIME,
    overtime_end TIME,
    overtime_hours NUMERIC(5, 2) NOT NULL DEFAULT 0.0,
    reason VARCHAR(255),
    remarks TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    is_deleted BOOLEAN NOT NULL DEFAULT false,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_overtime_records_user_id ON overtime_records(user_id);
CREATE INDEX IF NOT EXISTS idx_overtime_records_date ON overtime_records(overtime_date);
CREATE INDEX IF NOT EXISTS idx_overtime_records_user_date ON overtime_records(user_id, overtime_date);
CREATE INDEX IF NOT EXISTS idx_overtime_records_is_deleted ON overtime_records(is_deleted);

-- ============================================================
-- Holidays テーブル
-- ============================================================
CREATE TABLE IF NOT EXISTS holidays (
    holiday_date DATE PRIMARY KEY,
    name VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);


-- ============================================================
-- Attendance Submissions テーブル
-- ============================================================
CREATE TABLE IF NOT EXISTS attendance_submissions (
    submission_id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    target_year_month VARCHAR(7) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    submitted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    action_by BIGINT,
    action_comment TEXT,
    action_at TIMESTAMP WITH TIME ZONE,
    start_date DATE,
    end_date DATE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_attendance_submissions_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    CONSTRAINT fk_attendance_submissions_action_by FOREIGN KEY (action_by) REFERENCES users(user_id) ON DELETE SET NULL,
    CONSTRAINT uq_attendance_submissions_user_month UNIQUE (user_id, target_year_month),
    CONSTRAINT chk_attendance_submission_status CHECK (status IN ('PENDING', 'APPROVED', 'RETURNED', 'WITHDRAWN'))
);

CREATE INDEX IF NOT EXISTS idx_attendance_submissions_status ON attendance_submissions(status);
CREATE INDEX IF NOT EXISTS idx_attendance_submissions_month ON attendance_submissions(target_year_month);


-- ============================================================
-- Attendance Approver Assignments テーブル
-- ============================================================
CREATE TABLE IF NOT EXISTS attendance_department_approvers (
    id BIGSERIAL PRIMARY KEY,
    department_name VARCHAR(100) NOT NULL,
    approver_user_id BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_attendance_department_approvers_user FOREIGN KEY (approver_user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    CONSTRAINT uq_attendance_department_approvers UNIQUE (department_name, approver_user_id)
);

CREATE TABLE IF NOT EXISTS attendance_user_approvers (
    id BIGSERIAL PRIMARY KEY,
    applicant_user_id BIGINT NOT NULL,
    approver_user_id BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_attendance_user_approvers_applicant FOREIGN KEY (applicant_user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    CONSTRAINT fk_attendance_user_approvers_approver FOREIGN KEY (approver_user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    CONSTRAINT uq_attendance_user_approvers UNIQUE (applicant_user_id, approver_user_id)
);


-- ============================================================
-- system_settings テーブル（システム設定マスタ）
-- ============================================================
CREATE TABLE IF NOT EXISTS system_settings (
    setting_key VARCHAR(100) PRIMARY KEY,
    setting_value VARCHAR(255) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- ============================================================
-- attendance_correction_requests テーブル（勤怠修正申請）
-- 目的: 月次勤怠が承認済み（APPROVED）または申請中（PENDING）で
--       直接編集できない場合に、個別日の修正を申請するワークフローを実現する。
-- フロー: ユーザーが修正内容・理由を入力して申請（PENDING）→
--         承認者が承認（APPROVED）→ attendance_records が実際に更新される
--         承認者が却下（REJECTED）→ attendance_records は変更されない
-- ステータス: PENDING / APPROVED / REJECTED / WITHDRAWN
-- ============================================================
CREATE TABLE IF NOT EXISTS attendance_correction_requests (
    request_id         BIGSERIAL PRIMARY KEY,
    user_id            BIGINT NOT NULL,
    attendance_date    DATE NOT NULL,
    target_year_month  VARCHAR(7) NOT NULL,          -- yyyy-MM 形式（月次グループ管理用）

    -- 申請後の希望値
    requested_start_time TIME,                       -- NULL は開始時刻を削除する意図
    requested_end_time   TIME,                       -- NULL は終了時刻を削除する意図
    requested_remarks    TEXT,

    -- 修正理由（必須）
    reason TEXT NOT NULL,

    -- 申請時点のスナップショット（承認画面での差分表示用）
    current_start_time TIME,
    current_end_time   TIME,
    current_remarks    TEXT,

    requested_break_time_minutes INTEGER,
    current_break_time_minutes INTEGER,
    requested_night_shift_hours NUMERIC(5, 2),
    current_night_shift_hours NUMERIC(5, 2),

    -- ステータス管理
    status             VARCHAR(20) NOT NULL DEFAULT 'PENDING',

    -- 申請情報
    submitted_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    -- 承認/却下情報
    action_by          BIGINT,
    action_comment     TEXT,
    action_at          TIMESTAMP WITH TIME ZONE,

    created_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_acr_user
        FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    CONSTRAINT fk_acr_action_by
        FOREIGN KEY (action_by) REFERENCES users(user_id) ON DELETE SET NULL,
    CONSTRAINT chk_acr_status
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'WITHDRAWN'))
);

CREATE INDEX IF NOT EXISTS idx_acr_user_id
    ON attendance_correction_requests(user_id);
CREATE INDEX IF NOT EXISTS idx_acr_status
    ON attendance_correction_requests(status);
CREATE INDEX IF NOT EXISTS idx_acr_user_date
    ON attendance_correction_requests(user_id, attendance_date);
CREATE INDEX IF NOT EXISTS idx_acr_target_year_month
    ON attendance_correction_requests(target_year_month);

-- ============================================================
-- user_notifications テーブル（ダッシュボード通知）
-- ============================================================
CREATE TABLE IF NOT EXISTS user_notifications (
    notification_id   BIGSERIAL PRIMARY KEY,
    user_id           BIGINT        NOT NULL,
    message           TEXT          NOT NULL,
    is_read           BOOLEAN       NOT NULL DEFAULT false,
    notification_type VARCHAR(50)   NOT NULL DEFAULT 'REMINDER',
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_un_user
        FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_user_notifications_user_id
    ON user_notifications(user_id);
CREATE INDEX IF NOT EXISTS idx_user_notifications_user_unread
    ON user_notifications(user_id, is_read);

-- ============================================================
-- paid_leave_balance テーブル（有給休暇年次残高）
-- 目的: 有給休暇の年次管理（付与日・失効日・繰越）を保持するため。
--       利用時に年次残高の used_days を減算することで正確な残日数を管理する。
-- ============================================================
CREATE TABLE IF NOT EXISTS paid_leave_balance (
    balance_id        BIGSERIAL PRIMARY KEY,
    user_id           BIGINT          NOT NULL,
    grant_year        INTEGER         NOT NULL,           -- 付与年度（例: 2026）
    granted_days      NUMERIC(5, 1)   NOT NULL,           -- 当年付与日数
    grant_date        DATE            NOT NULL,           -- 付与日
    expiry_date       DATE            NOT NULL,           -- 失効日
    carried_over_days NUMERIC(5, 1)   NOT NULL DEFAULT 0.0, -- 前年度繰越日数
    used_days         NUMERIC(5, 1)   NOT NULL DEFAULT 0.0, -- 使用済み日数
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_plb_user
        FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    CONSTRAINT uq_plb_user_year
        UNIQUE (user_id, grant_year),
    CONSTRAINT chk_plb_granted_days
        CHECK (granted_days >= 0),
    CONSTRAINT chk_plb_carried_over_days
        CHECK (carried_over_days >= 0),
    CONSTRAINT chk_plb_used_days
        CHECK (used_days >= 0)
);

CREATE INDEX IF NOT EXISTS idx_plb_user_id
    ON paid_leave_balance(user_id);
CREATE INDEX IF NOT EXISTS idx_plb_expiry_date
    ON paid_leave_balance(expiry_date);

-- ============================================================
-- admin_announcements テーブル（管理者お知らせ）
-- 目的: 管理者がダッシュボードに掲示するお知らせメッセージを管理するため。
--       表示期間を設定でき、有効なお知らせのみがダッシュボードに表示される。
-- ============================================================
CREATE TABLE IF NOT EXISTS admin_announcements (
    announcement_id    BIGSERIAL PRIMARY KEY,
    title              VARCHAR(200)             NOT NULL,           -- お知らせタイトル
    message            TEXT                     NOT NULL,           -- お知らせ本文
    is_active          BOOLEAN                  NOT NULL DEFAULT TRUE, -- 有効フラグ
    display_start_date TIMESTAMP WITH TIME ZONE NOT NULL,           -- 表示開始日時
    display_end_date   TIMESTAMP WITH TIME ZONE,                    -- 表示終了日時 (NULL=期限なし)
    created_by         BIGINT                   NOT NULL,           -- 作成者ユーザーID
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    is_deleted         BOOLEAN                  NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_ann_created_by
        FOREIGN KEY (created_by) REFERENCES users(user_id) ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_ann_is_active
    ON admin_announcements(is_active);
CREATE INDEX IF NOT EXISTS idx_ann_display_dates
    ON admin_announcements(display_start_date, display_end_date);
CREATE INDEX IF NOT EXISTS idx_ann_is_deleted
    ON admin_announcements(is_deleted);

CREATE INDEX IF NOT EXISTS idx_users_account_locked ON users(account_locked);

-- ============================================================
-- audit_logs テーブル（操作監査ログ）
-- 目的: 勤怠申請の承認・差戻し・取下げ、ユーザーの作成・更新・削除・
--       パスワード初期化といった主要更新操作を不変ログとして記録し、
--       運用監査・インシデント調査に活用する。
-- 設計方針:
--   - actor_user_id / target_user_id に外部キー制約を設けない
--     （ユーザー削除後もログを保全するため）
--   - description には操作の補足情報のみ格納し、個人情報は含めない
-- ============================================================
CREATE TABLE IF NOT EXISTS audit_logs (
    log_id         BIGSERIAL PRIMARY KEY,
    event_type     VARCHAR(50)                  NOT NULL,
    actor_user_id  BIGINT,                                -- 操作者 ID（NULL=システム処理）
    target_user_id BIGINT,                                -- 影響を受けたユーザー ID
    target_type    VARCHAR(50)                  NOT NULL, -- 'ATTENDANCE_SUBMISSION' / 'USER'
    target_id      BIGINT,                                -- 対象エンティティの PK
    description    TEXT,                                  -- 操作の補足（コメント等）
    created_at     TIMESTAMP WITH TIME ZONE     NOT NULL DEFAULT NOW()
);

-- 操作者ごとの監査履歴検索
CREATE INDEX IF NOT EXISTS idx_audit_logs_actor_user_id
    ON audit_logs(actor_user_id);

-- 影響ユーザーごとの監査履歴検索
CREATE INDEX IF NOT EXISTS idx_audit_logs_target_user_id
    ON audit_logs(target_user_id);

-- エンティティ種別・ID の複合検索（特定申請・ユーザーの操作履歴）
CREATE INDEX IF NOT EXISTS idx_audit_logs_target_type_id
    ON audit_logs(target_type, target_id);

-- 期間フィルタ用
CREATE INDEX IF NOT EXISTS idx_audit_logs_created_at
    ON audit_logs(created_at);

-- ============================================================
-- パーミッション設定 (postgres ユーザー)
-- ============================================================
GRANT ALL PRIVILEGES ON TABLE users TO postgres;
GRANT ALL PRIVILEGES ON TABLE event_types TO postgres;
GRANT ALL PRIVILEGES ON TABLE attendance_records TO postgres;
GRANT ALL PRIVILEGES ON TABLE leave_applications TO postgres;
GRANT ALL PRIVILEGES ON TABLE overtime_records TO postgres;
GRANT ALL PRIVILEGES ON TABLE holidays TO postgres;
GRANT ALL PRIVILEGES ON TABLE attendance_submissions TO postgres;
GRANT ALL PRIVILEGES ON TABLE attendance_department_approvers TO postgres;
GRANT ALL PRIVILEGES ON TABLE attendance_user_approvers TO postgres;
GRANT ALL PRIVILEGES ON TABLE work_schedule_classes TO postgres;
GRANT ALL PRIVILEGES ON TABLE system_settings TO postgres;
GRANT ALL PRIVILEGES ON TABLE attendance_correction_requests TO postgres;
GRANT ALL PRIVILEGES ON TABLE user_notifications TO postgres;
GRANT ALL PRIVILEGES ON TABLE paid_leave_balance TO postgres;
GRANT ALL PRIVILEGES ON TABLE admin_announcements TO postgres;
GRANT ALL PRIVILEGES ON TABLE audit_logs TO postgres;
GRANT ALL PRIVILEGES ON SEQUENCE users_user_id_seq TO postgres;
GRANT ALL PRIVILEGES ON SEQUENCE event_types_event_type_id_seq TO postgres;
GRANT ALL PRIVILEGES ON SEQUENCE attendance_records_record_id_seq TO postgres;
GRANT ALL PRIVILEGES ON SEQUENCE leave_applications_application_id_seq TO postgres;
GRANT ALL PRIVILEGES ON SEQUENCE overtime_records_overtime_id_seq TO postgres;
GRANT ALL PRIVILEGES ON SEQUENCE attendance_submissions_submission_id_seq TO postgres;
GRANT ALL PRIVILEGES ON SEQUENCE attendance_department_approvers_id_seq TO postgres;
GRANT ALL PRIVILEGES ON SEQUENCE attendance_user_approvers_id_seq TO postgres;
GRANT ALL PRIVILEGES ON SEQUENCE work_schedule_classes_class_id_seq TO postgres;
GRANT ALL PRIVILEGES ON SEQUENCE attendance_correction_requests_request_id_seq TO postgres;
GRANT ALL PRIVILEGES ON SEQUENCE user_notifications_notification_id_seq TO postgres;
GRANT USAGE, SELECT ON SEQUENCE paid_leave_balance_balance_id_seq TO postgres;
GRANT USAGE, SELECT ON SEQUENCE admin_announcements_announcement_id_seq TO postgres;
