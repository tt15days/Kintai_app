package com.attendance.app.controller.admin;

import com.attendance.app.entity.PayrollExportFormat;
import com.attendance.app.service.PayrollExportService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminAttendanceExportController")
class AdminAttendanceExportControllerTest {

    @Mock
    private PayrollExportService payrollExportService;

    @InjectMocks
    private AdminAttendanceExportController controller;

    @Nested
    @DisplayName("exportPayrollCsv")
    class ExportPayrollCsv {

        @Test
        @DisplayName("正常系: 正しい引数を指定してGZIP形式のCSVをエクスポートする")
        void exportCsv_success() throws IOException {
            byte[] mockData = new byte[]{1, 2, 3};
            when(payrollExportService.generatePayrollCsvGzip(eq(YearMonth.of(2026, 5)), eq(PayrollExportFormat.MONEYFORWARD), eq(StandardCharsets.UTF_8)))
                    .thenReturn(mockData);

            ResponseEntity<byte[]> response = controller.exportPayrollCsv("2026-05", PayrollExportFormat.MONEYFORWARD, "UTF-8");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getHeaders().getContentType().toString()).isEqualTo("application/gzip");
            assertThat(response.getBody()).isEqualTo(mockData);
            assertThat(response.getHeaders().getFirst("Content-Disposition")).contains("payroll_moneyforward_2026-05.csv.gz");
        }

        @Test
        @DisplayName("正常系: 形式が不正な日付の場合は、自動的に前月分でエクスポートされる")
        void exportCsv_invalidDate_fallsBackToPreviousMonth() throws IOException {
            byte[] mockData = new byte[]{4, 5};
            YearMonth expectedMonth = YearMonth.now().minusMonths(1);
            when(payrollExportService.generatePayrollCsvGzip(eq(expectedMonth), eq(PayrollExportFormat.MONEYFORWARD), any(Charset.class)))
                    .thenReturn(mockData);

            ResponseEntity<byte[]> response = controller.exportPayrollCsv("invalid-date", PayrollExportFormat.MONEYFORWARD, "Shift_JIS");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(payrollExportService).generatePayrollCsvGzip(eq(expectedMonth), eq(PayrollExportFormat.MONEYFORWARD), any(Charset.class));
        }

        @Test
        @DisplayName("正常系: エンコーディングにShift_JISを指定すると、Charset.forName(Shift_JIS)が使用される")
        void exportCsv_encodingShiftJis_usesShiftJis() throws IOException {
            byte[] mockData = new byte[]{0};
            Charset sjis = Charset.forName("Shift_JIS");
            when(payrollExportService.generatePayrollCsvGzip(eq(YearMonth.of(2026, 5)), eq(PayrollExportFormat.MONEYFORWARD), eq(sjis)))
                    .thenReturn(mockData);

            ResponseEntity<byte[]> response = controller.exportPayrollCsv("2026-05", PayrollExportFormat.MONEYFORWARD, "Shift_JIS");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(payrollExportService).generatePayrollCsvGzip(eq(YearMonth.of(2026, 5)), eq(PayrollExportFormat.MONEYFORWARD), eq(sjis));
        }

        @Test
        @DisplayName("異常系: IOException発生時はHTTP 500(Internal Server Error)が返る")
        void exportCsv_ioException_returnsInternalServerError() throws IOException {
            when(payrollExportService.generatePayrollCsvGzip(any(), any(), any()))
                    .thenThrow(new IOException("Disk Full"));

            ResponseEntity<byte[]> response = controller.exportPayrollCsv("2026-05", PayrollExportFormat.MONEYFORWARD, "UTF-8");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
