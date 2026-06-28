-- ============================================================
-- Attendance Management System - Sample Data
-- マイグレーション V2: サンプルデータ・システム設定のみ
-- 統合対象: 旧 V1・V3・V5・V7 のデータ変更をすべて反映済み
--   - サンプルユーザー (旧 V1)
--   - サンプル勤怠記録 (旧 V1)
--   - サンプル勤怠勤務時間補正 (旧 V3)
--   - CSVファイル名パターン初期設定 (旧 V3)
--   - サンプル休暇申請 (旧 V1)
--   - サンプル残業記録 (旧 V1)
--   - サンプル勤務クラスデータ (旧 V5)
--   - 勤怠表示期間設定 (旧 V3)
--   - バッチ処理設定 (旧 V7)
--   - 有給休暇残高サンプルデータ (旧 V3マイグレーション)
--   - 管理者お知らせサンプルデータ (旧 V4マイグレーション)
-- ============================================================

-- ============================================================
-- サンプル勤務クラス挿入
-- ============================================================
INSERT INTO work_schedule_classes (name, work_location, address, station, telephone, section_name, is_active, start_time, end_time)
VALUES
    ('標準勤務', '本社', '東京都千代田区大手町1-1-1', '大手町駅', '03-1234-5678', '開発部', true, '09:00:00', '18:00:00'),
    ('早番',     '本社', '東京都千代田区大手町1-1-1', '大手町駅', '03-1234-5678', '開発部', true, '08:00:00', '17:00:00'),
    ('遅番',     '本社', '東京都千代田区大手町1-1-1', '大手町駅', '03-1234-5678', '開発部', true, '10:00:00', '19:00:00'),
    ('時短勤務', '本社', '東京都千代田区大手町1-1-1', '大手町駅', '03-1234-5678', '総務部', true, '09:00:00', '15:00:00'),
    ('夜勤',     '本社', '東京都千代田区大手町1-1-1', '大手町駅', '03-1234-5678', '警備部', true, '22:00:00', '07:00:00')
ON CONFLICT (name) DO NOTHING;

-- ============================================================
-- サンプルユーザー挿入
-- ============================================================

-- 管理者ユーザー (パスワード: admin123)
-- BCryptハッシュ (strength=10): $2a$10$PSi7qMbR2f7JmU/znUTzBeD83llgp72q/JUaIc5MsBy3ZQMHPfRya
INSERT INTO users (emp_no, department, employment_type, email, password, full_name, user_role, is_active, can_approve_attendance, created_at, updated_at)
VALUES (
    'EMP-001',
    '総務部',
    'FULL_TIME',
    'admin@example.com',
    '$2a$10$PSi7qMbR2f7JmU/znUTzBeD83llgp72q/JUaIc5MsBy3ZQMHPfRya',
    '管理者ユーザー',
    'ADMIN',
    true,
    true,
    NOW(),
    NOW()
) ON CONFLICT (email) DO NOTHING;

-- 一般ユーザー (パスワード: user123)
-- BCryptハッシュ (strength=10): $2a$10$RIJgy08RGibEwnudUXCMvuwcxV9VVKxwZO3QzvlbkHcxd3qZ3XbPa
INSERT INTO users (emp_no, department, employment_type, email, password, full_name, user_role, is_active, can_approve_attendance, created_at, updated_at)
VALUES (
    'EMP-002',
    '営業部',
    'FULL_TIME',
    'user@example.com',
    '$2a$10$RIJgy08RGibEwnudUXCMvuwcxV9VVKxwZO3QzvlbkHcxd3qZ3XbPa',
    '一般ユーザー',
    'USER',
    true,
    false,
    NOW(),
    NOW()
) ON CONFLICT (email) DO NOTHING;

-- テスト用ユーザー (パスワード: test123)
-- BCryptハッシュ (strength=10): $2a$10$NzFqXf8RXB/zEBkQdFB/wernyCLyc.yKsqHgIMXkpTyfpPW1peWve
INSERT INTO users (emp_no, department, employment_type, email, password, full_name, user_role, is_active, can_approve_attendance, created_at, updated_at)
VALUES (
    'EMP-003',
    '開発部',
    'CONTRACT',
    'test@example.com',
    '$2a$10$NzFqXf8RXB/zEBkQdFB/wernyCLyc.yKsqHgIMXkpTyfpPW1peWve',
    'テストユーザー',
    'USER',
    true,
    true,
    NOW(),
    NOW()
) ON CONFLICT (email) DO NOTHING;

-- ============================================================
-- サンプル勤怠記録挿入 (ユーザーID: 2)
-- ============================================================

INSERT INTO attendance_records (user_id, attendance_date, start_time, end_time, working_hours, event_type_id, created_at, updated_at)
VALUES (
    2,
    NOW(),
    (((NOW() AT TIME ZONE 'Asia/Tokyo')::DATE + TIME '09:00') AT TIME ZONE 'Asia/Tokyo'),
    (((NOW() AT TIME ZONE 'Asia/Tokyo')::DATE + TIME '18:00') AT TIME ZONE 'Asia/Tokyo'),
    8.0,
    1,
    NOW(),
    NOW()
) ON CONFLICT (user_id, attendance_date) DO NOTHING;

