package com.attendance.app.controller;

import com.attendance.app.entity.Holiday;
import com.attendance.app.service.SystemSettingService;
import com.attendance.app.service.AttendancePeriodSettingService;
import com.attendance.app.service.BatchSettingService;
import com.attendance.app.service.CsvFilenamePatternService;
import com.attendance.app.service.HolidayService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.bind.support.SessionStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SettingsController Unit Tests")
class SettingsControllerTest {

    @Mock
    private SystemSettingService systemSettingService;

    @Mock
    private AttendancePeriodSettingService attendancePeriodSettingService;

    @Mock
    private BatchSettingService batchSettingService;

    @Mock
    private CsvFilenamePatternService csvFilenamePatternService;

    @Mock
    private HolidayService holidayService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    @InjectMocks
    private SettingsController controller;

    @Test
    @DisplayName("showSettings: 設定値がnullの場合、デフォルト値がモデルに設定されること")
    void showSettings_withNullSettings_setsDefaultValues() {
        // Arrange
        when(systemSettingService.getSettingValue("PAID_LEAVE_GRANT_DATE")).thenReturn(null);
        when(systemSettingService.getSettingValue("PAID_LEAVE_GRANT_DAYS")).thenReturn(null);
        when(systemSettingService.getSettingValue("COPYRIGHT_TEXT")).thenReturn(null);
        when(systemSettingService.getSettingValue("SYSTEM_NAME")).thenReturn(null);

        when(attendancePeriodSettingService.getStartDay()).thenReturn(21);
        when(attendancePeriodSettingService.getEndDay()).thenReturn(20);
        when(batchSettingService.getMonthlySummaryDaysAfterEnd()).thenReturn(5);
        when(batchSettingService.getReminderDay()).thenReturn(25);
        when(batchSettingService.getReminderHour()).thenReturn(9);
        when(batchSettingService.getAlertArticle36Limit1()).thenReturn(45);
        when(batchSettingService.getAlertArticle36Limit2()).thenReturn(80);
        when(batchSettingService.getAlertPaidLeaveMonths()).thenReturn(6);
        when(batchSettingService.getAlertPaidLeaveDays()).thenReturn(5);
        when(csvFilenamePatternService.getPattern()).thenReturn("yyyyMM");
        when(holidayService.getAllHolidays()).thenReturn(Collections.emptyList());

        ExtendedModelMap model = new ExtendedModelMap();

        // Act
        String viewName = controller.showSettings(model);

        // Assert
        assertThat(viewName).isEqualTo("admin/settings");
        assertThat(model.get("paidLeaveGrantDate")).isEqualTo("04-01");
        assertThat(model.get("paidLeaveGrantDays")).isEqualTo("10");
        assertThat(model.get("copyrightText")).isEqualTo("© 2026 勤怠管理システム");
        assertThat(model.get("systemName")).isEqualTo("勤怠管理システム");
        assertThat(model.get("attendancePeriodStartDay")).isEqualTo(21);
        assertThat(model.get("existingHolidays")).isEqualTo(Collections.emptyList());
        assertThat(model.get("holidayCount")).isEqualTo(0);
    }

    @Test
    @DisplayName("showSettings: 設定値が存在する場合、その値がモデルに設定されること")
    void showSettings_withExistingSettings_setsExistingValues() {
        // Arrange
        when(systemSettingService.getSettingValue("PAID_LEAVE_GRANT_DATE")).thenReturn("10-01");
        when(systemSettingService.getSettingValue("PAID_LEAVE_GRANT_DAYS")).thenReturn("20");
        when(systemSettingService.getSettingValue("COPYRIGHT_TEXT")).thenReturn("© Custom Copyright");
        when(systemSettingService.getSettingValue("SYSTEM_NAME")).thenReturn("Custom System");

        when(attendancePeriodSettingService.getStartDay()).thenReturn(1);
        when(attendancePeriodSettingService.getEndDay()).thenReturn(31);
        when(holidayService.getAllHolidays()).thenReturn(List.of(new Holiday()));

        ExtendedModelMap model = new ExtendedModelMap();

        // Act
        String viewName = controller.showSettings(model);

        // Assert
        assertThat(viewName).isEqualTo("admin/settings");
        assertThat(model.get("paidLeaveGrantDate")).isEqualTo("10-01");
        assertThat(model.get("paidLeaveGrantDays")).isEqualTo("20");
        assertThat(model.get("copyrightText")).isEqualTo("© Custom Copyright");
        assertThat(model.get("systemName")).isEqualTo("Custom System");
        assertThat(model.get("holidayCount")).isEqualTo(1);
    }

