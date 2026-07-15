package com.attendance.app.controller;

import com.attendance.app.entity.AttendanceCorrectionRequest;
import com.attendance.app.entity.AttendanceRecord;
import com.attendance.app.entity.AttendanceSubmission;
import com.attendance.app.entity.LeaveApplication;
import com.attendance.app.entity.LeaveStatus;
import com.attendance.app.entity.LeaveType;
import com.attendance.app.entity.User;
import com.attendance.app.security.SecurityUtil;
import com.attendance.app.service.AttendanceCorrectionRequestService;
import com.attendance.app.service.AttendanceRecordService;
import com.attendance.app.service.AttendanceSubmissionService;
import com.attendance.app.service.AttendancePeriodSettingService;
import com.attendance.app.service.BatchSettingService;
import com.attendance.app.service.WorkScheduleClassService;
import com.attendance.app.service.CsvFilenamePatternService;
import com.attendance.app.service.EventTypeService;
import com.attendance.app.service.HolidayService;
import com.attendance.app.service.LeaveApplicationService;
import com.attendance.app.service.PaidLeaveBalanceService;
import com.attendance.app.service.ReportService;
import com.attendance.app.service.UserNotificationService;
import com.attendance.app.service.UserService;
import com.attendance.app.util.DateTimeUtil;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * AttendanceRecord Controller - 勤怠画面表示および一括保存
 */
@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/attendance")
@PreAuthorize("isAuthenticated()")
public class AttendanceRecordController {

    private static final String ATTENDANCE_VIEW = "user/attendance";
    private static final String ATTENDANCE_REDIRECT = "redirect:/attendance";
    private static final String ATTENDANCE_QUICK_REDIRECT = "redirect:/attendance/quick";
    private static final String CORRECTION_LIST_VIEW = "user/correction-request-list";
    private static final String CORRECTION_CREATE_VIEW = "user/correction-request-create";
    private static final String CORRECTION_LIST_REDIRECT = "redirect:/attendance/corrections";
    private static final String INVALID_YEAR_MONTH_LOG = "yearMonth形式が不正: {}";

    private final AttendanceRecordService attendanceRecordService;
    private final AttendanceSubmissionService attendanceSubmissionService;
    private final AttendanceCorrectionRequestService correctionRequestService;
    private final LeaveApplicationService leaveApplicationService;
    private final HolidayService holidayService;
    private final SecurityUtil securityUtil;
    private final UserService userService;
    private final AttendancePeriodSettingService attendancePeriodSettingService;
    private final UserNotificationService userNotificationService;
    private final PaidLeaveBalanceService paidLeaveBalanceService;
    private final EventTypeService eventTypeService;
    private final ReportService reportService;
    private final CsvFilenamePatternService csvFilenamePatternService;
    private final BatchSettingService batchSettingService;
    private final WorkScheduleClassService workScheduleClassService;

