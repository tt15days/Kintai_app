package com.attendance.app.controller;

import com.attendance.app.entity.AdminAnnouncement;
import com.attendance.app.entity.User;
import com.attendance.app.entity.UserRole;
import com.attendance.app.security.SecurityUtil;
import com.attendance.app.service.AdminAnnouncementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminAnnouncementController")
class AdminAnnouncementControllerTest {

    @Mock
    private AdminAnnouncementService adminAnnouncementService;

    @Mock
    private SecurityUtil securityUtil;

    @InjectMocks
    private AdminAnnouncementController controller;

    private User adminUser;

    @BeforeEach
    void setUp() {
        adminUser = new User();
        adminUser.setUserId(1L);
        adminUser.setUserRole(UserRole.ADMIN);
    }

    @Test
    @DisplayName("listAnnouncements: 一覧とフォーム初期値をモデルに設定する")
    void listAnnouncements_populatesModel() {
        List<AdminAnnouncement> announcements = List.of(AdminAnnouncement.builder().announcementId(1L).build());
        when(adminAnnouncementService.getAllAnnouncements()).thenReturn(announcements);

        ExtendedModelMap model = new ExtendedModelMap();
        String view = controller.listAnnouncements(model);

        assertThat(view).isEqualTo("admin/announcements");
        assertThat(model.getAttribute("announcements")).isEqualTo(announcements);
        assertThat(model.getAttribute("newAnnouncement")).isInstanceOf(AdminAnnouncement.class);
    }

    @Test
    @DisplayName("createAnnouncement: 正常時は登録して成功メッセージを設定する")
    void createAnnouncement_success() {
        when(securityUtil.getCurrentUser()).thenReturn(adminUser);

        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();
        String view = controller.createAnnouncement(
                " お知らせ ", " 本文 ", LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31), redirect);

        assertThat(view).isEqualTo("redirect:/admin/announcements");
        ArgumentCaptor<AdminAnnouncement> captor = ArgumentCaptor.forClass(AdminAnnouncement.class);
        verify(adminAnnouncementService).create(captor.capture());
        AdminAnnouncement created = captor.getValue();
        assertThat(created.getTitle()).isEqualTo("お知らせ");
        assertThat(created.getMessage()).isEqualTo("本文");
        assertThat(created.getIsActive()).isTrue();
        assertThat(created.getCreatedBy()).isEqualTo(1L);
        assertThat(redirect.getFlashAttributes().get("successMessage")).isEqualTo("お知らせを登録しました。");
    }

    @Test
    @DisplayName("createAnnouncement: 終了日未指定の場合はdisplayEndDateがnullになる")
    void createAnnouncement_withoutEndDate_setsNullEndDate() {
        when(securityUtil.getCurrentUser()).thenReturn(adminUser);

        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();
        controller.createAnnouncement("タイトル", "本文", LocalDate.of(2026, 5, 1), null, redirect);

        ArgumentCaptor<AdminAnnouncement> captor = ArgumentCaptor.forClass(AdminAnnouncement.class);
        verify(adminAnnouncementService).create(captor.capture());
        assertThat(captor.getValue().getDisplayEndDate()).isNull();
    }

    @Test
    @DisplayName("createAnnouncement: 例外発生時はエラーメッセージを設定する")
    void createAnnouncement_exception_setsErrorMessage() {
        when(securityUtil.getCurrentUser()).thenReturn(adminUser);
        doThrow(new RuntimeException("DB error")).when(adminAnnouncementService).create(any());

        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();
        String view = controller.createAnnouncement("タイトル", "本文", LocalDate.of(2026, 5, 1), null, redirect);

        assertThat(view).isEqualTo("redirect:/admin/announcements");
        assertThat(redirect.getFlashAttributes().get("errorMessage")).isEqualTo("お知らせの登録に失敗しました。");
    }

    @Test
    @DisplayName("updateAnnouncement: 正常時は更新して成功メッセージを設定する")
    void updateAnnouncement_success() {
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();
        String view = controller.updateAnnouncement(
                10L, "更新後タイトル", "更新後本文", true, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), redirect);

        assertThat(view).isEqualTo("redirect:/admin/announcements");
        ArgumentCaptor<AdminAnnouncement> captor = ArgumentCaptor.forClass(AdminAnnouncement.class);
        verify(adminAnnouncementService).update(captor.capture());
        AdminAnnouncement updated = captor.getValue();
        assertThat(updated.getAnnouncementId()).isEqualTo(10L);
        assertThat(updated.getIsActive()).isTrue();
        assertThat(redirect.getFlashAttributes().get("successMessage")).isEqualTo("お知らせを更新しました。");
    }

    @Test
    @DisplayName("updateAnnouncement: 例外発生時はエラーメッセージを設定する")
    void updateAnnouncement_exception_setsErrorMessage() {
        doThrow(new RuntimeException("DB error")).when(adminAnnouncementService).update(any());

        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();
        String view = controller.updateAnnouncement(
                10L, "タイトル", "本文", false, LocalDate.of(2026, 6, 1), null, redirect);

        assertThat(view).isEqualTo("redirect:/admin/announcements");
        assertThat(redirect.getFlashAttributes().get("errorMessage")).isEqualTo("お知らせの更新に失敗しました。");
    }

    @Test
    @DisplayName("deleteAnnouncement: 正常時は削除して成功メッセージを設定する")
    void deleteAnnouncement_success() {
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();
        String view = controller.deleteAnnouncement(10L, redirect);

        assertThat(view).isEqualTo("redirect:/admin/announcements");
        verify(adminAnnouncementService).delete(10L);
        assertThat(redirect.getFlashAttributes().get("successMessage")).isEqualTo("お知らせを削除しました。");
    }

    @Test
    @DisplayName("deleteAnnouncement: 例外発生時はエラーメッセージを設定し削除失敗とする")
    void deleteAnnouncement_exception_setsErrorMessage() {
        doThrow(new RuntimeException("DB error")).when(adminAnnouncementService).delete(10L);

        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();
        String view = controller.deleteAnnouncement(10L, redirect);

        assertThat(view).isEqualTo("redirect:/admin/announcements");
        assertThat(redirect.getFlashAttributes().get("errorMessage")).isEqualTo("お知らせの削除に失敗しました。");
    }
}
