# テスト仕様書 (Test Specification)

## 1. 概要
本ドキュメントは、勤怠管理システム（`attendance-app`）におけるテストコード（`src/test`）の仕様およびテストケースの一覧について記述します。
システムは、単体テスト（JUnit 5 + Mockito）および統合テスト（Spring Boot Test + MockMvc + PostgreSQL）の2層のテストで品質を担保しています。

### 1.1 テスト実行環境・方法
テストの実行は Maven を使用して行います。
- **全テストの実行:**
  ```powershell
  mvn test
  ```
- **特定のテストの実行 (例: コントローラーテストのみ):**
  ```powershell
  mvn test "-Dtest=*ControllerTest"
  ```
- **統合テストのみの実行:**
  ```powershell
  mvn test "-Dtest=*IntegrationTest"
  ```

---

## 2. テストの構成・分類

システムにおけるテストは以下のように分類されます。

1. **コントローラー単体テスト (Controller Unit Tests)**
   - `SettingsControllerTest`, `ProfileControllerTest`, `DashboardControllerTest` など。
   - `Mockito` を使用して依存サービスをモック化し、コントローラーのビジネスロジック、バリデーション、例外処理、メッセージ・モデルのバインド、リダイレクト動作等を検証します。
2. **サービス単体テスト (Service Unit Tests)**
   - `UserServiceTest`, `AttendanceRecordServiceTest`, `PaidLeaveBalanceServiceTest` など。
   - サービス内の計算処理（有給消化・残日数算出）、バッチ処理設定の保存ロジック、事由判定、アラート閾値ロジック等を検証します。
3. **統合テスト (Integration Tests)**
   - `SettingsControllerIntegrationTest`, `LeaveApplicationControllerIntegrationTest`, `SecurityAuthorizationIntegrationTest` など。
   - データベース（PostgreSQL）や Spring Security などのコンテキスト全体を立ち上げ、認証認可の制御（ADMIN/USERロール別のアクセス許可・拒否）、および実際のテーブルに対する書き込み・更新の永続化動作を検証します。
4. **マッパーテスト (Mapper Integration Tests)**
   - `UserMapperIntegrationTest`, `AttendanceSubmissionMapperIntegrationTest` など。
   - MyBatisの Mapper XML と DB テーブルを直接結合し、SQL文の動作（CRUD処理）を検証します。

---

## 3. テストクラスおよびケース詳細

### 3.1 コントローラー単体テスト

#### `SettingsControllerTest` (休日・システム設定コントローラー)
- **`showSettings` (設定画面表示)**
  - 設定値が `null` の場合にデフォルト値がモデルに設定されること。
  - 既存設定値が存在する場合に、その値がモデルに設定されビュー `"admin/settings"` が返されること。
- **`saveSettings` (有給付与設定保存)**
  - 正常な有給付与日（MM-DD）の更新。
  - 日付フォーマット不正時のエラーメッセージ設定とリダイレクト検証。
- **各種設定更新**
  - コピーライト表示、システム名表示、勤怠締め日、バッチ処理、アラート閾値、CSVファイル名パターンなどの更新正常系、および `IllegalArgumentException` 等の例外発生時のエラーハンドリング検証。勤怠期間設定は終了日（締め日）のみを受け付ける。
- **`uploadCsv` (祝日CSVアップロード)**
  - CSVのパース成功時にプレビューデータがモデルに設定されること。
  - IOエラー発生時のハンドリング検証。
  - 日付形式不正行を含むCSV（`IllegalArgumentException`）発生時のハンドリング検証。
- **`confirmAndSave` (祝日設定確定・保存)**
  - 正常なJSONのデシリアライズ、`createdAt`の自動補完、保存、およびセッションクリア検証。
  - データ空、JSONパースエラー、DB保存エラー発生時のハンドリング検証。
- **`cancelPreview` (プレビューキャンセル)**
  - セッション情報のクリアおよび設定画面へのリダイレクト検証。

#### `ProfileControllerTest` (プロフィールコントローラー)
- **`showProfile` (プロフィール画面表示)**
  - ログイン中ユーザーのプロフィール、有給消化日数・有給残日数の計算検証。
  - 有給付与日数が `null` の際に `0` として安全に計算され表示されること。
  - DBアクセス例外等のエラー発生時のエラー画面表示検証。
  - プロフィールには勤務クラス自己更新POSTを公開せず、変更を管理者経路に限定する。

