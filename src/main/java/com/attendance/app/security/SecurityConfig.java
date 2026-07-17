package com.attendance.app.security;

import com.attendance.app.entity.User;
import com.attendance.app.entity.AuditEventType;
import com.attendance.app.service.AuditLogService;
import com.attendance.app.service.LoginAttemptResult;
import com.attendance.app.service.LoginAttemptService;
import com.attendance.app.service.UserService;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.session.HttpSessionEventPublisher;

import lombok.extern.slf4j.Slf4j;

/**
 * Security Configuration - セキュリティ設定
 *
 * Spring Security の設定を行う。以下の設定を含む:
 * - HTTP セキュリティ設定（認証/認可）
 * - パスワードエンコーディング設定
 *
 * 注意: 循環参照を避けるため、UserDetailsService への直接的な依存を避け、
 * Spring が自動的に解決するようにしている。
 */
@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    /**
     * SecurityFilterChain Bean - HTTP セキュリティルール定義
     *
     * 以下のルールを設定:
     * - ログインページ、ダッシュボード、admin ページへのアクセス許可
     * - その他のパスは認証が必要
     * - フォームベースのログインを使用
     * - CSRF 保護を有効化（Thymeleaf テンプレートで自動的にトークンを処理）
     *
     * @param http HttpSecurity
     * @param userService ユーザーサービス
     * @param loginAttemptService ログイン試行サービス
     * @return SecurityFilterChain
     * @throws Exception セキュリティ設定時の例外
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, UserService userService, LoginAttemptService loginAttemptService, AuditLogService auditLogService, SessionRegistry sessionRegistry) throws Exception {
        http
                .addFilterAfter(new MdcFilter(), SecurityContextHolderFilter.class)
                .authorizeHttpRequests(authorize -> authorize
                        // ログインページ、ログアウト、ログアウト完了ページとリソースはアクセス許可
                        // /error は Spring Security 6 で ERROR ディスパッチも認可対象となるため明示的に許可（未認証時の認証リダイレクト防止）
                    .requestMatchers("/login", "/logout", "/logout-success", "/access-denied", "/error", "/tailwind.css", "/css/**", "/js/**", "/images/**").permitAll()
                        // ヘルスチェックはコンテナ/監視から未認証で到達可能にする。その他の actuator は管理者限定
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        // 上記以外はすべて認証が必要
                        .anyRequest().authenticated()
                )
                // アカウント無効化・削除・ロール変更・パスワード変更時に既存セッションを失効させるため
                // SessionRegistry へセッションを登録する。同時セッションは1つに制限（新ログイン優先）
                .sessionManagement(session -> session
                        .maximumSessions(1)
                        .maxSessionsPreventsLogin(false)
                        .sessionRegistry(sessionRegistry)
                        .expiredUrl("/login?expired=true")
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .usernameParameter("email")
                        .passwordParameter("password")
                        .successHandler((request, response, authentication) -> {
                            String email = authentication.getName();
                            String contextPath = request.getContextPath();

                            try {
                                loginAttemptService.handleSuccess(email);
                                User user = userService.getUserByEmail(email).orElse(null);
                                if (user == null) {
                                    response.sendRedirect(contextPath + "/dashboard");
                                    return;
                                }

                                userService.updateLastLogin(user.getUserId());

                                log.info("ログイン成功: userId={}", user.getUserId());
                                auditLogService.recordUserEvent(AuditEventType.LOGIN_SUCCESS, user.getUserId(), user.getUserId(), "ログイン成功");

                                if (Boolean.TRUE.equals(user.getPasswordResetRequired())) {
                                    response.sendRedirect(contextPath + "/dashboard/change-password?required=true");
                                    return;
                                }
                            } catch (Exception e) {
                                log.error("ログイン成功時処理に失敗", e);
                            }

                            response.sendRedirect(contextPath + "/dashboard");
                        })
                        .failureHandler((request, response, exception) -> {
                            String failEmail = request.getParameter("email");
                            String contextPath = request.getContextPath();

                            log.warn("ログイン失敗: email={}, 原因={}", maskEmail(failEmail), exception.getMessage(), exception);
                            try {
                                auditLogService.recordUserEvent(AuditEventType.LOGIN_FAILED, null, null, "ログイン失敗");
                            } catch (Exception e) {
                                log.error("監査ログ(ログイン失敗)の記録に失敗", e);
                            }

                            if (exception instanceof LockedException) {
                                // ロック中でも試行カウントをインクリメント（一時ロック → 永続ロックへの移行を可能にする）
                                if (failEmail != null && !failEmail.isBlank()) {
                                    try {
                                        LoginAttemptResult lockResult = loginAttemptService.handleFailure(failEmail);
                                        if (lockResult == LoginAttemptResult.LOCKED) {
                                            response.sendRedirect(contextPath + "/login?error=locked");
                                        } else {
                                            response.sendRedirect(contextPath + "/login?error=templock");
                                        }
                                    } catch (Exception ex) {
                                        log.error("ロック中試行のカウント更新に失敗", ex);
                                        response.sendRedirect(contextPath + "/login?error=templock");
                                    }
                                } else {
                                    response.sendRedirect(contextPath + "/login?error=templock");
                                }
                            } else {
                                if (failEmail != null && !failEmail.isBlank()) {
                                    try {
                                        LoginAttemptResult result = loginAttemptService.handleFailure(failEmail);
                                        if (result == LoginAttemptResult.LOCKED) {
                                            response.sendRedirect(contextPath + "/login?error=locked");
                                        } else if (result == LoginAttemptResult.TEMP_LOCKED) {
                                            response.sendRedirect(contextPath + "/login?error=templock");
                                        } else {
                                            response.sendRedirect(contextPath + "/login?error=true");
                                        }
                                    } catch (Exception ex) {
                                        log.error("ログイン試行記録に失敗", ex);
                                        response.sendRedirect(contextPath + "/login?error=true");
                                    }
                                } else {
                                    response.sendRedirect(contextPath + "/login?error=true");
                                }
                            }
                        })
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .addLogoutHandler((request, response, authentication) -> {
                            if (authentication != null) {
                                String email = authentication.getName();
                                log.info("ログアウト: email={}", maskEmail(email));
                                try {
                                    auditLogService.recordUserEvent(AuditEventType.LOGOUT, null, null, "ログアウト");
                                } catch (Exception e) {
                                    log.error("監査ログ(ログアウト)の記録に失敗", e);
                                }
                            }
                        })
                        .logoutSuccessUrl("/logout-success")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll()
                )
                .exceptionHandling(exception -> exception
                    .accessDeniedPage("/access-denied")
                )
                // CSP: script-src は既存テンプレートの onclick/onchange/onsubmit 属性ハンドラ（多数）が
                // 動作するため 'unsafe-inline' を許容する。インラインスクリプト注入そのものは防げないが、
                // object-src/base-uri/form-action/frame-ancestors の制限と、img-src/connect-src を self に
                // 限定することで XSS 成立後の外部データ持ち出し・フォーム乗っ取り・クリックジャッキングを防ぐ。
                // 'unsafe-inline' を外すには全テンプレートの onclick 属性を addEventListener へ移行する
                // 別途のフロントエンド改修が必要（issue化して追跡）。
                .headers(headers -> headers.contentSecurityPolicy(csp -> csp.policyDirectives(
                        "default-src 'self'; "
                                + "script-src 'self' 'unsafe-inline'; "
                                + "style-src 'self' https://fonts.googleapis.com https://cdnjs.cloudflare.com; "
                                + "font-src 'self' https://fonts.gstatic.com https://cdnjs.cloudflare.com; "
                                + "img-src 'self'; "
                                + "connect-src 'self'; "
                                + "object-src 'none'; "
                                + "base-uri 'self'; "
                                + "form-action 'self'; "
                                + "frame-ancestors 'none'"
                )));
                // CSRF 保護はデフォルトで有効化（Spring Security 6.4以降）
                // Thymeleaf テンプレートで自動的に _csrf トークンが処理されます

        return http.build();
    }

    /**
     * AuthenticationManager Bean - 認証処理を管理
     *
     * CustomUserDetailsService と BCryptPasswordEncoder を使用して
     * ユーザー認証を行う。
     *
     * @param authenticationConfiguration 認証設定
     * @return AuthenticationManager
     * @throws Exception 認証マネージャー生成時の例外
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        log.debug("AuthenticationManager Bean を初期化しています");
        return authenticationConfiguration.getAuthenticationManager();
    }

    /**
     * パスワードエンコーディング
     * 
     * BCryptPasswordEncoder を使用してパスワードをハッシュ化する。
     * このBeanは自動的にSpring Securityと各Serviceで使用される。
     *
     * @return PasswordEncoder パスワードエンコーダ
     */
    /**
     * SessionRegistry Bean - アクティブセッションの追跡
     *
     * ユーザー無効化・削除・ロール変更・パスワード変更時のセッション失効
     * （SessionInvalidationService）と同時セッション制御に使用する。
     * インメモリ実装のため単一インスタンス構成が前提。
     *
     * @return SessionRegistry
     */
    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    /**
     * セッションの生成・破棄イベントを SessionRegistry へ伝搬させる。
     *
     * @return HttpSessionEventPublisher
     */
    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        log.debug("PasswordEncoder Bean を初期化しています");
        return new BCryptPasswordEncoder(10); // 強度10でエンコード
    }

    /**
     * ログに出力するメールアドレスの個人情報漏洩を抑えるため、先頭1文字以外をマスクします。
     */
    private static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return email;
        }
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) {
            return "*".repeat(email.length());
        }
        return email.charAt(0) + "***" + email.substring(atIndex);
    }
}
