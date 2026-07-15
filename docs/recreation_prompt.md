# システム再構築用プロンプト (System Recreation Prompt)

このドキュメントは、将来新しいAIモデルに対して、現在の勤怠管理システムと全く同等のシステムをゼロから再構築させるための指示書（プロンプト）です。以下の内容をそのままコピーしてAIに入力することで、要件定義から実装までを高い精度で再現させることができます。

---
以下の「---PROMPT START---」から「---PROMPT END---」までのテキストをコピーして使用してください。
---

---PROMPT START---
あなたはシニアレベルのJava/Spring Bootエンジニアです。
要件定義書および詳細仕様に基づき、エンタープライズ向けの「勤怠管理システム」をゼロからフルスクラッチで構築してください。

## 1. テクノロジースタック
以下の技術スタックを用いて実装してください。
*   **言語**: Java 25
*   **フレームワーク**: Spring Boot 4.1.0
*   **ビルドツール**: Maven
*   **データベース**: PostgreSQL
*   **O/Rマッパー**: MyBatis (`mybatis-spring-boot-starter` 4.0.1、XMLによるSQLマッピングを使用)
*   **DBマイグレーション**: Flyway (`spring-boot-starter-flyway` + `flyway-database-postgresql`。スキーマ用: `src/main/resources/db/migration`, サンプルデータ用: `src/main/resources/db/sample`。手動適用用に `flyway-maven-plugin` も設定)
*   **フロントエンド**: Thymeleaf (`thymeleaf-layout-dialect` による共通レイアウト `layout/base.html`、`thymeleaf-extras-springsecurity6`)、Tailwind CSS (`tailwind-maven-plugin` io.github.4ndreidev v1.1.0 でビルド時に自動生成。グラスモフィズムを取り入れたリッチなデザインシステムを構築するため、`tailwind.config.js` にてプライマリ・アクセントカラー、グラスモフィズム用カラー（`glass`）、角丸（`panel`）、影（`glass`）、およびアニメーション等のカスタムテーマ設定を行う。)
*   **セキュリティ**: Spring Security
*   **バリデーション**: `spring-boot-starter-validation`
*   **キャッシュ**: `spring-boot-starter-cache`
*   **監視**: Spring Boot Actuator
*   **ユーティリティ**: Lombok, Gson, Apache Commons CSV 1.10.0, Jackson JSR310
*   **テスト**: `spring-boot-starter-test`, `spring-security-test`

## 2. 開発要件とアーキテクチャ
*   **アーキテクチャ**: コントローラー、サービス、マッパー(MyBatis)、エンティティの層を持つ標準的なMVCアーキテクチャを採用してください。
*   **Mavenプロファイル設定**: `pom.xml` に以下の2つのプロファイルを定義してください。
    *   **`local`プロファイル（デフォルト）**: `<activeByDefault>true</activeByDefault>` を設定し、`mvn spring-boot:run` 実行時に自動的に有効になるようにする。`application-local.yml` と、Mavenプロファイル値を埋め込んだ `application.yml` のみをクラスパスに含める。
    *   **`release`プロファイル**: `mvn package -P release` などで明示的に指定する本番ビルド用。`application-release.yml` と、Mavenプロファイル値を埋め込んだ `application.yml` のみをクラスパスに含める。リリースビルドでもテストを実行し、スキップは必要な場合に限ってCLIから明示する。
    *   **`spring-boot-maven-plugin`** の `<profiles>` と `application.yml` の `spring.profiles.active` を `${spring.profiles.active}` から連動させ、JAR直接起動時も選択したMavenプロファイルを使用すること。
*   **設定ファイル構成**:
    *   `application.yml`: 全プロファイル共通の設定（MyBatis設定、ログレベル、Flyway、graceful shutdown等）
    *   `application-local.yml`: ローカル開発用のDB接続情報（`local`プロファイル専用）
    *   `application-release.yml`: 本番環境用の設定（`release`プロファイル専用。環境変数からDB接続情報を注入する形式を推奨）
