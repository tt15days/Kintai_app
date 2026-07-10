-- 休暇申請の実消化日数を永続化するカラムを追加
-- 承認時に計算した消化日数を保存し、返還（削除）時は再計算せず保存値を優先する。
-- 既存データはNULL許容とし、旧データ（NULL）は返還時のみ従来どおり再計算でフォールバックする。
ALTER TABLE leave_applications ADD COLUMN consumed_days NUMERIC(4,1);