    @Test
    @DisplayName("showSettings: 例外発生時はerror属性を設定して同一画面を返す")
    void showSettings_exception_setsErrorAttribute() {
        when(systemSettingService.getSettingValue("PAID_LEAVE_GRANT_DATE"))
                .thenThrow(new RuntimeException("DB Error"));

        ExtendedModelMap model = new ExtendedModelMap();

        String viewName = controller.showSettings(model);

        assertThat(viewName).isEqualTo("admin/settings");
        assertThat(model.get("error")).isEqualTo("システム設定画面の表示に失敗しました");
    }

    @Test
    @DisplayName("saveSettings: 正常値の更新")
    void saveSettings_validParameters_success() {
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        String viewName = controller.saveSettings("04-01", "10", redirectAttributes);

        assertThat(viewName).isEqualTo("redirect:/admin/settings");
        assertThat(redirectAttributes.getFlashAttributes().get("message")).isEqualTo("システム設定を更新しました。");
        verify(systemSettingService).updateSettingValue("PAID_LEAVE_GRANT_DATE", "04-01");
        verify(systemSettingService).updateSettingValue("PAID_LEAVE_GRANT_DAYS", "10");
    }

    @Test
    @DisplayName("saveSettings: 有給付与日（日付フォーマット）が不正な場合はエラーを返す")
    void saveSettings_invalidDatePattern_returnsError() {
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        String viewName = controller.saveSettings("04/01", "10", redirectAttributes);

        assertThat(viewName).isEqualTo("redirect:/admin/settings");
        assertThat(redirectAttributes.getFlashAttributes().get("errorMessage"))
                .isEqualTo("有給付与日はMM-DD形式（例: 04-01）で入力してください。");
        verify(systemSettingService, never()).updateSettingValue(any(), any());
    }

    @Test
    @DisplayName("saveSettings: 有給付与日数が範囲外（下限）の場合はエラーを返す")
    void saveSettings_outOfBoundsDaysLow_returnsError() {
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        String viewName = controller.saveSettings("04-01", "0", redirectAttributes);

        assertThat(viewName).isEqualTo("redirect:/admin/settings");
        assertThat(redirectAttributes.getFlashAttributes().get("errorMessage"))
                .isEqualTo("有給付与日数は1〜40の範囲で指定してください。");
        verify(systemSettingService, never()).updateSettingValue(any(), any());
    }

    @Test
    @DisplayName("saveSettings: 有給付与日数が範囲外（上限）の場合はエラーを返す")
    void saveSettings_outOfBoundsDaysHigh_returnsError() {
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        String viewName = controller.saveSettings("04-01", "41", redirectAttributes);

        assertThat(viewName).isEqualTo("redirect:/admin/settings");
        assertThat(redirectAttributes.getFlashAttributes().get("errorMessage"))
                .isEqualTo("有給付与日数は1〜40の範囲で指定してください。");
        verify(systemSettingService, never()).updateSettingValue(any(), any());
    }

    @Test
    @DisplayName("saveSettings: 有給付与日数が数値以外の場合はエラーを返す")
    void saveSettings_nonNumericDays_returnsError() {
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        String viewName = controller.saveSettings("04-01", "abc", redirectAttributes);

        assertThat(viewName).isEqualTo("redirect:/admin/settings");
        assertThat(redirectAttributes.getFlashAttributes().get("errorMessage"))
                .isEqualTo("有給付与日数は有効な数値を入力してください。");
        verify(systemSettingService, never()).updateSettingValue(any(), any());
    }

    @Test
    @DisplayName("updateCopyrightSetting: 正常値の更新")
    void updateCopyrightSetting_success() {
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        String viewName = controller.updateCopyrightSetting("© 2026 Test", redirectAttributes);

        assertThat(viewName).isEqualTo("redirect:/admin/settings");
        assertThat(redirectAttributes.getFlashAttributes().get("message")).isEqualTo("コピーライト表示設定を更新しました");
        verify(systemSettingService).updateSettingValue("COPYRIGHT_TEXT", "© 2026 Test");
    }

