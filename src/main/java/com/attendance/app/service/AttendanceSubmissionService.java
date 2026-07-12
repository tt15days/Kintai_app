package com.attendance.app.service;

import com.attendance.app.entity.AttendanceSubmission;
import com.attendance.app.entity.AuditEventType;
import com.attendance.app.entity.User;
import com.attendance.app.mapper.AttendanceSubmissionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * 月次勤怠申請の状態遷移と承認可否を扱うサービスです。
 *
 * <p>承認・差戻し・取下げ・申請提出など状態変更を行うメソッドでは、
 * 処理完了後に {@link AuditLogService} を通じて操作監査ログを記録します。
 * 監査ログの記録は呼び出し元のトランザクションに参加するため、
 * メイン処理が失敗した場合は監査ログもロールバックされます。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AttendanceSubmissionService {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_RETURNED = "RETURNED";
    public static final String STATUS_WITHDRAWN = "WITHDRAWN";

    private static final DateTimeFormatter YEAR_MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    private final AttendanceSubmissionMapper attendanceSubmissionMapper;
    private final UserService userService;
    private final AttendancePeriodSettingService attendancePeriodSettingService;
    private final AuditLogService auditLogService;
    private final AttendanceApproverAssignmentService approverAssignmentService;

    /**
     * 指定ユーザー・対象月の勤怠申請を取得します。
     */
    public Optional<AttendanceSubmission> getSubmission(Long userId, YearMonth yearMonth) {
        return attendanceSubmissionMapper.selectByUserAndMonth(userId, toYearMonthKey(yearMonth));
    }

    /**
     * 指定ユーザーの勤怠申請一覧を取得します。
     */
    public List<AttendanceSubmission> getSubmissionsByUserId(Long userId) {
        return attendanceSubmissionMapper.selectByUserId(userId);
    }

    /**
     * 申請IDで勤怠申請を取得します。
     *
     * @param submissionId 申請ID
     * @return 勤怠申請（存在しない場合は empty）
     */
    @Transactional(readOnly = true)
    public Optional<AttendanceSubmission> getSubmissionById(Long submissionId) {
        return attendanceSubmissionMapper.selectById(submissionId);
    }

    /**
     * 対象月がユーザーによって編集可能か判定します。
     */
    public boolean isEditableMonth(Long userId, YearMonth yearMonth) {
        Optional<AttendanceSubmission> current = getSubmission(userId, yearMonth);
        if (current.isEmpty()) {
            return true;
        }
        String status = current.get().getStatus();
        return STATUS_RETURNED.equals(status) || STATUS_WITHDRAWN.equals(status);
    }

    /**
     * 対象月が編集不可の場合に例外を送出します。
     */
    public void assertEditableMonth(Long userId, YearMonth yearMonth) {
        Optional<AttendanceSubmission> current = getSubmission(userId, yearMonth);
        if (current.isEmpty()) {
            return;
        }

        String status = current.get().getStatus();
        if (STATUS_PENDING.equals(status)) {
            throw new IllegalArgumentException("この月は申請中のため修正できません");
        }
        if (STATUS_APPROVED.equals(status)) {
            throw new IllegalArgumentException("この月は承認済みのため修正できません");
        }
        // RETURNED および WITHDRAWN は編集可能のため通過
    }

    /**
     * 指定月の勤怠申請を提出し、既存申請があれば再申請状態に更新します。
     */
    @CacheEvict(cacheNames = "pendingSubmissionsCount", allEntries = true)
    public AttendanceSubmission submitMonth(Long userId, YearMonth yearMonth) {
        Optional<AttendanceSubmission> existing = getSubmission(userId, yearMonth);
        String key = toYearMonthKey(yearMonth);

        int startDay = attendancePeriodSettingService.getStartDay();
        int endDay = attendancePeriodSettingService.getEndDay();
        if (startDay < 1 || startDay > 28) {
            startDay = 21;
        }
        if (endDay < 1 || endDay > 28) {
            endDay = 20;
        }
        LocalDate startDate = yearMonth.minusMonths(1).atDay(startDay);
        LocalDate endDate = yearMonth.atDay(endDay);

        if (existing.isPresent()) {
            AttendanceSubmission submission = existing.get();
            if (STATUS_PENDING.equals(submission.getStatus())) {
                throw new IllegalArgumentException("この月は既に申請中です");
            }
            if (STATUS_APPROVED.equals(submission.getStatus())) {
                throw new IllegalArgumentException("この月は既に承認済みです");
            }

            submission.setStatus(STATUS_PENDING);
            submission.setSubmittedAt(Instant.now());
            submission.setActionBy(null);
            submission.setActionComment(null);
            submission.setActionAt(null);
            submission.setStartDate(startDate);
            submission.setEndDate(endDate);
            attendanceSubmissionMapper.update(submission);

            auditLogService.recordSubmissionEvent(
                    AuditEventType.SUBMISSION_SUBMITTED,
                    userId,
                    userId,
                    submission.getSubmissionId(),
                    "対象月: " + key + " (再申請)");
            return submission;
        }

        AttendanceSubmission newSubmission = AttendanceSubmission.builder()
                .userId(userId)
                .targetYearMonth(key)
                .status(STATUS_PENDING)
                .submittedAt(Instant.now())
                .startDate(startDate)
                .endDate(endDate)
                .build();
        try {
            attendanceSubmissionMapper.insert(newSubmission);
        } catch (DuplicateKeyException e) {
            // uq_attendance_submissions_user_month に対する同時実行競合（申請ボタンの二重クリック等）。
            throw new IllegalArgumentException("この月は既に申請中です", e);
        }

        auditLogService.recordSubmissionEvent(
                AuditEventType.SUBMISSION_SUBMITTED,
                userId,
                userId,
                newSubmission.getSubmissionId(),
                "対象月: " + key);
        return newSubmission;
    }

    /**
     * 現在ユーザーが承認可能な申請中一覧を返します。
     */
    @Cacheable(cacheNames = "pendingSubmissionsCount", key = "#currentUser.userId")
    public List<AttendanceSubmission> getPendingSubmissions(User currentUser) {
        ensureApprover(currentUser);

        List<AttendanceSubmission> submissions = attendanceSubmissionMapper.selectByStatus(STATUS_PENDING);
        if (currentUser.getUserRole() == com.attendance.app.entity.UserRole.ADMIN) {
            return submissions;
        }

        return submissions.stream()
                .filter(submission -> canApproveSubmission(currentUser, submission.getUserId()))
                .toList();
    }

    /**
     * 申請中の勤怠申請を承認します。
     */
    @CacheEvict(cacheNames = "pendingSubmissionsCount", allEntries = true)
    public void approve(Long submissionId, Long approverUserId, String comment) {
        User approver = userService.getUserById(approverUserId)
                .orElseThrow(() -> new IllegalArgumentException("承認ユーザーが見つかりません"));
        ensureApprover(approver);

        AttendanceSubmission submission = attendanceSubmissionMapper.selectByIdForUpdate(submissionId)
                .orElseThrow(() -> new IllegalArgumentException("申請が見つかりません"));

        if (!canApproveSubmission(approver, submission.getUserId())) {
            throw new IllegalArgumentException("この申請を承認する権限がありません");
        }

        if (!STATUS_PENDING.equals(submission.getStatus())) {
            throw new IllegalArgumentException("申請中のデータのみ承認できます");
        }

        submission.setStatus(STATUS_APPROVED);
        submission.setActionBy(approverUserId);
        submission.setActionComment(normalizeComment(comment));
        submission.setActionAt(Instant.now());
        attendanceSubmissionMapper.update(submission);

        auditLogService.recordSubmissionEvent(
                AuditEventType.SUBMISSION_APPROVED,
                approverUserId,
                submission.getUserId(),
                submissionId,
                "対象月: " + submission.getTargetYearMonth());
    }

    /**
     * 申請中の勤怠申請を差し戻します。
     */
    @CacheEvict(cacheNames = "pendingSubmissionsCount", allEntries = true)
    public void returnForCorrection(Long submissionId, Long approverUserId, String comment) {
        User approver = userService.getUserById(approverUserId)
                .orElseThrow(() -> new IllegalArgumentException("承認ユーザーが見つかりません"));
        ensureApprover(approver);

        AttendanceSubmission submission = attendanceSubmissionMapper.selectByIdForUpdate(submissionId)
                .orElseThrow(() -> new IllegalArgumentException("申請が見つかりません"));

        if (!canApproveSubmission(approver, submission.getUserId())) {
            throw new IllegalArgumentException("この申請を差し戻す権限がありません");
        }

        if (!STATUS_PENDING.equals(submission.getStatus())) {
            throw new IllegalArgumentException("申請中のデータのみ差し戻しできます");
        }

        submission.setStatus(STATUS_RETURNED);
        submission.setActionBy(approverUserId);
        submission.setActionComment(normalizeComment(comment));
        submission.setActionAt(Instant.now());
        attendanceSubmissionMapper.update(submission);

        auditLogService.recordSubmissionEvent(
                AuditEventType.SUBMISSION_RETURNED,
                approverUserId,
                submission.getUserId(),
                submissionId,
                "対象月: " + submission.getTargetYearMonth());
    }

    /**
     * 申請者本人が申請中の勤怠申請を取り下げます。
     */
    @CacheEvict(cacheNames = "pendingSubmissionsCount", allEntries = true)
    public void withdrawSubmission(Long userId, YearMonth yearMonth) {
        Optional<AttendanceSubmission> existing = getSubmission(userId, yearMonth);
        if (existing.isEmpty()) {
            throw new IllegalArgumentException("申請が見つかりません");
        }
        if (!STATUS_PENDING.equals(existing.get().getStatus())) {
            throw new IllegalArgumentException("申請中のデータのみ取り下げできます");
        }
        // 取り下げ日時を保持するためソフトデリート（WITHDRAWN ステータスに更新）
        AttendanceSubmission submission = existing.get();
        submission.setStatus(STATUS_WITHDRAWN);
        submission.setActionBy(userId);
        submission.setActionAt(Instant.now());
        attendanceSubmissionMapper.update(submission);

        auditLogService.recordSubmissionEvent(
                AuditEventType.SUBMISSION_WITHDRAWN,
                userId,
                userId,
                submission.getSubmissionId(),
                "対象月: " + submission.getTargetYearMonth());
    }

    /**
     * 対象月に属する勤怠申請一覧を返します。
     */
    public List<AttendanceSubmission> getSubmissionsByTargetYearMonth(YearMonth yearMonth) {
        return attendanceSubmissionMapper.selectByTargetYearMonth(toYearMonthKey(yearMonth));
    }

    /**
     * 承認済み申請を管理者が差し戻し状態へ戻します。
     */
    @CacheEvict(cacheNames = "pendingSubmissionsCount", allEntries = true)
    public void revokeApproval(Long submissionId, Long adminUserId) {
        AttendanceSubmission submission = attendanceSubmissionMapper.selectByIdForUpdate(submissionId)
                .orElseThrow(() -> new IllegalArgumentException("申請が見つかりません"));
        if (!STATUS_APPROVED.equals(submission.getStatus())) {
            throw new IllegalArgumentException("承認済みの申請のみ取り消しできます");
        }
        submission.setStatus(STATUS_RETURNED);
        submission.setActionBy(adminUserId);
        submission.setActionComment("管理者により承認を取り消しました");
        submission.setActionAt(Instant.now());
        attendanceSubmissionMapper.update(submission);

        auditLogService.recordSubmissionEvent(
                AuditEventType.APPROVAL_REVOKED,
                adminUserId,
                submission.getUserId(),
                submissionId,
                "対象月: " + submission.getTargetYearMonth());
    }

    /**
     * 勤怠日から、その日付が属する給与月を解決します。
     */
    public YearMonth resolvePayrollMonth(LocalDate attendanceDate) {
        int startDay = attendancePeriodSettingService.getStartDay();
        if (attendanceDate.getDayOfMonth() >= startDay) {
            return YearMonth.from(attendanceDate).plusMonths(1);
        }
        return YearMonth.from(attendanceDate);
    }

    private String toYearMonthKey(YearMonth yearMonth) {
        return yearMonth.format(YEAR_MONTH_FORMATTER);
    }

    private void ensureApprover(User user) {
        if (!userService.isAttendanceApprover(user)) {
            throw new IllegalArgumentException("勤怠承認権限がありません");
        }
    }

    /**
     * 指定した承認者が申請者の勤怠申請を承認できるかを判定します。
     * 承認可否（個人/部署アサイン優先、フォールバックで同一勤務クラスのみ）を
     * 外部（通知の宛先絞り込み等）から利用するための公開ラッパーです。
     *
     * @param approver        承認者ユーザー
     * @param applicantUserId 申請者のユーザーID
     * @return 承認可能であれば true
     */
    @Transactional(readOnly = true)
    public boolean canApprove(User approver, Long applicantUserId) {
        return canApproveSubmission(approver, applicantUserId);
    }

    private boolean canApproveSubmission(User approver, Long applicantUserId) {
        if (approver == null || applicantUserId == null) {
            return false;
        }

        if (!userService.isAttendanceApprover(approver)) {
            return false;
        }

        if (approver.getUserRole() == com.attendance.app.entity.UserRole.ADMIN) {
            return true;
        }

        User applicant = userService.getUserById(applicantUserId).orElse(null);
        if (applicant == null) {
            return false;
        }

        // 管理者が個人・部署アサインを1件でも設定している場合は、アサイン済み承認者のみ許可する
        List<Long> assignedApproverIds = approverAssignmentService.resolveAssignedApproverIds(
                applicantUserId, applicant.getClassName());
        if (!assignedApproverIds.isEmpty()) {
            return assignedApproverIds.contains(approver.getUserId());
        }

        // アサインが一件も無い場合は、同じ勤務クラスの申請のみ承認可能というフォールバックルールを適用
        String approverClass = approver.getClassName();
        if (approverClass == null || approverClass.trim().isEmpty()) {
            return false;
        }

        String applicantClass = applicant.getClassName();
        return approverClass.trim().equals(applicantClass != null ? applicantClass.trim() : null);
    }

    private String normalizeComment(String comment) {
        if (comment == null) {
            return null;
        }
        String trimmed = comment.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
