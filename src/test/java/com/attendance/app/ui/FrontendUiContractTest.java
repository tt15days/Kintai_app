package com.attendance.app.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("フロントエンドUI契約")
class FrontendUiContractTest {

    @Test
    @DisplayName("モバイルナビゲーションの境界が767pxで統一されていること")
    void mobileNavigationBreakpoint_isAligned() throws IOException {
        String inputCss = resource("/static/css/input.css");
        String generatedCss = resource("/static/tailwind.css");
        String mainJs = resource("/static/js/main.js");

        assertThat(inputCss)
                .containsPattern("@media \\(max-width: 767px\\) \\{\\s*\\.sidebar")
                .doesNotContainPattern("@media \\(max-width: 768px\\) \\{\\s*\\.sidebar");
        assertThat(generatedCss)
                .contains("@media (max-width:767px){.sidebar")
                .doesNotContain("@media (max-width:768px){.sidebar");
        assertThat(mainJs).contains("window.matchMedia('(max-width: 767px)')");
    }

    @Test
    @DisplayName("通知要素のクラスと既読後の残件セレクターが一致すること")
    void dashboardNotifications_matchRemainingItemSelector() throws IOException {
        String dashboard = resource("/templates/dashboard.html");

        assertThat(dashboard)
                .containsPattern("th:each=\"note[^>]*[\\s\\S]*?class=\"notification-item ")
                .contains("querySelectorAll('.notification-item')");
    }

    @Test
    @DisplayName("勤務クラスのタグと休憩行がアクセシブルなDOM契約を持つこと")
    void workScheduleControls_haveAccessibleDomContracts() throws IOException {
        String template = resource("/templates/admin/work-schedules.html");

        assertThat(template)
                .contains("role=\"group\" aria-labelledby=\"tagFilterLabel\"")
                .contains("document.createElement('button')")
                .contains("badge.setAttribute('aria-pressed', 'false')")
                .contains("role=\"status\" aria-live=\"polite\"")
                .contains("data-break-row")
                .contains("break-start-label")
                .contains("break-end-label")
                .contains("min-h-11 w-full sm:w-11 sm:p-0");
    }

    @Test
    @DisplayName("入れ子モーダルでもbodyまでの背景状態を保存して復元すること")
    void modalBackgroundIsolation_walksAncestorsAndRestoresState() throws IOException {
        String mainJs = resource("/static/js/main.js");

        assertThat(mainJs)
                .contains("while (branch.parentElement)")
                .contains("if (parent === document.body) break")
                .contains("inert: element.hasAttribute('inert')")
                .contains("ariaHidden: element.getAttribute('aria-hidden')")
                .contains("[...state.backgroundElements].reverse()")
                .contains("element.toggleAttribute('inert', previousInert)");
    }

    @Test
    @DisplayName("長いシステム名でもモバイルヘッダー操作を維持すること")
    void longSystemName_isBoundedAndTruncated() throws IOException {
        String settings = resource("/templates/admin/settings.html");
        String layout = resource("/templates/layout/base.html");

        assertThat(settings)
                .contains("id=\"systemName\"")
                .contains("maxlength=\"255\"")
                .contains("id=\"systemName-help\"");
        assertThat(layout)
                .contains("block min-w-0 truncate font-bold text-xl")
                .contains("min-w-0 flex-1 mx-2 truncate text-center")
                .contains("th:title=\"${systemName != null ? systemName : '勤怠管理システム'}\"");
    }

    @Test
    @DisplayName("有休アラート基準が消化日数として説明されること")
    void paidLeaveAlertThreshold_describesUsedDays() throws IOException {
        String settings = resource("/templates/admin/settings.html");

        assertThat(settings)
                .contains("有休消化日数 (アラート基準)")
                .contains("aria-describedby=\"paidLeaveDaysHelp\"")
                .contains("消化日数がこの基準未満の場合に通知します")
                .doesNotContain("有休残日数 (アラート閾値)");
    }