    @Test
    @DisplayName("updateCopyrightSetting: 空文字の場合はエラーを返す")
    void updateCopyrightSetting_emptyText_returnsError() {
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        String viewName = controller.updateCopyrightSetting("  ", redirectAttributes);

        assertThat(viewName).isEqualTo("redirect:/admin/settings");
        assertThat(redirectAttributes.getFlashAttributes().get("errorMessage")).isEqualTo("コピーライト表示文言を入力してください");
        verify(systemSettingService, never()).updateSettingValue(any(), any());
    }

    @Test
    @DisplayName("updateCopyrightSetting: 255文字を超える場合はエラーを返す")
    void updateCopyrightSetting_tooLongText_returnsError() {
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();
        String longText = "a".repeat(256);

        String viewName = controller.updateCopyrightSetting(longText, redirectAttributes);

        assertThat(viewName).isEqualTo("redirect:/admin/settings");
        assertThat(redirectAttributes.getFlashAttributes().get("errorMessage")).isEqualTo("コピーライト表示文言は255文字以内で入力してください");
        verify(systemSettingService, never()).updateSettingValue(any(), any());
    }

    @Test
    @DisplayName("updateCopyrightSetting: 例外発生時のハンドリング")
    void updateCopyrightSetting_exception_returnsError() {
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();
        doThrow(new RuntimeException("DB Error")).when(systemSettingService).updateSettingValue(any(), any());

        String viewName = controller.updateCopyrightSetting("© 2026 Test", redirectAttributes);

        assertThat(viewName).isEqualTo("redirect:/admin/settings");
        assertThat(redirectAttributes.getFlashAttributes().get("errorMessage")).isEqualTo("コピーライト表示設定の更新に失敗しました");
    }

    @Test
    @DisplayName("updateSystemNameSetting: 正常値の更新")
    void updateSystemNameSetting_success() {
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        String viewName = controller.updateSystemNameSetting("New System Name", redirectAttributes);

        assertThat(viewName).isEqualTo("redirect:/admin/settings");
        assertThat(redirectAttributes.getFlashAttributes().get("message")).isEqualTo("システム名表示設定を更新しました");
        verify(systemSettingService).updateSettingValue("SYSTEM_NAME", "New System Name");
    }

    @Test
    @DisplayName("updateSystemNameSetting: 空文字の場合はエラーを返す")
    void updateSystemNameSetting_empty_returnsError() {
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        String viewName = controller.updateSystemNameSetting("", redirectAttributes);

        assertThat(viewName).isEqualTo("redirect:/admin/settings");
        assertThat(redirectAttributes.getFlashAttributes().get("errorMessage")).isEqualTo("システム名を入力してください");
        verify(systemSettingService, never()).updateSettingValue(any(), any());
    }

    @Test
    @DisplayName("updateSystemNameSetting: 255文字を超える場合はエラーを返す")
    void updateSystemNameSetting_tooLong_returnsError() {
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();
        String longText = "a".repeat(256);

        String viewName = controller.updateSystemNameSetting(longText, redirectAttributes);

        assertThat(viewName).isEqualTo("redirect:/admin/settings");
        assertThat(redirectAttributes.getFlashAttributes().get("errorMessage")).isEqualTo("システム名は255文字以内で入力してください");
    }

    @Test
    @DisplayName("updateSystemNameSetting: 例外発生時のハンドリング")
    void updateSystemNameSetting_exception_returnsError() {
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();
        doThrow(new RuntimeException("DB Error")).when(systemSettingService).updateSettingValue(any(), any());

        String viewName = controller.updateSystemNameSetting("New System Name", redirectAttributes);

        assertThat(viewName).isEqualTo("redirect:/admin/settings");
        assertThat(redirectAttributes.getFlashAttributes().get("errorMessage")).isEqualTo("システム名表示設定の更新に失敗しました");
    }

