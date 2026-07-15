package com.attendance.app.integration;

import com.attendance.app.entity.AdminAnnouncement;
import com.attendance.app.mapper.AdminAnnouncementMapper;
import com.attendance.app.mapper.UserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = com.attendance.app.AttendanceApplication.class)
@ActiveProfiles("integration")
@Transactional
@DisplayName("AdminAnnouncementMapper Integration")
class AdminAnnouncementMapperIntegrationTest {

    @Autowired
    private AdminAnnouncementMapper adminAnnouncementMapper;

    @Autowired
    private UserMapper userMapper;

    @Test
    @DisplayName("100件超でもページ取得件数がlimitを超えず全件数を返す")
    void selectPage_over100Rows_isBounded() {
        long before = adminAnnouncementMapper.countAll();
        Long adminId = userMapper.selectByEmail("admin@example.com").orElseThrow().getUserId();
        for (int n = 1; n <= 105; n++) {
            AdminAnnouncement announcement = AdminAnnouncement.builder()
                    .title("Paging Test " + n)
                    .message("message")
                    .isActive(true)
                    .displayStartDate(Instant.parse("2099-01-01T00:00:00Z"))
                    .createdBy(adminId)
                    .build();
            assertThat(adminAnnouncementMapper.insert(announcement)).isEqualTo(1);
        }

        List<AdminAnnouncement> firstPage = adminAnnouncementMapper.selectPage(0, 10);
        List<AdminAnnouncement> pageAfterHundred = adminAnnouncementMapper.selectPage(100, 10);

        assertThat(adminAnnouncementMapper.countAll()).isEqualTo(before + 105);
        assertThat(firstPage).hasSize(10);
        assertThat(firstPage).allMatch(announcement -> announcement.getTitle().startsWith("Paging Test "));
        assertThat(pageAfterHundred).hasSizeGreaterThanOrEqualTo(5).hasSizeLessThanOrEqualTo(10);
    }

    @Test
    @DisplayName("論理削除済みIDの更新・再削除は更新件数0を返す")
    void updateAndDelete_deletedRow_returnZero() {
        Long adminId = userMapper.selectByEmail("admin@example.com").orElseThrow().getUserId();
        AdminAnnouncement announcement = AdminAnnouncement.builder()
                .title("更新件数テスト")
                .message("本文")
                .isActive(true)
                .displayStartDate(Instant.parse("2099-01-01T00:00:00Z"))
                .createdBy(adminId)
                .build();
        assertThat(adminAnnouncementMapper.insert(announcement)).isEqualTo(1);

        announcement.setTitle("更新後");
        assertThat(adminAnnouncementMapper.update(announcement)).isEqualTo(1);
        assertThat(adminAnnouncementMapper.deleteById(announcement.getAnnouncementId())).isEqualTo(1);
        assertThat(adminAnnouncementMapper.update(announcement)).isZero();
        assertThat(adminAnnouncementMapper.deleteById(announcement.getAnnouncementId())).isZero();
    }
}