*   **タイムゾーン統一**: 業務日時はすべて日本時間（`Asia/Tokyo`）で扱うこと。アプリケーション起動時（`main` メソッド）に `TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))` を実行し、「今日」「現在時刻」の取得は `Asia/Tokyo` 固定の日時ユーティリティクラス（`DateTimeUtil`）に集約する。`@Scheduled` の cron には必ず `zone = "Asia/Tokyo"` を指定する。DBの日時カラムは `TIMESTAMP WITH TIME ZONE` を使用する。
*   **キャッシュ**: `@EnableCaching` を有効化し、マスタ系データ（祝日、システム設定、勤務クラス、勤怠事由、承認待ち件数等）を `ConcurrentMapCacheManager` でキャッシュする。コミット前のEvictによる旧値再キャッシュを防ぐため、CacheManagerは `TransactionAwareCacheManagerProxy` でラップし、Evict/Putをトランザクションコミット後に遅延させること。
*   **定期バッチ**: `@Scheduled`（すべて `Asia/Tokyo`）による自動実行バッチを実装する（年次有給の自動付与: 毎日0時、利用終了日到達ユーザーの無効化: 毎日0時30分、月次集計チェック: 毎日1時、勤怠提出リマインドチェック: 毎時、36協定アラート・有給消化アラート: 毎月1日3時）。月次集計・有給付与・リマインド・利用終了日無効化は管理者ダッシュボードからの手動実行も可能にする。
*   **運用設定**: graceful shutdown（`server.shutdown: graceful`、`timeout-per-shutdown-phase: 30s`）、HikariCPの `connection-init-sql` で `lock_timeout = '3s'`、MyBatisの `default-statement-timeout: 30` / `default-fetch-size: 100` を設定する。Flywayの `baseline-on-migrate` は `false` とし、履歴なしの非空DBはfail-fastさせる。手動baselineはバックアップとスキーマ版の監査後に限る。適用済みV1/V2は編集せず、固定 `postgres` GRANT・プロファイル間のサンプル履歴差・fresh DB用B5以降の設計は Issue #182 の後続とする。
*   **例外・エラー画面**: `@ControllerAdvice` によるグローバル例外ハンドラを実装し、`400` / `500` / アクセス拒否 / アカウントロック / 楽観ロック競合（データ競合）用のエラーテンプレートを用意する。
*   **ロールベースのアクセス制御**: 一般ユーザー（`USER`）とシステム管理者（`ADMIN`）の権限を分離し、Thymeleafの画面ディレクトリも `user/` と `admin/` に分けてください。
*   **Tailwind CSS設定**:
    *   `tailwind.config.js` をルートディレクトリに配置し、以下のカスタム設定を含めてください。
        *   `content`: `./src/main/resources/templates/**/*.html`
        *   `theme.extend.colors`:
            *   `primary`: DEFAULT `#2563eb`, hover `#1d4ed8`, light `rgba(37, 99, 235, 0.1)`
            *   `accent`: DEFAULT `#7c3aed`, light `rgba(124, 58, 237, 0.1)`
            *   `success`: DEFAULT `#10b981`, light `rgba(16, 185, 129, 0.2)`
            *   `danger`: DEFAULT `#ef4444`, light `rgba(239, 68, 68, 0.2)`
            *   `warning`: DEFAULT `#f59e0b`, light `rgba(245, 158, 11, 0.2)`
            *   `glass` (グラスモフィズム用背景・境界線・ホバーなど): `bg` (`rgba(255, 255, 255, 0.7)`), `border` (`rgba(0, 0, 0, 0.08)`), `hover` (`rgba(0, 0, 0, 0.03)`), `nav-hover` (`rgba(0, 0, 0, 0.05)`), `input` (`rgba(255, 255, 255, 0.9)`)
            *   `txt` (テキスト用): `primary` (`#020617`), `secondary` (`#334155`)
        *   `theme.extend.fontFamily`: `sans` (`['Inter', 'sans-serif']`), `mono` (`['"JetBrains Mono"', 'monospace']`)
        *   `theme.extend.boxShadow`: `glass` (グラスモフィズム用の影), `glass-heavy` (より強調された影), `primary` / `primary-hover`
        *   `theme.extend.backdropBlur`: `glass` (`12px`), `glass-heavy` (`16px`)
        *   `theme.extend.borderRadius`: `panel` (`16px`), `panel-heavy` (`24px`)
        *   `theme.extend.keyframes`/`animation`: `fadeIn` / `fade-in` (`0.5s ease forwards`), `pulse` / `pulse-slow` (`2s infinite`)
    *   `src/main/resources/static/css/input.css` に `@tailwind base;`, `@tailwind components;`, `@tailwind utilities;` を記述してください。

