package com.attendance.app.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
@DisplayName("GlobalExceptionHandlerTest")
class GlobalExceptionHandlerTest {

    @InjectMocks
    private GlobalExceptionHandler exceptionHandler;

    @Test
    @DisplayName("handleAccessDenied - 認可エラー時にアクセス拒否画面のビュー名 'access-denied' を返すこと")
    void testHandleAccessDenied() {
        String view = exceptionHandler.handleAccessDenied();
        assertEquals("access-denied", view);
    }

    @Test
    @DisplayName("handleException - 予期しない例外時に 'error/500' を返すこと")
    void testHandleException() {
        String view = exceptionHandler.handleException(new RuntimeException("予期しないエラー"));
        assertEquals("error/500", view);
    }
}
