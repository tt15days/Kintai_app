package com.attendance.app.controller;

import com.attendance.app.entity.User;
import com.attendance.app.mapper.SystemSettingMapper;
import com.attendance.app.security.SecurityUtil;
import com.attendance.app.service.UserService;
import com.attendance.app.service.AttendanceSubmissionService;
import com.attendance.app.service.AttendanceCorrectionRequestService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ExtendedModelMap;

import java.util.Optional;
import java.util.List;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GlobalControllerAdviceTest")
class GlobalControllerAdviceTest {

    @Mock
    private SecurityUtil securityUtil;

    @Mock
    private SystemSettingMapper systemSettingMapper;

    @Mock
    private UserService userService;

    @Mock
    private AttendanceSubmissionService attendanceSubmissionService;

    @Mock
    private AttendanceCorrectionRequestService correctionRequestService;

    @InjectMocks
    private GlobalControllerAdvice advice;

    @Nested
    @DisplayName("addCurrentUser")
    class AddCurrentUser {

        @Test
        @DisplayName("ログイン済みかつ有効なユーザーの場合、モデルにcurrentUserが追加されること")
        void addCurrentUser_authenticated() {
            User user = User.builder().userId(10L).fullName("テストユーザー").build();
            when(securityUtil.isAuthenticated()).thenReturn(true);
            when(securityUtil.getCurrentUsername()).thenReturn(Optional.of("test@example.com"));
            when(securityUtil.getCurrentUser()).thenReturn(user);

            ExtendedModelMap model = new ExtendedModelMap();
            advice.addCurrentUser(model);

            assertEquals(user, model.getAttribute("currentUser"));
        }

        @Test
        @DisplayName("匿名ユーザー(anonymousUser)の場合、モデルにcurrentUserは追加されないこと")
        void addCurrentUser_anonymous() {
            when(securityUtil.isAuthenticated()).thenReturn(true);
            when(securityUtil.getCurrentUsername()).thenReturn(Optional.of("anonymousUser"));

            ExtendedModelMap model = new ExtendedModelMap();
            advice.addCurrentUser(model);

            assertNull(model.getAttribute("currentUser"));
        }

        @Test
        @DisplayName("未ログイン（isAuthenticatedがfalse）の場合、モデルにcurrentUserは追加されないこと")
        void addCurrentUser_notAuthenticated() {
            when(securityUtil.isAuthenticated()).thenReturn(false);

            ExtendedModelMap model = new ExtendedModelMap();
            advice.addCurrentUser(model);

            assertNull(model.getAttribute("currentUser"));
        }

        @Test
        @DisplayName("例外が発生した場合、エラーにならずに安全に無視されること")
        void addCurrentUser_exceptionIgnored() {
            when(securityUtil.isAuthenticated()).thenThrow(new RuntimeException("Security context error"));

            ExtendedModelMap model = new ExtendedModelMap();
            // 例外がスローされずに処理が終了することを確認
            advice.addCurrentUser(model);
            assertNull(model.getAttribute("currentUser"));
        }
    }

    @Nested
    @DisplayName("addCopyrightText")
    class AddCopyrightText {

        @Test
        @DisplayName("データベースからコピーライト設定が正常に取得できた場合、その値がモデルに設定されること")
        void addCopyright_fromDb() {
            when(systemSettingMapper.selectValueByKey("COPYRIGHT_TEXT")).thenReturn("© 2026 Test Copyright");

            ExtendedModelMap model = new ExtendedModelMap();
            advice.addCopyrightText(model);

            assertEquals("© 2026 Test Copyright", model.getAttribute("copyrightText"));
        }

        @Test
        @DisplayName("コピーライト設定が空またはnullの場合、デフォルトのコピーライトが設定されること")
        void addCopyright_defaultWhenEmpty() {
            when(systemSettingMapper.selectValueByKey("COPYRIGHT_TEXT")).thenReturn(null);

            ExtendedModelMap model = new ExtendedModelMap();
            advice.addCopyrightText(model);

            assertEquals("© 2026 勤怠管理システム", model.getAttribute("copyrightText"));
        }

