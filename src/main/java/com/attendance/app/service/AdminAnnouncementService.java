package com.attendance.app.service;

import com.attendance.app.entity.AdminAnnouncement;
import com.attendance.app.mapper.AdminAnnouncementMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * 管理者お知らせの業務ロジックを提供するサービスクラスです。
 *
 * 管理者がダッシュボードに掲示するお知らせメッセージの登録、更新、取得、削除などの
 * ライフサイクル全体を管理するためのCRUD機能を提供します。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AdminAnnouncementService {

    private static final String LOG_CREATE = "お知らせを登録しました: announcementId={}, title={}";
    private static final String LOG_UPDATE = "お知らせを更新しました: announcementId={}, title={}";
    private static final String LOG_DELETE = "お知らせを削除しました: announcementId={}";
    private static final String LOG_NOT_FOUND = "お知らせが見つかりません: announcementId={}";
    private static final String TIMEZONE_JST = "Asia/Tokyo";

    private final AdminAnnouncementMapper adminAnnouncementMapper;

    /**
     * 現在有効かつ表示期間内のお知らせ一覧を取得します。
     * ユーザーのダッシュボード表示などに利用されます。
     *
     * @return 有効なお知らせのリスト
     */
    @Transactional(readOnly = true)
    public List<AdminAnnouncement> getActiveAnnouncements() {
        return adminAnnouncementMapper.selectActive();
    }

    /**
     * システムに登録されている全てのお知らせ一覧を取得します。
     * 管理者画面での一覧表示などに利用されます。
     *
     * @return 全てのお知らせのリスト
     */
    @Transactional(readOnly = true)
    public List<AdminAnnouncement> getAllAnnouncements() {
        return adminAnnouncementMapper.selectAll();
    }

    /**
     * 指定されたお知らせIDに紐づくお知らせ情報を1件取得します。
     *
     * @param announcementId 取得対象のお知らせID
     * @return お知らせエンティティ。存在しない場合は {@code null}
     */
    @Transactional(readOnly = true)
    public AdminAnnouncement getById(Long announcementId) {
        return adminAnnouncementMapper.selectById(announcementId);
    }

    /**
     * 新しいお知らせをシステムに登録します。
     * 表示開始日が指定されていない場合は、現在日時（日本標準時）の0時が自動的に設定されます。
     *
     * @param announcement 登録するお知らせ情報
     */
    public void create(AdminAnnouncement announcement) {
        if (announcement.getDisplayStartDate() == null) {
            ZoneId jst = ZoneId.of(TIMEZONE_JST);
            announcement.setDisplayStartDate(LocalDate.now(jst).atStartOfDay(jst).toInstant());
        }
        adminAnnouncementMapper.insert(announcement);
        log.info(LOG_CREATE, announcement.getAnnouncementId(), announcement.getTitle());
    }

    /**
     * 既存のお知らせ情報を更新します。
     * 指定されたIDのお知らせが存在しない場合は、更新を行わずに処理を終了します。
     *
     * @param announcement 更新するお知らせ情報（announcementIdが必須）
     */
    public void update(AdminAnnouncement announcement) {
        AdminAnnouncement existing = adminAnnouncementMapper.selectById(announcement.getAnnouncementId());
        if (existing == null) {
            log.warn(LOG_NOT_FOUND, announcement.getAnnouncementId());
            return;
        }
        adminAnnouncementMapper.update(announcement);
        log.info(LOG_UPDATE, announcement.getAnnouncementId(), announcement.getTitle());
    }

    /**
     * 指定されたお知らせIDに紐づくお知らせをシステムから削除します。
     *
     * @param announcementId 削除対象のお知らせID
     */
    public void delete(Long announcementId) {
        adminAnnouncementMapper.deleteById(announcementId);
        log.info(LOG_DELETE, announcementId);
    }
}
