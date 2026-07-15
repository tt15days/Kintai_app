package com.attendance.app.service;

import com.attendance.app.entity.User;
import com.attendance.app.entity.UserNotification;
import com.attendance.app.entity.UserRole;
import com.attendance.app.mapper.UserNotificationMapper;
import com.attendance.app.mapper.WorkScheduleClassMapper;
import com.attendance.app.entity.WorkScheduleClass;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * ユーザー通知に関する業務ロジックを提供するサービスです。
 *
 * ダッシュボード通知（勤怠提出リマインドなど）の生成・既読管理を提供します。
 * バッチ処理および管理者による手動送信の両方から利用されます。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class UserNotificationService {

    public static final String TYPE_REMINDER = "REMINDER";
    public static final String TYPE_APPROVAL_REQUEST = "APPROVAL_REQUEST";
    public static final String TYPE_APPROVED = "APPROVED";
    public static final String TYPE_RETURNED = "RETURNED";
    public static final String TYPE_REJECTED = "REJECTED";
    public static final String TYPE_ARTICLE36_ALERT = "ARTICLE36_ALERT";
    public static final String TYPE_ADMIN_MESSAGE = "ADMIN_MESSAGE";
    public static final String TYPE_INTERVAL_ALERT = "INTERVAL_ALERT";

    private static final DateTimeFormatter YEAR_MONTH_DISPLAY = DateTimeFormatter.ofPattern("yyyy年MM月");

    private final UserNotificationMapper userNotificationMapper;
    private final UserService userService;
    private final AttendanceSubmissionService attendanceSubmissionService;
    private final WorkScheduleClassMapper workScheduleClassMapper;

    /**
     * 指定ユーザーの未読通知一覧を返します（作成日時降順）。
     *
     * @param userId ユーザーID
     * @return 未読通知リスト
     */
    @Transactional(readOnly = true)
    public List<UserNotification> getUnreadByUserId(Long userId) {
        return userNotificationMapper.selectUnreadByUserId(userId);
    }

    /**
     * 指定した通知を既読にします。
     * 他のユーザーの通知は操作できません。
     *
     * @param notificationId 通知ID
     * @param userId         操作するユーザーID
     */
    public void markAsRead(Long notificationId, Long userId) {
        userNotificationMapper.markAsRead(notificationId, userId);
    }

    /**
     * 指定ユーザーの全未読通知を既読にします。
     *
     * @param userId ユーザーID
     */
    public void markAllAsRead(Long userId) {
        userNotificationMapper.markAllAsRead(userId);
    }

    /**
     * 指定年月の勤怠を未提出（または差し戻し・取り下げ）の全アクティブユーザーに
     * リマインド通知を作成します。管理者ユーザーは対象外です。
     *
     * @param targetMonth 通知対象年月
     * @return 通知を作成したユーザー数
     */
    public int createRemindersForUnsubmittedUsers(YearMonth targetMonth) {
        List<User> activeUsers = userService.getActiveUsers();
        String monthDisplay = targetMonth.format(YEAR_MONTH_DISPLAY);
        String message = monthDisplay + "の勤怠がまだ提出されていません。提出期限をご確認の上、月次勤怠を申請してください。";

        // N+1対策として対象月の全提出を一括取得し、ユーザーIDをキーにMap参照する
        Map<Long, com.attendance.app.entity.AttendanceSubmission> submissionByUserId =
                attendanceSubmissionService.getSubmissionsByTargetYearMonth(targetMonth).stream()
                        .collect(java.util.stream.Collectors.toMap(
                                com.attendance.app.entity.AttendanceSubmission::getUserId,
                                s -> s));

        int count = 0;
        for (User user : activeUsers) {
            if (user.getUserRole() == UserRole.ADMIN) {
                continue;
            }
            if (needsReminder(submissionByUserId.get(user.getUserId()))) {
                UserNotification notification = UserNotification.builder()
                        .userId(user.getUserId())
                        .message(message)
                        .notificationType(TYPE_REMINDER)
                        .idempotencyKey(targetMonth.toString())
                        .isRead(false)
                        .senderUserId(null)
                        .build();
                count += userNotificationMapper.insertBatchIfAbsent(notification);
            }
        }
        log.info("勤怠リマインド通知を作成: targetMonth={}, count={}", targetMonth, count);
        return count;
    }

    /**
     * 月次勤怠が申請されたとき、その申請を承認できる承認者にのみ通知を送信します。
     *
     * @param applicantUserId   申請者のユーザーID
     * @param applicantName     申請者の氏名
     * @param targetYearMonth   対象年月（例: "2026-05"）
     */
    public void notifyApproversNewSubmission(Long applicantUserId, String applicantName, String targetYearMonth) {
        String message = applicantName + " さんが " + targetYearMonth + " の月次勤怠を申請しました。承認をお願いします。";
        notifyApprovers(applicantUserId, message, TYPE_APPROVAL_REQUEST);
    }

    /**
     * 勤怠修正申請が提出されたとき、その申請を承認できる承認者にのみ通知を送信します。
     *
     * @param applicantUserId   申請者のユーザーID
     * @param applicantName     申請者の氏名
     * @param attendanceDateStr 修正対象日（例: "2026-05-01"）
     */
    public void notifyApproversNewCorrectionRequest(Long applicantUserId, String applicantName, String attendanceDateStr) {
        String message = applicantName + " さんが " + attendanceDateStr + " の勤怠修正を申請しました。承認をお願いします。";
        notifyApprovers(applicantUserId, message, TYPE_APPROVAL_REQUEST);
    }

    /**
     * 申請が承認されたとき、申請者本人に通知を送信します。
     *
     * @param applicantUserId ユーザーID
     * @param description     通知に表示する申請内容（例: "2026年05月分の月次勤怠申請"）
     */
    public void notifyApplicantApproved(Long applicantUserId, String description) {
        String message = description + " が承認されました。";
        insertNotification(applicantUserId, message, TYPE_APPROVED, null);
        log.info("承認通知を送信: userId={}, description={}", applicantUserId, description);
    }

    /**
     * 申請が差し戻されたとき、申請者本人に通知を送信します。
     *
     * @param applicantUserId ユーザーID
     * @param description     通知に表示する申請内容
     * @param comment         差し戻しコメント（任意）
     */
    public void notifyApplicantReturned(Long applicantUserId, String description, String comment) {
        String message = description + " が差し戻されました。"
                + (comment != null && !comment.isBlank() ? " コメント: " + comment : "");
        insertNotification(applicantUserId, message, TYPE_RETURNED, null);
        log.info("差し戻し通知を送信: userId={}, description={}", applicantUserId, description);
    }

    /**
     * 申請が却下されたとき、申請者本人に通知を送信します。
     *
     * @param applicantUserId ユーザーID
     * @param description     通知に表示する申請内容
     * @param comment         却下コメント（任意）
     */
    public void notifyApplicantRejected(Long applicantUserId, String description, String comment) {
        String message = description + " が却下されました。"
                + (comment != null && !comment.isBlank() ? " コメント: " + comment : "");
        insertNotification(applicantUserId, message, TYPE_REJECTED, null);
        log.info("却下通知を送信: userId={}, description={}", applicantUserId, description);
    }

    /**
     * 申請者の勤怠申請を実際に承認できる有効な承認者にのみ通知を送信します。
     * 承認可否は {@link AttendanceSubmissionService#canApprove} と同一の
     * アサイン制ロジック（個人/部署アサイン優先、フォールバックで同一勤務クラスのみ）で判定します。
     * 申請者自身には送信しません。
     *
     * @param applicantUserId 申請者のユーザーID（除外対象）
     * @param message         通知メッセージ
     * @param notificationType 通知タイプ
     */
    private void notifyApprovers(Long applicantUserId, String message, String notificationType) {
        List<User> activeUsers = userService.getActiveUsers();
        Map<Long, Boolean> approvalByUser = attendanceSubmissionService
                .canApproveAllForApplicant(applicantUserId, activeUsers);
        int count = 0;
        for (User user : activeUsers) {
            if (user.getUserId().equals(applicantUserId)) {
                continue;
            }
            if (approvalByUser.getOrDefault(user.getUserId(), false)) {
                insertNotification(user.getUserId(), message, notificationType, null);
                count++;
            }
        }
        log.info("承認者通知を送信: count={}, type={}", count, notificationType);
    }

    /**
     * 通知を1件挿入します。
     *
     * @param userId 通知対象のユーザーID
     * @param message 通知メッセージ
     * @param notificationType 通知タイプ
     */
    private void insertNotification(Long userId, String message, String notificationType, Long senderUserId) {
        UserNotification notification = UserNotification.builder()
                .userId(userId)
                .message(message)
                .notificationType(notificationType)
                .isRead(false)
                .senderUserId(senderUserId)
                .build();
        userNotificationMapper.insert(notification);
    }

    /**
     * 管理者が指定ユーザーにカスタムメッセージを送信します。
     *
     * @param userId  送信先ユーザーID
     * @param message 通知メッセージ
     * @param senderUserId 送信元のユーザーID
     */
    public void sendCustomNotification(Long userId, String message, Long senderUserId) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("メッセージを入力してください");
        }
        insertNotification(userId, message, TYPE_ADMIN_MESSAGE, senderUserId);
        log.info("管理者カスタム通知を送信: userId={}, senderUserId={}", userId, senderUserId);
    }

    /**
     * 管理者が全アクティブユーザー（ADMIN除く）にカスタムメッセージを一括送信します。
     *
     * @param message 通知メッセージ
     * @param senderUserId 送信元のユーザーID
     * @return 送信したユーザー数
     */
    public int sendCustomNotificationToAll(String message, Long senderUserId) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("メッセージを入力してください");
        }
        List<User> activeUsers = userService.getActiveUsers();
        int count = 0;
        for (User user : activeUsers) {
            if (user.getUserRole() == UserRole.ADMIN) {
                continue;
            }
            insertNotification(user.getUserId(), message, TYPE_ADMIN_MESSAGE, senderUserId);
            count++;
        }
        log.info("管理者一括通知を送信: count={}, senderUserId={}", count, senderUserId);
        return count;
    }

    /**
     * 管理者が指定勤務クラスに所属する全アクティブユーザー（ADMIN除く）にカスタムメッセージを一括送信します。
     *
     * @param classId      勤務クラスID
     * @param message      通知メッセージ
     * @param senderUserId 送信者のユーザーID
     * @return 送信したユーザー数
     */
    public int sendCustomNotificationToClass(Long classId, String message, Long senderUserId) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("メッセージを入力してください");
        }
        WorkScheduleClass wsc = workScheduleClassMapper.selectById(classId)
                .orElseThrow(() -> new IllegalArgumentException("勤務クラスが見つかりません: id=" + classId));
        
        List<User> activeUsers = userService.getActiveUsers();
        int count = 0;
        for (User user : activeUsers) {
            if (user.getUserRole() == UserRole.ADMIN) {
                continue;
            }
            if (wsc.getName().equals(user.getClassName())) {
                insertNotification(user.getUserId(), message, TYPE_ADMIN_MESSAGE, senderUserId);
                count++;
            }
        }
        log.info("管理者クラス一括通知を送信: classId={}, count={}, senderUserId={}", classId, count, senderUserId);
        return count;
    }

    /**
     * 36協定アラートを指定ユーザーに送信します。
     *
     * @param userId  対象ユーザーID
     * @param message アラートメッセージ
     */
    public void notifyArticle36Alert(Long userId, String message) {
        insertNotification(userId, message, TYPE_ARTICLE36_ALERT, null);
        log.info("36協定アラート通知を送信: userId={}", userId);
    }

    /**
     * 勤務間インターバル不足の警告を指定ユーザーに送信します。
     *
     * @param userId  対象ユーザーID
     * @param message アラートメッセージ
     */
    public void notifyIntervalAlert(Long userId, String message) {
        insertNotification(userId, message, TYPE_INTERVAL_ALERT, null);
        log.info("勤務間インターバル不足アラート通知を送信: userId={}", userId);
    }

    /**
     * 指定の勤怠申請がリマインド対象かどうかを判定します。
     * 申請なし・差し戻し・取り下げの場合は true を返します。
     *
     * @param submission 対象年月の勤怠申請（存在しない場合は null）
     * @return リマインド対象であれば true
     */
    private boolean needsReminder(com.attendance.app.entity.AttendanceSubmission submission) {
        if (submission == null) {
            return true;
        }
        String status = submission.getStatus();
        return AttendanceSubmissionService.STATUS_RETURNED.equals(status)
                || AttendanceSubmissionService.STATUS_WITHDRAWN.equals(status);
    }
}
