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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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

            service.create(ann);

            assertThat(ann.getDisplayStartDate()).isEqualTo(fixedDate);
            verify(adminAnnouncementMapper).insert(ann);
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
            AdminAnnouncement existing = AdminAnnouncement.builder().announcementId(1L).title("旧タイトル").build();
            when(adminAnnouncementMapper.selectById(1L)).thenReturn(existing);

            AdminAnnouncement updated = AdminAnnouncement.builder().announcementId(1L).title("新タイトル").build();
            service.update(updated);

            verify(adminAnnouncementMapper).update(updated);
        }

        @Test
        @DisplayName("存在しない ID の場合は update が呼ばれない（no-op）")
        void notFoundId_noUpdate() {
            when(adminAnnouncementMapper.selectById(999L)).thenReturn(null);

            AdminAnnouncement ann = AdminAnnouncement.builder().announcementId(999L).title("存在しない").build();
            service.update(ann);

            verify(adminAnnouncementMapper, never()).update(any());
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
            service.delete(5L);
            verify(adminAnnouncementMapper).deleteById(5L);
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
