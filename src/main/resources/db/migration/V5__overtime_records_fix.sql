-- ============================================================
-- マイグレーション V5: overtime_records の型不整合・重複データを解消
-- ============================================================
-- 目的1: overtime_start/overtime_end はDDL上 TIME だが、マッパーは InstantTypeHandler
--        （TIMESTAMPで読み書き）を使用しており型が不整合。また日付跨ぎ残業では
--        日付情報を保持できず値が失われる。TIMESTAMP WITH TIME ZONE に変更し、
--        既存値は overtime_date + 時刻値 を Asia/Tokyo で合成して移行する。
--        日跨ぎ（end <= start となる時刻）の既存行は end の日付を +1日として復元する。
-- 目的2: overtime_records には (user_id, overtime_date) の一意制約がなく、
--        syncFromAttendance の select-then-insert が同時実行されると重複行が
--        発生しうる（attendance_records の V3 対応と同種の問題）。既存の重複を
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