    @Test
    @DisplayName("updateAttendancePeriod: 正常値の更新")
    void updateAttendancePeriod_success() {
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        String viewName = controller.updateAttendancePeriod(21, 20, redirectAttributes);

        assertThat(viewName).isEqualTo("redirect:/admin/settings");
        assertThat((String) redirectAttributes.getFlashAttributes().get("message")).contains("勤怠期間設定を更新しました");
        verify(attendancePeriodSettingService).updatePeriod(21, 20);
    }

    @Test
    @DisplayName("updateAttendancePeriod: IllegalArgumentException 発生時のハンドリング")
    void updateAttendancePeriod_invalidArgs_returnsError() {
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();
        doThrow(new IllegalArgumentException("開始日は1から28の間で指定してください。")).when(attendancePeriodSettingService).updatePeriod(anyInt(), anyInt());

        String viewName = controller.updateAttendancePeriod(30, 29, redirectAttributes);

        assertThat(viewName).isEqualTo("redirect:/admin/settings");
        assertThat(redirectAttributes.getFlashAttributes().get("errorMessage")).isEqualTo("開始日は1から28の間で指定してください。");
    }

    @Test
    @DisplayName("updateAttendancePeriod: その他例外発生時のハンドリング")
    void updateAttendancePeriod_exception_returnsError() {
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();
        doThrow(new RuntimeException("Other error")).when(attendancePeriodSettingService).updatePeriod(anyInt(), anyInt());

        String viewName = controller.updateAttendancePeriod(21, 20, redirectAttributes);

        assertThat(viewName).isEqualTo("redirect:/admin/settings");
        assertThat(redirectAttributes.getFlashAttributes().get("errorMessage")).isEqualTo("勤怠期間設定の更新に失敗しました");
    }

    @Test
    @DisplayName("updateBatchSettings: 正常値の更新")
    void updateBatchSettings_success() {
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        String viewName = controller.updateBatchSettings(5, 25, 9, redirectAttributes);

        assertThat(viewName).isEqualTo("redirect:/admin/settings");
        assertThat(redirectAttributes.getFlashAttributes().get("message")).isEqualTo("バッチ処理設定を更新しました");
        verify(batchSettingService).updateSettings(5, 25, 9);
    }

    @Test
    @DisplayName("updateBatchSettings: IllegalArgumentException 発生時のハンドリング")
    void updateBatchSettings_invalidArgs_returnsError() {
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();
        doThrow(new IllegalArgumentException("Invalid batch parameter")).when(batchSettingService).updateSettings(anyInt(), anyInt(), anyInt());

        String viewName = controller.updateBatchSettings(99, 25, 9, redirectAttributes);

        assertThat(viewName).isEqualTo("redirect:/admin/settings");
        assertThat(redirectAttributes.getFlashAttributes().get("errorMessage")).isEqualTo("Invalid batch parameter");
    }

    @Test
    @DisplayName("updateBatchSettings: その他例外発生時のハンドリング")
    void updateBatchSettings_exception_returnsError() {
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();
        doThrow(new RuntimeException("Other error")).when(batchSettingService).updateSettings(anyInt(), anyInt(), anyInt());

        String viewName = controller.updateBatchSettings(5, 25, 9, redirectAttributes);

        assertThat(viewName).isEqualTo("redirect:/admin/settings");
        assertThat(redirectAttributes.getFlashAttributes().get("errorMessage")).isEqualTo("バッチ処理設定の更新に失敗しました");
    }

    @Test
    @DisplayName("updateAlertSettings: 正常値の更新")
    void updateAlertSettings_success() {
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        String viewName = controller.updateAlertSettings(45, 80, 6, 5, 11, redirectAttributes);

        assertThat(viewName).isEqualTo("redirect:/admin/settings");
        assertThat(redirectAttributes.getFlashAttributes().get("message")).isEqualTo("アラート閾値設定を更新しました");
        verify(batchSettingService).updateAlertSettings(45, 80, 6, 5, 11);
    }

    @Test
    @DisplayName("updateAlertSettings: IllegalArgumentException 発生時のハンドリング")
    void updateAlertSettings_invalidArgs_returnsError() {
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();
        doThrow(new IllegalArgumentException("Invalid alert parameter")).when(batchSettingService).updateAlertSettings(anyInt(), anyInt(), anyInt(), anyInt(), anyInt());

        String viewName = controller.updateAlertSettings(45, 80, 6, 5, 11, redirectAttributes);

        assertThat(viewName).isEqualTo("redirect:/admin/settings");
        assertThat(redirectAttributes.getFlashAttributes().get("errorMessage")).isEqualTo("Invalid alert parameter");
    }

