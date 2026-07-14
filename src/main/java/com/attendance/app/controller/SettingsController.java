package com.attendance.app.controller;

import com.attendance.app.entity.Holiday;
import com.attendance.app.service.AttendancePeriodSettingService;
import com.attendance.app.service.SystemSettingService;
import com.attendance.app.service.BatchSettingService;
import com.attendance.app.service.CsvFilenamePatternService;
import com.attendance.app.service.HolidayService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.support.SessionStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.time.Instant;
import java.time.MonthDay;
import java.time.format.DateTimeParseException;
import java.util.List;

@Slf4j
@Controller
@RequestMapping("/admin/settings")
@PreAuthorize("hasRole('ADMIN')")
@SessionAttributes("previewHolidays")
@RequiredArgsConstructor
public class SettingsController {

    private final SystemSettingService systemSettingService;
    private final AttendancePeriodSettingService attendancePeriodSettingService;
    private final BatchSettingService batchSettingService;
    private final CsvFilenamePatternService csvFilenamePatternService;
    private final HolidayService holidayService;
    private final ObjectMapper objectMapper;

    @GetMapping
    public String showSettings(Model model) {
        try {
            populateSettingsModel(model);
        } catch (Exception e) {
            log.error("システム設定画面の表示に失敗しました", e);
            model.addAttribute("error", "システム設定画面の表示に失敗しました");
        }
        return "admin/settings";
    }

    private void populateSettingsModel(Model model) {
        String grantDate = systemSettingService.getSettingValue(SystemSettingService.PAID_LEAVE_GRANT_DATE_KEY);
        String grantDays = systemSettingService.getSettingValue(SystemSettingService.PAID_LEAVE_GRANT_DAYS_KEY);
        String copyrightText = systemSettingService.getSettingValue("COPYRIGHT_TEXT");
        String systemName = systemSettingService.getSettingValue("SYSTEM_NAME");
        String empNoPrefix = systemSettingService.getSettingValue("EMP_NO_PREFIX");

        if (grantDate == null) grantDate = SystemSettingService.DEFAULT_PAID_LEAVE_GRANT_DATE;
        if (grantDays == null) grantDays = SystemSettingService.DEFAULT_PAID_LEAVE_GRANT_DAYS;
        if (copyrightText == null || copyrightText.trim().isEmpty()) {
            copyrightText = "© 2026 勤怠管理システム";
        }
        if (systemName == null || systemName.trim().isEmpty()) {
            systemName = "勤怠管理システム";
        }
        if (empNoPrefix == null) {
            empNoPrefix = "";
        }

        addIfAbsent(model, "paidLeaveGrantDate", grantDate);
        addIfAbsent(model, "paidLeaveGrantDays", grantDays);
        addIfAbsent(model, "copyrightText", copyrightText);
        addIfAbsent(model, "systemName", systemName);
        addIfAbsent(model, "empNoPrefix", empNoPrefix);
        addIfAbsent(model, "attendancePeriodStartDay", attendancePeriodSettingService.getStartDay());
        addIfAbsent(model, "attendancePeriodEndDay", attendancePeriodSettingService.getEndDay());
        addIfAbsent(model, "batchSettingDaysAfterEnd", batchSettingService.getMonthlySummaryDaysAfterEnd());
        addIfAbsent(model, "batchSettingReminderDay", batchSettingService.getReminderDay());
        addIfAbsent(model, "batchSettingReminderHour", batchSettingService.getReminderHour());
        addIfAbsent(model, "alertArticle36Limit1", batchSettingService.getAlertArticle36Limit1());
        addIfAbsent(model, "alertArticle36Limit2", batchSettingService.getAlertArticle36Limit2());
        addIfAbsent(model, "alertPaidLeaveMonths", batchSettingService.getAlertPaidLeaveMonths());
        addIfAbsent(model, "alertPaidLeaveDays", batchSettingService.getAlertPaidLeaveDays());
        addIfAbsent(model, "alertMinIntervalHours", batchSettingService.getAlertMinIntervalHours());
        addIfAbsent(model, "csvFilenamePattern", csvFilenamePatternService.getPattern());

        // 登録済みの祝日を取得してモデルに設定
        List<Holiday> existingHolidays = holidayService.getAllHolidays();
        model.addAttribute("holidayCount", existingHolidays.size());
        model.addAttribute("existingHolidays", existingHolidays);
    }

    private void addIfAbsent(Model model, String name, Object value) {
        if (!model.containsAttribute(name)) {
            model.addAttribute(name, value);
        }
    }

    private void preserveForm(RedirectAttributes redirectAttributes, String errorField, Object... values) {
        redirectAttributes.addFlashAttribute("errorField", errorField);
        for (int i = 0; i < values.length; i += 2) {
            redirectAttributes.addFlashAttribute((String) values[i], values[i + 1]);
        }
    }