## 3. データベース設計要件
以下の主要機能を持つテーブル群をFlywayスクリプト（`V1__Initial_Schema.sql`）として定義してください。
**【重要】** 本システムでは原則として**論理削除**を採用してください。各テーブルに `is_deleted`（boolean, default false）や `is_active`（boolean, default true）等のフラグカラムを設け、データの削除時は物理削除ではなくフラグの更新（UPDATE）を行うようにしてください。日時カラムは `TIMESTAMP WITH TIME ZONE` を使用してください。

1.  **ユーザー・マスタ系**:
    *   `users`: メールアドレス、パスワード、氏名、権限、所属、年次有給設定、アカウントロック機能。
    *   `work_schedule_classes`: 勤務クラス（所定労働時間）。
    *   `work_schedule_class_breaks`: 勤務クラスごとの休憩時間スロット（1クラスに複数保持。`class_id` + 開始時刻でユニーク、クラス削除時はCASCADE）。
    *   `event_types`: 勤怠事由マスタ（通常・遅刻・早退・有休・特別休暇・欠勤・土曜出勤・休日出勤・振替出勤・振替休日・自宅待機・テレワーク・その他・週休・休職の15種を初期データとして投入）。
    *   `holidays`: 祝日マスタ。
    *   `system_settings`: システム共通設定。
2.  **勤怠・申請系**:
    *   `attendance_records`: 日次の出退勤時間、休憩分数、残業時間、深夜労働時間、勤怠事由（`event_type_id` で `event_types` を参照）。
    *   `attendance_submissions`: 月次勤怠の申請ステータス（PENDING, APPROVED, RETURNED, WITHDRAWN）。
    *   `attendance_correction_requests`: 承認済み勤怠に対する事後の修正申請ワークフロー機能。
    *   `leave_applications`: 休暇申請（有給、欠勤、特別休暇等）。
    *   `overtime_records`: 残業申請と実績。
    *   `paid_leave_balance`: 年次ごとの有給休暇残高（付与、利用、繰越）。
3.  **承認・通知・ログ系**:
    *   `attendance_department_approvers` / `attendance_user_approvers`: 部門・ユーザーごとの承認者マッピング。
    *   `user_notifications`: ユーザーダッシュボード向け通知。
    *   `admin_announcements`: 管理者向けダッシュボードからの全体お知らせ配信。
    *   `audit_logs`: システムの重要操作（勤怠承認、ユーザー変更等）を追記保存する監査正本。重要な業務更新と同一トランザクションでINSERTし、監査保存失敗時は業務更新もロールバックする。監査CSVはコミット後に出力する非正本の運用補助で、月次gzipアーカイブを24か月保持する。CSV欠損時は監査DBから再出力し、CSV失敗で業務をロールバックしない。
4.  **サンプルデータ**:
    *   開発・動作確認用に、初期管理者ユーザー（`admin@example.com` / `admin123`）や一般のダミー従業員（`user@example.com` / `user123` 等）、各種マスタデータを投入するスクリプトを `src/main/resources/db/sample/V2__Sample_Data.sql` として作成してください。

## 4. 画面・機能要件
以下の画面とそれに対応するビジネスロジックを実装してください。
各画面はURL・入力機能・出力機能を忠実に再現してください。

### 【共通機能（認証）】

#### (0) ログイン画面 (`/login`)
*   **入力機能**: メールアドレス・パスワードの入力と「ログイン」ボタン（Spring SecurityのフォームログインでPOST `/login` を処理）
*   **出力機能**: ログイン失敗・一時ロック（`?error=templock`: 「30分後に再試行」警告）・本ロック（`?error=locked`: 「管理者に問い合わせ」エラー）・ログアウト完了の各メッセージ表示
*   **パスワード忘れ**: セルフサービスの再設定は行わない。管理者がユーザー詳細画面から一時パスワードを発行し、対象ユーザーは次回ログイン時に新しいパスワードへ強制変更する
*   **付随要件**:
    *   ログイン失敗回数の追跡によるアカウントロック機能（連続失敗で30分の一時ロック、さらに失敗が続くと本ロック）
    *   ルートパス `/` は認証済みならダッシュボード、未認証ならログイン画面へリダイレクト
    *   ログアウト完了画面（`/logout-success`）とアクセス拒否画面（`/access-denied`）

