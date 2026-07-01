# 勤怠管理システム プロジェクト概要 (Project Overview)

## 1. このSpringBootプロジェクトについて
本プロジェクトは、Java 25 および Spring Boot 4.1.0 をベースに構築された「勤怠管理システム (Attendance Management System)」です。
従業員の出退勤打刻、有給休暇申請、残業申請、および月次勤怠の承認ワークフローなど、勤怠管理に関わる一連の業務をシステム化することを目的としています。
現在のバージョンは `0.0.8` です。

### 主な採用技術とアーキテクチャ
- **バックエンド**: Java 25, Spring Boot 4.1.0
- **データベース**: PostgreSQL
- **O/Rマッパー**: MyBatis (XMLによるSQLマッピング), mybatis-spring-boot-starter 4.0.1
- **DBマイグレーション**: Flyway (起動時に `db/migration` 配下のスクリプトを自動実行)
- **フロントエンド**: Thymeleaf (テンプレートエンジン), Tailwind CSS (`tailwind-maven-plugin` によりビルド時に `src/main/resources/static/tailwind.css` を自動生成)
- **セキュリティ**: Spring Security (ログイン認証・権限管理)
- **ユーティリティ**: Lombok, Gson, Apache Commons CSV 1.10.0, Jackson JSR310

アーキテクチャとしては、MVC (Model-View-Controller) パターンを採用し、`Controller` で画面からのリクエストを受け付け、ビジネスロジックを実行後、Thymeleaf を用いて画面 (`src/main/resources/templates`) をレンダリングする構成となっています。

## 2. Mavenでの利用の方法について
本プロジェクトは Maven によってビルドおよび依存関係の管理が行われています。以下のコマンドで開発・実行を行います。

### アプリケーションの起動 (開発時)
```bash
mvn spring-boot:run
```
※ `pom.xml` の設定により、デフォルトで `local` プロファイルがアクティブになります（これにより `application-local.yml` が読み込まれます）。

### ビルド・パッケージング
```bash
mvn clean package -P local
```
上記コマンドを実行すると、ソースコードのコンパイルとテストが行われ、`target/` ディレクトリ配下に実行可能なJARファイル（`attendance-app-0.0.8.jar`）が生成されます。
プロダクション環境等で実行する場合は、このJARファイルを利用します（例: `java -jar target/attendance-app-0.0.8.jar`）。

テストをスキップしてビルドする場合:
```bash
mvn clean package -DskipTests -P local
```

### Mavenプロファイル
| プロファイルID | 用途 | デフォルト |
|---|---|---|
| `local` | ローカル開発用（`application-local.yml` を使用） | ✅（activeByDefault） |
| `release` | 本番リリース用（`application-release.yml` を使用、テストスキップ） | - |

### Tailwind CSSの設定とビルドについて
本プロジェクトでは、リッチでモダンなUIデザイン（グラスモフィズム等）を実現するために Tailwind CSS を採用しています。

#### ビルドと自動生成
`tailwind-maven-plugin` (io.github.4ndreidev, v1.1.0) を使用しており、`mvn compile` 以降のフェーズで `src/main/resources/static/css/input.css` を入力として `src/main/resources/static/tailwind.css` が自動生成されます。

#### `tailwind.config.js` の設定内容
プロジェクトルートの `tailwind.config.js` には、以下のカスタムテーマ設定が定義されており、システム全体で統一されたデザインシステムを提供しています。

- **コンテンツスキャン対象 (`content`)**:
  - `./src/main/resources/templates/**/*.html` (すべての Thymeleaf テンプレート)
- **カスタムカラー (`theme.extend.colors`)**:
  - `primary`: DEFAULT `#2563eb` (Blue), hover `#1d4ed8`, light `rgba(37, 99, 235, 0.1)`
  - `accent`: DEFAULT `#7c3aed` (Purple), light `rgba(124, 58, 237, 0.1)`
  - `success`: DEFAULT `#10b981` (Green), light `rgba(16, 185, 129, 0.2)`
  - `danger`: DEFAULT `#ef4444` (Red), light `rgba(239, 68, 68, 0.2)`
  - `warning`: DEFAULT `#f59e0b` (Amber), light `rgba(245, 158, 11, 0.2)`
  - `glass` (グラスモフィズム用背景・境界線):
    - `bg`: `rgba(255, 255, 255, 0.7)`
    - `border`: `rgba(0, 0, 0, 0.08)`
    - `hover`: `rgba(0, 0, 0, 0.03)`
    - `nav-hover`: `rgba(0, 0, 0, 0.05)`
    - `input`: `rgba(255, 255, 255, 0.9)`
  - `txt` (テキスト用ベースカラー):
    - `primary`: `#020617` (Slate 950)
    - `secondary`: `#334155` (Slate 700)
- **カスタムフォント (`theme.extend.fontFamily`)**:
  - `sans`: `Inter`, `sans-serif`
  - `mono`: `JetBrains Mono`, `monospace`
- **カスタムシャドウ (`theme.extend.boxShadow`)**:
  - `glass`: グラスモフィズム用の柔らかい影
  - `glass-heavy`: より深い立体感を出す影
  - `primary` / `primary-hover`: プライマリボタン等の光彩効果付きの影
- **背景ぼかし (`theme.extend.backdropBlur`)**:
  - `glass`: `12px`
  - `glass-heavy`: `16px`
- **角丸 (`theme.extend.borderRadius`)**:
  - `panel`: `16px`
  - `panel-heavy`: `24px`
- **アニメーション (`theme.extend.keyframes` & `animation`)**:
  - `fade-in`: 画面遷移やモーダル表示時のフェードイン（fadeIn 0.5s ease forwards）
  - `pulse-slow`: ゆっくりとしたパルス効果（pulse 2s infinite）

### データベースについて
Flyway が導入されているため、アプリケーション起動時に `src/main/resources/db/migration` フォルダ内のSQLスクリプトが自動的に実行され、データベースの初期化やテーブルのマイグレーションが行われます。開発環境（ローカル）の場合は、必要に応じて `src/main/resources/db/sample/V2__Sample_Data.sql` も自動的に実行され、テスト用のサンプルデータが投入される設定になっています。
