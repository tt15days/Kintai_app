package com.attendance.app.controller;

import com.attendance.app.entity.AttendanceCorrectionRequest;
import com.attendance.app.entity.AttendanceRecord;
import com.attendance.app.entity.AttendanceSubmission;
import com.attendance.app.entity.LeaveApplication;
import com.attendance.app.entity.LeaveStatus;
import com.attendance.app.entity.LeaveType;
import com.attendance.app.entity.User;
import com.attendance.app.entity.UserRole;
import com.attendance.app.entity.WorkScheduleClass;
import com.attendance.app.security.SecurityUtil;
import com.attendance.app.service.AttendanceCorrectionRequestService;
import com.attendance.app.service.AttendanceRecordService;
import com.attendance.app.service.AttendanceSubmissionService;
import com.attendance.app.service.BatchSettingService;
import com.attendance.app.service.LeaveApplicationService;
import com.attendance.app.service.PaidLeaveBalanceService;
import com.attendance.app.service.UserNotificationService;
import com.attendance.app.service.UserService;
import com.attendance.app.service.WorkScheduleClassService;
import com.attendance.app.service.EventTypeService;
import com.attendance.app.service.HolidayService;
import com.attendance.app.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/attendance/approval")
@PreAuthorize("isAuthenticated()")
public class AttendanceApprovalController {

    private static final String APPROVAL_VIEW = "user/attendance-approval";
    private static final String APPROVAL_REDIRECT = "redirect:/attendance/approval";
    private static final String CORRECTION_APPROVAL_REDIRECT = "redirect:/attendance/approval/corrections";
    private static final String USER_ATTENDANCE_DETAIL_VIEW = "admin/user-attendance-detail";
    private static final LocalTime DEFAULT_STANDARD_END_TIME = LocalTime.of(18, 0);
    private static final String INVALID_YEAR_MONTH_LOG = "yearMonth形式が不正: {}";

    private final AttendanceSubmissionService attendanceSubmissionService;
    private final AttendanceCorrectionRequestService correctionRequestService;
    private final SecurityUtil securityUtil;
    private final UserService userService;
    private final UserNotificationService userNotificationService;
    private final AttendanceRecordService attendanceRecordService;
    private final LeaveApplicationService leaveApplicationService;
    private final WorkScheduleClassService workScheduleClassService;
    private final EventTypeService eventTypeService;
    private final HolidayService holidayService;
    private final PaidLeaveBalanceService paidLeaveBalanceService;
    private final BatchSettingService batchSettingService;

    /**
     * 現在ユーザーが承認可能な勤怠申請一覧を表示します。
     */
    @GetMapping
    public String showPendingApprovals(Model model) {
        try {
            User currentUser = securityUtil.getCurrentUser();
            List<AttendanceSubmission> pendingSubmissions = attendanceSubmissionService.getPendingSubmissions(currentUser);

            // N+1対策: 申請者IDをdistinctに収集し、ユーザー取得はID単位で1回のみ行う
            Map<Long, User> applicantUsers = new HashMap<>();
            Set<Long> applicantUserIds = pendingSubmissions.stream()
                    .map(submission -> submission.getUserId())
                    .collect(java.util.stream.Collectors.toSet());
            for (Long applicantUserId : applicantUserIds) {
                userService.getUserById(applicantUserId)
                        .ifPresent(user -> applicantUsers.put(user.getUserId(), user));
            }

            model.addAttribute("pendingSubmissions", pendingSubmissions);
            model.addAttribute("applicantUsers", applicantUsers);
            model.addAttribute("submissionStatusPending", AttendanceSubmissionService.STATUS_PENDING);
            model.addAttribute("submissionStatusApproved", AttendanceSubmissionService.STATUS_APPROVED);
            model.addAttribute("submissionStatusReturned", AttendanceSubmissionService.STATUS_RETURNED);
        } catch (Exception e) {
            log.error("承認一覧の表示に失敗: {}", e.getMessage());
            model.addAttribute("error", "承認一覧の表示に失敗しました");
            model.addAttribute("pendingSubmissions", java.util.Collections.emptyList());
            model.addAttribute("applicantUsers", java.util.Collections.emptyMap());
        }
        return APPROVAL_VIEW;
    }