#### `DashboardControllerTest` (ユーザーダッシュボード・AI分析コントローラー)
- **`showDashboard` (ダッシュボード表示)**
  - 初回ログインなどでパスワード変更（リセット）が必要な場合、パスワード変更画面へ強制リダイレクトされること。
  - 当月の総実労働時間、残業時間、深夜労働時間、有給残日数、出勤実績日数、お知らせ、通知がモデルに正しく集計・設定されて表示されること。
  - 一般ユーザー（承認権限なし）に承認者向け統計が表示されないこと。
  - 承認者ユーザーの場合、承認待ち件数（勤怠提出、修正申請）が正しく取得されて表示されること。
- **`changePassword` (パスワード変更処理)**
  - パスワード変更の正常系。
  - 新しいパスワードと確認用パスワードの不一致、現在のパスワードの不一致（`IllegalArgumentException`）、その他例外発生時のエラーハンドリング検証。
- **`readAllNotifications` (通知の一括既読化)**
  - 全通知の既読処理実行後のダッシュボードリダイレクト検証。
- **`getUnreadNotifications` (未読通知一覧のJSON取得)**
  - 未読通知一覧をJSONレスポンスとして正常に返却すること。例外時の空リスト返却。
- **`readNotification` (個別通知の既読化)**
  - 個別通知IDに対する既読処理の正常（200 OK）、および例外（500 Internal Server Error）のレスポンス検証。
- **`analyzeAttendance` (AI健康アドバイス診断)**
  - 残業時間（30時間以上/10時間以上/10時間以下）、深夜労働の有無、有休残日数に応じた適切なAIアドバイス（HTMLブロック）がJSON形式で生成され返却されること。
  - 非常に健康的な勤務傾向の場合のアドバイス生成。例外発生時の500エラー返却。

#### `AttendanceRecordControllerTest` (勤怠記録コントローラー)
- 一般・管理者向けの出退勤フォーム表示、打刻処理（出勤・退勤・休憩開始・休憩終了）、各処理での日付跨ぎの処理・パラメータバリデーション、打刻状態に応じたボタン制御、モデル設定等を検証。
- 勤怠修正申請（`/attendance/corrections/*`）の一覧表示、申請フォーム表示（既存勤怠記録の反映有無）、申請提出（正常系・`IllegalArgumentException`時のエラーメッセージ）、申請取り下げ（正常系・エラー時）の各処理を検証。

#### `AdminAnnouncementControllerTest` (管理者お知らせコントローラー)
- お知らせ一覧表示時のページング（空状態、100件超、範囲外・不正な `page` / `size` の補正）、登録・更新・削除の正常系、および入力不正・対象なし・DB例外時に成功表示しないことを検証。

#### `AttendanceApprovalControllerTest` (勤怠承認コントローラー)
- 承認者による申請一覧表示、一括/個別承認・差し戻し処理、認可権限なしの際のエラーハンドリング、メッセージ設定等を検証。

#### `LeaveApplicationControllerTest` (休暇申請コントローラー)
- 有給休暇・特別休暇などの申請一覧表示、申請処理、申請取消（削除）、過去締め日やロック月に対する申請・削除拒否ロジックを検証。

#### `LoginControllerTest` (ログインコントローラー)
- ログインフォーム表示時のエラー（認証失敗）、ログアウト後のメッセージ設定等を検証。

#### `AdminControllerTest` (管理者コントローラー)
- 管理者ダッシュボード・ユーザー管理系の処理に加え、`@Nested Article36` クラスにて36協定対象ユーザーの超過時間一覧表示、アラート閾値超過の判定ロジック等を検証。

#### `AdminLeaveUsageControllerTest` (有給取得状況コントローラー)
- 管理者向け有給取得状況（消化管理）画面の表示・集計を検証。

#### `GlobalControllerAdviceTest` / `GlobalExceptionHandlerTest` (共通アドバイス・例外ハンドラ)
- 全コントローラー共通のモデル属性設定、および共通例外ハンドリングを検証。

#### `LogoutControllerTest` (ログアウトコントローラー)
- ログアウト処理とリダイレクトを検証。