---

### 【一般ユーザー向け機能 (`user/`)】

#### (1) ユーザーダッシュボード (`/dashboard`)
*   **入力機能**:
    *   「通知をすべて既読にする」ボタン（POST `/dashboard/notifications/read-all`）と通知個別の既読API（POST `/dashboard/notifications/{id}/read`）
    *   「勤務状況を分析」ボタン（POST `/dashboard/analyze`。当月の労働時間・残業・深夜・有給残から健康アドバイスHTMLを生成しJSONで返す）
*   **出力機能**:
    *   今月の総労働時間・有給休暇残日数・残業時間・深夜労働時間の統計表示
    *   直近の勤怠記録（日付・出勤・退勤・ステータス）の一覧
    *   管理者からのお知らせメッセージの掲示板表示
    *   未読通知件数の取得API（GET `/dashboard/notifications/unread`）

#### (2) 打刻・出退勤画面 (`/attendance/quick`)
> テンプレートは `attendance/input.html`。
*   **入力機能**:
    *   「出勤」ボタン（POST `/attendance/quick-start`。現在時刻を出勤打刻として登録）
    *   「退勤」ボタン（POST `/attendance/quick-end`。現在時刻を退勤打刻として登録、休日出勤チェックボックスあり）
    *   「+15分」「+60分」ボタンによる休憩時間の追加登録（POST `/attendance/quick-break`）
*   **出力機能**:
    *   現在の打刻ステータス（出勤中・退勤済み等）のリアルタイム表示
    *   本日の出勤・退勤時刻と合計労働時間の表示
    *   勤怠事由（イベント種別）マスタの選択肢表示

#### (3) 月次勤怠画面 (`/attendance`)
*   **入力機能**:
    *   プルダウンによる表示月度の切り替え
    *   日別行のインライン直接編集（出勤/退勤時刻・休憩・事由・備考）と「一括保存」ボタン（POST `/attendance/saveAll`）
    *   日別行の「削除」ボタン（POST `/attendance/delete/{recordId}`）
    *   「勤怠提出」ボタン（POST `/attendance/submit-month`。承認者宛てに月次申請を送信）
    *   「取り下げる」ボタン（POST `/attendance/withdraw-month`。申請中ステータスの自己取り下げ）
    *   日毎の「修正申請」ボタン（勤怠修正申請フォームへ遷移）
    *   日毎のワンクリック休暇申請（当日分の有給: POST `/leave/applyPaid`、特休・欠勤等: POST `/leave/apply`。いずれも承認フロー不要で作成と同時に即時承認される）
*   **出力機能**:
    *   1ヶ月分の日別勤怠記録（日付・曜日・勤務クラス・出勤/退勤時間・実働/残業/深夜時間・備考・ステータス）の一覧
    *   提出ステータス（未提出・申請中・承認済み・差戻し）の表示
    *   「CSVエクスポート」ボタン（GET `/attendance/export`。月次勤怠データのCSVダウンロード）
*   **勤怠期間**: 設定された締め日から、対象給与月を「前月締め日の翌日〜当月締め日」として算出する。初回提出時に開始日・終了日をスナップショット保存し、締め日変更後の再申請でも既存スナップショットを保持する

#### (4) 勤怠修正申請フォーム (`/attendance/corrections/new`)
> 承認済み月度の個別レコードに対する修正申請。申請理由は必須。
*   **入力機能**: 修正後の「出勤時刻」「退勤時刻」「休憩時間」の入力、申請理由テキスト、「申請する」ボタン（POST `/attendance/corrections/submit`）
*   **出力機能**: 修正前（現在）の打刻データスナップショット表示

#### (5) 修正申請一覧（自分の申請履歴）(`/attendance/corrections`)
*   **入力機能**: 申請中（PENDING）の申請に対する「取り下げ」ボタン（POST `/attendance/corrections/{requestId}/withdraw`）
*   **出力機能**: 自分が申請した修正申請の一覧（対象日、申請日、ステータス）