    @Test
    @DisplayName("アイコン専用操作が対象と目的を含む名前を持つこと")
    void iconOnlyButtons_haveAccessibleNames() throws IOException {
        String announcements = resource("/templates/admin/announcements.html");
        String departments = resource("/templates/admin/departments.html");
        String attendanceDetail = resource("/templates/admin/user-attendance-detail.html");

        assertThat(announcements)
                .contains("th:aria-label=\"|${ann.title}を編集|\"")
                .contains("th:aria-label=\"|${ann.title}を削除|\"");
        assertThat(departments).contains("th:aria-label=\"|${dept.name}を編集|\"");
        assertThat(attendanceDetail).contains("aria-label=\"表示月を検索\"");
    }

    @Test
    @DisplayName("お知らせ一覧がページングされ編集フォームを1つだけ共有すること")
    void announcements_useBoundedPaginationAndSingleEditModal() throws IOException {
        String announcements = resource("/templates/admin/announcements.html");

        assertThat(announcements)
                .contains("th:each=\"pageNumber : ${pageNumbers}\"")
                .contains("aria-label=\"お知らせ一覧のページ\"")
                .contains("id=\"announcementEditModal\"")
                .contains("id=\"announcementEditForm\"")
                .contains("name=\"displayStartDate\"")
                .contains("name=\"displayEndDate\"")
                .contains("name=\"isActive\"")
                .contains("grid grid-cols-[auto_minmax(0,1fr)]")
                .doesNotContain("th:each=\"ann : ${announcements}\">\n            <div class=\"modal")
                .doesNotContain("name=\"expiryDate\"", "name=\"active\"");
    }

    @Test
    @DisplayName("お知らせ本文の入力上限が作成・編集で一致すること")
    void announcements_messageLength_isConsistent() throws IOException {
        String announcements = resource("/templates/admin/announcements.html");

        assertThat(announcements.split("maxlength=\"2000\"", -1)).hasSize(3);
    }

    @Test
    @DisplayName("修正理由とコメントがhoverなしで全文表示されること")
    void correctionLongText_wrapsWithoutHoverDependency() throws IOException {
        String requests = resource("/templates/user/correction-request-list.html");
        String approval = resource("/templates/user/correction-approval.html");

        assertThat(requests)
                .contains("min-w-[12rem] max-w-sm whitespace-pre-wrap break-words align-top")
                .doesNotContain("max-w-xs truncate");
        assertThat(approval)
                .contains("min-w-[12rem] max-w-sm whitespace-pre-wrap break-words align-top")
                .doesNotContain("max-w-xs truncate");
    }

    @Test
    @DisplayName("状態色と操作色が白背景・白文字で十分なコントラストを持つこと")
    void semanticColors_meetTextContrastAndUseActionTokens() throws IOException {
        String inputCss = resource("/static/css/input.css");

        assertThat(contrast(cssColor(inputCss, "success"), "#ffffff")).isGreaterThanOrEqualTo(4.5);
        assertThat(contrast(cssColor(inputCss, "danger"), "#ffffff")).isGreaterThanOrEqualTo(4.5);
        assertThat(contrast(cssColor(inputCss, "warning"), "#ffffff")).isGreaterThanOrEqualTo(4.5);
        assertThat(contrast(cssColor(inputCss, "success-action"), "#ffffff")).isGreaterThanOrEqualTo(4.5);
        assertThat(contrast(cssColor(inputCss, "danger-action"), "#ffffff")).isGreaterThanOrEqualTo(4.5);
        assertThat(contrast(cssColor(inputCss, "warning-action"), "#ffffff")).isGreaterThanOrEqualTo(4.5);
        assertThat(inputCss)
                .contains("bg-success-action text-white hover:bg-success-action-hover")
                .contains("bg-danger-action text-white hover:bg-danger-action-hover")
                .contains("bg-warning-action text-white hover:bg-warning-action-hover");
    }