    /**
     * 勤怠申請を承認し、一覧画面へ戻します。
     */
    @PostMapping("/{submissionId}/approve")
    public String approveSubmission(
            @PathVariable Long submissionId,
            @RequestParam(required = false) String comment,
            RedirectAttributes redirectAttributes) {
        try {
            Long approverUserId = securityUtil.getCurrentUserId();
            // 申請情報を承認前に取得（通知生成用）
            AttendanceSubmission submission = attendanceSubmissionService.getSubmissionById(submissionId).orElse(null);
            attendanceSubmissionService.approve(submissionId, approverUserId, comment);
            if (submission != null) {
                userNotificationService.notifyApplicantApproved(submission.getUserId(),
                        submission.getTargetYearMonth() + "分の月次勤怠申請");
            }
            redirectAttributes.addFlashAttribute("message", "勤怠申請を承認しました");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            log.warn("勤怠申請の承認に失敗: {}", e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "勤怠申請の承認に失敗しました");
            log.error("勤怠申請の承認に失敗: {}", e.getMessage());
        }
        return APPROVAL_REDIRECT;
    }

    /**
     * 勤怠申請を差し戻し、一覧画面へ戻します。
     */
    @PostMapping("/{submissionId}/return")
    public String returnSubmission(
            @PathVariable Long submissionId,
            @RequestParam(required = false) String comment,
            RedirectAttributes redirectAttributes) {
        try {
            Long approverUserId = securityUtil.getCurrentUserId();
            // 申請情報を差し戻し前に取得（通知生成用）
            AttendanceSubmission submission = attendanceSubmissionService.getSubmissionById(submissionId).orElse(null);
            attendanceSubmissionService.returnForCorrection(submissionId, approverUserId, comment);
            if (submission != null) {
                userNotificationService.notifyApplicantReturned(submission.getUserId(),
                        submission.getTargetYearMonth() + "分の月次勤怠申請", comment);
            }
            redirectAttributes.addFlashAttribute("message", "勤怠申請を差し戻しました");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            log.warn("勤怠申請の差し戻しに失敗: {}", e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "勤怠申請の差し戻しに失敗しました");
            log.error("勤怠申請の差し戻しに失敗: {}", e.getMessage());
        }
        return APPROVAL_REDIRECT;
    }

    /**
     * 勤怠修正申請の承認待ち一覧を表示します。
     */
    @GetMapping("/corrections")
    public String showPendingCorrectionRequests(Model model) {
        try {
            User currentUser = securityUtil.getCurrentUser();
            List<AttendanceCorrectionRequest> pendingRequests =
                    correctionRequestService.getPendingRequests(currentUser);

            // N+1対策: 申請者IDをdistinctに収集し、ユーザー取得はID単位で1回のみ行う
            Map<Long, User> applicantUsers = new HashMap<>();
            Set<Long> applicantUserIds = pendingRequests.stream()
                    .map(request -> request.getUserId())
                    .collect(java.util.stream.Collectors.toSet());
            for (Long applicantUserId : applicantUserIds) {
                userService.getUserById(applicantUserId)
                        .ifPresent(user -> applicantUsers.put(user.getUserId(), user));
            }

            model.addAttribute("pendingRequests", pendingRequests);
            model.addAttribute("applicantUsers", applicantUsers);
            model.addAttribute("correctionStatusPending", AttendanceCorrectionRequestService.STATUS_PENDING);
            model.addAttribute("correctionStatusApproved", AttendanceCorrectionRequestService.STATUS_APPROVED);
            model.addAttribute("correctionStatusRejected", AttendanceCorrectionRequestService.STATUS_REJECTED);
        } catch (Exception e) {
            log.error("勤怠修正申請一覧の表示に失敗: {}", e.getMessage());
            model.addAttribute("error", "勤怠修正申請一覧の表示に失敗しました");
            model.addAttribute("pendingRequests", java.util.Collections.emptyList());
            model.addAttribute("applicantUsers", java.util.Collections.emptyMap());
        }
        return "user/correction-approval";
    }

