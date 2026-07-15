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
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
            doAnswer(invocation -> {
                invocation.getArgument(3, java.io.OutputStream.class).write(mockData);
                return null;
            }).when(payrollExportService).writePayrollCsvGzip(eq(YearMonth.of(2026, 5)),
                    eq(PayrollExportFormat.MONEYFORWARD), eq(StandardCharsets.UTF_8), any());

            ResponseEntity<StreamingResponseBody> response = controller.exportPayrollCsv("2026-05", PayrollExportFormat.MONEYFORWARD, "UTF-8");
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            response.getBody().writeTo(outputStream);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getHeaders().getContentType().toString()).isEqualTo("application/gzip");
            assertThat(outputStream.toByteArray()).isEqualTo(mockData);
            assertThat(response.getHeaders().getFirst("Content-Disposition")).contains("payroll_moneyforward_2026-05.csv.gz");
        }

        @Test
        @DisplayName("正常系: 形式が不正な日付の場合は、自動的に前月分でエクスポートされる")
        void exportCsv_invalidDate_fallsBackToPreviousMonth() throws IOException {
            YearMonth expectedMonth = YearMonth.now().minusMonths(1);

            ResponseEntity<StreamingResponseBody> response = controller.exportPayrollCsv("invalid-date", PayrollExportFormat.MONEYFORWARD, "Shift_JIS");
            response.getBody().writeTo(new ByteArrayOutputStream());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(payrollExportService).writePayrollCsvGzip(eq(expectedMonth), eq(PayrollExportFormat.MONEYFORWARD), any(Charset.class), any());
        }

        @Test
        @DisplayName("正常系: エンコーディングにShift_JISを指定すると、Charset.forName(Shift_JIS)が使用される")
        void exportCsv_encodingShiftJis_usesShiftJis() throws IOException {
            Charset sjis = Charset.forName("Shift_JIS");

            ResponseEntity<StreamingResponseBody> response = controller.exportPayrollCsv("2026-05", PayrollExportFormat.MONEYFORWARD, "Shift_JIS");
            response.getBody().writeTo(new ByteArrayOutputStream());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(payrollExportService).writePayrollCsvGzip(eq(YearMonth.of(2026, 5)), eq(PayrollExportFormat.MONEYFORWARD), eq(sjis), any());
        }

        @Test
        @DisplayName("異常系: ストリーム書き込み失敗は呼び出し元へ伝播する")
        void exportCsv_ioException_isPropagatedFromStream() throws IOException {
            doThrow(new IOException("Disk Full")).when(payrollExportService)
                    .writePayrollCsvGzip(any(), any(), any(), any());

            ResponseEntity<StreamingResponseBody> response = controller.exportPayrollCsv("2026-05", PayrollExportFormat.MONEYFORWARD, "UTF-8");

            assertThatThrownBy(() -> response.getBody().writeTo(new ByteArrayOutputStream()))
                    .isInstanceOf(IOException.class);
        }
    }
}