INSERT INTO attendance_records (user_id, attendance_date, start_time, end_time, working_hours, event_type_id, created_at, updated_at)
VALUES (
    2,
    NOW() - INTERVAL '1 day',
    ((((NOW() - INTERVAL '1 day') AT TIME ZONE 'Asia/Tokyo')::DATE + TIME '09:15') AT TIME ZONE 'Asia/Tokyo'),
    ((((NOW() - INTERVAL '1 day') AT TIME ZONE 'Asia/Tokyo')::DATE + TIME '17:45') AT TIME ZONE 'Asia/Tokyo'),
    7.5,
    1,
    NOW(),
    NOW()
) ON CONFLICT (user_id, attendance_date) DO NOTHING;

INSERT INTO attendance_records (user_id, attendance_date, start_time, end_time, working_hours, overtime_hours, event_type_id, remarks)
VALUES
    (1, '2026-04-21 00:00:00+09', '2026-04-21 09:00:00+09', '2026-04-21 17:00:00+09', 7.0,  0.0,  1, 'Regular workday'),
    (1, '2026-04-22 00:00:00+09', '2026-04-22 09:15:00+09', '2026-04-22 17:15:00+09', 7.0,  0.0,  2, 'Late start'),
    (1, '2026-04-23 00:00:00+09', '2026-04-23 09:00:00+09', '2026-04-23 17:00:00+09', 7.0,  0.0,  1, 'Regular workday'),
    (2, '2026-04-24 00:00:00+09', '2026-04-24 08:45:00+09', '2026-04-24 16:45:00+09', 7.0,  0.0,  1, 'Early start'),
    (2, '2026-04-25 00:00:00+09', '2026-04-25 09:00:00+09', '2026-04-25 17:00:00+09', 7.0,  0.0,  1, 'Regular workday'),
    (2, '2026-04-27 00:00:00+09', '2026-04-27 08:00:00+09', '2026-04-27 19:00:00+09', 10.0, 1.0,  1, 'Regular workday'),
    (2, '2026-04-28 00:00:00+09', '2026-04-28 12:45:00+09', '2026-04-28 23:15:00+09', 9.5,  5.25, 2, 'Late start'),
    (2, '2026-04-30 00:00:00+09', '2026-04-30 09:00:00+09', '2026-04-30 23:45:00+09', 13.75, 5.75, 1, 'Regular workday')
ON CONFLICT (user_id, attendance_date) DO NOTHING;

-- ============================================================
-- サンプル勤怠記録挿入 (ユーザーID: 3 テストユーザー / 夜勤パターン)
-- 勤務クラス: 夜勤 (22:00-翌07:00, 休憩60分, 実労働8h)
-- 対象月: 2026-05 (2026-04-21〜2026-05-20)
-- ============================================================
INSERT INTO attendance_records (user_id, attendance_date, start_time, end_time, working_hours, overtime_hours, event_type_id, remarks)
VALUES
    -- 通常夜勤 (22:00-翌07:00 = 8h, 残業なし)
    (3, '2026-04-21 00:00:00+09', '2026-04-21 22:00:00+09', '2026-04-22 07:00:00+09', 8.0, 0.0, 1, '夜勤'),
    -- 夜勤・残業あり (22:00-翌09:00 = 10h, 残業2h)
    (3, '2026-04-23 00:00:00+09', '2026-04-23 22:00:00+09', '2026-04-24 09:00:00+09', 10.0, 2.0, 1, '夜勤・残業'),
    -- 通常夜勤 (22:00-翌07:00 = 8h, 残業なし)
    (3, '2026-04-25 00:00:00+09', '2026-04-25 22:00:00+09', '2026-04-26 07:00:00+09', 8.0, 0.0, 1, '夜勤'),
    -- 夜勤・早退 (22:00-翌05:00 = 6h, 残業なし)
    (3, '2026-04-27 00:00:00+09', '2026-04-27 22:00:00+09', '2026-04-28 05:00:00+09', 6.0, 0.0, 3, '夜勤・早退')
ON CONFLICT (user_id, attendance_date) DO NOTHING;

-- ============================================================
-- サンプル休暇申請挿入
-- ============================================================

INSERT INTO leave_applications (user_id, leave_start_date, leave_end_date, leave_type, reason, status, created_at, updated_at)
VALUES (
    2,
    CURRENT_DATE + INTERVAL '10 days',
    CURRENT_DATE + INTERVAL '12 days',
    'PAID_LEAVE',
    'テスト用休暇申請',
    'PENDING',
    NOW(),
    NOW()
);

-- ============================================================
-- サンプル残業記録挿入
-- ============================================================

INSERT INTO overtime_records (user_id, overtime_date, overtime_start, overtime_end, overtime_hours, reason, created_at, updated_at)
VALUES (
    2,
    CURRENT_DATE,
    '18:00:00'::TIME,
    '20:00:00'::TIME,
    2.0,
    'プロジェクト作業',
    NOW(),
    NOW()
);