    /**
     * 勤怠修正申請を承認します。
     */
    @PostMapping("/corrections/{requestId}/approve")
    public String approveCorrectionRequest(
            @PathVariable Long requestId,
            @RequestParam(required = false) String comment,
            RedirectAttributes redirectAttributes) {
        try {
            Long approverUserId = securityUtil.getCurrentUserId();
            // 申請情報を承認前に取得（通知生成用）
            AttendanceCorrectionRequest request = correctionRequestService.getRequestById(requestId).orElse(null);
            correctionRequestService.approveRequest(requestId, approverUserId, comment);
            if (request != null) {
                userNotificationService.notifyApplicantApproved(request.getUserId(),
                        request.getAttendanceDate() + " の勤怠修正申請");
            }
            redirectAttributes.addFlashAttribute("message", "勤怠修正申請を承認し、勤怠記録を更新しました");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            log.warn("勤怠修正申請の承認に失敗: {}", e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "勤怠修正申請の承認に失敗しました");
            log.error("勤怠修正申請の承認に失敗: {}", e.getMessage());
        }
        return CORRECTION_APPROVAL_REDIRECT;
    }

    /**
     * 勤怠修正申請を却下します。
     */
    @PostMapping("/corrections/{requestId}/reject")
    public String rejectCorrectionRequest(
            @PathVariable Long requestId,
            @RequestParam(required = false) String comment,
            RedirectAttributes redirectAttributes) {
        try {
            Long approverUserId = securityUtil.getCurrentUserId();
            // 申請情報を却下前に取得（通知生成用）
            AttendanceCorrectionRequest request = correctionRequestService.getRequestById(requestId).orElse(null);
            correctionRequestService.rejectRequest(requestId, approverUserId, comment);
            if (request != null) {
                userNotificationService.notifyApplicantRejected(request.getUserId(),
                        request.getAttendanceDate() + " の勤怠修正申請", comment);
            }
            redirectAttributes.addFlashAttribute("message", "勤怠修正申請を却下しました");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            log.warn("勤怠修正申請の却下に失敗: {}", e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "勤怠修正申請の却下に失敗しました");
            log.error("勤怠修正申請の却下に失敗: {}", e.getMessage());
        }
        return CORRECTION_APPROVAL_REDIRECT;
    }

    /**
     * ユーザーの勤怠記録詳細を表示します。
     * 承認待ちのリスト等から遷移します。一般承認者は同一勤務クラスのユーザーのみ閲覧可能です。
     */
    @GetMapping("/{userId}/detail")
    public String showUserAttendanceRecords(
            @PathVariable Long userId,
            @RequestParam(required = false) String yearMonth,
            @RequestParam(required = false) String from,
            Model model) {

        checkViewPermission(userId);

        User user = null;
        YearMonth currentMonth = parseYearMonthOrNow(yearMonth);
        List<AttendanceRecord> records = Collections.emptyList();
        double totalWorkingHours = 0.0;
        double totalOvertimeHours = 0.0;
        double totalSaturdayWorkHours = 0.0;
        double totalHolidayWorkHours = 0.0;
        java.math.BigDecimal totalPaidLeaveDays = java.math.BigDecimal.ZERO;
        double totalPaidLeaveHours = 0.0;
        long totalUnpaidLeaveDays = 0;
        long totalAbsenceDays = 0;
        AttendanceSubmission submission = null;
        java.math.BigDecimal yearlyUsedPaidLeaveDays = java.math.BigDecimal.ZERO;
        java.math.BigDecimal remainingPaidLeaveDays = java.math.BigDecimal.ZERO;
        String article36Status = "NORMAL";

        try {
            user = userService.getUserById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("ユーザーが見つかりません"));

            records = attendanceRecordService.getRecordsByUserAndMonth(userId, currentMonth);
            totalWorkingHours = records.stream()
                    .filter(r -> r.getWorkingHours() != null)
                    .mapToDouble(r -> r.getWorkingHours().doubleValue())
                    .sum();
            totalOvertimeHours = records.stream()
                    .mapToDouble(r -> this.resolveOvertimeHours(r))
                    .sum();

            // 土曜・日祝休出時間の集計
            for (AttendanceRecord r : records) {
                if (r.getHolidayWorkHours() != null && r.getHolidayWorkHours() > 0) {
                    LocalDate recDate = DateTimeUtil.toLocalDate(r.getAttendanceDate());
                    if (recDate != null) {
                        if (recDate.getDayOfWeek().getValue() == 6) {
                            totalSaturdayWorkHours += r.getHolidayWorkHours();
                        } else {
                            totalHolidayWorkHours += r.getHolidayWorkHours();
                        }
                    }
                }
            }

            // 休暇集計
            AttendanceRecordService.MonthRange monthRange = attendanceRecordService.getMonthRange(currentMonth);
            List<LeaveApplication> leaveApplications = leaveApplicationService
                    .getApplicationsByUserAndDateRange(userId, monthRange.getStartDate(), monthRange.getEndDate());
            if (leaveApplications != null) {
                Map<LocalDate, LeaveApplication> leaveMap = new HashMap<>();
                for (LeaveApplication la : leaveApplications) {
                    LocalDate d = la.getLeaveStartDate();
                    while (!d.isAfter(la.getLeaveEndDate())) {
                        leaveMap.put(d, la);
                        d = d.plusDays(1);
                    }
                }
                for (LeaveApplication la : leaveMap.values()) {
                    if (la.getStatus() == LeaveStatus.APPROVED) {
                        if (la.getLeaveType() == LeaveType.PAID_LEAVE) {
                            totalPaidLeaveDays = totalPaidLeaveDays.add(
                                    leaveApplicationService.calculateDailyConsumedDays(la.getLeaveDurationType()));
                        } else if (la.getLeaveType() == LeaveType.UNPAID_LEAVE) {
                            totalUnpaidLeaveDays++;
                        } else if (la.getLeaveType() == LeaveType.ABSENCE) {
                            totalAbsenceDays++;
                        }
                    }
                }
            }
            totalPaidLeaveHours = totalPaidLeaveDays.doubleValue() * attendanceRecordService.getStandardWorkingHours(userId, currentMonth);

            // 当年の有給使用日数と残日数を計算
            int currentYear = LocalDate.now().getYear();
            yearlyUsedPaidLeaveDays = leaveApplicationService.calculateYearlyUsedPaidLeaveDays(userId, currentYear);
            remainingPaidLeaveDays = paidLeaveBalanceService.getTotalRemainingDays(userId);
            article36Status = attendanceRecordService.checkArticle36(totalOvertimeHours);

            submission = attendanceSubmissionService
                    .getSubmission(userId, currentMonth).orElse(null);

            log.info("ユーザー勤怠記録詳細を表示: userId={}, yearMonth={}, count={}", userId, currentMonth, records.size());
        } catch (Exception e) {
            log.error("ユーザー勤怠記録詳細表示に失敗: {}", e.getMessage());
            model.addAttribute("error", "勤怠記録詳細の表示に失敗しました");
        }