    @PostMapping
    public String saveSettings(
            @RequestParam String paidLeaveGrantDate,
            @RequestParam String paidLeaveGrantDays,
            RedirectAttributes redirectAttributes) {
        
        if (paidLeaveGrantDate == null || !paidLeaveGrantDate.matches("^(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01])$")) {
            preserveForm(redirectAttributes, "paidLeaveGrantDate", "paidLeaveGrantDate", paidLeaveGrantDate, "paidLeaveGrantDays", paidLeaveGrantDays);
            redirectAttributes.addFlashAttribute("errorMessage", "有給付与日はMM-DD形式（例: 04-01）で入力してください。");
            return "redirect:/admin/settings";
        }

        try {
            MonthDay.parse("--" + paidLeaveGrantDate);
        } catch (DateTimeParseException e) {
            preserveForm(redirectAttributes, "paidLeaveGrantDate", "paidLeaveGrantDate", paidLeaveGrantDate, "paidLeaveGrantDays", paidLeaveGrantDays);
            redirectAttributes.addFlashAttribute("errorMessage", "有給付与日は実在する月日を入力してください。");
            return "redirect:/admin/settings";
        }

        int grantDays;
        try {
            grantDays = Integer.parseInt(paidLeaveGrantDays.trim());
            if (grantDays < 1 || grantDays > 40) {
                preserveForm(redirectAttributes, "paidLeaveGrantDays", "paidLeaveGrantDate", paidLeaveGrantDate, "paidLeaveGrantDays", paidLeaveGrantDays);
                redirectAttributes.addFlashAttribute("errorMessage", "有給付与日数は1〜40の範囲で指定してください。");
                return "redirect:/admin/settings";
            }
        } catch (NumberFormatException e) {
            preserveForm(redirectAttributes, "paidLeaveGrantDays", "paidLeaveGrantDate", paidLeaveGrantDate, "paidLeaveGrantDays", paidLeaveGrantDays);
            redirectAttributes.addFlashAttribute("errorMessage", "有給付与日数は有効な数値を入力してください。");
            return "redirect:/admin/settings";
        }
        
        systemSettingService.updatePaidLeaveGrantSettings(paidLeaveGrantDate, grantDays);

        redirectAttributes.addFlashAttribute("message", "システム設定を更新しました。");
        return "redirect:/admin/settings";
    }

    @PostMapping("/copyright")
    public String updateCopyrightSetting(
            @RequestParam String copyrightText,
            RedirectAttributes redirectAttributes) {
        if (copyrightText == null || copyrightText.trim().isEmpty()) {
            preserveForm(redirectAttributes, "copyrightText", "copyrightText", copyrightText);
            redirectAttributes.addFlashAttribute("errorMessage", "コピーライト表示文言を入力してください");
            return "redirect:/admin/settings";
        }
        if (copyrightText.length() > 255) {
            preserveForm(redirectAttributes, "copyrightText", "copyrightText", copyrightText);
            redirectAttributes.addFlashAttribute("errorMessage", "コピーライト表示文言は255文字以内で入力してください");
            return "redirect:/admin/settings";
        }
        try {
            systemSettingService.updateSettingValue("COPYRIGHT_TEXT", copyrightText);
            redirectAttributes.addFlashAttribute("message", "コピーライト表示設定を更新しました");
            log.info("コピーライト表示設定を更新: {}", copyrightText);
        } catch (Exception e) {
            preserveForm(redirectAttributes, "copyrightText", "copyrightText", copyrightText);
            redirectAttributes.addFlashAttribute("errorMessage", "コピーライト表示設定の更新に失敗しました");
            log.error("コピーライト表示設定の更新に失敗", e);
        }
        return "redirect:/admin/settings";
    }

    @PostMapping("/system-name")
    public String updateSystemNameSetting(
            @RequestParam String systemName,
            RedirectAttributes redirectAttributes) {
        if (systemName == null || systemName.trim().isEmpty()) {
            preserveForm(redirectAttributes, "systemName", "systemName", systemName);
            redirectAttributes.addFlashAttribute("errorMessage", "システム名を入力してください");
            return "redirect:/admin/settings";
        }
        if (systemName.length() > 255) {
            preserveForm(redirectAttributes, "systemName", "systemName", systemName);
            redirectAttributes.addFlashAttribute("errorMessage", "システム名は255文字以内で入力してください");
            return "redirect:/admin/settings";
        }
        try {
            systemSettingService.updateSettingValue("SYSTEM_NAME", systemName);
            redirectAttributes.addFlashAttribute("message", "システム名表示設定を更新しました");
            log.info("システム名表示設定を更新: {}", systemName);
        } catch (Exception e) {
            preserveForm(redirectAttributes, "systemName", "systemName", systemName);
            redirectAttributes.addFlashAttribute("errorMessage", "システム名表示設定の更新に失敗しました");
            log.error("システム名表示設定の更新に失敗", e);
        }
        return "redirect:/admin/settings";
    }