INSERT INTO overtime_records (user_id, overtime_date, overtime_start, overtime_end, overtime_hours, reason, created_at, updated_at)
VALUES (
    2,
    CURRENT_DATE - INTERVAL '1 day',
    '19:00:00'::TIME,
    '21:00:00'::TIME,
    2.0,
    'クライアント対応',
    NOW(),
    NOW()
);

-- ============================================================
-- システム設定（CSVファイル名パターン）
-- ============================================================
INSERT INTO system_settings (setting_key, setting_value, updated_at)
VALUES ('csv_filename_pattern', '{yyyy}-{MM}_{userId}_{name}_{downloadAt}', NOW())
ON CONFLICT (setting_key) DO NOTHING;

-- ============================================================
-- 勤怠表示期間設定（旧 V3）
-- attendance_period_start_day: 勤怠期間の開始日（前月の何日から）
-- attendance_period_end_day  : 勤怠期間の終了日（当月の何日まで）
-- ============================================================
INSERT INTO system_settings (setting_key, setting_value, updated_at)
VALUES ('attendance_period_start_day', '21', NOW())
ON CONFLICT (setting_key) DO NOTHING;

INSERT INTO system_settings (setting_key, setting_value, updated_at)
VALUES ('attendance_period_end_day', '20', NOW())
ON CONFLICT (setting_key) DO NOTHING;

-- ============================================================
-- バッチ処理設定（旧 V7）
-- monthly_summary_days_after_end: 月次集計実行日（勤怠期間終了日の何日後か）
-- paid_leave_grant_month        : 年次有給付与月（1-12）
-- paid_leave_grant_day          : 年次有給付与日（1-28）
-- reminder_day                  : 勤怠提出リマインド送信日（1-28）
-- reminder_hour                 : 勤怠提出リマインド送信時刻（0-23）
-- ============================================================
INSERT INTO system_settings (setting_key, setting_value) VALUES
    ('monthly_summary_days_after_end', '5'),
    ('paid_leave_grant_month',         '4'),
    ('paid_leave_grant_day',           '1'),
    ('reminder_day',                   '20'),
    ('reminder_hour',                  '9')
ON CONFLICT (setting_key) DO NOTHING;

-- ============================================================
-- サンプル有給休暇残高挿入（2026年度）
-- ============================================================
-- 管理者ユーザー (admin@example.com): 2026年度 10日付与、前年繰越 5日
INSERT INTO paid_leave_balance (user_id, grant_year, granted_days, grant_date, expiry_date, carried_over_days, used_days)
SELECT user_id, 2026, 10.0, '2026-04-01', '2028-03-31', 5.0, 0.0
  FROM users WHERE email = 'admin@example.com'
ON CONFLICT (user_id, grant_year) DO NOTHING;

-- 一般ユーザー (user@example.com): 2026年度 10日付与、前年繰越 0日、2日使用済み
INSERT INTO paid_leave_balance (user_id, grant_year, granted_days, grant_date, expiry_date, carried_over_days, used_days)
SELECT user_id, 2026, 10.0, '2026-04-01', '2028-03-31', 0.0, 2.0
  FROM users WHERE email = 'user@example.com'
ON CONFLICT (user_id, grant_year) DO NOTHING;

-- テストユーザー (test@example.com): 2026年度 10日付与、前年繰越 3日
INSERT INTO paid_leave_balance (user_id, grant_year, granted_days, grant_date, expiry_date, carried_over_days, used_days)
SELECT user_id, 2026, 10.0, '2026-04-01', '2028-03-31', 3.0, 0.0
  FROM users WHERE email = 'test@example.com'
ON CONFLICT (user_id, grant_year) DO NOTHING;

-- ============================================================
-- サンプル月次勤怠申請挿入
-- ============================================================
INSERT INTO attendance_submissions (user_id, target_year_month, status, start_date, end_date, submitted_at, action_by, action_at)
SELECT u.user_id, '2026-04', 'APPROVED', '2026-03-21', '2026-04-20', NOW() - INTERVAL '1 month', a.user_id, NOW() - INTERVAL '1 month'
  FROM users u, users a
 WHERE u.email = 'user@example.com' AND a.email = 'admin@example.com'
ON CONFLICT (user_id, target_year_month) DO NOTHING;

INSERT INTO attendance_submissions (user_id, target_year_month, status, start_date, end_date, submitted_at)
SELECT u.user_id, '2026-05', 'PENDING', '2026-04-21', '2026-05-20', NOW()
  FROM users u
 WHERE u.email = 'user@example.com'
ON CONFLICT (user_id, target_year_month) DO NOTHING;

-- ============================================================
-- サンプル管理者お知らせ挿入
-- ============================================================
INSERT INTO admin_announcements (title, message, is_active, display_start_date, display_end_date, created_by)
SELECT
    '勤怠管理システムへようこそ',
    '本システムをご利用いただきありがとうございます。不明点は管理者までお問い合わせください。',
    true,
    DATE_TRUNC('day', NOW() AT TIME ZONE 'Asia/Tokyo') AT TIME ZONE 'Asia/Tokyo',
    NULL,
    user_id
  FROM users
 WHERE email = 'admin@example.com'
LIMIT 1;
