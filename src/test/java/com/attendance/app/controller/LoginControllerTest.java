package com.attendance.app.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ExtendedModelMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
public class LoginControllerTest {

    @InjectMocks
    private LoginController controller;

    @Test
    void testShowLoginForm() {
        ExtendedModelMap model = new ExtendedModelMap();
        String view = controller.showLoginForm(null, null, model);
        assertEquals("login", view);
        assertEquals(null, model.getAttribute("error"));
        assertEquals(null, model.getAttribute("message"));
    }

    @Test
    void testShowLoginForm_WithError() {
        ExtendedModelMap model = new ExtendedModelMap();
        String view = controller.showLoginForm("true", null, model);
        assertEquals("login", view);
        assertEquals("メールアドレスまたはパスワードが正しくありません", model.getAttribute("error"));
    }

    @Test
    void testShowLoginForm_WithLogout() {
        ExtendedModelMap model = new ExtendedModelMap();
        String view = controller.showLoginForm(null, "true", model);
        assertEquals("login", view);
        assertEquals("ログアウトしました", model.getAttribute("message"));
    }
}