    @PostMapping("/emp-no-prefix")
    public String updateEmpNoPrefixSetting(
            @RequestParam(required = false) String empNoPrefix,
            RedirectAttributes redirectAttributes) {
        String prefix = (empNoPrefix != null) ? empNoPrefix.trim() : "";
        if (prefix.length() > 50) {
            preserveForm(redirectAttributes, "empNoPrefix", "empNoPrefix", empNoPrefix);
            redirectAttributes.addFlashAttribute("errorMessage", "社員番号プレフィックスは50文字以内で入力してください");
            return "redirect:/admin/settings";
        }
        try {
            systemSettingService.updateSettingValue("EMP_NO_PREFIX", prefix);
            redirectAttributes.addFlashAttribute("message", "社員番号プレフィックス設定を更新しました");
            log.info("社員番号プレフィックス設定を更新: {}", prefix);
        } catch (Exception e) {
            preserveForm(redirectAttributes, "empNoPrefix", "empNoPrefix", empNoPrefix);
            redirectAttributes.addFlashAttribute("errorMessage", "社員番号プレフィックス設定の更新に失敗しました");
            log.error("社員番号プレフィックス設定の更新に失敗", e);
        }
        return "redirect:/admin/settings";
    }

    @PostMapping("/attendance-period")
    public String updateAttendancePeriod(
            @RequestParam int startDay,
            @RequestParam int endDay,
            RedirectAttributes redirectAttributes) {
        try {
            attendancePeriodSettingService.updatePeriod(startDay, endDay);
            redirectAttributes.addFlashAttribute("message",
                    "勤怠期間設定を更新しました（前月" + startDay + "日〜当月" + endDay + "日）");
            log.info("勤怠期間設定を更新: startDay={}, endDay={}", startDay, endDay);
        } catch (IllegalArgumentException e) {
            preserveForm(redirectAttributes, "startDay", "attendancePeriodStartDay", startDay, "attendancePeriodEndDay", endDay);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            log.warn("勤怠期間設定の更新に失敗", e);
        } catch (Exception e) {
            preserveForm(redirectAttributes, "startDay", "attendancePeriodStartDay", startDay, "attendancePeriodEndDay", endDay);
            redirectAttributes.addFlashAttribute("errorMessage", "勤怠期間設定の更新に失敗しました");
            log.error("勤怠期間設定の更新に失敗", e);
        }
        return "redirect:/admin/settings";
    }

    @PostMapping("/batch")
    public String updateBatchSettings(
            @RequestParam int daysAfterEnd,
            @RequestParam int reminderDay,
            @RequestParam int reminderHour,
            RedirectAttributes redirectAttributes) {
        try {
            batchSettingService.updateSettings(daysAfterEnd, reminderDay, reminderHour);
            redirectAttributes.addFlashAttribute("message", "バッチ処理設定を更新しました");
            log.info("バッチ処理設定を更新: daysAfterEnd={}, reminderDay={}, reminderHour={}",
                    daysAfterEnd, reminderDay, reminderHour);
        } catch (IllegalArgumentException e) {
            preserveForm(redirectAttributes, "daysAfterEnd", "batchSettingDaysAfterEnd", daysAfterEnd, "batchSettingReminderDay", reminderDay, "batchSettingReminderHour", reminderHour);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            log.warn("バッチ処理設定の更新に失敗", e);
        } catch (Exception e) {
            preserveForm(redirectAttributes, "daysAfterEnd", "batchSettingDaysAfterEnd", daysAfterEnd, "batchSettingReminderDay", reminderDay, "batchSettingReminderHour", reminderHour);
            redirectAttributes.addFlashAttribute("errorMessage", "バッチ処理設定の更新に失敗しました");
            log.error("バッチ処理設定の更新に失敗", e);
        }
        return "redirect:/admin/settings";
    }

