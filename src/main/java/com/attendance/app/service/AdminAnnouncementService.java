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

    public static final int MAX_TITLE_LENGTH = 200;
    public static final int MAX_MESSAGE_LENGTH = 2000;
    public static final int MAX_PAGE_SIZE = 50;

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

    /** 管理画面用のお知らせ一覧をページ取得します。 */
    @Transactional(readOnly = true)
    public List<AdminAnnouncement> getAnnouncementsPage(long offset, int limit) {
        if (offset < 0 || limit < 1 || limit > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("ページ指定が不正です。");
        }
        return adminAnnouncementMapper.selectPage(offset, limit);
    }

    /** 管理画面用の未削除お知らせ件数を取得します。 */
    @Transactional(readOnly = true)
    public long countAnnouncements() {
        return adminAnnouncementMapper.countAll();
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
        normalizeAndValidate(announcement);
        if (announcement.getDisplayStartDate() == null) {
            ZoneId jst = ZoneId.of(TIMEZONE_JST);
            announcement.setDisplayStartDate(LocalDate.now(jst).atStartOfDay(jst).toInstant());
        }
        if (adminAnnouncementMapper.insert(announcement) != 1) {
            throw new IllegalStateException("お知らせを登録できませんでした。");
        }
        log.info(LOG_CREATE, announcement.getAnnouncementId(), announcement.getTitle());
    }

    /**
     * 既存のお知らせ情報を更新します。
     * @param announcement 更新するお知らせ情報（announcementIdが必須）
     */
    public void update(AdminAnnouncement announcement) {
        normalizeAndValidate(announcement);
        if (adminAnnouncementMapper.update(announcement) != 1) {
            log.warn(LOG_NOT_FOUND, announcement.getAnnouncementId());
            throw new AdminAnnouncementNotFoundException(announcement.getAnnouncementId());
        }
        log.info(LOG_UPDATE, announcement.getAnnouncementId(), announcement.getTitle());
    }

    /**
     * 指定されたお知らせIDに紐づくお知らせをシステムから削除します。
     *
     * @param announcementId 削除対象のお知らせID
     */
    public void delete(Long announcementId) {
        if (adminAnnouncementMapper.deleteById(announcementId) != 1) {
            log.warn(LOG_NOT_FOUND, announcementId);
            throw new AdminAnnouncementNotFoundException(announcementId);
        }
        log.info(LOG_DELETE, announcementId);
    }

    private void normalizeAndValidate(AdminAnnouncement announcement) {
        if (announcement == null) {
            throw new IllegalArgumentException("お知らせを入力してください。");
        }
        String title = normalizeRequired(announcement.getTitle(), "タイトル");
        String message = normalizeRequired(announcement.getMessage(), "本文");
        if (title.length() > MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException("タイトルは" + MAX_TITLE_LENGTH + "文字以内で入力してください。");
        }
        if (message.length() > MAX_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("本文は" + MAX_MESSAGE_LENGTH + "文字以内で入力してください。");
        }
        announcement.setTitle(title);
        announcement.setMessage(message);
    }

    private String normalizeRequired(String value, String label) {
        if (value == null || value.strip().isEmpty()) {
            throw new IllegalArgumentException(label + "を入力してください。");
        }
        return value.strip();
    }
}
