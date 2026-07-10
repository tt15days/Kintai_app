---
name: fix-worker-tiered
description: Opus/Sonnet サブエージェントが .tmp/ISSUES のレビュー指摘を低コストで修正するための作業規約。判断できない事項は修正せず UNRESOLVED として Fable(親) へ報告する。
---

# Fix Worker (Opus/Sonnet 用)

## モデル分担
- **Opus**: 業務ロジック・トランザクション・DBマイグレーションなど判断が重い修正
- **Sonnet 5**: 条件追加・クエリ修正・UI統一など定型的な修正
- **Fable(親セッション)**: UNRESOLVED の裁定、ビルド/テストの実行と最終検証

## 作業手順
1. 割り当てられた `.tmp/ISSUES/ISSUE-NNN-*.md` を読む（担当分のみ）
2. 関連skill（`java.md` / `mybatis.md` / `thymeleaf_ui.md` / `db_schema.md`）の規約に従う
3. Grep で該当箇所を狙い撃ち → 必要範囲だけ Read。全文丸読み禁止
4. ISSUE の「推奨対応」を基本方針とするが、実装を確認して矛盾があれば UNRESOLVED にする
5. 修正は最小差分。頼まれていないリファクタ・コメント追加をしない
6. ISSUE に「ドキュメント影響」があれば docs/ の該当箇所も更新する

## 禁止事項
- **mvn の実行禁止**（compile/test含む。並列作業のため親が一括検証する）
- **統合テスト(*IntegrationTest)の実行・追加実行は絶対禁止**（開発DBを破壊する）
- `target/`, `node_modules/`, `logs/`, `data/` を読まない
- git commit/push しない
- 担当外ファイルの修正をしない（気づきは1行メモで報告）

## 環境制約
- Node.js 未導入のため Tailwind ビルド不可。`input.css` を変更した場合は「要ビルド」として報告
- タイムゾーンは `DateTimeUtil.todayJapan()` に統一する方針

## 報告（最終メッセージ）
以下のみを簡潔に返す。コード再掲・長い説明をしない:
```
修正: ISSUE-NNN → 変更ファイル一覧（path:line、1行要約）
UNRESOLVED: あれば「何を・なぜ判断できないか」
テスト影響: 既存単体テストで修正が必要になりそうな箇所（あれば）
```
