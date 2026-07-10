-- ============================================================
-- マイグレーション V6: emp_no 採番をDBシーケンスに置換
-- ============================================================
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
