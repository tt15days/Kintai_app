-- ============================================================
-- マイグレーション V2: attendance_records の一意制約を論理削除対応に置換
-- ============================================================
-- 目的: 旧 unique_user_attendance UNIQUE(user_id, attendance_date) は is_deleted を
--       無視するため、論理削除済み行が残る日への再登録が INSERT 分岐で一意制約違反となる。
-- 対応: 制約を DROP し、有効行(is_deleted = false)のみを対象とする部分一意インデックスに置換する。
--       これにより「有効な勤怠は1日1件」を担保しつつ、論理削除済み行との共存・再登録を許可する。
-- 影響範囲: attendance_records の重複制御ロジックのみ。既存有効データは (user_id, attendance_date)
--           が一意のため移行不要。

ALTER TABLE attendance_records DROP CONSTRAINT IF EXISTS unique_user_attendance;

CREATE UNIQUE INDEX IF NOT EXISTS uq_attendance_user_date_active
    ON attendance_records(user_id, attendance_date)
    WHERE is_deleted = false;
