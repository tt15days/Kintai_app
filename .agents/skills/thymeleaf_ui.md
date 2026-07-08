---
name: thymeleaf-ui-rules
description: Thymeleafテンプレートおよび画面UI/UX、JavaScript、CSSを変更する際のルール。TailwindCSS v4系ユーティリティクラスを使用し、共通アセット・レイアウトの再利用、入力バリデーション、Null安全、画面文言・トーン、ロール・レスポンシブ、構文・変数・エラー・セキュリティなどのルールに従ってThymeleaf/UIを変更する。
---

## 1. TailwindCSS 基本方針

- **インラインスタイル禁止**: `style="..."` や `th:style` は原則使用しない。動的な状態は `th:classappend` で制御する。
- **カスタム `<style>` タグ禁止**: テンプレート内に `<style>` ブロックを書かない。スタイルは `src/main/resources/static/css/input.css` で定義する。
- **Bootstrap クラス禁止**: `btn`, `btn-primary`, `alert`, `badge`, `form-control`, `modal`（Bootstrap固有）などの Bootstrap 由来クラスは使用しない。
- **CSS 変数禁止**: `var(--primary-color)` などの CSS カスタムプロパティは使用しない。Tailwind のテーマカラーを使用する。

## 2. テーマカラー・テーマトークン

`tailwind.config.js` で定義済みのトークンを必ず使用する。

| 用途 | Tailwind クラス | 説明 |
|------|----------------|------|
| メインカラー | `text-primary`, `bg-primary`, `border-primary` | #2563eb |
| アクセント | `text-accent`, `bg-accent` | #7c3aed |
| 成功 | `text-success`, `bg-success` | #10b981 |
| 危険・エラー | `text-danger`, `bg-danger` | #ef4444 |
| 警告 | `text-warning`, `bg-warning` | #f59e0b |
| 本文テキスト | `text-txt-primary`, `text-txt-secondary` | ダーク系テキスト |
| ガラスパネル背景 | `bg-glass/70`, `backdrop-blur-glass` | Glassmorphism |
| ガラスボーダー | `border-glass-border` | パネル枠線 |
| ガラス影 | `shadow-glass`, `shadow-glass-heavy` | パネル影 |

## 3. 共通コンポーネントパターン

### パネル（カード）
```html
<div class="bg-glass/70 backdrop-blur-glass border border-glass-border shadow-glass rounded-[24px] p-6 transition-all duration-300">
```

### パネル見出し
```html
<h4 class="text-base font-bold text-txt-primary pb-4 mb-4 border-b border-glass-border/30 flex items-center gap-1.5">
    <i class="fa-solid fa-xxx text-primary"></i> 見出しテキスト
</h4>
```

### ボタン（プライマリ）
```html
<button type="submit" class="bg-primary text-white hover:bg-primary/90 inline-flex items-center justify-center gap-2 px-6 py-2.5 rounded-xl font-semibold text-sm transition-all duration-200 active:scale-95 cursor-pointer shadow-primary hover:shadow-primary-hover">
    <i class="fa-solid fa-save"></i> 保存
</button>
```

### ボタン（セカンダリ）
```html
<button type="button" class="bg-black/5 hover:bg-black/10 border border-glass-border text-txt-primary inline-flex items-center justify-center gap-2 px-4 py-2 rounded-xl font-semibold text-sm transition-all duration-200 active:scale-95 cursor-pointer">
    キャンセル
</button>
```

### ボタン（危険）
```html
<button type="submit" class="bg-danger text-white hover:bg-danger/90 inline-flex items-center justify-center gap-2 px-4 py-2 rounded-xl font-semibold text-sm transition-all duration-200 active:scale-95 cursor-pointer">
    削除
</button>
```

### フォーム入力
```html
<input type="text" class="w-full px-4 py-2.5 rounded-xl border border-glass-border bg-glass-input/90 text-txt-primary focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all duration-200">
```

### フォームラベル
```html
<label class="block text-sm font-semibold text-txt-secondary mb-1.5">ラベル <span class="text-danger">*</span></label>
```

### アラート（成功）
```html
<div class="bg-success/15 border border-success/30 text-success p-4 rounded-xl flex justify-between items-center mb-6 text-sm font-semibold transition-all duration-200">
    <span><i class="fas fa-check-circle"></i> <span th:text="${successMessage}"></span></span>
    <button type="button" class="text-txt-primary hover:opacity-100 opacity-70 bg-transparent border-0 cursor-pointer" onclick="this.parentElement.remove()">×</button>
</div>
```

### アラート（エラー）
```html
<div class="bg-danger/15 border border-danger/30 text-danger p-4 rounded-xl flex justify-between items-center mb-6 text-sm font-semibold transition-all duration-200">
    <span><i class="fas fa-exclamation-circle"></i> <span th:text="${errorMessage}"></span></span>
    <button type="button" class="text-txt-primary hover:opacity-100 opacity-70 bg-transparent border-0 cursor-pointer" onclick="this.parentElement.remove()">×</button>
</div>
```