#### (6) 休暇申請一覧画面 (`/leave`)
*   **入力機能**:
    *   「新規休暇申請」ボタン（登録フォームへ遷移）
    *   申請中（PENDING）の申請に対する「取消」ボタン（POST `/leave/delete/{applicationId}`）
*   **出力機能**: 開始/終了日・休暇タイプ・理由・状況（申請中/承認/却下）の申請履歴一覧

#### (7) 休暇申請新規登録フォーム (`/leave/create`)
*   **入力機能**:
    *   開始日・終了日のカレンダー入力
    *   休暇タイプのプルダウン（有給休暇・無給休暇・病気休暇・特別休暇・欠勤）
    *   期間種別のラジオボタン（終日・午前半休・午後半休）
    *   申請理由のテキスト入力、「申請する」ボタン
*   **出力機能**: なし（登録後は一覧画面へリダイレクト）

#### (8) 残業実績画面 (`/overtime`)
> 閲覧専用画面。残業は月次勤怠画面で保存した勤務実績から自動計算される（画面上にその旨を明記）。
*   **入力機能**: プルダウンによる表示月度の切り替えのみ
*   **出力機能**: 当月の残業時間合計、残業実績（日付・開始/終了時刻・残業時間）の一覧

#### (9) パスワード変更画面 (`/dashboard/change-password`)
> ログイン時の強制変更要求、またはプロファイルから遷移。
*   **入力機能**: 現在のパスワード・新しいパスワード・確認用パスワードの入力と「パスワードを変更する」ボタン
*   **出力機能**: なし（変更後はダッシュボードへリダイレクト）

#### (10) プロフィール画面 (`/profile`)
> サイドバーから遷移。ログイン中ユーザーの情報と所属勤務クラスを表示する。
*   **入力機能**: なし。所属勤務クラスは読取専用で表示し、変更は管理者のユーザー編集画面から行う
    *   **出力機能**: ログイン中ユーザーの基本情報（社員番号、氏名、部署等）、有給消化日数・残日数、本人が承認なしで変更できる勤務クラス選択（有効な勤務クラスまたは未設定）

---

### 【承認者（マネージャー）向け機能】
> 一般ユーザーロールを持ちながら、配下ユーザーの承認者として登録されている場合にのみアクセス可能な画面。

#### (1) 勤怠承認待ち一覧 (`/attendance/approval`)
*   **入力機能**: 「承認」ボタン（POST `/attendance/approval/{submissionId}/approve`）、「差戻し」ボタン（POST `/attendance/approval/{submissionId}/return`。差戻しコメント入力テキストボックスあり）
*   **出力機能**: 承認待ち従業員の一覧（氏名・対象月・提出日時・ステータス）

#### (2) 修正申請承認待ち一覧 (`/attendance/approval/corrections`)
*   **入力機能**: 「承認」ボタン（POST `/attendance/approval/corrections/{requestId}/approve`）、「却下」ボタン（POST `/attendance/approval/corrections/{requestId}/reject`。却下理由コメント入力必須）
*   **出力機能**: 修正申請中の従業員一覧と修正前・修正後のデータ差分比較表示

#### (3) 配下従業員の勤怠詳細 (`/attendance/approval/{userId}/detail`)
*   **入力機能**: 配下従業員の日次勤怠のインライン編集と「一括保存」ボタン（POST `/attendance/approval/{userId}/detail/saveAll`）
*   **出力機能**: 指定従業員の月次勤怠明細（日別記録、残業・深夜時間、ステータス）

---

### 【システム管理者向け機能 (`admin/`)】
> `@PreAuthorize("hasRole('ADMIN')")` による保護が必要。

#### (1) 管理者ダッシュボード (`/admin/dashboard`)
*   **入力機能**:
    *   対象年月入力 + 「月次集計を実行」ボタン（POST `/admin/batch/monthly-summary`。手動バッチ）
    *   「年次有給付与を実行」ボタン（POST `/admin/batch/annual-leave-grant`）
    *   「勤怠提出リマインドを送信」ボタン（POST `/admin/batch/reminder`）
    *   「利用終了日到達ユーザーを無効化」ボタン（POST `/admin/batch/deactivate-expired`）
*   **出力機能**: バッチ処理完了・成否ログメッセージ、システム全体の勤務統計サマリー

