package com.attendance.app.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
@DisplayName("LogoutControllerTest")
class LogoutControllerTest {

    @InjectMocks
    private LogoutController controller;

    @Test
    @DisplayName("showLogoutSuccess - ログアウト完了画面のビュー名 'logout-success' を返すこと")
    void testShowLogoutSuccess() {
        String view = controller.showLogoutSuccess();
        assertEquals("logout-success", view);
    }
}
