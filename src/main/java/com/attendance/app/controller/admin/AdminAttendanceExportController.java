package com.attendance.app.controller.admin;

import com.attendance.app.entity.*;
import com.attendance.app.service.PayrollExportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;

@Slf4j
@Controller
@RequestMapping("/admin/attendance")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminAttendanceExportController {

    private final PayrollExportService payrollExportService;

    @GetMapping("/export-payroll-csv")
    public ResponseEntity<StreamingResponseBody> exportPayrollCsv(
            @RequestParam String yearMonth,
            @RequestParam PayrollExportFormat format,
            @RequestParam(defaultValue = "Shift_JIS") String encoding) {
        try {
            YearMonth targetMonth;
            try {
                targetMonth = YearMonth.parse(yearMonth);
            } catch (DateTimeParseException e) {
                targetMonth = YearMonth.now().minusMonths(1);
            }

            Charset charset;
            if ("UTF-8".equalsIgnoreCase(encoding)) {
                charset = StandardCharsets.UTF_8;
            } else {
                charset = Charset.forName("Shift_JIS");
            }
            final YearMonth exportMonth = targetMonth;
            final Charset exportCharset = charset;

            String filename = "payroll_" + format.name().toLowerCase() + "_" + exportMonth.toString() + ".csv.gz";
            String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFilename)
                    .contentType(MediaType.parseMediaType("application/gzip"))
                    .body(outputStream -> payrollExportService.writePayrollCsvGzip(exportMonth, format, exportCharset, outputStream));

        } catch (Exception e) {
            log.error("給与CSV(GZIP)の生成に失敗しました: yearMonth={}, format={}", yearMonth, format, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