#### (2) ユーザー管理画面 (`/admin/users`)
*   **入力機能**: 「新規ユーザー登録」ボタン（登録フォームへ遷移）、一覧からの「詳細」リンク
*   **出力機能**: 社員番号・氏名・メールアドレス・部署・権限・状態・最終ログイン日時の従業員一覧

#### (3) 新規ユーザー登録フォーム (`/admin/users/create`)
*   **入力機能**: 社員番号・氏名・メールアドレス・パスワード・部署・雇用区分・職位・電話番号・勤務クラス・権限（ADMIN/USER）の入力と「登録する」ボタン
*   **初期有給残高**: ユーザー作成と同時に、初期有給残高と次回付与日数をともに10日で作成する
*   **出力機能**: なし（登録後は一覧画面へリダイレクト）

#### (4) ユーザー詳細・有給設定画面 (`/admin/users/{userId}`)
*   **入力機能**:
    *   各種登録情報の編集・更新（POST `/admin/users/{userId}/update`）、アカウント有効/無効の切り替え
    *   パスワード忘れ時に使用する「一時パスワードを発行」ボタン（POST `/admin/users/{userId}/reset-password`）。次回ログイン後はパスワード変更画面へ強制遷移する
    *   年次有給付与日数・最大有給保持日数・有給付与増分の設定と更新ボタン（POST `/admin/users/{userId}/leave-settings`）
    *   承認者マッピングの設定（ユーザー承認者: POST `/admin/users/{userId}/approvers/user`、部門承認者: POST `/admin/users/{userId}/approvers/department`）
*   **出力機能**: 従業員個人の登録情報、現在の有給付与パラメータ設定値、承認者マッピングの現在値
*   **補足**: ユーザーの論理削除エンドポイント（POST `/admin/users/{userId}/delete`）を実装すること（現状は画面上の導線なし）。

#### (5) 勤怠管理画面 (`/admin/attendance`)
*   **入力機能**:
    *   対象年月および所属部署による表示絞り込み
    *   承認済みステータスに対する「承認取消」ボタン（POST `/admin/attendance/submissions/{submissionId}/revoke`）
*   **出力機能**:
    *   社員別の総労働時間・残業時間・出勤日数・承認状況の月次サマリー一覧
    *   各行の「詳細」リンク（`/admin/attendance/{userId}` へ遷移）
    *   「全従業員CSV(ZIP)エクスポート」ボタン（GET `/admin/attendance/export/zip`）
*   **補足**: 給与連携CSVエクスポートのエンドポイント（GET `/admin/attendance/export-payroll-csv`。フォーマット・文字コードを指定しGZIP圧縮でダウンロード）を実装すること（現状は画面上の導線なし）。

#### (5)-2 従業員勤怠詳細画面 (`/admin/attendance/{userId}`)
*   **入力機能**: 管理者による特定従業員の日次勤怠の直接修正
*   **出力機能**:
    *   指定従業員の月次勤怠明細（日別記録、残業・深夜時間、ステータス）
    *   「月次勤怠CSVエクスポート」ボタン（GET `/admin/attendance/export/csv`。ファイル名はCSVファイル名テンプレート設定に従う）

#### (6) 勤務クラス設定（所定時間マスタ）(`/admin/work-schedules`)
*   **入力機能**:
    *   クラス名・勤務地・始業/終業時刻・休憩時間スロット（動的追加式で複数登録可、最低1つ必須）の入力と「作成する」ボタン（POST `/admin/work-schedules/create`）
    *   登録済みクラスの修正・更新ボタン（POST `/admin/work-schedules/{classId}/update`）、「削除」ボタン（POST `/admin/work-schedules/{classId}/delete`）
*   **出力機能**: 登録されているすべての勤務クラス（標準勤務・早番・夜勤等）と設定時間の一覧

#### (7) 36協定管理・監視ダッシュボード (`/admin/article36`)
*   **入力機能**:
    *   ドロップダウンによる表示月度の切り替え
    *   注意水準（40時間超）または超過水準（45時間超）の従業員に対する「アラート通知送信」ボタン（POST `/admin/article36/notify/{userId}`。対象ユーザーの `user_notifications` に警告通知を発行する。正常範囲（NORMAL）の従業員には送信不可）
