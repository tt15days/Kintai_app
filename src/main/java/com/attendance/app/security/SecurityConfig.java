package com.attendance.app.security;

import com.attendance.app.entity.User;
import com.attendance.app.service.LoginAttemptResult;
import com.attendance.app.service.LoginAttemptService;
import com.attendance.app.service.UserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

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
    public SecurityFilterChain securityFilterChain(HttpSecurity http, UserService userService, LoginAttemptService loginAttemptService) throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize
                        // ログインページ、ログアウト、ログアウト完了ページとリソースはアクセス許可
                    .requestMatchers("/login", "/logout", "/logout-success", "/access-denied", "/tailwind.css", "/css/**", "/js/**", "/images/**").permitAll()
                        // 上記以外はすべて認証が必要
                        .anyRequest().authenticated()
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

                                if (Boolean.TRUE.equals(user.getPasswordResetRequired())) {
                                    response.sendRedirect(contextPath + "/dashboard/change-password?required=true");
                                    return;
                                }
                            } catch (Exception e) {
                                log.error("ログイン成功時処理に失敗: {}", e.getMessage());
                            }

                            response.sendRedirect(contextPath + "/dashboard");
                        })
                        .failureHandler((request, response, exception) -> {
                            String failEmail = request.getParameter("email");
                            String contextPath = request.getContextPath();

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
                                        log.error("ロック中試行のカウント更新に失敗: {}", ex.getMessage());
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
                                        log.error("ログイン試行記録に失敗: {}", ex.getMessage());
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
                        .logoutSuccessUrl("/logout-success")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll()
                )
                .exceptionHandling(exception -> exception
                    .accessDeniedPage("/access-denied")
                );
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
    @Bean
    public PasswordEncoder passwordEncoder() {
        log.debug("PasswordEncoder Bean を初期化しています");
        return new BCryptPasswordEncoder(10); // 強度10でエンコード
    }
}