    /**
     * 勤怠画面（通常）を表示します。
     * 対象月の勤怠記録、休暇情報、承認状況などを一括取得してモデルに設定します。
     *
     * @param yearMonth 対象年月（yyyy-MM、省略時は当月）
     * @param model     Spring MVC モデル
     * @return 勤怠画面テンプレート名
     */
    @GetMapping
    public String showAttendanceForm(@RequestParam(required = false) String yearMonth, Model model) {
        DateTimeFormatter monthFormatter = DateTimeFormatter.ofPattern("yyyy-MM");
        YearMonth defaultTargetMonth = resolveDefaultTargetMonth();

        // Defaults
        YearMonth current = defaultTargetMonth;
        List<LocalDate> monthDates = new ArrayList<>();

        Map<String, String> startTimeMap = new HashMap<>();
        Map<String, String> endTimeMap = new HashMap<>();
        Map<String, String> remarksMap = new HashMap<>();
        Map<String, Integer> eventTypeIdMap = new HashMap<>();
        Map<String, Long> classIdMap = new HashMap<>();
        Set<String> holidayWorkDates = new HashSet<>();
        Set<String> saturdayWorkDates = new HashSet<>();
        Set<LocalDate> holidays = Collections.emptySet();
        Map<LocalDate, LeaveApplication> leaveMap = new HashMap<>();
        Optional<AttendanceRecord> todayRecord = Optional.empty();
        AttendanceRecordService.MonthRange monthRange = attendanceRecordService.getMonthRange(current);
        double totalWorkingHours = 0.0;
        double totalOvertimeHours = 0.0;
        double totalHolidayWorkHours = 0.0;
        double totalSaturdayWorkHours = 0.0;
        java.math.BigDecimal totalPaidLeaveDays = java.math.BigDecimal.ZERO;
        double totalPaidLeaveHours = 0.0;
        long totalUnpaidLeaveDays = 0;
        long totalAbsenceDays = 0;
        AttendanceSubmission monthSubmission = null;
        boolean monthEditable = true;
        boolean canApproveAttendance = false;
        Long userId = null;
        java.math.BigDecimal yearlyUsedPaidLeaveDays = java.math.BigDecimal.ZERO;
        java.math.BigDecimal remainingPaidLeaveDays = java.math.BigDecimal.ZERO;

        try {
            current = parseYearMonthOrDefault(yearMonth, current);

            userId = securityUtil.getCurrentUserId();
            canApproveAttendance = userService.isAttendanceApprover(securityUtil.getCurrentUser());

            monthRange = attendanceRecordService.getMonthRange(current);
            monthSubmission = attendanceSubmissionService.getSubmission(userId, current).orElse(null);
            monthEditable = attendanceSubmissionService.isEditableMonth(userId, current);

            // generate list of dates in the displayed range
            LocalDate iter = monthRange.getStartDate();
            while (!iter.isAfter(monthRange.getEndDate())) {
                monthDates.add(iter);
                iter = iter.plusDays(1);
            }

            // holidays (records分類前に必要)
            holidays = holidayService.getHolidaysByYear(current.getYear());

            // load existing records and map times to HH:mm
            List<AttendanceRecord> records = attendanceRecordService.getRecordsByUserAndMonth(userId, current);
            totalWorkingHours = records.stream()
                    .mapToDouble(r -> this.resolveWorkingHours(r))
                    .sum();
            totalOvertimeHours = records.stream()
                    .mapToDouble(r -> this.resolveOvertimeHours(r))
                    .sum();
            DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
            for (AttendanceRecord r : records) {
                LocalDate recDate = com.attendance.app.util.DateTimeUtil.toLocalDate(r.getAttendanceDate());
                if (recDate == null)
                    continue;
                String dateKey = recDate.toString();
                if (r.getStartTime() != null) {
                    startTimeMap.put(dateKey,
                            com.attendance.app.util.DateTimeUtil.toLocalTime(r.getStartTime()).format(timeFormatter));
                }
                if (r.getEndTime() != null) {
                    endTimeMap.put(dateKey,
                            com.attendance.app.util.DateTimeUtil.toLocalTime(r.getEndTime()).format(timeFormatter));
                }
                if (r.getRemarks() != null) {
                    remarksMap.put(dateKey, r.getRemarks());
                }
                if (r.getEventTypeId() != null) {
                    eventTypeIdMap.put(dateKey, r.getEventTypeId());
                }
                if (r.getClassId() != null) {
                    classIdMap.put(dateKey, r.getClassId());
                }
                if (r.getHolidayWorkHours() != null && r.getHolidayWorkHours() > 0) {
                    if (recDate.getDayOfWeek().getValue() == 6) { // 土曜日
                        saturdayWorkDates.add(dateKey);
                        totalSaturdayWorkHours += r.getHolidayWorkHours();
                    } else { // 日曜・祝日
                        holidayWorkDates.add(dateKey);
                        totalHolidayWorkHours += r.getHolidayWorkHours();
                    }
                }
            }

            // leave applications for the range -> per-day map
            List<LeaveApplication> leaveApplications = leaveApplicationService.getApplicationsByUserAndDateRange(userId,
                    monthRange.getStartDate(), monthRange.getEndDate());
            if (leaveApplications != null) {
                for (LeaveApplication la : leaveApplications) {
                    LocalDate d = la.getLeaveStartDate();
                    while (!d.isAfter(la.getLeaveEndDate())) {
                        leaveMap.put(d, la);
                        d = d.plusDays(1);
                    }
                }
            }

            // paid leave totals
            for (Map.Entry<LocalDate, LeaveApplication> entry : leaveMap.entrySet()) {
                LeaveApplication la = entry.getValue();
                if (la.getLeaveType() == LeaveType.PAID_LEAVE && la.getStatus() == LeaveStatus.APPROVED) {
                    totalPaidLeaveDays = totalPaidLeaveDays.add(
                            leaveApplicationService.calculateDailyConsumedDays(la.getLeaveDurationType()));
                }
                if (la.getLeaveType() == LeaveType.UNPAID_LEAVE && la.getStatus() == LeaveStatus.APPROVED) {
                    totalUnpaidLeaveDays++;
                }
                if (la.getLeaveType() == LeaveType.ABSENCE && la.getStatus() == LeaveStatus.APPROVED) {
                    totalAbsenceDays++;
                }
            }
            totalPaidLeaveHours = totalPaidLeaveDays.doubleValue() * attendanceRecordService.getStandardWorkingHours(userId, current);

            int currentYear = DateTimeUtil.todayJapan().getYear();
            yearlyUsedPaidLeaveDays = leaveApplicationService.calculateYearlyUsedPaidLeaveDays(userId, currentYear);
            remainingPaidLeaveDays = paidLeaveBalanceService.getTotalRemainingDays(userId);

            // today's record
            todayRecord = attendanceRecordService.getRecordByUserAndDate(userId, DateTimeUtil.todayJapan());

            model.addAttribute("records", records);
            model.addAttribute("leaveMap", leaveMap);
        } catch (Exception e) {
            addViewError(model, e, "勤怠管理画面表示に失敗", "画面の表示に失敗しました");
        }

        // Ensure attributes exist so Thymeleaf SpEL won't index into null
        model.addAttribute("startTimeMap", startTimeMap);
        model.addAttribute("endTimeMap", endTimeMap);
        model.addAttribute("remarksMap", remarksMap);
        model.addAttribute("eventTypeIdMap", eventTypeIdMap);
        model.addAttribute("classIdMap", classIdMap);
        model.addAttribute("workScheduleClasses", workScheduleClassService.getAllActiveClasses());
        model.addAttribute("eventTypes", eventTypeService.getAllActiveEventTypes());
        model.addAttribute("holidayWorkDates", holidayWorkDates);
        model.addAttribute("saturdayWorkDates", saturdayWorkDates);
        model.addAttribute("holidays", holidays);
        model.addAttribute("leaveMap", leaveMap);
        model.addAttribute("todayRecord", todayRecord.orElse(null));
        model.addAttribute("yearMonth", current.format(monthFormatter));

        model.addAttribute("monthRange", monthRange);
        model.addAttribute("monthDates", monthDates);
        model.addAttribute("totalWorkingHours", totalWorkingHours);
        model.addAttribute("totalWorkingHoursStr", com.attendance.app.util.DateTimeUtil.formatHoursToHHmm(totalWorkingHours));
        model.addAttribute("totalOvertimeHours", totalOvertimeHours);
        model.addAttribute("totalOvertimeHoursStr", com.attendance.app.util.DateTimeUtil.formatHoursToHHmm(totalOvertimeHours));
        model.addAttribute("totalHolidayWorkHours", totalHolidayWorkHours);
        model.addAttribute("totalHolidayWorkHoursStr", com.attendance.app.util.DateTimeUtil.formatHoursToHHmm(totalHolidayWorkHours));
        model.addAttribute("totalSaturdayWorkHours", totalSaturdayWorkHours);
        model.addAttribute("totalSaturdayWorkHoursStr", com.attendance.app.util.DateTimeUtil.formatHoursToHHmm(totalSaturdayWorkHours));
        model.addAttribute("totalPaidLeaveDays", totalPaidLeaveDays);
        model.addAttribute("totalPaidLeaveHours", totalPaidLeaveHours);
        model.addAttribute("totalPaidLeaveHoursStr", com.attendance.app.util.DateTimeUtil.formatHoursToHHmm(totalPaidLeaveHours));
        model.addAttribute("totalUnpaidLeaveDays", totalUnpaidLeaveDays);
        model.addAttribute("totalAbsenceDays", totalAbsenceDays);
        model.addAttribute("monthSubmission", monthSubmission);
        model.addAttribute("monthEditable", monthEditable);
        model.addAttribute("canApproveAttendance", canApproveAttendance);
        model.addAttribute("yearlyUsedPaidLeaveDays", yearlyUsedPaidLeaveDays);
        model.addAttribute("remainingPaidLeaveDays", remainingPaidLeaveDays);
        model.addAttribute("paidLeaveBalances", userId != null
                ? paidLeaveBalanceService.getBalancesByUserId(userId)
                : java.util.Collections.emptyList());
        model.addAttribute("totalRemainingPaidLeaveDays", userId != null
                ? paidLeaveBalanceService.getTotalRemainingDays(userId)
                : java.math.BigDecimal.ZERO);
        model.addAttribute("article36Status", attendanceRecordService.checkArticle36(totalOvertimeHours));
        model.addAttribute("article36MonthlyLimit", batchSettingService.getAlertArticle36Limit2());
        model.addAttribute("article36MonthlyWarning", batchSettingService.getAlertArticle36Limit1());
        model.addAttribute("currentYear", DateTimeUtil.todayJapan().getYear());
        model.addAttribute("submissionStatusPending", AttendanceSubmissionService.STATUS_PENDING);
        model.addAttribute("submissionStatusApproved", AttendanceSubmissionService.STATUS_APPROVED);
        model.addAttribute("submissionStatusReturned", AttendanceSubmissionService.STATUS_RETURNED);
        model.addAttribute("submissionStatusWithdrawn", AttendanceSubmissionService.STATUS_WITHDRAWN);
        model.addAttribute("userId", userId);

        return ATTENDANCE_VIEW;
    }