    @Test
    @DisplayName("低い画面でも認証フォームをスクロールして操作できること")
    void loginLayout_usesDynamicViewportAndScrollableContent() throws IOException {
        String layout = resource("/templates/layout/base.html");
        String login = resource("/templates/login.html");

        assertThat(layout)
                .contains("overflow-hidden h-dvh")
                .contains("!p-0 !overflow-y-auto flex items-start")
                .doesNotContain("h-screen w-screen");
        assertThat(login)
                .contains("min-h-full w-full items-start sm:items-center")
                .contains("p-5 sm:p-8");
    }

    @Test
    @DisplayName("検索・通知フォームの入力欄がラベルと関連付けられていること")
    void filterAndNotificationForms_haveAssociatedLabels() throws IOException {
        String attendance = resource("/templates/admin/attendance-manage.html");
        String users = resource("/templates/admin/user-list.html");
        String leaveUsage = resource("/templates/admin/leave-usage.html");
        String today = resource("/templates/user/today-status.html");
        String notifications = resource("/templates/admin/notifications.html");

        assertThat(attendance)
                .contains("for=\"attendanceYearMonth\"").contains("id=\"attendanceYearMonth\"")
                .contains("for=\"attendanceDepartment\"").contains("id=\"attendanceDepartment\"")
                .contains("for=\"attendanceKeyword\"").contains("id=\"attendanceKeyword\"");
        assertThat(users)
                .contains("for=\"userDepartment\"").contains("id=\"userDepartment\"")
                .contains("for=\"userKeyword\"").contains("id=\"userKeyword\"");
        assertThat(leaveUsage)
                .contains("for=\"leaveUsageDepartment\"").contains("id=\"leaveUsageDepartment\"")
                .contains("for=\"leaveUsageKeyword\"").contains("id=\"leaveUsageKeyword\"");
        assertThat(today)
                .contains("for=\"todayDepartment\"").contains("id=\"todayDepartment\"")
                .contains("for=\"todayClassName\"").contains("id=\"todayClassName\"")
                .contains("(empNoPrefix != null ? empNoPrefix : '') + #numbers.formatInteger(row.user.userId, 3)")
                .contains("grid grid-cols-[minmax(0,1fr)_auto]")
                .doesNotContain("row.user.empNo");
        assertThat(notifications)
                .contains("for=\"notificationMessage\"").contains("id=\"notificationMessage\"")
                .contains("<fieldset class=\"space-y-3\">")
                .contains("<legend class=\"form-label\">送信対象</legend>")
                .contains("flex flex-wrap items-center gap-x-6 gap-y-2")
                .contains("for=\"userIdSelect\"").contains("for=\"classIdSelect\"");
    }

    @Test
    @DisplayName("ログアウト完了画面が利用者の操作なしに遷移しないこと")
    void logoutSuccess_doesNotRedirectAutomatically() throws IOException {
        String logout = resource("/templates/logout-success.html");

        assertThat(logout)
                .contains("th:href=\"@{/login}\"")
                .doesNotContain("startCountdown", "setInterval", "window.location.href", "id=\"countdown\"");
    }

    @Test
    @DisplayName("主要管理テーブルが空状態と絞り込み解除手段を示すこと")
    void adminTables_showEmptyStatesAndFilterReset() throws IOException {
        String attendance = resource("/templates/admin/attendance-manage.html");
        String leaveUsage = resource("/templates/admin/leave-usage.html");
        String article36 = resource("/templates/admin/article36-dashboard.html");

        assertThat(attendance)
                .contains("users == null or #lists.isEmpty(users)")
                .contains("条件に一致する従業員がいません")
                .contains("th:href=\"@{/admin/attendance(yearMonth=${yearMonth})}\"")
                .contains(">条件を解除</a>");
        assertThat(leaveUsage)
                .contains("users == null or #lists.isEmpty(users)")
                .contains("条件に一致する従業員がいません")
                .contains("th:href=\"@{/admin/leave-usage}\"")
                .contains(">条件を解除</a>");
        assertThat(article36)
                .contains("users == null or #lists.isEmpty(users)")
                .contains("colspan=\"6\"")
                .contains("表示対象の従業員がいません");
    }

