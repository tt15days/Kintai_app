package com.attendance.app.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

/**
 * Login Controller - ログイン画面表示とリダイレクト
 *
 * 主な責務:
 * - ログイン画面表示
 * - ログイン/ログアウト後のリダイレクト（Spring Security が自動処理）
 * - ホーム画面へのリダイレクト
 *
 * 注意:
 * - ログイン認証処理は SecurityConfig で設定した FormLogin が自動処理
 * - ログアウト処理は SecurityConfig で設定した Logout が自動処理
 * - LoginController は画面表示とリダイレクトの制御のみ
 */
@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/")
public class LoginController {

    private static final String LOGIN_VIEW = "login";
    private static final String LOGIN_ERROR_REDIRECT = "redirect:/login?error=true";
    private static final String DASHBOARD_REDIRECT = "redirect:/dashboard";
    private static final String LOGIN_REDIRECT = "redirect:/login";

    /**
     * ログイン画面を表示します。
     * 
     * ログイン失敗時、エラーメッセージをモデルに追加します。
     *
     * @param error ログイン失敗時のエラーパラメータ
     * @param expired セッション失効時のパラメータ（別端末ログイン・管理者操作等による強制失効）
     * @param logout ログアウト完了時のパラメータ
     * @param model Spring MVC のモデル
     * @return テンプレート名 (login)
     */
    @GetMapping("/login")
    public String showLoginForm(
            @RequestParam(required = false) String error,
            @RequestParam(required = false) String expired,
            @RequestParam(required = false) String logout,
            Model model) {
        if (error != null) {
            if ("locked".equals(error)) {
                model.addAttribute("error", "アカウントがロックされています。システム管理者にお問い合わせください。");
                log.warn("ロックアカウントへのログイン試行を検知しました");
            } else if ("templock".equals(error)) {
                model.addAttribute("warning", "ログイン失敗が続いています。30分後に再試行してください。このまま失敗が続くとアカウントがロックされます。");
                log.warn("一時ロック中のログイン試行を検知しました");
            } else {
                model.addAttribute("error", "メールアドレスまたはパスワードが正しくありません");
                log.warn("ログインに失敗");
            }
        }
        if (expired != null) {
            model.addAttribute("message", "セッションが無効になりました。再度ログインしてください。");
            log.info("失効セッションからのアクセスを検知しました");
        }
        if (logout != null) {
            model.addAttribute("message", "ログアウトしました");
            log.info("ログアウト完了");
        }
        return LOGIN_VIEW;
    }

    @GetMapping("/access-denied")
    public String showAccessDeniedPage() {
        return "access-denied";
    }

    /**
     * ログインページが POST リクエストを受け取る場合の処理
     * 
     * 通常は Spring Security が /login の POST を処理するため、
     * このメソッドが呼ばれることはありません。
     * ただし、設定エラーの場合の安全弁として定義しています。
     *
     * @return エラーメッセージ
     */
    @PostMapping("/login")
    public String handleLoginPost() {
        log.error("{}: {}", "ログインフォームPOST処理に失敗", "Spring Security設定を確認してください");
        return LOGIN_ERROR_REDIRECT;
    }

    /**
     * ホーム画面へリダイレクトします。
     * 
     * ルートパス (/) にアクセスされた場合、認証済みユーザーはダッシュボードに、
     * 未認証ユーザーはログインページにリダイレクトします。
     *
     * @return ダッシュボードへリダイレクト、または未認証の場合はログインページへ
     */
    @GetMapping
    public String index() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !authentication.getPrincipal().equals("anonymousUser")) {
            return DASHBOARD_REDIRECT;
        }
        return LOGIN_REDIRECT;
    }
}