    /**
     * ログイン中ユーザー自身の月次勤怠CSVをダウンロードします。
     * 他ユーザーの指定は不可で、常にログイン中ユーザー自身のデータのみを出力します。
     *
     * @param yearMonth 対象年月（YYYY-MM形式、省略可）
     * @return CSVファイルレスポンス
     */
    @GetMapping("/export")
    public ResponseEntity<StreamingResponseBody> exportOwnAttendanceCsv(@RequestParam(required = false) String yearMonth) {
        try {
            Long userId = securityUtil.getCurrentUserId();
            User user = userService.getUserById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("ユーザーが見つかりません: userId=" + userId));
            YearMonth targetMonth = parseYearMonthOrDefault(yearMonth, resolveDefaultTargetMonth());
            OffsetDateTime downloadedAt = com.attendance.app.util.DateTimeUtil
                    .toOffsetDateTime(com.attendance.app.util.DateTimeUtil.now());
            String filename = csvFilenamePatternService.buildCsvFilename(user, targetMonth, downloadedAt);
            log.info("月次勤怠CSVをダウンロード（本人）: userId={}, yearMonth={}, filename={}", userId, targetMonth, filename);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodeFilename(filename))
                    .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                    .body(outputStream -> reportService.writeUserAttendanceCsv(user, targetMonth, outputStream));
        } catch (IllegalArgumentException e) {
            log.warn("月次勤怠CSVダウンロード（本人）に失敗", e);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            logError(e, "月次勤怠CSVダウンロード（本人）に失敗");
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * RFC 5987 に準拠した Content-Disposition filename* 用のパーセントエンコードを行います。
     */
    private String encodeFilename(String filename) {
        return URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /**
     * 当月分の勤怠（時刻・備考等）をまとめて保存します。
     *
     * @param yearMonth            対象年月
     * @param attendanceDateStrs   対象日付のリスト
     * @param startTimeStrs        開始時刻のリスト
     * @param endTimeStrs          終了時刻のリスト
     * @param remarksList          備考のリスト
     * @param holidayWorkDateList  日曜・祝日休出のリスト
     * @param saturdayWorkDateList 土曜休出のリスト
     * @param redirectAttributes   リダイレクト属性
     * @return 勤怠画面へのリダイレクト
     */
    @PostMapping("/saveAll")
    public String saveAllAttendance(
            @RequestParam(required = false) String yearMonth,
            @RequestParam("attendanceDate") List<String> attendanceDateStrs,
            @RequestParam(value = "startTime", required = false) List<String> startTimeStrs,
            @RequestParam(value = "endTime", required = false) List<String> endTimeStrs,
            @RequestParam(value = "remarks", required = false) List<String> remarksList,
            @RequestParam(value = "eventTypeId", required = false) List<Integer> eventTypeIdList,
            @RequestParam(value = "classId", required = false) List<String> classIdStrs,
            RedirectAttributes redirectAttributes) {
        YearMonth selectedMonth = parseYearMonthOrDefault(yearMonth, resolveDefaultTargetMonth());
        try {
            Long userId = securityUtil.getCurrentUserId();
            attendanceSubmissionService.assertEditableMonth(userId, selectedMonth);

            // event_types マスタから「土出」「休出」のIDを取得（N+1対策: 1回のみ取得）
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
            List<Long> classIds = new ArrayList<>();

            // 休暇日行は disabled のためフォーム送信されない。
            // attendanceDateStrs は全行分だが
            // startTimeStrs/endTimeStrs/remarksList/eventTypeIdList は非休暇日のみ。
            // j を独立カウンタとして使い、インデックスずれを防ぐ。
            int j = 0;
            for (int i = 0; i < attendanceDateStrs.size(); i++) {
                String dateStr = attendanceDateStrs.get(i);
                if (dateStr == null || dateStr.isEmpty())
                    continue;

                LocalDate date;
                try {
                    date = LocalDate.parse(dateStr);
                } catch (DateTimeParseException ex) {
                    throw new IllegalArgumentException("日付の形式が不正です: " + dateStr);
                }

                // Skip dates that already have leave
                if (leaveDateSet.contains(date)) {
                    log.info("休暇申請済みのため保存をスキップ: date={}", date);
                    // 休暇日行は disabled のため startTime/endTime/remarks/eventTypeId は送信されない。j
                    // はインクリメントしない。
                    continue;
                }

                dates.add(date);

                LocalTime s = null;
                if (startTimeStrs != null && startTimeStrs.size() > j) {
                    s = parseTimeOrThrow(startTimeStrs.get(j), "開始時刻", i + 1);
                }
                starts.add(s);

                LocalTime e = null;
                if (endTimeStrs != null && endTimeStrs.size() > j) {
                    e = parseTimeOrThrow(endTimeStrs.get(j), "終了時刻", i + 1);
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

                Long cid = null;
                if (classIdStrs != null && classIdStrs.size() > j) {
                    String cs = classIdStrs.get(j);
                    if (cs != null && !cs.isBlank()) {
                        try {
                            cid = Long.valueOf(cs.trim());
                        } catch (NumberFormatException nfe) {
                            throw new IllegalArgumentException("勤務地（勤務クラス）の指定が不正です: 行=" + (i + 1));
                        }
                    }
                }
                classIds.add(cid);

                j++;
            }

            if (!dates.isEmpty()) {
                int savedCount = attendanceRecordService.saveRecordsBatch(userId, dates, starts, ends, filteredRemarks,
                        holidayWorkDateSet, eventTypeIds, classIds);
                if (savedCount > 0) {
                    redirectAttributes.addFlashAttribute("message", "当月分を保存しました。変更保存件数: " + savedCount);
                } else {
                    redirectAttributes.addFlashAttribute("message", "変更はありませんでした");
                }
            } else {
                redirectAttributes.addFlashAttribute("message", "保存対象はありませんでした（休暇日が除外されています）");
            }
        } catch (IllegalArgumentException ex) {
            addValidationRedirectError(redirectAttributes, ex, "まとめ保存の入力検証で失敗");
        } catch (Exception ex) {
            addRedirectError(redirectAttributes, ex, "まとめ保存に失敗", "保存中にエラーが発生しました");
        }
        return redirectToMonth(selectedMonth);
    }

    /**
     * 当月分の勤怠申請（締め）を行います。
     *
     * @param yearMonth          対象年月
     * @param redirectAttributes リダイレクト属性
     * @return 勤怠画面へのリダイレクト
     */
    @PostMapping("/submit-month")
    public String submitMonth(@RequestParam String yearMonth, RedirectAttributes redirectAttributes) {
        YearMonth selectedMonth = parseYearMonthOrDefault(yearMonth, resolveDefaultTargetMonth());
        try {
            Long userId = securityUtil.getCurrentUserId();
            attendanceSubmissionService.submitMonth(userId, selectedMonth);
            redirectAttributes.addFlashAttribute("message", "今月分の勤怠を申請しました");
            try {
                // 承認権限を持つユーザーに通知を送信
                com.attendance.app.entity.User currentUser = userService.getUserById(userId).orElse(null);
                String applicantName = currentUser != null ? currentUser.getFullName() : "不明";
                userNotificationService.notifyApproversNewSubmission(userId, applicantName, selectedMonth.toString());
            } catch (Exception notifyEx) {
                redirectAttributes.addFlashAttribute("warning", "勤怠申請は完了しましたが、承認者への通知に失敗しました");
                log.warn("月次勤怠申請の承認者通知に失敗: userId={}, yearMonth={}", userId, selectedMonth, notifyEx);
            }
        } catch (IllegalArgumentException e) {
            addValidationRedirectError(redirectAttributes, e, "月次申請に失敗");
        } catch (Exception e) {
            addRedirectError(redirectAttributes, e, "月次申請に失敗", "申請中にエラーが発生しました");
        }
        return redirectToMonth(selectedMonth);
    }

    /**
     * 申請中の当月分勤怠を取り下げます。
     *
     * @param yearMonth          対象年月
     * @param redirectAttributes リダイレクト属性
     * @return 勤怠画面へのリダイレクト
     */
    @PostMapping("/withdraw-month")
    public String withdrawMonth(@RequestParam String yearMonth, RedirectAttributes redirectAttributes) {
        YearMonth selectedMonth = parseYearMonthOrDefault(yearMonth, resolveDefaultTargetMonth());
        try {
            Long userId = securityUtil.getCurrentUserId();
            attendanceSubmissionService.withdrawSubmission(userId, selectedMonth);
            redirectAttributes.addFlashAttribute("message", "申請を取り下げました");
        } catch (IllegalArgumentException e) {
            addValidationRedirectError(redirectAttributes, e, "申請取り下げに失敗");
        } catch (Exception e) {
            addRedirectError(redirectAttributes, e, "申請取り下げに失敗", "取り下げ中にエラーが発生しました");
        }
        return redirectToMonth(selectedMonth);
    }

    /**
     * ワンクリック勤怠画面（打刻画面）を表示します。
     *
     * @param model Spring MVC モデル
     * @return ワンクリック勤怠画面テンプレート名
     */
    @GetMapping("/quick")
    public String showQuickAttendance(Model model) {
        try {
            Long userId = securityUtil.getCurrentUserId();
            Optional<AttendanceRecord> todayRecord = attendanceRecordService.getRecordByUserAndDate(userId,
                    DateTimeUtil.todayJapan());
            model.addAttribute("userId", userId);
            model.addAttribute("todayRecord", todayRecord.orElse(null));
            model.addAttribute("eventTypes", eventTypeService.getAllActiveEventTypes());
            model.addAttribute("workScheduleClasses", workScheduleClassService.getAllActiveClasses());
        } catch (Exception e) {
            addViewError(model, e, "ワンクリック勤怠画面表示に失敗", "画面の表示に失敗しました");
        }
        return "attendance/input";
    }

    /**
     * ワンクリックで本日の勤務開始時刻を記録します。
     *
     * @param redirectAttributes リダイレクト属性
     * @return ワンクリック勤怠画面へのリダイレクト
     */
    @PostMapping("/quick-start")
    public String quickStartAttendance(
            @RequestParam(required = false) Long classId,
            RedirectAttributes redirectAttributes) {
        try {
            Long userId = securityUtil.getCurrentUserId();
            attendanceSubmissionService.assertEditableMonth(userId,
                    attendanceSubmissionService.resolvePayrollMonth(DateTimeUtil.todayJapan()));
            AttendanceRecord record = attendanceRecordService.quickStartAttendance(userId, classId);
            String startTimeStr = com.attendance.app.util.DateTimeUtil.toLocalTime(record.getStartTime())
                    .format(DateTimeFormatter.ofPattern("HH:mm"));
            redirectAttributes.addFlashAttribute("message", "勤務開始時刻を記録しました: " + startTimeStr);
            log.info("ワンクリック勤務開始: userId={}, time={}", userId, record.getStartTime());
        } catch (IllegalArgumentException e) {
            addValidationRedirectError(redirectAttributes, e, "ワンクリック勤務開始に失敗");
        } catch (Exception e) {
            addRedirectError(redirectAttributes, e, "ワンクリック勤務開始に失敗", "予期しないエラーが発生しました");
        }
        return ATTENDANCE_QUICK_REDIRECT;
    }

    /**
     * ワンクリックで本日の勤務終了時刻を記録します。
     *
     * @param redirectAttributes リダイレクト属性
     * @return ワンクリック勤怠画面へのリダイレクト
     */
    @PostMapping("/quick-end")
    public String quickEndAttendance(
            @RequestParam(defaultValue = "false") boolean isHolidayWork,
            RedirectAttributes redirectAttributes) {
        try {
            Long userId = securityUtil.getCurrentUserId();
            attendanceSubmissionService.assertEditableMonth(userId,
                    attendanceSubmissionService.resolvePayrollMonth(DateTimeUtil.todayJapan()));
            AttendanceRecord record = attendanceRecordService.quickEndAttendance(userId, isHolidayWork);
            String endTimeStr = com.attendance.app.util.DateTimeUtil.toLocalTime(record.getEndTime())
                    .format(DateTimeFormatter.ofPattern("HH:mm"));
            String message = String.format("勤務終了時刻を記録しました: %s (勤務時間: %.1f時間)", endTimeStr,
                    isHolidayWork ? record.getHolidayWorkHours() : record.getWorkingHours());
            redirectAttributes.addFlashAttribute("message", message);
            log.info("ワンクリック勤務終了: userId={}, time={}, workingHours={}", userId, record.getEndTime(),
                    record.getWorkingHours());
        } catch (IllegalArgumentException e) {
            addValidationRedirectError(redirectAttributes, e, "ワンクリック勤務終了に失敗");
        } catch (Exception e) {
            addRedirectError(redirectAttributes, e, "ワンクリック勤務終了に失敗", "予期しないエラーが発生しました");
        }
        return ATTENDANCE_QUICK_REDIRECT;
    }

    @PostMapping("/quick-break")
    public String quickBreakAttendance(
            @RequestParam int breakTimeMinutes,
            RedirectAttributes redirectAttributes) {
        try {
            Long userId = securityUtil.getCurrentUserId();
            attendanceSubmissionService.assertEditableMonth(userId,
                    attendanceSubmissionService.resolvePayrollMonth(DateTimeUtil.todayJapan()));

            attendanceRecordService.addBreakTime(userId, breakTimeMinutes);
            redirectAttributes.addFlashAttribute("message", "休憩時間（" + breakTimeMinutes + "分）を記録しました");
        } catch (IllegalArgumentException e) {
            addValidationRedirectError(redirectAttributes, e, "休憩時間の記録に失敗");
        } catch (Exception e) {
            addRedirectError(redirectAttributes, e, "休憩時間の記録に失敗", "予期しないエラーが発生しました");
        }
        return ATTENDANCE_QUICK_REDIRECT;
    }

    /**
     * 指定された勤怠記録を削除します。
     *
     * @param recordId           勤怠記録ID
     * @param redirectAttributes リダイレクト属性
     * @return 勤怠画面へのリダイレクト
     */
    @PostMapping("/delete/{recordId}")
    public String deleteAttendance(@PathVariable Long recordId, RedirectAttributes redirectAttributes) {
        try {
            Optional<AttendanceRecord> record = attendanceRecordService.getRecordById(recordId);
            if (record.isPresent()) {
                Long userId = securityUtil.getCurrentUserId();
                AttendanceRecord rec = record.get();
                if (!rec.getUserId().equals(userId)) {
                    throw new IllegalArgumentException("他人の勤怠記録は削除できません。");
                }
                LocalDate attendanceDate = com.attendance.app.util.DateTimeUtil.toLocalDate(rec.getAttendanceDate());
                attendanceSubmissionService.assertEditableMonth(userId,
                        attendanceSubmissionService.resolvePayrollMonth(attendanceDate));
                attendanceRecordService.deleteRecord(recordId);
                log.info("勤怠記録を削除: recordId={}, userId={}", recordId, userId);
                redirectAttributes.addFlashAttribute("message", "勤怠記録を削除しました");
            } else {
                redirectAttributes.addFlashAttribute("error", "指定された勤怠記録が見つかりません。");
            }
        } catch (IllegalArgumentException e) {
            addValidationRedirectError(redirectAttributes, e, "勤怠記録の削除に失敗");
        } catch (Exception e) {
            logError(e, "勤怠記録の削除に失敗");
            redirectAttributes.addFlashAttribute("error", "勤怠記録の削除に失敗しました");
        }
        return ATTENDANCE_REDIRECT;
    }

    private String redirectToMonth(YearMonth yearMonth) {
        return ATTENDANCE_REDIRECT + "?yearMonth=" + yearMonth;
    }

    /**
     * N+1対策: 対象日付リストの最小〜最大日付の範囲で休暇申請を1回だけ取得し、
     * 各申請の期間（leaveStartDate〜leaveEndDate）を展開して休暇日のSetを構築します。
     *
     * @param userId           対象ユーザーID
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
            } catch (DateTimeParseException ex) {
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

    /**
     * 既存データ互換のため、workingHours が未設定の場合は開始/終了時刻から算出する。
     */
    private double resolveWorkingHours(AttendanceRecord record) {
        if (record == null) {
            return 0.0;
        }
        if (record.getWorkingHours() != null) {
            return record.getWorkingHours();
        }
        if (record.getStartTime() == null || record.getEndTime() == null) {
            return 0.0;
        }

        long minutes = Duration.between(record.getStartTime(), record.getEndTime()).toMinutes();
        if (minutes <= 0) {
            return 0.0;
        }
        return minutes / 60.0;
    }

    /**
     * 既存データ互換のため、overtimeHours が未設定の場合は基準終了時刻と終了時刻から算出する。
     */
    private double resolveOvertimeHours(AttendanceRecord record) {
        return attendanceRecordService.resolveOvertimeHours(record);
    }

    /**
     * 画面表示系エラー時のログ出力とモデルへのエラー設定を行います。
     *
     * @param model       モデル
     * @param e           発生した例外
     * @param logMessage  ログ用メッセージ
     * @param userMessage 画面表示用メッセージ
     */
    private void addViewError(Model model, Exception e, String logMessage, String userMessage) {
        logError(e, logMessage);
        model.addAttribute("error", userMessage);
    }

    /**
     * 入力検証エラー時の警告ログ出力とフラッシュエラー設定を行います。
     *
     * @param redirectAttributes リダイレクト属性
     * @param e                  発生した入力検証例外
     * @param logMessage         ログ用メッセージ
     */
    private void addValidationRedirectError(RedirectAttributes redirectAttributes, IllegalArgumentException e,
            String logMessage) {
        logWarn(e, logMessage);
        redirectAttributes.addFlashAttribute("error", e.getMessage());
    }

    /**
     * 例外時のエラーログ出力とフラッシュエラー設定を行います。
     *
     * @param redirectAttributes リダイレクト属性
     * @param e                  発生した例外
     * @param logMessage         ログ用メッセージ
     * @param userMessage        表示用メッセージ
     */
    private void addRedirectError(RedirectAttributes redirectAttributes, Exception e, String logMessage,
            String userMessage) {
        logError(e, logMessage);
        redirectAttributes.addFlashAttribute("error", userMessage);
    }

    /**
     * 年月文字列を YearMonth に変換し、不正値の場合は既定値を返します。
     *
     * @param yearMonth    年月文字列（yyyy-MM）
     * @param defaultValue 変換失敗時の既定値
     * @return 解析成功時は指定年月、失敗時は既定値
     */
    private YearMonth parseYearMonthOrDefault(String yearMonth, YearMonth defaultValue) {
        if (yearMonth == null || yearMonth.isEmpty()) {
            return defaultValue;
        }
        try {
            return YearMonth.parse(yearMonth);
        } catch (Exception e) {
            logWarn(INVALID_YEAR_MONTH_LOG, yearMonth);
            return defaultValue;
        }
    }

    /**
     * 当日が含まれる勤怠対象月（設定に従った開始日から当月終了日まで）を返します。
     */
    private YearMonth resolveDefaultTargetMonth() {
        LocalDate today = DateTimeUtil.todayJapan();
        return attendancePeriodSettingService.resolvePayrollMonth(today);
    }

    /**
     * 例外メッセージ付きのエラーログを出力します。
     *
     * @param e          発生した例外
     * @param logMessage ログ用メッセージ
     */
    private void logError(Exception e, String logMessage) {
        log.error("{}", logMessage, e);
    }

    /**
     * 例外メッセージ付きの警告ログを出力します。
     *
     * @param e          発生した例外
     * @param logMessage ログ用メッセージ
     */
    private void logWarn(Exception e, String logMessage) {
        log.warn("{}", logMessage, e);
    }

    /**
     * 置換パラメータ付きの警告ログを出力します。
     *
     * @param logMessage ログフォーマット
     * @param arg        置換パラメータ
     */
    private void logWarn(String logMessage, Object arg) {
        log.warn(logMessage, arg);
    }

    // -------------------------------------------------------
    // 勤怠修正申請関連エンドポイント
    // -------------------------------------------------------

    /**
     * ユーザー自身の修正申請一覧を表示します。
     */
    @GetMapping("/corrections")
    public String showCorrectionRequestList(Model model) {
        try {
            Long userId = securityUtil.getCurrentUserId();
            List<AttendanceCorrectionRequest> requests = correctionRequestService.getRequestsByUserId(userId);
            model.addAttribute("requests", requests);
            model.addAttribute("correctionStatusPending", AttendanceCorrectionRequestService.STATUS_PENDING);
            model.addAttribute("correctionStatusApproved", AttendanceCorrectionRequestService.STATUS_APPROVED);
            model.addAttribute("correctionStatusRejected", AttendanceCorrectionRequestService.STATUS_REJECTED);
            model.addAttribute("correctionStatusWithdrawn", AttendanceCorrectionRequestService.STATUS_WITHDRAWN);
        } catch (Exception e) {
            log.error("修正申請一覧の表示に失敗", e);
            model.addAttribute("error", "修正申請一覧の表示に失敗しました");
            model.addAttribute("requests", java.util.Collections.emptyList());
        }
        return CORRECTION_LIST_VIEW;
    }

    /**
     * 勤怠修正申請フォームを表示します。
     *
     * @param attendanceDate 修正対象日（yyyy-MM-dd）
     */
    @GetMapping("/corrections/new")
    public String showCorrectionRequestForm(
            @RequestParam(required = false) String attendanceDate,
            @RequestParam(required = false) String yearMonth,
            Model model) {
        try {
            Long userId = securityUtil.getCurrentUserId();

            LocalDate targetDate = null;
            if (attendanceDate != null && !attendanceDate.isEmpty()) {
                try {
                    targetDate = LocalDate.parse(attendanceDate);
                } catch (Exception ignored) {
                }
            }

            // 現在の勤怠記録を取得してフォームに表示
            String currentStart = null;
            String currentEnd = null;
            String currentRemarks = null;
            if (targetDate != null) {
                var rec = attendanceRecordService.getRecordByUserAndDate(userId, targetDate).orElse(null);
                if (rec != null) {
                    DateTimeFormatter tf = DateTimeFormatter.ofPattern("HH:mm");
                    if (rec.getStartTime() != null) {
                        currentStart = com.attendance.app.util.DateTimeUtil.toLocalTime(rec.getStartTime()).format(tf);
                    }
                    if (rec.getEndTime() != null) {
                        currentEnd = com.attendance.app.util.DateTimeUtil.toLocalTime(rec.getEndTime()).format(tf);
                    }
                    currentRemarks = rec.getRemarks();
                }
            }

            model.addAttribute("targetDate", targetDate);
            model.addAttribute("yearMonth", yearMonth);
            model.addAttribute("currentStart", currentStart);
            model.addAttribute("currentEnd", currentEnd);
            model.addAttribute("currentRemarks", currentRemarks);

        } catch (Exception e) {
            log.error("修正申請フォームの表示に失敗", e);
            model.addAttribute("error", "修正申請フォームの表示に失敗しました");
        }
        return CORRECTION_CREATE_VIEW;
    }

    /**
     * 勤怠修正申請を提出します。
     */
    @PostMapping("/corrections/submit")
    public String submitCorrectionRequest(
            @RequestParam String attendanceDate,
            @RequestParam(required = false) String requestedStartTime,
            @RequestParam(required = false) String requestedEndTime,
            @RequestParam(required = false) String requestedRemarks,
            @RequestParam String reason,
            RedirectAttributes redirectAttributes) {
        try {
            Long userId = securityUtil.getCurrentUserId();
            LocalDate date = LocalDate.parse(attendanceDate);
            LocalTime startTime = parseTimeOrNull(requestedStartTime);
            LocalTime endTime = parseTimeOrNull(requestedEndTime);

            correctionRequestService.submitRequest(userId, date, startTime, endTime, requestedRemarks, reason);
            redirectAttributes.addFlashAttribute("message",
                    date + " の勤怠修正申請を提出しました。承認者の確認をお待ちください。");
            try {
                // 承認権限を持つユーザーに通知を送信
                com.attendance.app.entity.User currentUser = userService.getUserById(userId).orElse(null);
                String applicantName = currentUser != null ? currentUser.getFullName() : "不明";
                userNotificationService.notifyApproversNewCorrectionRequest(userId, applicantName, date.toString());
            } catch (Exception notifyEx) {
                redirectAttributes.addFlashAttribute("warning", "勤怠修正申請は完了しましたが、承認者への通知に失敗しました");
                log.warn("勤怠修正申請の承認者通知に失敗: userId={}, attendanceDate={}", userId, date, notifyEx);
            }
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            log.warn("勤怠修正申請の提出に失敗", e);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "勤怠修正申請の提出に失敗しました");
            log.error("勤怠修正申請の提出に失敗", e);
        }
        return CORRECTION_LIST_REDIRECT;
    }

    /**
     * 勤怠修正申請を取り下げます。
     */
    @PostMapping("/corrections/{requestId}/withdraw")
    public String withdrawCorrectionRequest(
            @PathVariable Long requestId,
            RedirectAttributes redirectAttributes) {
        try {
            Long userId = securityUtil.getCurrentUserId();
            correctionRequestService.withdrawRequest(requestId, userId);
            redirectAttributes.addFlashAttribute("message", "勤怠修正申請を取り下げました");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            log.warn("勤怠修正申請の取り下げに失敗", e);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "勤怠修正申請の取り下げに失敗しました");
            log.error("勤怠修正申請の取り下げに失敗", e);
        }
        return CORRECTION_LIST_REDIRECT;
    }

    /**
     * 時刻文字列をパースします。空文字・nullは「時刻の削除意図」としてnullを返し、
     * 値があるのにパースできない場合は入力エラーとして例外を送出します。
     *
     * @param value 時刻文字列（HH:mm）
     * @return パース結果。空入力の場合は null
     * @throws IllegalArgumentException 時刻の形式が不正な場合
     */
    private LocalTime parseTimeOrNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalTime.parse(value.trim(), DateTimeFormatter.ofPattern("HH:mm"));
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("時刻の形式が不正です（HH:mm形式で入力してください）: " + value);
        }
    }

    /**
     * 一括保存の時刻文字列をパースします。空文字・nullはnull（未入力・削除意図）を返し、
     * パース失敗時は行番号付きの入力検証例外を送出します。
     *
     * @param value     時刻文字列
     * @param fieldName 項目名（エラーメッセージ用）
     * @param rowNumber 行番号（1始まり、エラーメッセージ用）
     * @return パース結果。空入力の場合は null
     * @throws IllegalArgumentException 時刻の形式が不正な場合
     */
    private LocalTime parseTimeOrThrow(String value, String fieldName, int rowNumber) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return LocalTime.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(fieldName + "の形式が不正です: 行=" + rowNumber + ", 値=" + value);
        }
    }

}