    @PostMapping("/alert")
    public String updateAlertSettings(
            @RequestParam int article36Limit1,
            @RequestParam int article36Limit2,
            @RequestParam int paidLeaveMonths,
            @RequestParam int paidLeaveDays,
            @RequestParam int minIntervalHours,
            RedirectAttributes redirectAttributes) {
        try {
            batchSettingService.updateAlertSettings(article36Limit1, article36Limit2, paidLeaveMonths, paidLeaveDays, minIntervalHours);
            redirectAttributes.addFlashAttribute("message", "アラート閾値設定を更新しました");
            log.info("アラート閾値設定を更新: article36Limit1={}, article36Limit2={}, paidLeaveMonths={}, paidLeaveDays={}, minIntervalHours={}",
                    article36Limit1, article36Limit2, paidLeaveMonths, paidLeaveDays, minIntervalHours);
        } catch (IllegalArgumentException e) {
            preserveForm(redirectAttributes, "article36Limit1", "alertArticle36Limit1", article36Limit1, "alertArticle36Limit2", article36Limit2, "alertPaidLeaveMonths", paidLeaveMonths, "alertPaidLeaveDays", paidLeaveDays, "alertMinIntervalHours", minIntervalHours);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            log.warn("アラート閾値設定の更新に失敗", e);
        } catch (Exception e) {
            preserveForm(redirectAttributes, "article36Limit1", "alertArticle36Limit1", article36Limit1, "alertArticle36Limit2", article36Limit2, "alertPaidLeaveMonths", paidLeaveMonths, "alertPaidLeaveDays", paidLeaveDays, "alertMinIntervalHours", minIntervalHours);
            redirectAttributes.addFlashAttribute("errorMessage", "アラート閾値設定の更新に失敗しました");
            log.error("アラート閾値設定の更新に失敗", e);
        }
        return "redirect:/admin/settings";
    }

    @PostMapping("/csv-pattern")
    public String updateCsvFilenamePattern(
            @RequestParam String pattern,
            RedirectAttributes redirectAttributes) {
        try {
            csvFilenamePatternService.updatePattern(pattern);
            redirectAttributes.addFlashAttribute("message", "CSVファイル名パターン設定を更新しました");
            log.info("CSVファイル名パターン設定を更新: {}", pattern);
        } catch (IllegalArgumentException e) {
            preserveForm(redirectAttributes, "pattern", "csvFilenamePattern", pattern);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            log.warn("CSVファイル名パターン設定の更新に失敗", e);
        } catch (Exception e) {
            preserveForm(redirectAttributes, "pattern", "csvFilenamePattern", pattern);
            redirectAttributes.addFlashAttribute("errorMessage", "CSVファイル名パターン設定の更新に失敗しました");
            log.error("CSVファイル名パターン設定 of 更新に失敗", e);
        }
        return "redirect:/admin/settings";
    }

    /**
     * アップロードした CSV を解析し、確認用のプレビューをセッションに設定します。
     */
    @PostMapping("/holidays/upload")
    public String uploadCsv(@RequestParam("file") MultipartFile file, RedirectAttributes redirectAttributes, Model model) {
        try {
            List<Holiday> holidays = holidayService.parseFromCsv(file);
            model.addAttribute("previewHolidays", holidays);
            redirectAttributes.addFlashAttribute("message", "CSVファイルをアップロードしました。プレビューを確認して保存してください。");
        } catch (IOException e) {
            log.error("祝日CSVの解析に失敗しました", e);
            redirectAttributes.addFlashAttribute("errorMessage", "ファイルの解析に失敗しました: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("祝日CSVの解析に失敗しました", e);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/settings";
    }

    /**
     * 確認画面の JSON データを保存し、祝日マスタを更新します。
     */
    @PostMapping("/holidays/confirm")
    public String confirmAndSave(
            @RequestParam(required = false) String holidaysData,
            RedirectAttributes redirectAttributes,
            SessionStatus sessionStatus) {
        
        if (holidaysData == null || holidaysData.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "保存するデータがありません。");
            return "redirect:/admin/settings";
        }
        
        try {
            log.debug("受信したJSONデータ: {}", holidaysData);
            List<Holiday> holidays = objectMapper.readValue(holidaysData, new TypeReference<List<Holiday>>() {});
            log.debug("パース成功。件数: {}", holidays.size());
            
            for (Holiday h : holidays) {
                if (h.getCreatedAt() == null) {
                    h.setCreatedAt(Instant.now());
                }
            }
            
            holidayService.saveHolidays(holidays);
            
            redirectAttributes.addFlashAttribute("message", "祝日設定を保存しました。");
            redirectAttributes.addFlashAttribute("savedHolidays", holidays);
            
            sessionStatus.setComplete(); // セッション属性をクリア（previewHolidaysをクリア）
        } catch (IOException e) {
            log.error("祝日データのパースに失敗しました", e);
            redirectAttributes.addFlashAttribute("errorMessage", "データの処理に失敗しました: " + e.getMessage());
        } catch (Exception e) {
            log.error("祝日設定の保存に失敗しました", e);
            redirectAttributes.addFlashAttribute("errorMessage", "祝日設定の保存に失敗しました: " + e.getMessage());
        }
        
        return "redirect:/admin/settings";
    }

    /**
     * プレビュー設定をキャンセルし、セッション情報を破棄します。
     */
    @GetMapping("/holidays/cancel")
    public String cancelPreview(SessionStatus sessionStatus, RedirectAttributes redirectAttributes) {
        sessionStatus.setComplete();
        redirectAttributes.addFlashAttribute("message", "プレビューをキャンセルしました。");
        return "redirect:/admin/settings";
    }
}