### ステータスバッジ
```html
<!-- 成功 -->
<span class="px-2.5 py-1 text-xs font-bold rounded-full bg-success/15 text-success border border-success/20 inline-flex items-center gap-1">有効</span>
<!-- 危険 -->
<span class="px-2.5 py-1 text-xs font-bold rounded-full bg-danger/15 text-danger border border-danger/20 inline-flex items-center gap-1">無効</span>
<!-- 警告 -->
<span class="px-2.5 py-1 text-xs font-bold rounded-full bg-warning/15 text-warning border border-warning/20 inline-flex items-center gap-1">保留</span>
```

### テーブル
```html
<div class="overflow-x-auto">
    <table class="min-w-full divide-y divide-glass-border/30 text-left border-collapse">
        <thead class="bg-black/10">
            <tr>
                <th class="p-3 text-xs font-bold uppercase tracking-wider text-txt-secondary border-b border-glass-border/30">列名</th>
            </tr>
        </thead>
        <tbody class="divide-y divide-glass-border/10">
            <tr class="hover:bg-glass-nav-hover/10 transition-colors">
                <td class="p-3 text-sm text-txt-primary">値</td>
            </tr>
        </tbody>
    </table>
</div>
```

## 4. Thymeleaf との組み合わせルール

- **動的クラス付与**: `th:classappend` で条件に応じたクラスを追加する。`th:style` は使わない。
  ```html
  <!-- 良い例 -->
  <tr th:classappend="${record.isLate} ? 'bg-danger/5 border-l-2 border-l-danger' : ''">
  <!-- 悪い例（禁止） -->
  <tr th:style="${record.isLate} ? 'background: rgba(239,68,68,0.05);' : ''">
  ```
- **`th:if` / `th:unless`**: Null チェックを必ず組み合わせる。
  ```html
  <span th:if="${user != null and user.isActive}">有効</span>
  ```
- **CSRF トークン**: フォームには必ず含める。
  ```html
  <input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}" />
  ```
- **`layout:fragment`**: ページタイトルとコンテンツは必ず fragment に入れる。
  ```html
  <h2 layout:fragment="page-title">ページタイトル</h2>
  <div layout:fragment="content">...</div>
  ```
- **フラッシュメッセージは `<body>` 内に配置**: `<head>` タグ内にコンテンツを書かない。

## 5. モーダル制御

Bootstrap の `data-bs-toggle` / `data-bs-dismiss` は使用しない。純粋な JS で制御する。

```html
<!-- トリガーボタン -->
<button type="button" onclick="openModal('myModal')">開く</button>

<!-- モーダル本体 -->
<div id="myModal" class="modal fixed inset-0 z-50 hidden items-center justify-center p-4 bg-black/40 backdrop-blur-sm">
    <div class="max-w-lg w-full mx-auto bg-glass/90 backdrop-blur-glass border border-glass-border rounded-[24px] shadow-glass-heavy p-6">
        <div class="flex items-center justify-between pb-4 border-b border-glass-border/30 mb-4">
            <h5 class="text-base font-bold text-txt-primary">タイトル</h5>
            <button type="button" class="text-txt-primary opacity-70 hover:opacity-100 cursor-pointer bg-transparent border-0 text-xl" onclick="closeModal('myModal')">×</button>
        </div>
        <!-- コンテンツ -->
    </div>
</div>

<script>
function openModal(id) {
    const el = document.getElementById(id);
    el.classList.remove('hidden');
    el.classList.add('flex');
}
function closeModal(id) {
    const el = document.getElementById(id);
    el.classList.add('hidden');
    el.classList.remove('flex');
}
// 背景クリックで閉じる
document.addEventListener('click', function(e) {
    document.querySelectorAll('.modal').forEach(function(modal) {
        if (e.target === modal) closeModal(modal.id);
    });
});
</script>
```

## 6. その他ルール

- **入力バリデーション**: フォームにはサーバ側バリデーション前提で必要な入力チェックを追加する。
- **Null 安全**: Thymeleaf の条件式で Null チェックを行う（`${obj != null and obj.field}`）。
- **画面文言・トーン**: 画面文言は日本語業務用語と既存トーンに合わせる。
- **ロール別表示**: 管理者/一般ユーザーの表示分岐（`th:if` + Security）を壊さない。
- **レスポンシブ**: `sm:`, `md:`, `lg:` プレフィックスで Tailwind のレスポンシブを実現する（モバイルファースト）。
- **フォント**: `font-mono` クラスを時刻・数値・コードに使用する（JetBrains Mono）。
- **アニメーション**: `animate-fade-in` でフェードイン、`transition-all duration-200` でホバーアニメーションを付与する。