#### `AdminAttendanceExportControllerTest` (管理者向け勤怠エクスポートコントローラー)
- 管理者による全従業員のCSV/ZIPエクスポート機能の動作を検証。

#### `OvertimeRecordControllerTest` (残業記録コントローラー)
- 残業申請フォームの表示、申請処理、およびバリデーションのエラーハンドリング等を検証。

---

### 3.2 サービス単体テスト

#### `UserServiceTest` / `UserServiceAuditTest` (ユーザー管理サービス)
- 新規ユーザー作成（パスワード強度の桁数・英数混在ポリシーチェック、メール重複チェック）、パスワード変更、管理者による初期パスワードリセット、ソフトデリート処理、承認権限判定（ADMINおよび承認フラグ）、ログイン認証の各ロジックを検証。
- 新規ユーザーの有給残数、初年度残高、および次回付与日数がすべて10日で作成されることを検証。
- ユーザー情報の変更・削除に伴う監査ログの記録を検証。

#### `AdminAnnouncementServiceTest` (お知らせ管理サービス)
- お知らせの作成、公開期間の判定、管理画面向けページ取得、タイトル・本文の必須/上限境界、存在しない・削除済みIDの更新/削除拒否を検証。

#### `AttendanceApproverAssignmentServiceTest` (承認者割り当てサービス)
- ユーザーに対する承認者の割り当て、一括設定、取得ロジック等を検証。

#### `AttendanceCorrectionRequestServiceTest` (勤怠修正申請サービス)
- 修正申請の作成、ステータス変更、および権限チェック等を検証。
- 承認・却下時の監査ログ（`CORRECTION_APPROVED`/`CORRECTION_REJECTED`）記録を検証。
- 個人・部署アサインが設定されている場合、同じ勤務クラスでもアサイン外の承認者は承認できないこと（承認権限の優先順位）を検証。

#### `AttendancePeriodSettingServiceTest` (勤怠期間設定サービス)
- 締め日の取得・更新、対象給与月を「前月締め日の翌日〜当月締め日」とする期間算出、締め日前後の日付からの給与月解決を検証。
- 業務用の期間算出が旧 `attendance_period_start_day` を参照せず、終了日（締め日）のみを正本とすることを検証。

#### `AttendanceSubmissionService` 関連テスト群 (勤怠提出サービス)
- `AttendanceSubmissionServiceAuditTest`, `AttendanceSubmissionServiceStateTest`
- 勤怠提出の監査ログ、提出ステータスの状態遷移ルール（未提出→提出済→承認済など）を検証。
- 個人・部署アサインが設定されている場合、同じ勤務クラスでもアサイン外の承認者は承認できず、アサインされた承認者のみ承認できること（承認権限の優先順位）を検証。
- 初回提出時に算出期間をスナップショット保存し、締め日変更後の再申請では既存の開始日・終了日を保持することを検証。

#### `AuditLogServiceTest` (監査ログサービス)
- アクション実行時に `audit_logs` へ1件追記し、DBコミット後に同じ内容を補助監査CSVへ単一行で出力することを検証。
- DBへの追記件数が1件でない場合は例外とし、補助CSVを成功扱いで出力しないことを検証。

#### `AutoGrantPaidLeaveServiceTest` (有給自動付与サービス)
- 入社日や勤続年数に基づく有給の自動計算および付与ロジックを検証。

#### `CsvFilenamePatternServiceTest` (CSVファイル名パターンサービス)
- レポート等を出力する際のファイル名パターンの適用・生成ロジックを検証。

#### `EventTypeServiceTest` (イベントタイプサービス)
- イベント種別マスタの取得および判定ロジックを検証。

#### `LeaveApplicationServiceTest` (休暇申請サービス)
- 休暇申請の登録、承認時の有給残高減算、ステータス更新等のビジネスロジックを検証。
- 承認・却下時の監査ログ記録を検証。

#### `LoginAttemptServiceTest` (ログイン試行・ロックアウトサービス)
- 連続ログイン失敗時のアカウントロック処理と、一定時間経過後のロック解除判定を検証。