    @Test
    @DisplayName("updateAlertSettings: その他例外発生時のハンドリング")
    void updateAlertSettings_exception_returnsError() {
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();
        doThrow(new RuntimeException("Other error")).when(batchSettingService).updateAlertSettings(anyInt(), anyInt(), anyInt(), anyInt(), anyInt());

        String viewName = controller.updateAlertSettings(45, 80, 6, 5, 11, redirectAttributes);

        assertThat(viewName).isEqualTo("redirect:/admin/settings");
        assertThat(redirectAttributes.getFlashAttributes().get("errorMessage")).isEqualTo("アラート閾値設定の更新に失敗しました");
    }

    @Test
    @DisplayName("updateCsvFilenamePattern: 正常値の更新")
    void updateCsvFilenamePattern_success() {
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        String viewName = controller.updateCsvFilenamePattern("[yyyyMM]", redirectAttributes);

        assertThat(viewName).isEqualTo("redirect:/admin/settings");
        assertThat(redirectAttributes.getFlashAttributes().get("message")).isEqualTo("CSVファイル名パターン設定を更新しました");
        verify(csvFilenamePatternService).updatePattern("[yyyyMM]");
    }

    @Test
    @DisplayName("updateCsvFilenamePattern: IllegalArgumentException 発生時のハンドリング")
    void updateCsvFilenamePattern_invalidArgs_returnsError() {
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();
        doThrow(new IllegalArgumentException("Invalid pattern")).when(csvFilenamePatternService).updatePattern(anyString());

        String viewName = controller.updateCsvFilenamePattern("invalid", redirectAttributes);

        assertThat(viewName).isEqualTo("redirect:/admin/settings");
        assertThat(redirectAttributes.getFlashAttributes().get("errorMessage")).isEqualTo("Invalid pattern");
    }

    @Test
    @DisplayName("updateCsvFilenamePattern: その他例外発生時のハンドリング")
    void updateCsvFilenamePattern_exception_returnsError() {
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();
        doThrow(new RuntimeException("Other error")).when(csvFilenamePatternService).updatePattern(anyString());

        String viewName = controller.updateCsvFilenamePattern("[yyyyMM]", redirectAttributes);

        assertThat(viewName).isEqualTo("redirect:/admin/settings");
        assertThat(redirectAttributes.getFlashAttributes().get("errorMessage")).isEqualTo("CSVファイル名パターン設定の更新に失敗しました");
    }

    @Test
    @DisplayName("uploadCsv: 正常にパースされ、プレビュー用の祝日リストが設定されること")
    void uploadCsv_success() throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();
        ExtendedModelMap model = new ExtendedModelMap();

        List<Holiday> parsedHolidays = List.of(
                Holiday.builder().holidayDate(LocalDate.of(2026, 1, 1)).name("元日").build()
        );
        when(holidayService.parseFromCsv(file)).thenReturn(parsedHolidays);

        String viewName = controller.uploadCsv(file, redirectAttributes, model);

