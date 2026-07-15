package com.attendance.app.controller;

import com.attendance.app.entity.AttendanceCorrectionRequest;
import com.attendance.app.entity.AttendanceSubmission;
import com.attendance.app.entity.User;
import com.attendance.app.entity.UserRole;
import com.attendance.app.security.SecurityUtil;
import com.attendance.app.service.AttendanceCorrectionRequestService;
import com.attendance.app.service.AttendanceSubmissionService;
import com.attendance.app.service.UserNotificationService;
import com.attendance.app.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AttendanceApprovalController")
class AttendanceApprovalControllerTest {

    @Mock private AttendanceSubmissionService attendanceSubmissionService;
    @Mock private AttendanceCorrectionRequestService correctionRequestService;
    @Mock private SecurityUtil securityUtil;
    @Mock private UserService userService;
    @Mock private UserNotificationService userNotificationService;

    @InjectMocks
    private AttendanceApprovalController controller;

    @Test
    @DisplayName("checkViewPermission: 管理者は承認アサインに関係なく閲覧できる")
    void checkViewPermission_admin_isAllowed() {
        User admin = User.builder().userId(1L).userRole(UserRole.ADMIN).build();
        when(securityUtil.getCurrentUser()).thenReturn(admin);

        assertThatCode(() -> controller.checkViewPermission(10L)).doesNotThrowAnyException();
        verify(attendanceSubmissionService, never()).canApprove(admin, 10L);
    }

    @Test
    @DisplayName("checkViewPermission: 同一勤務クラスでも明示アサイン対象外は閲覧できない")
    void checkViewPermission_sameClassButNotAssigned_isDenied() {
        User approver = User.builder().userId(2L).userRole(UserRole.USER).className("東京").build();
        when(securityUtil.getCurrentUser()).thenReturn(approver);
        when(attendanceSubmissionService.canApprove(approver, 10L)).thenReturn(false);

        assertThatThrownBy(() -> controller.checkViewPermission(10L))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("勤怠の閲覧権限がありません");
    }

    @Test
    @DisplayName("checkViewPermission: 個人アサイン済みの異勤務クラスユーザーを閲覧できる")
    void checkViewPermission_individuallyAssigned_isAllowed() {
        User approver = User.builder().userId(2L).userRole(UserRole.USER).className("東京").build();
        when(securityUtil.getCurrentUser()).thenReturn(approver);
        when(attendanceSubmissionService.canApprove(approver, 10L)).thenReturn(true);

        assertThatCode(() -> controller.checkViewPermission(10L)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("checkViewPermission: 部署アサイン済みの異勤務クラスユーザを閲覧できる")
    void checkViewPermission_departmentAssigned_isAllowed() {
        User approver = User.builder().userId(2L).userRole(UserRole.USER).className("東京").build();
        when(securityUtil.getCurrentUser()).thenReturn(approver);
        when(attendanceSubmissionService.canApprove(approver, 20L)).thenReturn(true);

        assertThatCode(() -> controller.checkViewPermission(20L)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("showPendingApprovals: 承認一覧と申請者マップをモデルに設定する")
    void showPendingApprovals_populatesModel() {
        User approver = User.builder().userId(1L).userRole(UserRole.ADMIN).build();
        AttendanceSubmission s1 = AttendanceSubmission.builder().submissionId(101L).userId(11L).status(AttendanceSubmissionService.STATUS_PENDING).build();
        AttendanceSubmission s2 = AttendanceSubmission.builder().submissionId(102L).userId(12L).status(AttendanceSubmissionService.STATUS_PENDING).build();

        when(securityUtil.getCurrentUser()).thenReturn(approver);
        when(attendanceSubmissionService.getPendingSubmissions(approver)).thenReturn(List.of(s1, s2));
        when(userService.getUserById(11L)).thenReturn(Optional.of(User.builder().userId(11L).fullName("A").build()));
        when(userService.getUserById(12L)).thenReturn(Optional.of(User.builder().userId(12L).fullName("B").build()));

        ExtendedModelMap model = new ExtendedModelMap();
        String view = controller.showPendingApprovals(model);

        assertThat(view).isEqualTo("user/attendance-approval");
        assertThat((List<?>) model.getAttribute("pendingSubmissions")).hasSize(2);
        assertThat(model.getAttribute("applicantUsers")).isNotNull();
    }

    @Test
    @DisplayName("approveSubmission: 承認成功時に申請者へ通知し一覧へ戻る")
    void approveSubmission_success_notifiesApplicant() {
        AttendanceSubmission submission = AttendanceSubmission.builder()
                .submissionId(201L)
                .userId(21L)
                .targetYearMonth("2026-06")
                .build();

        when(securityUtil.getCurrentUserId()).thenReturn(1L);
        when(attendanceSubmissionService.getSubmissionById(201L)).thenReturn(Optional.of(submission));

        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();
        String view = controller.approveSubmission(201L, "ok", redirect);

        assertThat(view).isEqualTo("redirect:/attendance/approval");
        verify(attendanceSubmissionService).approve(201L, 1L, "ok");
        verify(userNotificationService).notifyApplicantApproved(21L, "2026-06分の月次勤怠申請");
        assertThat(redirect.getFlashAttributes().get("message")).isEqualTo("勤怠申請を承認しました");
    }

    @Test
    @DisplayName("returnSubmission: 業務例外時はエラーメッセージを返して通知しない")
    void returnSubmission_onIllegalArgument_setsError() {
        when(securityUtil.getCurrentUserId()).thenReturn(1L);
        doThrow(new IllegalArgumentException("差し戻し不可"))
            .when(attendanceSubmissionService).returnForCorrection(301L, 1L, "修正");

        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();
        String view = controller.returnSubmission(301L, "修正", redirect);

        assertThat(view).isEqualTo("redirect:/attendance/approval");
        assertThat(redirect.getFlashAttributes().get("error")).isEqualTo("差し戻し不可");
        verify(userNotificationService, never()).notifyApplicantReturned(anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("approveCorrectionRequest: 承認成功時に修正申請通知を送信する")
    void approveCorrectionRequest_success_notifiesApplicant() {
        AttendanceCorrectionRequest request = AttendanceCorrectionRequest.builder()
                .requestId(401L)
                .userId(41L)
                .attendanceDate(LocalDate.of(2026, 5, 10))
                .build();

        when(securityUtil.getCurrentUserId()).thenReturn(1L);
        when(correctionRequestService.getRequestById(401L)).thenReturn(Optional.of(request));

        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();
        String view = controller.approveCorrectionRequest(401L, "確認", redirect);

        assertThat(view).isEqualTo("redirect:/attendance/approval/corrections");
        verify(correctionRequestService).approveRequest(401L, 1L, "確認");
        verify(userNotificationService).notifyApplicantApproved(41L, "2026-05-10 の勤怠修正申請");
    }
}