        // 例外の有無に関わらず必ずモデルに設定する
        model.addAttribute("user", user);
        model.addAttribute("yearMonth", currentMonth);
        model.addAttribute("records", records);
        model.addAttribute("totalWorkingHours", totalWorkingHours);
        model.addAttribute("totalWorkingHoursStr", com.attendance.app.util.DateTimeUtil.formatHoursToHHmm(totalWorkingHours));
        model.addAttribute("totalOvertimeHours", totalOvertimeHours);
        model.addAttribute("totalOvertimeHoursStr", com.attendance.app.util.DateTimeUtil.formatHoursToHHmm(totalOvertimeHours));
        model.addAttribute("totalSaturdayWorkHours", totalSaturdayWorkHours);
        model.addAttribute("totalSaturdayWorkHoursStr", com.attendance.app.util.DateTimeUtil.formatHoursToHHmm(totalSaturdayWorkHours));
        model.addAttribute("totalHolidayWorkHours", totalHolidayWorkHours);
        model.addAttribute("totalHolidayWorkHoursStr", com.attendance.app.util.DateTimeUtil.formatHoursToHHmm(totalHolidayWorkHours));
        model.addAttribute("totalPaidLeaveDays", totalPaidLeaveDays);
        model.addAttribute("totalPaidLeaveHours", totalPaidLeaveHours);
        model.addAttribute("totalPaidLeaveHoursStr", com.attendance.app.util.DateTimeUtil.formatHoursToHHmm(totalPaidLeaveHours));
        model.addAttribute("totalUnpaidLeaveDays", totalUnpaidLeaveDays);
        model.addAttribute("totalAbsenceDays", totalAbsenceDays);
        model.addAttribute("submission", submission);
        model.addAttribute("yearlyUsedPaidLeaveDays", yearlyUsedPaidLeaveDays);
        model.addAttribute("remainingPaidLeaveDays", remainingPaidLeaveDays);
        model.addAttribute("article36Status", article36Status);
        model.addAttribute("article36MonthlyLimit", batchSettingService.getAlertArticle36Limit2());
        model.addAttribute("article36MonthlyWarning", batchSettingService.getAlertArticle36Limit1());
        model.addAttribute("currentYear", LocalDate.now().getYear());
        model.addAttribute("submissionStatusPending", AttendanceSubmissionService.STATUS_PENDING);
        model.addAttribute("submissionStatusApproved", AttendanceSubmissionService.STATUS_APPROVED);
        model.addAttribute("submissionStatusReturned", AttendanceSubmissionService.STATUS_RETURNED);
        model.addAttribute("submissionStatusWithdrawn", AttendanceSubmissionService.STATUS_WITHDRAWN);

