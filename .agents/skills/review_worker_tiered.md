---
name: review-worker-tiered
description: Opus/Sonnet サブエージェントが単一視点のコードレビューを低コストで実行するための作業規約。full_code_review.md の視点分担を前提に、確度の低い判断は Fable(親) へエスカレーションする。
---

# Review Worker (Opus/Sonnet 用)

## モデル分担
- **Sonnet 5**: Security / UI / Data(MyBatis) / Docs&Tests の各視点
- **Opus**: Domain/Backend（勤怠計算・有給・残業・承認フローなど判断が重い視点）
- **Fable(親セッション)**: ワーカーが `UNRESOLVED` とした項目の最終判断のみ

## 作業手順
1. `docs/overview.md`・`docs/detail.md` を読み、担当視点に関係する範囲だけ把握する
2. Grep で疑わしいパターンを狙い撃ち → 必要範囲だけ Read（全文丸読み禁止）
3. `target/`, `node_modules/`, `logs/`, `data/` は読まない
4. 指摘は必ず `ファイルパス:行番号` の根拠付き。推測で断定しない
5. 判断に確信が持てない項目は削除せず **UNRESOLVED** として残す

## 出力（担当ファイルに Write）
`.tmp/reviews/<perspective>_YYYYMMDD.md` に以下の構成で書く:

```markdown
# <視点> レビュー YYYY-MM-DD
## サマリ（3行以内）
## 指摘一覧
### [S1] タイトル (severity: critical/high/medium/low)
- File: path:line
- 問題: / 根拠: / 推奨対応:
## UNRESOLVED（Fable への相談事項）
### [U1] タイトル
- File: path:line
- 疑い: / 判断できない理由:
## ドキュメント乖離
- docs/xxx.md の記述 vs 実装の差分（あれば）
```

## トークン規約
- 前置き・コード全文再掲をしない。根拠は数行の引用まで
- 最終メッセージは「指摘N件 / UNRESOLVED M件 / docs乖離K件」の1行＋重大指摘の見出しのみ
