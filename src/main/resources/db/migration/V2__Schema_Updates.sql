-- ============================================================
-- マイグレーション V2: V1初期スキーマ以降のスキーマ変更を集約
-- ============================================================
-- 本アプリは未リリースのため、V1（初期スキーマ）以降の変更はすべて本ファイルに
-- 集約する運用とする（Flywayマイグレーションファイルは V1・V2 のみを維持し、
-- 個別のバージョンファイルを増やさない）。

-- ----------------------------------------------------------------
-- (1) attendance_records の一意制約を論理削除対応に置換
-- ----------------------------------------------------------------
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

-- ----------------------------------------------------------------
-- (2) 休暇申請の実消化日数を永続化するカラムを追加
-- ----------------------------------------------------------------
-- 承認時に計算した消化日数を保存し、返還（削除）時は再計算せず保存値を優先する。
-- 既存データはNULL許容とし、旧データ（NULL）は返還時のみ従来どおり再計算でフォールバックする。

ALTER TABLE leave_applications ADD COLUMN consumed_days NUMERIC(4,1);

-- ----------------------------------------------------------------
-- (3) overtime_records の型不整合・重複データを解消
-- ----------------------------------------------------------------
-- 目的1: overtime_start/overtime_end はDDL上 TIME だが、マッパーは InstantTypeHandler
--        （TIMESTAMPで読み書き）を使用しており型が不整合。また日付跨ぎ残業では
--        日付情報を保持できず値が失われる。TIMESTAMP WITH TIME ZONE に変更し、
--        既存値は overtime_date + 時刻値 を Asia/Tokyo で合成して移行する。
--        日跨ぎ（end <= start となる時刻）の既存行は end の日付を +1日として復元する。
-- 目的2: overtime_records には (user_id, overtime_date) の一意制約がなく、
--        syncFromAttendance の select-then-insert が同時実行されると重複行が
--        発生しうる（(1) の attendance_records 対応と同種の問題）。既存の重複を
--        overtime_id が最大の1件を残して論理削除した上で、有効行(is_deleted = false)
--        のみを対象とする部分一意インデックスを追加する。
-- 影響範囲: overtime_records のみ。

ALTER TABLE overtime_records
    ALTER COLUMN overtime_start TYPE TIMESTAMP WITH TIME ZONE
        USING (CASE
                   WHEN overtime_start IS NULL THEN NULL
                   ELSE (overtime_date + overtime_start) AT TIME ZONE 'Asia/Tokyo'
               END),
    ALTER COLUMN overtime_end TYPE TIMESTAMP WITH TIME ZONE
        USING (CASE
                   WHEN overtime_end IS NULL THEN NULL
                   WHEN overtime_start IS NOT NULL AND overtime_end <= overtime_start
                       THEN ((overtime_date + 1) + overtime_end) AT TIME ZONE 'Asia/Tokyo'
                   ELSE (overtime_date + overtime_end) AT TIME ZONE 'Asia/Tokyo'
               END);

-- 有効行(is_deleted = false)の (user_id, overtime_date) 重複を、overtime_id が
-- 最大の1件を残して論理削除する（部分一意インデックス作成前のdedupe）。
UPDATE overtime_records o
SET is_deleted = true,
    updated_at = NOW()
WHERE o.is_deleted = false
AND o.overtime_id <> (
    SELECT MAX(o2.overtime_id)
    FROM overtime_records o2
    WHERE o2.user_id = o.user_id
    AND o2.overtime_date = o.overtime_date
    AND o2.is_deleted = false
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_overtime_user_date_active
    ON overtime_records(user_id, overtime_date)
    WHERE is_deleted = false;

-- ----------------------------------------------------------------
-- (4) emp_no 採番をDBシーケンスに置換
-- ----------------------------------------------------------------
-- 目的: UserMapper.selectNextUserId は SELECT COALESCE(MAX(user_id),0)+1 という
--       非アトミックな採番で、同時に createUser が実行されると同じ値を読み、
--       同一 emp_no を生成して UNIQUE 制約違反になりうる。
--       DBシーケンス（nextval）はアトミックに一意な値を払い出すため、この問題を解消する。
-- 対応: emp_no_seq を作成し、現在の MAX(user_id) に初期化する。

CREATE SEQUENCE IF NOT EXISTS emp_no_seq;

-- is_called=false のため次回 nextval() は設定値そのものを返す。
-- (MAX(user_id) + 1) を設定して従来の COALESCE(MAX(user_id),0)+1 と同じ初値を保証する
-- （users が 0 件でも設定値は 1 となり、minvalue=1 の下限に抵触しない）。
SELECT setval('emp_no_seq', COALESCE((SELECT MAX(user_id) FROM users), 0) + 1, false);