#### `OvertimeRecordServiceTest` (残業記録サービス)
- 勤怠実績からの残業記録同期（`syncFromAttendance`）および残業記録の作成ロジック（`createRecord`）を検証。36協定の上限規制判定は `AttendanceRecordServiceTest`、アラート対象者の抽出は `AlertBatchServiceTest` で検証する。

#### `PayrollExportServiceTest` (給与計算エクスポートサービス)
- 勤怠実績に基づく給与システム向けCSVデータのエクスポートフォーマット生成を検証。
- 期間設定変更を跨いで勤怠申請提出済みのユーザー（申請時スナップショット期間が既定monthRangeとずれるケース）でも、一括取得レンジが自動的に広がり集計から漏れないことを検証。
- 病気休暇・特別休暇（`SICK_LEAVE`/`SPECIAL_LEAVE`）が「その他休暇日数」列に計上されること、時間列が給与ソフトでの自動取込を想定した小数時間（小数点以下2桁）で出力されることを検証。

#### `ReportServiceTest` (レポート出力サービス)
- 勤務実績一覧、残業超過者一覧などのレポート作成ロジックを検証。

#### `UserNotificationServiceTest` (ユーザー通知サービス)
- システム内通知の生成、未読・既読ステータスの管理、および一括既読処理等を検証。
- 勤怠提出リマインドについて、対象年月の冪等性キーを使った原子的INSERTにより重複作成しないことを検証。

#### `AttendanceRecordServiceTest` (勤怠記録サービス)
- 勤務時間・残業・深夜労働・休日労働の計算ロジック、月度期間範囲（締め日）の算出、36協定超過ステータス判定（正常/超過など）、打刻順序（二重打刻防止など）のバリデーションを検証。
- `saveRecordsBatch`（月次勤怠画面の一括保存 `/attendance/saveAll` の中核ロジック）について、新規行のinsert、既存行の更新、開始・終了ともにnullの行の削除、内容に変化がない行のスキップ、休日出勤フラグのみが変化した場合の保存判定を検証。

#### `PaidLeaveBalanceServiceTest` (有給休暇残高サービス)
- 有給残高の取得、期限切れ（2年経過）の自動消滅判定、使用時の残高からの順次差し引き（古い付与分からの消化優先）などを検証。

#### `WorkScheduleClassServiceTest` (勤務スケジュールクラスサービス)
- 勤務シフト（S/A/Bクラス等）の取得、重複チェック、標準労働時間や休憩時間設定のバリデーションなどを検証。

#### `HolidayServiceTest` (祝日設定サービス)
- 祝日の自動判定（祝日の有無による稼働日判定）、祝日一覧取得、祝日マスタへの一括登録、およびCSVのアップロード解析（日付と名称の抽出）ロジックを検証。

#### `SystemSettingServiceTest` (システム設定サービス)
- 設定キーに対応する値の取得（存在時/未登録時）、設定値の更新（`upsertValue` への委譲・戻り値件数）を検証。

#### `BatchSchedulerServiceTest` / `BatchSettingServiceTest` / `AlertBatchServiceTest`
- 自動有給付与バッチ、利用終了日到達ユーザーの無効化バッチ、勤怠未提出リマインダー通知バッチ、超過残業アラート判定バッチなどのスケジュール駆動ロジック、および閾値設定のバリデーションを検証。

---

### 3.2.1 セキュリティ単体テスト

#### `CustomUserDetailsServiceTest` (Spring Security ユーザー詳細サービス)
- 未登録メールアドレス・無効化ユーザーに対する `UsernameNotFoundException` の送出を検証。
- 一般ユーザーの永久ロック中／一時ロック期限内／期限切れの各状態における `accountLocked` 判定を検証。
- 管理者ユーザーは `accountLocked`・`lockedUntil` が設定されていてもロック対象外（`isAccountNonLocked()=true`）となることを検証。
- 正常な一般ユーザーのロール（`ROLE_USER`）付与を検証。

---

### 3.3 統合テスト

#### `AttendancePeriodSettingServiceIntegrationTest`
- 互換用の旧2項目更新APIで終了日の保存が失敗した場合、トランザクションにより旧開始日キーの更新もロールバックされることを検証。このAPIと `attendance_period_start_day` は業務ロジックでは使用しない。

