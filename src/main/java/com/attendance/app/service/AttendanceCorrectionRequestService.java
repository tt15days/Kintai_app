package com.attendance.app.service;

import com.attendance.app.entity.AttendanceCorrectionRequest;
import com.attendance.app.entity.AttendanceRecord;
import com.attendance.app.entity.User;
import com.attendance.app.entity.UserRole;
import com.attendance.app.mapper.AttendanceCorrectionRequestMapper;
import com.attendance.app.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * AttendanceCorrectionRequest Service - 勤怠修正申請の業務ロジック
 *
 * フロー:
 *   1. ユーザーが修正内容と理由を入力して submitRequest() を呼び出す（PENDING）
 *   2. 承認者が approveRequest() を呼び出す → attendance_records に即時反映（APPROVED）
 *   3. 承認者が rejectRequest() を呼び出す → 勤怠レコードは変更なし（REJECTED）
 *   4. ユーザーが withdrawRequest() を呼び出す → 取り下げ（WITHDRAWN）
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AttendanceCorrectionRequestService {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_WITHDRAWN = "WITHDRAWN";

    private static final DateTimeFormatter YEAR_MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final int REASON_MAX_LENGTH = 500;

    private final AttendanceCorrectionRequestMapper correctionRequestMapper;
    private final AttendanceRecordService attendanceRecordService;
    private final AttendanceSubmissionService attendanceSubmissionService;
    private final UserService userService;

    /**
     * ユーザーが勤怠修正申請を提出します。
     * 申請時点の勤怠記録をスナップショットとして保存します。
     *
     * @param userId             申請ユーザーID
     * @param attendanceDate     修正対象日
     * @param requestedStartTime 修正後の開始時刻（null = 開始時刻を削除）
     * @param requestedEndTime   修正後の終了時刻（null = 終了時刻を削除）
     * @param requestedRemarks   修正後の備考
     * @param reason             修正理由（必須）
     * @return 作成された修正申請
     */
    @CacheEvict(cacheNames = "pendingCorrectionsCount", allEntries = true)
    public AttendanceCorrectionRequest submitRequest(
            Long userId,
            LocalDate attendanceDate,
            LocalTime requestedStartTime,
            LocalTime requestedEndTime,
            String requestedRemarks,
            String reason) {

        if (attendanceDate == null) {
            throw new IllegalArgumentException("修正対象日を指定してください");
        }
        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("修正理由を入力してください");
        }
        if (reason.trim().length() > REASON_MAX_LENGTH) {
            throw new IllegalArgumentException("修正理由は" + REASON_MAX_LENGTH + "文字以内で入力してください");
        }

        // 対象月を決定
        YearMonth targetYearMonth = attendanceSubmissionService.resolvePayrollMonth(attendanceDate);
        String targetYearMonthKey = targetYearMonth.format(YEAR_MONTH_FORMATTER);

        // 既に申請中の修正申請がないか確認
        List<AttendanceCorrectionRequest> existing = correctionRequestMapper.selectByUserAndMonth(userId, targetYearMonthKey);
        boolean hasPending = existing.stream()
                .anyMatch(r -> attendanceDate.equals(r.getAttendanceDate()) && STATUS_PENDING.equals(r.getStatus()));
        if (hasPending) {
            throw new IllegalArgumentException("この日付には既に承認待ちの修正申請があります");
        }

        // 現在の勤怠記録をスナップショット
        Optional<AttendanceRecord> currentRecord = attendanceRecordService.getRecordByUserAndDate(userId, attendanceDate);
        LocalTime currentStart = null;
        LocalTime currentEnd = null;
        String currentRemarks = null;
        Double currentNightShiftHours = null;
        if (currentRecord.isPresent()) {
            AttendanceRecord rec = currentRecord.get();
            if (rec.getStartTime() != null) {
                currentStart = DateTimeUtil.toLocalTime(rec.getStartTime());
            }
            if (rec.getEndTime() != null) {
                currentEnd = DateTimeUtil.toLocalTime(rec.getEndTime());
            }
            currentRemarks = rec.getRemarks();
            currentNightShiftHours = rec.getNightShiftHours();
        }

        AttendanceCorrectionRequest request = AttendanceCorrectionRequest.builder()
                .userId(userId)
                .attendanceDate(attendanceDate)
                .targetYearMonth(targetYearMonthKey)
                .requestedStartTime(requestedStartTime)
                .requestedEndTime(requestedEndTime)
                .requestedRemarks(normalizeText(requestedRemarks))
                // requestedNightShiftHours is calculated automatically when applying correction,
                // but we might want to store what it *would* be here if needed.
                // For now, let's just leave requestedNightShiftHours as null or not set it explicitly if not strictly needed.
                // Wait, let's just leave it empty and let the DB default to null, or set it to 0.0.
                .reason(reason.trim())
                .currentStartTime(currentStart)
                .currentEndTime(currentEnd)
                .currentRemarks(currentRemarks)
                .currentNightShiftHours(currentNightShiftHours)
                .status(STATUS_PENDING)
                .submittedAt(Instant.now())
                .build();

        correctionRequestMapper.insert(request);
        log.info("勤怠修正申請を登録: userId={}, attendanceDate={}, requestId={}", userId, attendanceDate, request.getRequestId());
        return request;
    }

    /**
     * 申請者が修正申請を取り下げます（PENDING → WITHDRAWN）。
     *
     * @param requestId 申請ID
     * @param userId    操作ユーザーID（本人確認用）
     */
    @CacheEvict(cacheNames = "pendingCorrectionsCount", allEntries = true)
    public void withdrawRequest(Long requestId, Long userId) {
        AttendanceCorrectionRequest request = correctionRequestMapper.selectByIdForUpdate(requestId)
                .orElseThrow(() -> new IllegalArgumentException("修正申請が見つかりません"));
        if (!request.getUserId().equals(userId)) {
            throw new IllegalArgumentException("自分の申請のみ取り下げできます");
        }
        if (!STATUS_PENDING.equals(request.getStatus())) {
            throw new IllegalArgumentException("承認待ちの申請のみ取り下げできます");
        }
        request.setStatus(STATUS_WITHDRAWN);
        request.setActionBy(userId);
        request.setActionAt(Instant.now());
        correctionRequestMapper.update(request);
        log.info("勤怠修正申請を取り下げ: requestId={}, userId={}", requestId, userId);
    }

    /**
     * 承認者が修正申請を承認します（PENDING → APPROVED）。
     * 承認後、attendance_records の対象日を修正内容で更新します。
     *
     * @param requestId       申請ID
     * @param approverUserId  承認者ユーザーID
     * @param comment         承認コメント（任意）
     */
    @CacheEvict(cacheNames = "pendingCorrectionsCount", allEntries = true)
    public void approveRequest(Long requestId, Long approverUserId, String comment) {
        User approver = userService.getUserById(approverUserId)
                .orElseThrow(() -> new IllegalArgumentException("承認ユーザーが見つかりません"));
        ensureApprover(approver);

        AttendanceCorrectionRequest request = correctionRequestMapper.selectByIdForUpdate(requestId)
                .orElseThrow(() -> new IllegalArgumentException("修正申請が見つかりません"));

        if (!canApproveRequest(approver, request.getUserId())) {
            throw new IllegalArgumentException("この申請を承認する権限がありません");
        }
        if (!STATUS_PENDING.equals(request.getStatus())) {
            throw new IllegalArgumentException("承認待ちの申請のみ承認できます");
        }

        // 申請内容を attendance_records に反映
        applyCorrection(request);

        request.setStatus(STATUS_APPROVED);
        request.setActionBy(approverUserId);
        request.setActionComment(normalizeText(comment));
        request.setActionAt(Instant.now());
        correctionRequestMapper.update(request);
        log.info("勤怠修正申請を承認: requestId={}, approverUserId={}, attendanceDate={}",
                requestId, approverUserId, request.getAttendanceDate());
    }

    /**
     * 承認者が修正申請を却下します（PENDING → REJECTED）。
     *
     * @param requestId       申請ID
     * @param approverUserId  承認者ユーザーID
     * @param comment         却下理由（任意）
     */
    @CacheEvict(cacheNames = "pendingCorrectionsCount", allEntries = true)
    public void rejectRequest(Long requestId, Long approverUserId, String comment) {
        User approver = userService.getUserById(approverUserId)
                .orElseThrow(() -> new IllegalArgumentException("承認ユーザーが見つかりません"));
        ensureApprover(approver);

        AttendanceCorrectionRequest request = correctionRequestMapper.selectByIdForUpdate(requestId)
                .orElseThrow(() -> new IllegalArgumentException("修正申請が見つかりません"));

        if (!canApproveRequest(approver, request.getUserId())) {
            throw new IllegalArgumentException("この申請を却下する権限がありません");
        }
        if (!STATUS_PENDING.equals(request.getStatus())) {
            throw new IllegalArgumentException("承認待ちの申請のみ却下できます");
        }

        request.setStatus(STATUS_REJECTED);
        request.setActionBy(approverUserId);
        request.setActionComment(normalizeText(comment));
        request.setActionAt(Instant.now());
        correctionRequestMapper.update(request);
        log.info("勤怠修正申請を却下: requestId={}, approverUserId={}", requestId, approverUserId);
    }

    /**
     * 承認者が確認できる承認待ち修正申請一覧を返します。
     *
     * @param approver 承認者ユーザー
     * @return 承認待ち修正申請一覧
     */
    @Cacheable(cacheNames = "pendingCorrectionsCount", key = "#approver.userId")
    public List<AttendanceCorrectionRequest> getPendingRequests(User approver) {
        ensureApprover(approver);
        List<AttendanceCorrectionRequest> pendingRequests = correctionRequestMapper.selectByStatus(STATUS_PENDING);
        if (approver.getUserRole() == UserRole.ADMIN) {
            return pendingRequests;
        }
        return pendingRequests.stream()
                .filter(req -> canApproveRequest(approver, req.getUserId()))
                .toList();
    }

    /**
     * ユーザー自身の修正申請一覧を返します。
     *
     * @param userId ユーザーID
     * @return 修正申請一覧（新しい順）
     */
    public List<AttendanceCorrectionRequest> getRequestsByUserId(Long userId) {
        return correctionRequestMapper.selectByUserId(userId);
    }

    /**
     * 申請IDで修正申請を取得します。
     *
     * @param requestId 申請ID
     * @return 修正申請（存在しない場合は empty）
     */
    @Transactional(readOnly = true)
    public Optional<AttendanceCorrectionRequest> getRequestById(Long requestId) {
        return correctionRequestMapper.selectById(requestId);
    }

    /**
     * 指定月のユーザーの修正申請一覧を返します。
     *
     * @param userId          ユーザーID
     * @param targetYearMonth 対象月（YearMonth）
     * @return 修正申請一覧
     */
    public List<AttendanceCorrectionRequest> getRequestsByUserAndMonth(Long userId, YearMonth targetYearMonth) {
        return correctionRequestMapper.selectByUserAndMonth(userId, targetYearMonth.format(YEAR_MONTH_FORMATTER));
    }

    // -------------------------------------------------------
    // private
    // -------------------------------------------------------

    /**
     * 修正申請の内容を attendance_records に反映します。
     * 勤怠記録が存在しない場合は新規作成します。
     *
     * @param request 修正申請情報
     */
    private void applyCorrection(AttendanceCorrectionRequest request) {
        LocalDate attendanceDate = request.getAttendanceDate();
        Long userId = request.getUserId();
        LocalTime newStart = request.getRequestedStartTime();
        LocalTime newEnd = request.getRequestedEndTime();
        String newRemarks = request.getRequestedRemarks();

        Optional<AttendanceRecord> existing = attendanceRecordService.getRecordByUserAndDate(userId, attendanceDate);
        Integer eventTypeId = existing.map(r -> r.getEventTypeId()).orElse(null);

        if (newStart != null || newEnd != null) {
            // saveRecord は開始または終了があれば記録を作成/更新する
            attendanceRecordService.saveRecord(userId, attendanceDate, newStart, newEnd, newRemarks, false, eventTypeId);
        } else {
            // 開始も終了も null の場合は既存レコードの備考のみ更新（または削除しない）
            if (existing.isPresent()) {
                AttendanceRecord rec = existing.get();
                rec.setRemarks(newRemarks);
                // updateRemarks は専用メソッドがないため saveRecord 経由で更新
                LocalTime existingStart = rec.getStartTime() != null
                        ? DateTimeUtil.toLocalTime(rec.getStartTime()) : null;
                LocalTime existingEnd = rec.getEndTime() != null
                        ? DateTimeUtil.toLocalTime(rec.getEndTime()) : null;
                attendanceRecordService.saveRecord(userId, attendanceDate, existingStart, existingEnd, newRemarks, false, eventTypeId);
            }
        }
    }

    /**
     * ユーザーが勤怠承認権限を持っているかを検証します。
     *
     * @param user 検証対象のユーザー
     * @throws IllegalArgumentException 勤怠承認権限がない場合
     */
    private void ensureApprover(User user) {
        if (!userService.isAttendanceApprover(user)) {
            throw new IllegalArgumentException("勤怠承認権限がありません");
        }
    }

    /**
     * 承認者が特定の申請者の修正申請を承認可能かどうかを判定します。
     *
     * @param approver 承認者ユーザー
     * @param applicantUserId 申請者ユーザーID
     * @return 承認可能であれば {@code true}、そうでなければ {@code false}
     */
    private boolean canApproveRequest(User approver, Long applicantUserId) {
        if (approver == null || applicantUserId == null) {
            return false;
        }
        if (!userService.isAttendanceApprover(approver)) {
            return false;
        }
        if (approver.getUserRole() == UserRole.ADMIN) {
            return true;
        }
        String approverClass = approver.getClassName();
        if (approverClass == null || approverClass.trim().isEmpty()) {
            return false;
        }
        User applicant = userService.getUserById(applicantUserId).orElse(null);
        if (applicant == null) {
            return false;
        }
        String applicantClass = applicant.getClassName();
        return approverClass.trim().equals(applicantClass != null ? applicantClass.trim() : null);
    }

    /**
     * 文字列の前後の空白を取り除き、空文字列の場合は null に正規化します。
     *
     * @param value 正規化対象の文字列
     * @return 正規化後の文字列、または {@code null}
     */
    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
