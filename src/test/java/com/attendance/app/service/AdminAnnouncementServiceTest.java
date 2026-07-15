package com.attendance.app.service;

import com.attendance.app.entity.AdminAnnouncement;
import com.attendance.app.mapper.AdminAnnouncementMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AdminAnnouncementService} の単体テスト。
 *
 * <p>お知らせ CRUD の正常系・準正常系（存在しない ID への update）を検証します。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdminAnnouncementService")
class AdminAnnouncementServiceTest {

    @Mock
    private AdminAnnouncementMapper adminAnnouncementMapper;

    @InjectMocks
    private AdminAnnouncementService service;

    // ─────────────────────────────────────────────────────────────────────────
    // getActiveAnnouncements
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getActiveAnnouncements")
    class GetActiveAnnouncements {

        @Test
        @DisplayName("Mapper の selectActive が返すリストをそのまま返す")
        void returnsActiveList() {
            AdminAnnouncement ann = AdminAnnouncement.builder().announcementId(1L).title("重要").build();
            when(adminAnnouncementMapper.selectActive()).thenReturn(List.of(ann));

            List<AdminAnnouncement> result = service.getActiveAnnouncements();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getTitle()).isEqualTo("重要");
        }

        @Test
        @DisplayName("お知らせなしの場合は空リストを返す")
        void noAnnouncements_returnsEmptyList() {
            when(adminAnnouncementMapper.selectActive()).thenReturn(List.of());
            assertThat(service.getActiveAnnouncements()).isEmpty();
        }
    }

    @Nested
    @DisplayName("管理画面ページング")
    class Paging {

        @Test
        @DisplayName("指定範囲の一覧と全件数をMapperから取得する")
        void returnsPageAndCount() {
            AdminAnnouncement ann = AdminAnnouncement.builder().announcementId(11L).build();
            when(adminAnnouncementMapper.selectPage(10, 10)).thenReturn(List.of(ann));
            when(adminAnnouncementMapper.countAll()).thenReturn(21L);

            assertThat(service.getAnnouncementsPage(10, 10)).containsExactly(ann);
            assertThat(service.countAnnouncements()).isEqualTo(21L);
        }

        @Test
        @DisplayName("負のoffsetと上限超過limitを拒否する")
        void invalidRange_isRejected() {
            assertThatThrownBy(() -> service.getAnnouncementsPage(-1, 10))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> service.getAnnouncementsPage(0, AdminAnnouncementService.MAX_PAGE_SIZE + 1))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // create
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("displayStartDate が null の場合は現在日時が自動設定される")
        void nullDisplayStartDate_getsAutoSet() {
            AdminAnnouncement ann = AdminAnnouncement.builder()
                    .title("テスト").message("内容").isActive(true)
                    .displayStartDate(null).build();
            when(adminAnnouncementMapper.insert(ann)).thenReturn(1);

            service.create(ann);

            assertThat(ann.getDisplayStartDate()).isNotNull();
            verify(adminAnnouncementMapper).insert(ann);
        }

        @Test
        @DisplayName("displayStartDate が設定済みの場合はそのまま insert される")
        void existingDisplayStartDate_notOverwritten() {
            Instant fixedDate = Instant.parse("2026-01-01T00:00:00Z");
            AdminAnnouncement ann = AdminAnnouncement.builder()
                    .title("テスト").message("内容").isActive(true)
                    .displayStartDate(fixedDate).build();
            when(adminAnnouncementMapper.insert(ann)).thenReturn(1);

            service.create(ann);

            assertThat(ann.getDisplayStartDate()).isEqualTo(fixedDate);
            verify(adminAnnouncementMapper).insert(ann);
        }

        @Test
        @DisplayName("空白のみのタイトル・本文を拒否する")
        void blankRequiredValues_areRejected() {
            AdminAnnouncement blankTitle = AdminAnnouncement.builder().title(" \n ").message("本文").build();
            AdminAnnouncement blankMessage = AdminAnnouncement.builder().title("タイトル").message("\t").build();

            assertThatThrownBy(() -> service.create(blankTitle))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("タイトルを入力してください。");
            assertThatThrownBy(() -> service.create(blankMessage))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("本文を入力してください。");
        }

        @Test
        @DisplayName("タイトルと本文の上限境界を検証する")
        void lengthBoundary_isValidated() {
            AdminAnnouncement boundary = AdminAnnouncement.builder()
                    .title("題".repeat(AdminAnnouncementService.MAX_TITLE_LENGTH))
                    .message("文".repeat(AdminAnnouncementService.MAX_MESSAGE_LENGTH))
                    .build();
            when(adminAnnouncementMapper.insert(boundary)).thenReturn(1);

            service.create(boundary);

            AdminAnnouncement overTitle = AdminAnnouncement.builder()
                    .title("題".repeat(AdminAnnouncementService.MAX_TITLE_LENGTH + 1)).message("本文").build();
            AdminAnnouncement overMessage = AdminAnnouncement.builder()
                    .title("タイトル").message("文".repeat(AdminAnnouncementService.MAX_MESSAGE_LENGTH + 1)).build();
            assertThatThrownBy(() -> service.create(overTitle)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> service.create(overMessage)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // update
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("既存のお知らせは Mapper.update が呼ばれる")
        void existingAnnouncement_callsUpdate() {
            AdminAnnouncement updated = AdminAnnouncement.builder()
                    .announcementId(1L).title("新タイトル").message("本文").build();
            when(adminAnnouncementMapper.update(updated)).thenReturn(1);
            service.update(updated);

            verify(adminAnnouncementMapper).update(updated);
        }

        @Test
        @DisplayName("存在しない ID の場合は業務例外を送出する")
        void notFoundId_throwsBusinessException() {
            AdminAnnouncement ann = AdminAnnouncement.builder()
                    .announcementId(999L).title("存在しない").message("本文").build();

            assertThatThrownBy(() -> service.update(ann))
                    .isInstanceOf(AdminAnnouncementNotFoundException.class);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // delete
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("Mapper の deleteById を呼び出す")
        void callsMapperDeleteById() {
            when(adminAnnouncementMapper.deleteById(5L)).thenReturn(1);
            service.delete(5L);
            verify(adminAnnouncementMapper).deleteById(5L);
        }

        @Test
        @DisplayName("存在しない・削除済みIDは業務例外を送出する")
        void notFoundId_throwsBusinessException() {
            when(adminAnnouncementMapper.deleteById(999L)).thenReturn(0);

            assertThatThrownBy(() -> service.delete(999L))
                    .isInstanceOf(AdminAnnouncementNotFoundException.class);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getById
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getById")
    class GetById {

        @Test
        @DisplayName("存在する ID は Mapper の戻り値をそのまま返す")
        void existingId_returnsAnnouncement() {
            AdminAnnouncement ann = AdminAnnouncement.builder().announcementId(1L).build();
            when(adminAnnouncementMapper.selectById(1L)).thenReturn(ann);

            assertThat(service.getById(1L)).isEqualTo(ann);
        }

        @Test
        @DisplayName("存在しない ID は null を返す")
        void notFoundId_returnsNull() {
            when(adminAnnouncementMapper.selectById(99L)).thenReturn(null);
            assertThat(service.getById(99L)).isNull();
        }
    }
}
