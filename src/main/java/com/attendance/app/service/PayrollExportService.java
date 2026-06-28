package com.attendance.app.service;

import com.attendance.app.entity.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/**
 * 給与計算ソフト向けCSV生成サービス
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayrollExportService {

    private final UserService userService;
    private final AttendanceRecordService attendanceRecordService;
    private final LeaveApplicationService leaveApplicationService;

    /**
     * 指定年月の全ユーザー給与計算連携用CSVを生成し、GZIP圧縮して返します。
     *
     * @param yearMonth 対象年月
     * @param format 出力フォーマット
     * @param charset 出力文字コード (Shift_JIS または UTF-8)
     * @return GZIP圧縮されたCSVバイト配列
     * @throws IOException
     */
    public byte[] generatePayrollCsvGzip(YearMonth yearMonth, PayrollExportFormat format, Charset charset) throws IOException {
        List<User> users = userService.getActiveUsers();
        AttendanceRecordService.MonthRange monthRange = attendanceRecordService.getMonthRange(yearMonth);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzipOut = new GZIPOutputStream(baos);
             OutputStreamWriter osw = new OutputStreamWriter(gzipOut, charset);
             CSVPrinter csvPrinter = new CSVPrinter(osw, CSVFormat.EXCEL)) {

            // ヘッダー書き込み (フォーマットに関わらず標準的な汎用ヘッダーとする)
            csvPrinter.printRecord(
                    "従業員コード",
                    "氏名",
                    "出勤日数",
                    "欠勤日数",
                    "有休消化日数",
                    "総労働時間",
                    "時間外労働時間",
                    "深夜労働時間",
                    "休日労働時間"
            );

            for (User user : users) {
                // 勤怠記録と休暇申請の取得
                List<AttendanceRecord> records = attendanceRecordService.getRecordsByUserAndMonth(user.getUserId(), yearMonth);
                List<LeaveApplication> leaveApplications = leaveApplicationService.getApplicationsByUserAndDateRange(
                        user.getUserId(), monthRange.getStartDate(), monthRange.getEndDate());

                int workingDays = 0;
                double totalWorkingHours = 0.0;
                double totalOvertimeHours = 0.0;
                double totalNightShiftHours = 0.0;
                double totalHolidayWorkHours = 0.0;

                for (AttendanceRecord r : records) {
                    if (r.getWorkingHours() != null && r.getWorkingHours() > 0) {
                        workingDays++;
                        totalWorkingHours += r.getWorkingHours();
                    }
                    if (r.getOvertimeHours() != null) {
                        totalOvertimeHours += r.getOvertimeHours();
                    }
                    if (r.getNightShiftHours() != null) {
                        totalNightShiftHours += r.getNightShiftHours();
                    }
                    if (r.getHolidayWorkHours() != null) {
                        totalHolidayWorkHours += r.getHolidayWorkHours();
                    }
                }

                int paidLeaveDays = 0;
                int unpaidLeaveDays = 0;
                int absenceDays = 0;

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
                                paidLeaveDays++;
                            } else if (la.getLeaveType() == LeaveType.UNPAID_LEAVE) {
                                unpaidLeaveDays++;
                            } else if (la.getLeaveType() == LeaveType.ABSENCE) {
                                absenceDays++;
                            }
                        }
                    }
                }

                // 各行をCSVに書き込み
                csvPrinter.printRecord(
                        user.getEmpNo() != null ? user.getEmpNo() : user.getUserId().toString(),
                        user.getFullName(),
                        workingDays,
                        absenceDays + unpaidLeaveDays,
                        paidLeaveDays,
                        String.format("%.2f", totalWorkingHours),
                        String.format("%.2f", totalOvertimeHours),
                        String.format("%.2f", totalNightShiftHours),
                        String.format("%.2f", totalHolidayWorkHours)
                );
            }
        }

        log.info("給与連携CSV(GZIP)生成完了: yearMonth={}, format={}, users={}", yearMonth, format, users.size());
        return baos.toByteArray();
    }
}