        // テンプレート用: Instant → 日本時刻に変換した日付・時刻マップ
        Map<Long, LocalDate> recordDateMap = new HashMap<>();
        Map<Long, String> recordStartTimeMap = new HashMap<>();
        Map<Long, String> recordEndTimeMap = new HashMap<>();
        Map<Long, Double> recordOvertimeHoursMap = new HashMap<>();
        for (AttendanceRecord r : records) {
            LocalDate recDate = DateTimeUtil.toLocalDate(r.getAttendanceDate());
            recordDateMap.put(r.getRecordId(), recDate);
            recordOvertimeHoursMap.put(r.getRecordId(), resolveOvertimeHours(r));
            if (r.getStartTime() != null) {
                LocalTime st = DateTimeUtil.toLocalTime(r.getStartTime());
                recordStartTimeMap.put(r.getRecordId(), String.format("%02d:%02d", st.getHour(), st.getMinute()));
            }
            if (r.getEndTime() != null) {
                LocalTime et = DateTimeUtil.toLocalTime(r.getEndTime());
                recordEndTimeMap.put(r.getRecordId(), String.format("%02d:%02d", et.getHour(), et.getMinute()));
            }
        }
        model.addAttribute("recordDateMap", recordDateMap);
        model.addAttribute("recordStartTimeMap", recordStartTimeMap);
        model.addAttribute("recordEndTimeMap", recordEndTimeMap);
        model.addAttribute("recordOvertimeHoursMap", recordOvertimeHoursMap);

        // テンプレート用: ユーザーの勤務クラス情報
        WorkScheduleClass userScheduleClass = null;
        if (user != null && user.getClassName() != null && !user.getClassName().trim().isEmpty()) {
            userScheduleClass = workScheduleClassService.getClassByName(user.getClassName()).orElse(null);
        }
        model.addAttribute("userScheduleClass", userScheduleClass);

        // 戻るボタンの動的な宛先を設定
        User currentUser = securityUtil.getCurrentUser();
        String backUrl = "/dashboard";
        boolean isAdmin = securityUtil.hasRole("ROLE_ADMIN");
        boolean isApprover = userService.isAttendanceApprover(currentUser);

        if (isAdmin) {
            if ("admin".equals(from)) {
                backUrl = "/admin/attendance";
            } else {
                backUrl = "/attendance/approval";
            }
        } else if (isApprover) {
            backUrl = "/attendance/approval";
        }
        model.addAttribute("backUrl", backUrl);
        model.addAttribute("from", from);

        // テンプレート用: マスタデータ
        Map<Integer, String> eventTypeMap = new HashMap<>();
        for (var et : eventTypeService.getAllActiveEventTypes()) {
            eventTypeMap.put(et.getEventTypeId(), et.getDisplayName());
        }
        model.addAttribute("eventTypeMap", eventTypeMap);
        model.addAttribute("eventTypes", eventTypeService.getAllActiveEventTypes());
        try {
            model.addAttribute("holidays", holidayService.getHolidaysByYear(currentMonth.getYear()));
        } catch (Exception e) {
            log.error("祝日のロードに失敗: {}", e.getMessage());
            model.addAttribute("holidays", Collections.emptySet());
        }