        assertThat(viewName).isEqualTo("redirect:/admin/settings");
        assertThat(model.get("previewHolidays")).isEqualTo(parsedHolidays);
        assertThat((String) redirectAttributes.getFlashAttributes().get("message")).contains("CSVファイルをアップロードしました");
    }

    @Test
    @DisplayName("uploadCsv: IOException 発生時のハンドリング")
    void uploadCsv_ioException_returnsError() throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();
        ExtendedModelMap model = new ExtendedModelMap();

        when(holidayService.parseFromCsv(file)).thenThrow(new IOException("Read error"));

        String viewName = controller.uploadCsv(file, redirectAttributes, model);

        assertThat(viewName).isEqualTo("redirect:/admin/settings");
        assertThat(model.get("previewHolidays")).isNull();
        assertThat((String) redirectAttributes.getFlashAttributes().get("errorMessage")).contains("ファイルの解析に失敗しました: Read error");
    }

    @Test
    @DisplayName("uploadCsv: 日付形式不正行を含むCSV(IllegalArgumentException)発生時のハンドリング")
    void uploadCsv_illegalArgumentException_returnsError() throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();
        ExtendedModelMap model = new ExtendedModelMap();

        when(holidayService.parseFromCsv(file)).thenThrow(new IllegalArgumentException("1行目の日付形式が不正です: 2026/01/01"));

        String viewName = controller.uploadCsv(file, redirectAttributes, model);

        assertThat(viewName).isEqualTo("redirect:/admin/settings");
        assertThat(model.get("previewHolidays")).isNull();
        assertThat((String) redirectAttributes.getFlashAttributes().get("errorMessage")).isEqualTo("1行目の日付形式が不正です: 2026/01/01");
    }

    @Test
    @DisplayName("confirmAndSave: 正常にJSONデータがデシリアライズされて保存されること")
    void confirmAndSave_success() throws IOException {
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();
        SessionStatus sessionStatus = mock(SessionStatus.class);
        String json = "[{\"holidayDate\":\"2026-01-01\",\"name\":\"元日\"}]";

        String viewName = controller.confirmAndSave(json, redirectAttributes, sessionStatus);

        assertThat(viewName).isEqualTo("redirect:/admin/settings");
        assertThat(redirectAttributes.getFlashAttributes().get("message")).isEqualTo("祝日設定を保存しました。");
        
        @SuppressWarnings("unchecked")
        List<Holiday> saved = (List<Holiday>) redirectAttributes.getFlashAttributes().get("savedHolidays");
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getHolidayDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(saved.get(0).getName()).isEqualTo("元日");

        verify(holidayService).saveHolidays(anyList());
        verify(sessionStatus).setComplete();
    }

    @Test
    @DisplayName("confirmAndSave: データが空の場合はエラーを返す")
    void confirmAndSave_emptyData_returnsError() {
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();
        SessionStatus sessionStatus = mock(SessionStatus.class);

        String viewName = controller.confirmAndSave("", redirectAttributes, sessionStatus);

        assertThat(viewName).isEqualTo("redirect:/admin/settings");
        assertThat(redirectAttributes.getFlashAttributes().get("errorMessage")).isEqualTo("保存するデータがありません。");
        verifyNoInteractions(objectMapper, holidayService, sessionStatus);
    }

    @Test
    @DisplayName("confirmAndSave: パースエラー（IOException）発生時のハンドリング")
    void confirmAndSave_parseError_returnsError() throws IOException {
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();
        SessionStatus sessionStatus = mock(SessionStatus.class);
        String json = "invalid json";

        String viewName = controller.confirmAndSave(json, redirectAttributes, sessionStatus);

        assertThat(viewName).isEqualTo("redirect:/admin/settings");
        assertThat((String) redirectAttributes.getFlashAttributes().get("errorMessage")).contains("データの処理に失敗しました:");
        verify(sessionStatus, never()).setComplete();
    }

    @Test
    @DisplayName("confirmAndSave: 保存エラー（その他例外）発生時のハンドリング")
    void confirmAndSave_saveError_returnsError() throws IOException {
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();
        SessionStatus sessionStatus = mock(SessionStatus.class);
        String json = "[]";

        doThrow(new RuntimeException("Save error")).when(holidayService).saveHolidays(any());

        String viewName = controller.confirmAndSave(json, redirectAttributes, sessionStatus);

        assertThat(viewName).isEqualTo("redirect:/admin/settings");
        assertThat((String) redirectAttributes.getFlashAttributes().get("errorMessage")).contains("祝日設定の保存に失敗しました: Save error");
        verify(sessionStatus, never()).setComplete();
    }

    @Test
    @DisplayName("cancelPreview: セッション情報がクリアされてリダイレクトされること")
    void cancelPreview_success() {
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();
        SessionStatus sessionStatus = mock(SessionStatus.class);

        String viewName = controller.cancelPreview(sessionStatus, redirectAttributes);

        assertThat(viewName).isEqualTo("redirect:/admin/settings");
        assertThat(redirectAttributes.getFlashAttributes().get("message")).isEqualTo("プレビューをキャンセルしました。");
        verify(sessionStatus).setComplete();
    }
}