#### `SecurityAuthorizationIntegrationTest` (セキュリティ認可制御)
- ゲスト（未ログイン）アクセス時のログイン画面リダイレクト、一般USERによるADMIN権限画面（`/admin/*`）アクセス時の `403 Forbidden`、ADMINによる正常アクセスを検証。

#### `SettingsControllerIntegrationTest` (設定更新・認可)
- ゲスト、USER、ADMINそれぞれの `/admin/settings` に対する認可挙動の検証。
- ADMINによる設定更新時の、実際のDBへの設定保存（`SystemSettingMapper` を通じた検証）とリダイレクト動作。

#### `LeaveApplicationControllerIntegrationTest` (休暇申請・承認の結合検証)
- `/leave/apply` からの申請送信により、実際に休暇申請データがテーブルにインサートされ、初期ステータス（APPROVED/PENDING）が正しく割り振られるかを検証。

#### `AttendanceApprovalControllerIntegrationTest` (勤怠承認結合テスト)
- 提出された勤怠が承認者によって承認され、提出テーブルのステータスが実際に更新される一連の処理の流れを検証。

#### （未実装・統合環境でのみ実施）勤怠修正申請の承認→勤怠上書き結合テスト
- 修正申請（`/attendance/corrections/*`）が承認された際に、対象日の勤怠レコードが申請内容で実際に上書きされることを検証するテスト。
- ローカル開発DBを直接書き換えるリスクがあるため、本リポジトリには追加していない。統合検証用の専用環境（CI/ステージング等のDB）でのみ実施すること。

#### `WorkSchedulesIntegrationTest` / `WorkSchedulesTransitionIntegrationTest`
- 勤務スケジュール設定の適用から、カレンダー表示や日の勤務シフト登録までの結合動作を検証。

#### `DepartmentsTransitionIntegrationTest` (部署管理画面遷移結合テスト)
- 部署管理画面（`/admin/departments`）が、登録済み部署を含めて編集モーダル付きで正しく描画されることを検証。

#### `LoginAttemptServiceIntegrationTest` (ログイン試行結合テスト)
- データベースを用いた実際のログイン失敗回数のカウントとアカウントロック、アンロック挙動を検証。

#### `PaidLeaveBalanceServiceIntegrationTest` (有給残高結合テスト)
- 有給付与バッチ実行時の実際のDBトランザクションと残高計算、使用時の引き去り動作を検証。

---

### 3.4 マッパー結合テスト (DBアクセス検証)
- `UserMapperIntegrationTest` / `AttendanceSubmissionMapperIntegrationTest` / `UserNotificationMapperIntegrationTest` / `AlertBatchMapperIntegrationTest` / `AdminAnnouncementMapperIntegrationTest` / `AuditLogMapperIntegrationTest`
- MyBatisのマッパーインターフェースに対応するSQL XMLの読み込みと実行、DB側での外部キー制約、ユニーク制約の検証を含めたCRUD処理が正しく動作することを確認。
- `UserNotificationMapperIntegrationTest` では、期間キー付き通知の同一ユーザー・通知種別・期間だけが原子的に重複排除され、キーなし通知は複数登録できることを確認。
- `AlertBatchMapperIntegrationTest` では、36協定・有休消化アラートが有効かつ未削除のユーザーだけを抽出することを確認。
- `AdminAnnouncementMapperIntegrationTest` では、100件超でもページ取得件数が上限内であることと、論理削除済みお知らせの更新・再削除が更新件数0になることを確認。
- `AuditLogMapperIntegrationTest` では、重要操作の監査イベントを `audit_logs` へ追記し、同じ内容を取得できることを確認。

---

### 3.5 エンティティ単体テスト

#### `WorkScheduleClassTest` (勤務スケジュールエンティティ)
- 勤務スケジュールのドメインロジック（休憩時間の計算、実労働時間の算出など）を検証。

### 3.6 ユーティリティ・キャッシュテスト

#### `DateTimeUtilTest` (日時ユーティリティ)
- 日本時間（Asia/Tokyo）基準の日付取得・変換ロジックを検証。

#### `CacheIntegrationTest` / `CachePerformanceTest` (キャッシュ)
- キャッシュ設定の有効性（結合）およびキャッシュ利用時の性能特性を検証。

---
本ドキュメントは、新たなテストケースの追加および仕様変更に伴い順次更新されます。