        return USER_ATTENDANCE_DETAIL_VIEW;
    }

    /**
     * 管理者がユーザーの勤怠記録を一括保存します。
     */
    @PostMapping("/{userId}/detail/saveAll")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public String saveUserAttendance(
            @PathVariable Long userId,
            @RequestParam(required = false) String yearMonth,
            @RequestParam(required = false) String from,
            @RequestParam("attendanceDate") List<String> attendanceDateStrs,
            @RequestParam(value = "startTime", required = false) List<String> startTimeStrs,
            @RequestParam(value = "endTime", required = false) List<String> endTimeStrs,
            @RequestParam(value = "remarks", required = false) List<String> remarksList,
            @RequestParam(value = "eventTypeId", required = false) List<Integer> eventTypeIdList,
            RedirectAttributes redirectAttributes) {

        try {
            // N+1対策: event_typesマスタは1回のみ取得
            List<com.attendance.app.entity.EventType> activeEventTypes = eventTypeService.getAllActiveEventTypes();
            Integer codeDoyou = activeEventTypes.stream()
                    .filter(et -> "土出".equals(et.getCode()))
                    .map(e -> e.getEventTypeId())
                    .findFirst().orElse(-1);
            Integer codeKyujitsu = activeEventTypes.stream()
                    .filter(et -> "休出".equals(et.getCode()))
                    .map(e -> e.getEventTypeId())
                    .findFirst().orElse(-1);

            // N+1対策: 対象日付範囲の休暇申請を1回にまとめて取得し、休暇日をSetに展開しておく
            Set<LocalDate> leaveDateSet = buildLeaveDateSet(userId, attendanceDateStrs);

            Set<LocalDate> holidayWorkDateSet = new HashSet<>();
            List<LocalDate> dates = new ArrayList<>();
            List<LocalTime> starts = new ArrayList<>();
            List<LocalTime> ends = new ArrayList<>();
            List<String> filteredRemarks = new ArrayList<>();
            List<Integer> eventTypeIds = new ArrayList<>();

            int j = 0;
            for (int i = 0; i < attendanceDateStrs.size(); i++) {
                String dateStr = attendanceDateStrs.get(i);
                if (dateStr == null || dateStr.isEmpty())
                    continue;

                LocalDate date = LocalDate.parse(dateStr);

                if (leaveDateSet.contains(date)) {
                    log.info("休暇申請済みのため保存をスキップ: date={}", date);
                    continue;
                }

                dates.add(date);

                LocalTime s = null;
                if (startTimeStrs != null && startTimeStrs.size() > j) {
                    String sStr = startTimeStrs.get(j);
                    if (sStr != null && !sStr.isEmpty())
                        s = LocalTime.parse(sStr);
                }
                starts.add(s);

                LocalTime e = null;
                if (endTimeStrs != null && endTimeStrs.size() > j) {
                    String eStr = endTimeStrs.get(j);
                    if (eStr != null && !eStr.isEmpty())
                        e = LocalTime.parse(eStr);
                }
                ends.add(e);

                String remark = null;
                if (remarksList != null && remarksList.size() > j)
                    remark = remarksList.get(j);
                filteredRemarks.add(remark);

                Integer et = null;
                if (eventTypeIdList != null && eventTypeIdList.size() > j)
                    et = eventTypeIdList.get(j);
                eventTypeIds.add(et);

                if (et != null && (et.equals(codeDoyou) || et.equals(codeKyujitsu))) {
                    holidayWorkDateSet.add(date);
                }

                j++;
            }

            if (!dates.isEmpty()) {
                int savedCount = attendanceRecordService.saveRecordsBatch(userId, dates, starts, ends, filteredRemarks,
                        holidayWorkDateSet, eventTypeIds);
                if (savedCount > 0) {
                    redirectAttributes.addFlashAttribute("successMessage", "当月分を保存しました。変更保存件数: " + savedCount);
                } else {
                    redirectAttributes.addFlashAttribute("successMessage", "変更はありませんでした");
                }
            } else {
                redirectAttributes.addFlashAttribute("successMessage", "保存対象はありませんでした");
            }
        } catch (IllegalArgumentException ex) {
            log.warn("管理者による勤怠保存の入力検証に失敗: {}", ex.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        } catch (Exception ex) {
            log.error("管理者による勤怠保存に失敗", ex);
            redirectAttributes.addFlashAttribute("errorMessage", "保存中にエラーが発生しました");
        }

        String redirectUrl = "redirect:/attendance/approval/" + userId + "/detail";
        List<String> queryParams = new ArrayList<>();
        if (from != null && !from.isEmpty()) {
            queryParams.add("from=" + from);
        }
        if (yearMonth != null && !yearMonth.isEmpty()) {
            queryParams.add("yearMonth=" + yearMonth);
        }
        if (!queryParams.isEmpty()) {
            redirectUrl += "?" + String.join("&", queryParams);
        }
        return redirectUrl;
    }

    /**
     * N+1対策: 対象日付リストの最小〜最大日付の範囲で休暇申請を1回だけ取得し、
     * 各申請の期間（leaveStartDate〜leaveEndDate）を展開して休暇日のSetを構築します。
     *
     * @param userId             対象ユーザーID
     * @param attendanceDateStrs 対象日付文字列のリスト（yyyy-MM-dd）
     * @return 休暇が存在する日付のSet
     */
    private Set<LocalDate> buildLeaveDateSet(Long userId, List<String> attendanceDateStrs) {
        LocalDate minDate = null;
        LocalDate maxDate = null;
        for (String dateStr : attendanceDateStrs) {
            if (dateStr == null || dateStr.isEmpty()) {
                continue;
            }
            LocalDate date;
            try {
                date = LocalDate.parse(dateStr);
            } catch (Exception ex) {
                continue;
            }
            if (minDate == null || date.isBefore(minDate)) {
                minDate = date;
            }
            if (maxDate == null || date.isAfter(maxDate)) {
                maxDate = date;
            }
        }

        Set<LocalDate> leaveDateSet = new HashSet<>();
        if (minDate == null) {
            return leaveDateSet;
        }

        List<LeaveApplication> leaveApplications = leaveApplicationService
                .getApplicationsByUserAndDateRange(userId, minDate, maxDate);
        if (leaveApplications != null) {
            for (LeaveApplication la : leaveApplications) {
                LocalDate d = la.getLeaveStartDate();
                while (!d.isAfter(la.getLeaveEndDate())) {
                    leaveDateSet.add(d);
                    d = d.plusDays(1);
                }
            }
        }
        return leaveDateSet;
    }

    private void checkViewPermission(Long targetUserId) {
        User currentUser = securityUtil.getCurrentUser();
        if (currentUser.getUserRole() == UserRole.ADMIN) {
            return;
        }

        if (!userService.isAttendanceApprover(currentUser)) {
            throw new org.springframework.security.access.AccessDeniedException("勤怠の閲覧権限がありません");
        }

        // 一般承認者は同じ勤務クラスのユーザーのみ閲覧可能
        String approverClass = currentUser.getClassName();
        if (approverClass == null || approverClass.trim().isEmpty()) {
            throw new org.springframework.security.access.AccessDeniedException("所属勤務クラスが未設定のため、他ユーザーの勤怠を閲覧できません");
        }

        User targetUser = userService.getUserById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("ユーザーが見つかりません"));
        String targetClass = targetUser.getClassName();
        if (!approverClass.trim().equals(targetClass != null ? targetClass.trim() : null)) {
            throw new org.springframework.security.access.AccessDeniedException("異なる勤務クラスのユーザーの勤怠は閲覧できません");
        }
    }

    private YearMonth parseYearMonthOrNow(String yearMonth) {
        if (yearMonth == null || yearMonth.isEmpty()) {
            return attendanceSubmissionService.resolvePayrollMonth(LocalDate.now());
        }
        try {
            return YearMonth.parse(yearMonth);
        } catch (Exception e) {
            log.warn(INVALID_YEAR_MONTH_LOG, yearMonth);
            return attendanceSubmissionService.resolvePayrollMonth(LocalDate.now());
        }
    }

    private double resolveOvertimeHours(AttendanceRecord record) {
        if (record == null) {
            return 0.0;
        }
        if (record.getOvertimeHours() != null) {
            return record.getOvertimeHours();
        }
        if (record.getAttendanceDate() == null || record.getEndTime() == null) {
            return 0.0;
        }

        LocalDate attendanceDate = DateTimeUtil.toLocalDate(record.getAttendanceDate());
        Instant standardEndInstant = DateTimeUtil.toInstant(attendanceDate, DEFAULT_STANDARD_END_TIME);
        if (standardEndInstant == null || !record.getEndTime().isAfter(standardEndInstant)) {
            return 0.0;
        }

        long minutes = Duration.between(standardEndInstant, record.getEndTime()).toMinutes();
        if (minutes <= 0) {
            return 0.0;
        }
        return minutes / 60.0;
    }
}

