---
name: issue-workflow
description: レビュー結果（.tmp/ISSUES）を GitHub issue 化し、closes 紐付けコミットと push まで行うワークフロー。gh CLI 前提。
---

# Issue化〜コミット/push ワークフロー (Kintai_app)

## 前提
- リモートは2つ: **origin=tt15days/Kintai_app（issue/PR はこちら）**、upstream=Kintai_app_custom。gh は `-R tt15days/Kintai_app` を必ず明示する
- 作業ブランチは dev。main への直接コミット禁止
- コミットメッセージ規約は `github.md` に従う（日本語・50文字以内・prefix必須）

## 手順
1. `.tmp/ISSUES/ISSUE-NNN-*.md` を1件=1 issueで登録
   - タイトル: ファイル先頭 H1 から「ISSUE-NNN: 」を除いた文言
   - 本文: ファイル内容をそのまま。修正済みなら「dev で修正済み・PR で close 予定」を冒頭に付記
   - ラベル: Area が security/backend/data → `bug`、ui/test/docs → `enhancement`（既存ラベルのみ使用。新規ラベルは作らない）
   - 採番結果（ISSUE-NNN → #M）を控えて closes 行に使う
2. コミットは論理単位で分割（例: fix=本体修正 / test=テスト追随・追加 / docs=ドキュメント・skill）
   - 本文に `closes #M` を列挙（dev→main の PR マージ時に自動 close される）
3. `git push origin dev`
4. 未対応の残課題は open のまま issue 登録だけ行う

## 注意
- `.tmp/` は gitignore 対象。ISSUE ファイル自体はコミットしない
- push 前に単体テスト（`mvn test "-Dtest=!*IntegrationTest"`）が全緑であることを確認
- gh の PATH が通らない場合: `$env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")`
