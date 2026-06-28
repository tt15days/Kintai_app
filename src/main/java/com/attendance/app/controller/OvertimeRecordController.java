package com.attendance.app.controller;

import java.time.YearMonth;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.attendance.app.entity.OvertimeRecord;
import com.attendance.app.security.SecurityUtil;
import com.attendance.app.service.OvertimeRecordService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Overtime Record Controller - 残業管理
 *
 * 主な責務:
 * - ユーザーの残業記録一覧表示
 * - 残業記録の保存
 * - 残業記録の削除
 */
@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/overtime")
@PreAuthorize("isAuthenticated()")
public class OvertimeRecordController {

    private static final String OVERTIME_VIEW = "user/overtime";
    private static final String INVALID_YEAR_MONTH_LOG = "yearMonth形式が不正: {}";

    private final OvertimeRecordService overtimeRecordService;
    private final SecurityUtil securityUtil;

    /**
     * 残業管理画面を表示します。
     * 
     * 指定された年月（デフォルトは現在の年月）の残業記録を表示します。
     *
     * @param yearMonth 表示する年月（YYYY-MM形式、省略可）
     * @param model Spring MVC のモデル
     * @return テンプレート名 (user/overtime)
     */
    @GetMapping
    public String showOvertimeForm(
            @RequestParam(required = false) String yearMonth,
            Model model) {
        try {
            YearMonth current = parseYearMonthOrNow(yearMonth);

            Long userId = securityUtil.getCurrentUserId();

            List<OvertimeRecord> records = overtimeRecordService.getRecordsByUserAndMonth(userId, current.getYear(), current.getMonthValue());
            Double monthlyOvertime = overtimeRecordService.getOvertimeHoursSumByUserAndMonth(userId, current.getYear(), current.getMonthValue());

            model.addAttribute("yearMonth", current);
            model.addAttribute("records", records);
            model.addAttribute("monthlyOvertime", monthlyOvertime);
            model.addAttribute("userId", userId);
            log.info("残業管理画面を表示: userId={}, yearMonth={}", userId, current);
        } catch (Exception e) {
            addViewError(model, e, "残業管理画面表示に失敗", "残業管理画面の表示に失敗しました");
        }

        return OVERTIME_VIEW;
    }

    /**
     * 画面表示系エラー時のログ出力とモデルへのエラー設定を行います。
     *
     * @param model モデル
     * @param e 発生した例外
     * @param logMessage ログ用メッセージ
     * @param userMessage 画面表示用メッセージ
     */
    private void addViewError(Model model, Exception e, String logMessage, String userMessage) {
        log.error("{}: {}", logMessage, e.getMessage());
        model.addAttribute("error", userMessage);
    }

    /**
     * 年月文字列を YearMonth に変換し、不正値の場合は現在年月を返します。
     *
     * @param yearMonth 年月文字列（yyyy-MM）
     * @return 解析成功時は指定年月、失敗時は現在年月
     */
    private YearMonth parseYearMonthOrNow(String yearMonth) {
        if (yearMonth == null || yearMonth.isEmpty()) {
            return YearMonth.now();
        }
        try {
            return YearMonth.parse(yearMonth);
        } catch (Exception e) {
            log.warn(INVALID_YEAR_MONTH_LOG, yearMonth);
            return YearMonth.now();
        }
    }
}