        @Test
        @DisplayName("設定取得で例外が発生した場合、デフォルトのコピーライトが設定されること")
        void addCopyright_defaultOnException() {
            when(systemSettingMapper.selectValueByKey("COPYRIGHT_TEXT")).thenThrow(new RuntimeException("DB Error"));

            ExtendedModelMap model = new ExtendedModelMap();
            advice.addCopyrightText(model);

            assertEquals("© 2026 勤怠管理システム", model.getAttribute("copyrightText"));
        }
    }

    @Nested
    @DisplayName("addSystemName")
    class AddSystemName {

        @Test
        @DisplayName("データベースからシステム名設定が正常に取得できた場合、その値がモデルに設定されること")
        void addSystemName_fromDb() {
            when(systemSettingMapper.selectValueByKey("SYSTEM_NAME")).thenReturn("カスタム勤怠");

            ExtendedModelMap model = new ExtendedModelMap();
            advice.addSystemName(model);

            assertEquals("カスタム勤怠", model.getAttribute("systemName"));
        }

        @Test
        @DisplayName("システム名設定が空またはnullの場合、デフォルトのシステム名が設定されること")
        void addSystemName_defaultWhenEmpty() {
            when(systemSettingMapper.selectValueByKey("SYSTEM_NAME")).thenReturn(" ");

            ExtendedModelMap model = new ExtendedModelMap();
            advice.addSystemName(model);

            assertEquals("勤怠管理システム", model.getAttribute("systemName"));
        }

        @Test
        @DisplayName("設定取得で例外が発生した場合、デフォルトのシステム名が設定されること")
        void addSystemName_defaultOnException() {
            when(systemSettingMapper.selectValueByKey("SYSTEM_NAME")).thenThrow(new RuntimeException("DB Error"));

            ExtendedModelMap model = new ExtendedModelMap();
            advice.addSystemName(model);

            assertEquals("勤怠管理システム", model.getAttribute("systemName"));
        }
    }

    @Nested
    @DisplayName("addPendingApprovalsCount")
    class AddPendingApprovalsCount {

        @Test
        @DisplayName("ログイン済みかつ承認者の場合、承認待ち件数がモデルに追加されること")
        void addPendingApprovalsCount_approver() {
            User user = User.builder().userId(10L).fullName("テストユーザー").build();
            when(securityUtil.isAuthenticated()).thenReturn(true);
            when(securityUtil.getCurrentUsername()).thenReturn(Optional.of("test@example.com"));
            when(securityUtil.getCurrentUser()).thenReturn(user);
            when(userService.isAttendanceApprover(user)).thenReturn(true);
            when(attendanceSubmissionService.getPendingSubmissions(user)).thenReturn(List.of(new com.attendance.app.entity.AttendanceSubmission()));
            when(correctionRequestService.getPendingRequests(user)).thenReturn(List.of(new com.attendance.app.entity.AttendanceCorrectionRequest(), new com.attendance.app.entity.AttendanceCorrectionRequest()));

            ExtendedModelMap model = new ExtendedModelMap();
            advice.addPendingApprovalsCount(model);

            assertEquals(1, model.getAttribute("pendingSubmissionsCount"));
            assertEquals(2, model.getAttribute("pendingCorrectionsCount"));
        }

        @Test
        @DisplayName("一般ユーザーの場合、承認待ち件数はモデルに追加されないこと")
        void addPendingApprovalsCount_notApprover() {
            User user = User.builder().userId(10L).fullName("テストユーザー").build();
            when(securityUtil.isAuthenticated()).thenReturn(true);
            when(securityUtil.getCurrentUsername()).thenReturn(Optional.of("test@example.com"));
            when(securityUtil.getCurrentUser()).thenReturn(user);
            when(userService.isAttendanceApprover(user)).thenReturn(false);

            ExtendedModelMap model = new ExtendedModelMap();
            advice.addPendingApprovalsCount(model);

            assertNull(model.getAttribute("pendingSubmissionsCount"));
            assertNull(model.getAttribute("pendingCorrectionsCount"));
        }

        @Test
        @DisplayName("未ログインの場合、承認待ち件数は追加されないこと")
        void addPendingApprovalsCount_notAuthenticated() {
            when(securityUtil.isAuthenticated()).thenReturn(false);

            ExtendedModelMap model = new ExtendedModelMap();
            advice.addPendingApprovalsCount(model);

            assertNull(model.getAttribute("pendingSubmissionsCount"));
            assertNull(model.getAttribute("pendingCorrectionsCount"));
        }
    }
}