*   **出力機能**:
    *   「超過警告（45時間超）」「超過注意（40時間超）」「安全ゾーン」の対象人数カウンタ
    *   従業員ごとの時間外労働時間と36協定ステータス（基準超過/注意水準/正常範囲）の一覧
    *   直近3ヶ月の平均残業時間推移グラフ

#### (8) お知らせ管理（掲示板管理）(`/admin/announcements`)
*   **入力機能**: タイトル（必須・200文字以内）・本文（必須・2000文字以内）・表示開始日時・表示終了日時の入力フォーム（POST `/admin/announcements/create`）、一覧で共用する編集モーダルからの既存投稿更新（POST `/admin/announcements/{announcementId}/update`）、アクティブ/非アクティブのトグル、「削除」ボタン（POST `/admin/announcements/{announcementId}/delete`）。空白のみの投稿と、存在しない・削除済み投稿の更新/削除はサーバー側で拒否する。
*   **出力機能**: 投稿済みのお知らせ一覧（タイトル・掲載期間・ステータス）。1ページ10件（最大50件）のサーバー側ページングを行う。

#### (9) 有給休暇取得状況画面 (`/admin/leave-usage`)
*   **入力機能**: 年月・氏名による検索入力ボックス
*   **出力機能**: 全従業員の有給休暇の取得日数・残日数・消化率（パーセンテージ）の一覧

#### (10) システム設定画面 (`/admin/settings`)
*   **入力機能**（設定ごとに個別の「保存」ボタンを持つフォーム群）:
    *   年次有給付与設定（付与日: MM-DD形式。POST `/admin/settings`）。付与日数は従業員ごとに設定する。2月は28日まで設定可能とする
    *   締め日の数値入力 1〜28（POST `/admin/settings/attendance-period`）。開始日は前月締め日の翌日として自動算出する。旧 `attendance_period_start_day` は互換用に残すが、業務ロジックでは参照しない
    *   CSVファイル名テンプレート文字列（例: `{yyyy}-{MM}_{userId}_{name}_{downloadAt}`）の入力（POST `/admin/settings/csv-pattern`）。`{yyyy}`、`{MM}`、`{userId}`、`{name}`、`{downloadAt}` はすべて必須
    *   アラート対象値の設定（POST `/admin/settings/alert`）。有休消化警告は「付与後の経過月数」と「その時点で消化済みであるべき最低日数」を指定する
    *   リマインド配信日時設定（POST `/admin/settings/batch`）
    *   システム名の設定（POST `/admin/settings/system-name`）
    *   コピーライト表記の設定（POST `/admin/settings/copyright`）
    *   社員番号プレフィックスの設定（POST `/admin/settings/emp-no-prefix`）
    *   祝日マスタのCSVアップロード（POST `/admin/settings/holidays/upload` → プレビュー確認 POST `/admin/settings/holidays/confirm` → キャンセル GET `/admin/settings/holidays/cancel` の2段階取込フロー）
*   **出力機能**: 現在のシステム設定パラメータ値の一覧

#### (11) 通知管理画面 (`/admin/notifications`)
*   **入力機能**: 通知送信フォーム（POST `/admin/notifications/send`。対象ユーザーへの通知を手動発行）
*   **出力機能**: システム全体で発行されたユーザー通知の一覧（対象ユーザー、内容、既読状態）

## 5. 生成ステップの指示
一度にすべてを出力するとトークン上限に達するため、以下のステップ順にコードを提示してください。
*   **Step 1**: プロジェクト構成（`pom.xml`, `application.yml`, `application-local.yml`, `tailwind.config.js`）の提示
*   **Step 2**: FlywayマイグレーションSQL（`V1__Initial_Schema.sql` および `V2__Sample_Data.sql`）の提示
*   **Step 3**: MyBatisのエンティティとMapperインターフェース/XMLの提示
*   **Step 4**: Spring Security・キャッシュ・タイムゾーンおよび全体設定クラスの提示
*   **Step 5**: Service層（ビジネスロジック、承認フロー、定期バッチの実装）の提示
*   **Step 6**: Controller層（一般ユーザー向け、承認者向け、管理者向け）の提示
*   **Step 7**: Thymeleafテンプレート（共通レイアウト・HTML）の提示

それでは、Step 1から実装を開始してください。
---PROMPT END---