    @Test
    @DisplayName("管理画面の成功通知と失敗通知が適切なライブ領域を使うこと")
    void adminFlashMessages_useStatusForSuccessAndAlertForFailure() throws IOException {
        String dashboard = resource("/templates/admin/dashboard.html");
        String users = resource("/templates/admin/user-list.html");
        String notifications = resource("/templates/admin/notifications.html");
        String workSchedules = resource("/templates/admin/work-schedules.html");

        for (String template : new String[]{dashboard, users, notifications, workSchedules}) {
            assertThat(template)
                    .containsPattern("th:if=\"\\$\\{successMessage[^\"]*}\"[^>]*role=\"status\"[^>]*aria-live=\"polite\"")
                    .containsPattern("th:if=\"\\$\\{errorMessage[^\"]*}\"[^>]*role=\"alert\"[^>]*aria-live=\"assertive\"");
        }
    }

    @Test
    @DisplayName("パスワード再設定の利用可能な手順を案内すること")
    void loginPasswordHelp_doesNotExposeDeadLink() throws IOException {
        String login = resource("/templates/login.html");

        assertThat(login)
                .contains("パスワードを忘れた場合は、システム管理者へ初期化を依頼してください。")
                .doesNotContain("href=\"#\"");
    }

    @Test
    @DisplayName("新規ユーザーの有休初期残高と次回付与日数を説明すること")
    void paidLeaveGrantSetting_explainsFixedInitialBalance() throws IOException {
        String userCreate = resource("/templates/admin/user-create.html");
        String settings = resource("/templates/admin/settings.html");

        assertThat(userCreate).contains("新規ユーザーの有休初期残高と次回付与日数は、ともに10日で作成されます");
        assertThat(settings).contains("従業員ごとに設定された有給付与日数を自動付与します");
    }

    @Test
    @DisplayName("モバイルの操作対象が44px以上であること")
    void mobileActions_haveMinimumTouchTargets() throws IOException {
        String inputCss = resource("/static/css/input.css");
        String announcements = resource("/templates/admin/announcements.html");
        String departments = resource("/templates/admin/departments.html");
        String settings = resource("/templates/admin/settings.html");

        assertThat(inputCss)
                .contains("@apply inline-flex min-h-11 items-center justify-center")
                .contains("button[aria-label=\"閉じる\"]")
                .contains("@apply min-h-11 min-w-11");
        assertThat(announcements).contains("btn btn-primary min-w-11", "btn btn-danger min-w-11");
        assertThat(departments).contains("btn btn-primary min-w-11");
        assertThat(settings).contains("btn btn-danger min-w-11");

        for (String template : new String[]{
                announcements,
                departments,
                settings,
                resource("/templates/admin/dashboard.html"),
                resource("/templates/admin/notifications.html"),
                resource("/templates/admin/work-schedules.html"),
                resource("/templates/admin/user-attendance-detail.html"),
                resource("/templates/attendance/input.html"),
                resource("/templates/user/attendance-approval.html"),
                resource("/templates/user/profile.html")}) {
            assertThat(template)
                    .doesNotContainPattern("<button(?![^>]*icon-button)[^>]*aria-label=\"閉じる\"");
        }
    }

    @Test
    @DisplayName("320px・390px相当の狭い画面で主領域が横方向へ膨張しないこと")
    void narrowMobileLayout_constrainsFlexAndScrollsTables() throws IOException {
        String layout = resource("/templates/layout/base.html");
        String inputCss = resource("/static/css/input.css");

        assertThat(layout)
                .contains("min-w-0 flex-1 flex flex-col overflow-hidden")
                .contains("min-w-0 w-full flex-1")
                .contains("min-w-0 flex flex-col min-h-full");
        assertThat(inputCss)
                .contains(".table-container")
                .contains("@apply overflow-x-auto");
    }

