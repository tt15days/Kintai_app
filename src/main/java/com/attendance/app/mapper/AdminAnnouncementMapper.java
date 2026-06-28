package com.attendance.app.mapper;

import com.attendance.app.entity.AdminAnnouncement;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * AdminAnnouncement Mapper - 管理者お知らせのDB操作
 */
@Mapper
public interface AdminAnnouncementMapper {

    /**
     * 現在表示対象のお知らせ一覧を取得します（有効・期間内）。
     *
     * @return お知らせ情報のリスト
     */
    List<AdminAnnouncement> selectActive();

    /**
     * 全お知らせ一覧を作成日時の降順で取得します（管理者用）。
     *
     * @return お知らせ情報のリスト
     */
    List<AdminAnnouncement> selectAll();

    /**
     * IDでお知らせを1件取得します。
     *
     * @param announcementId お知らせID
     * @return お知らせ情報。存在しない場合は null
     */
    AdminAnnouncement selectById(@Param("announcementId") Long announcementId);

    /**
     * お知らせを登録します。
     *
     * @param announcement 登録するお知らせ情報
     * @return 登録された件数
     */
    int insert(AdminAnnouncement announcement);

    /**
     * お知らせを更新します。
     *
     * @param announcement 更新するお知らせ情報
     * @return 更新された件数
     */
    int update(AdminAnnouncement announcement);

    /**
     * お知らせを削除します。
     *
     * @param announcementId 削除対象のお知らせID
     * @return 削除された件数
     */
    int deleteById(@Param("announcementId") Long announcementId);
}
