package com.attendance.app.mapper;

import com.attendance.app.entity.UserNotification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * UserNotification Mapper - ユーザー通知のDB操作
 */
@Mapper
public interface UserNotificationMapper {

    /**
     * ユーザーの未読通知を作成日時の降順で取得します。
     *
     * @param userId ユーザーID
     * @return ユーザー通知情報のリスト
     */
    List<UserNotification> selectUnreadByUserId(@Param("userId") Long userId);

    /**
     * 通知を挿入します。
     *
     * @param notification 登録する通知情報
     * @return 登録された件数
     */
    int insert(UserNotification notification);

    /**
     * 指定した通知を既読にします（本人のみ）。
     *
     * @param notificationId 通知ID
     * @param userId ユーザーID
     * @return 更新された件数
     */
    int markAsRead(@Param("notificationId") Long notificationId, @Param("userId") Long userId);

    /**
     * 指定ユーザーの全未読通知を既読にします。
     *
     * @param userId ユーザーID
     * @return 更新された件数
     */
    int markAllAsRead(@Param("userId") Long userId);
}