    @Test
    @DisplayName("動きを減らす端末設定をアニメーションへ反映すること")
    void animations_respectReducedMotionPreference() throws IOException {
        String inputCss = resource("/static/css/input.css");

        assertThat(inputCss)
                .contains("@media (prefers-reduced-motion: reduce)")
                .contains("animation-duration: 0.01ms !important")
                .contains("animation-iteration-count: 1 !important")
                .contains("transition-duration: 0.01ms !important")
                .contains("scroll-behavior: auto !important");
    }

    @Test
    @DisplayName("モバイルの入力欄と選択欄が44px以上であること")
    void mobileFormControls_haveMinimumTouchHeight() throws IOException {
        String inputCss = resource("/static/css/input.css");

        assertThat(inputCss)
                .containsPattern("\\.form-input \\{\\s*@apply min-h-11")
                .containsPattern("\\.form-select \\{\\s*@apply min-h-11");
    }

    @Test
    @DisplayName("共通ボタンがキーボードフォーカスを明示すること")
    void commonButtons_haveVisibleKeyboardFocus() throws IOException {
        String inputCss = resource("/static/css/input.css");

        assertThat(inputCss)
                .containsPattern("\\.btn \\{[^}]*focus-visible:ring-2[^}]*focus-visible:ring-primary[^}]*focus-visible:ring-offset-2")
                .containsPattern("\\.icon-button \\{[^}]*focus-visible:ring-2[^}]*focus-visible:ring-primary[^}]*focus-visible:ring-offset-2");
    }

    @Test
    @DisplayName("iOSの入力フォーカス時に意図しない自動拡大を起こさないこと")
    void mobileFormControls_useSixteenPixelText() throws IOException {
        String inputCss = resource("/static/css/input.css");

        assertThat(inputCss)
                .containsPattern("\\.form-input \\{[^}]*text-base md:text-sm")
                .containsPattern("\\.form-select \\{[^}]*text-base md:text-sm")
                .containsPattern("\\.form-textarea \\{[^}]*text-base md:text-sm");

        for (String template : new String[]{
                resource("/templates/user/attendance.html"),
                resource("/templates/user/correction-approval.html"),
                resource("/templates/admin/user-attendance-detail.html"),
                resource("/templates/admin/settings.html")}) {
            assertThat(template)
                    .doesNotContain("text-xs md:text-sm")
                    .doesNotContainPattern("form-(?:input|select|textarea)[^\"]* text-sm(?:\\s|\")");
        }
    }

    @Test
    @DisplayName("モバイルの主要なradio・checkboxラベルが44px以上であること")
    void mobileChoiceLabels_haveMinimumTouchHeight() throws IOException {
        String inputCss = resource("/static/css/input.css");
        String notifications = resource("/templates/admin/notifications.html");
        String leaveCreate = resource("/templates/user/leave-create.html");
        String attendanceInput = resource("/templates/attendance/input.html");
        String userDetail = resource("/templates/admin/user-detail.html");

        assertThat(inputCss).containsPattern("\\.choice-label \\{\\s*@apply inline-flex min-h-11");
        assertThat(notifications).contains("<label class=\"choice-label\">");
        assertThat(leaveCreate).contains("<label class=\"choice-label gap-2");
        assertThat(attendanceInput).contains("<label class=\"choice-label relative select-none\">");
        assertThat(userDetail).contains("<label class=\"choice-label text-sm font-semibold text-txt-primary\"");
    }

    @Test
    @DisplayName("有休付与日の2月は28日までに制限されること")
    void paidLeaveGrantDate_rejectsLeapDay() throws IOException {
        String settings = resource("/templates/admin/settings.html");

        assertThat(settings).contains("2月は28日まで設定できます。");
    }

    @Test
    @DisplayName("反復ナビゲーションを飛ばして本文へ移動できること")
    void baseLayout_hasKeyboardSkipLink() throws IOException {
        String layout = resource("/templates/layout/base.html");

        assertThat(layout)
                .contains("href=\"#main-content\"")
                .contains("focus:not-sr-only")
                .contains("本文へ移動")
                .contains("<main id=\"main-content\" tabindex=\"-1\"");
    }

