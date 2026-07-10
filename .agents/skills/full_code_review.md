---
name: full-code-review
description: Kintai_app 全体コードレビュー用。docs/overview・detail を前提に、セキュリティ・業務ロジック・UI・MyBatis/DB・テストを多視点でレビューし、.tmp に報告と ISSUES を残す。
---

# Full Code Review (Kintai_app)

## 前提

- 作業前に `docs/overview.md` と `docs/detail.md` を読む
- 既存スキル（`java.md`, `mybatis.md`, `thymeleaf_ui.md`, `db_schema.md`）の規約に照らす
- 生成物（`target/`, `node_modules/`, `logs/`）は対象外

## 多視点レビュー（サブエージェント推奨）

| 視点 | 重点 |
|------|------|
| Security | Spring Security、認可漏れ、CSRF、認証ロック、監査ログ、秘密情報 |
| Domain/Backend | 勤怠計算、有給、残業、承認フロー、Controller→Service→Mapper |
| UI/Frontend | Thymeleaf、Tailwind規約、UX、アクセシビリティ、XSS |
| Data/MyBatis | SQL注入、論理削除、N+1、Flyway整合 |
| Docs/Tests | ドキュメント乖離、テストカバレッジの穴 |

## 出力先

- 総合レポート: `.tmp/reviews/CODE_REVIEW_YYYYMMDD.md`
- 視点別: `.tmp/reviews/<perspective>_YYYYMMDD.md`
- 改善Issue: `.tmp/ISSUES/ISSUE-NNN-<slug>.md`

## Issue フォーマット

```markdown
# ISSUE-NNN: タイトル
- Severity: critical | high | medium | low
- Area: security | backend | ui | data | docs | test
- Files: path...
## 問題
## 再現/根拠
## 推奨対応
## ドキュメント影響
```

## ルール

- 推測でバグ断定しない。該当ファイル・行を根拠にする
- 重大度は影響（データ破壊・認可・計算誤り）で決める
- 指摘は再現可能・修正可能な粒度に分割する
- docs と実装の差は docs 更新案をレポートに含める
