package com.attendance.app.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Logout Controller - ログアウト完了画面
 *
 * 主な責務:
 * - ログアウト完了画面の表示
 */
@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/logout-success")
public class LogoutController {

    private static final String LOGOUT_SUCCESS_VIEW = "logout-success";

    /**
     * ログアウト完了画面を表示します。
     *
     * @return テンプレート名 (logout-success)
     */
    @GetMapping
    public String showLogoutSuccess() {
        log.info("ログアウト完了画面を表示");
        return LOGOUT_SUCCESS_VIEW;
    }
}