    @Test
    @DisplayName("勤怠期間設定は締め日だけを入力し連続した期間を説明すること")
    void attendancePeriodSetting_usesClosingDayOnly() throws IOException {
        String settings = resource("/templates/admin/settings.html");

        assertThat(settings)
                .contains("前月締め日の翌日〜当月締め日")
                .contains("<label for=\"endDay\" class=\"form-label\">締め日（当月）</label>")
                .contains("id=\"endDay\" name=\"endDay\"")
                .doesNotContain("id=\"startDay\"", "name=\"startDay\"", "attendancePeriodStartDay");
    }

    @Test
    @DisplayName("モーダルがスクロール位置によらず画面全体を覆うこと")
    void modalOverlays_areFixedToViewport() throws IOException {
        // fixed/inset-0等の配置指定は .modal コンポーネント定義(input.css)に集約されており、
        // 各テンプレートは class="modal" のみを付与する（重複ユーティリティの排除）。
        for (String template : new String[]{
                resource("/templates/admin/work-schedules.html"),
                resource("/templates/admin/departments.html"),
                resource("/templates/user/attendance-approval.html"),
                resource("/templates/attendance/input.html")}) {
            assertThat(template)
                    .contains("class=\"modal hidden\"")
                    .doesNotContain("modal absolute inset-0");
        }

        String announcements = resource("/templates/admin/announcements.html");
        assertThat(announcements)
                .contains("class=\"modal announcement-modal hidden\"")
                .doesNotContain("modal announcement-modal absolute inset-0");

        String styles = resource("/static/css/input.css");
        assertThat(styles)
                .contains(".modal {")
                .contains("@apply fixed inset-0 z-[2000] items-start justify-center overflow-y-auto bg-black/60 backdrop-blur-sm p-4 sm:p-8 transition-all duration-300;")
                .contains(".modal > .flex {")
                .contains("@apply w-full min-h-full shrink-0;");
    }

    @Test
    @DisplayName("エラー画面の戻る操作がjavascript URLに依存しないこと")
    void errorPages_useButtonForHistoryBackAndKeepDashboardFallback() throws IOException {
        for (String template : new String[]{
                resource("/templates/error/400.html"),
                resource("/templates/error/500.html"),
                resource("/templates/error/data-conflict.html"),
                resource("/templates/error/lock-error.html")}) {
            assertThat(template)
                    .contains("<button type=\"button\" data-history-back")
                    .contains("th:href=\"@{/dashboard}\"")
                    .doesNotContain("javascript:history.back()");
        }
        assertThat(resource("/static/js/main.js"))
                .contains("document.querySelectorAll('[data-history-back]')")
                .contains("window.history.back()");
    }

    private String cssColor(String css, String token) {
        Matcher matcher = Pattern.compile("--color-" + Pattern.quote(token) + ":\\s*(#[0-9a-fA-F]{6})").matcher(css);
        assertThat(matcher.find()).as("CSS color token %s", token).isTrue();
        return matcher.group(1);
    }

    private double contrast(String first, String second) {
        double light = Math.max(luminance(first), luminance(second));
        double dark = Math.min(luminance(first), luminance(second));
        return (light + 0.05) / (dark + 0.05);
    }

    private double luminance(String hex) {
        double red = linear(Integer.parseInt(hex.substring(1, 3), 16) / 255.0);
        double green = linear(Integer.parseInt(hex.substring(3, 5), 16) / 255.0);
        double blue = linear(Integer.parseInt(hex.substring(5, 7), 16) / 255.0);
        return 0.2126 * red + 0.7152 * green + 0.0722 * blue;
    }

    private double linear(double channel) {
        return channel <= 0.04045 ? channel / 12.92 : Math.pow((channel + 0.055) / 1.055, 2.4);
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
