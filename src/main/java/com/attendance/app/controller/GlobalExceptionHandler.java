package com.attendance.app.controller;

import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import lombok.extern.slf4j.Slf4j;

/**
 * Global Exception Handler
 * アプリケーション全体の例外ハンドリングを行うコントローラーアドバイスクラス。
 *
 * 主な責務:
 * - 画面遷移時等の例外（認可エラーなど）をキャッチし、適切なエラー画面へ遷移させる
 */
@Slf4j
@ControllerAdvice
@Controller
public class GlobalExceptionHandler {

    /**
     * 認可エラー（アクセス拒否）が発生した場合のハンドリングを行います。
     *
     * @return アクセス拒否画面のテンプレート名
     */
    @ExceptionHandler({AuthorizationDeniedException.class, AccessDeniedException.class})
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String handleAccessDenied() {
        return "access-denied";
    }

    /**
     * 悲観的ロックによるタイムアウト、またはデッドロックが発生した場合のハンドリングを行います。
     *
     * @return ロックエラー画面のテンプレート名
     */
    @ExceptionHandler({
        CannotAcquireLockException.class,
        PessimisticLockingFailureException.class
    })
    @ResponseStatus(HttpStatus.CONFLICT)
    public String handleLockTimeout() {
        return "error/lock-error";
    }

    /**
     * 予期せぬ例外が発生した場合のフォールバック処理を行います。
     * Whitelabel Error Pageの表示を防ぎます。
     *
     * @param e 発生した例外
     * @return 汎用エラー画面のテンプレート名
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleException(Exception e) {
        log.error("予期しない例外が発生しました", e);
        return "error/500";
    }
}