package com.attendance.app.service;

import com.attendance.app.entity.*;
import com.attendance.app.mapper.AttendanceRecordMapper;
import com.attendance.app.mapper.LeaveApplicationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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
    private final AttendanceRecordMapper attendanceRecordMapper;
    private final LeaveApplicationMapper leaveApplicationMapper;

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

        // 全ユーザー分の勤怠記録・休暇申請を一括取得し、userId でグルーピング(N+1回避)
        Instant rangeStart = com.attendance.app.util.DateTimeUtil.toInstant(monthRange.getStartDate());
        Instant rangeEnd = com.attendance.app.util.DateTimeUtil.toInstant(monthRange.getEndDate().plusDays(1));
        Map<Long, List<AttendanceRecord>> recordsByUser = attendanceRecordMapper.selectAllByDateRange(rangeStart, rangeEnd)
                .stream().collect(Collectors.groupingBy(AttendanceRecord::getUserId));
        Map<Long, List<LeaveApplication>> leaveApplicationsByUser = leaveApplicationMapper.selectAllByDateRange(
                        monthRange.getStartDate(), monthRange.getEndDate())
                .stream().collect(Collectors.groupingBy(LeaveApplication::getUserId));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzipOut = new GZIPOutputStream(baos);
             OutputStreamWriter osw = new OutputStreamWriter(gzipOut, charset);
             CSVPrinter csvPrinter = new CSVPrinter(osw, CSVFormat.EXCEL)) {

            // UTF-8 の場合はBOMを先頭に1回書き出す (Excel等での文字化け防止)
            if (StandardCharsets.UTF_8.equals(charset) || charset.name().toUpperCase().contains("UTF-8")) {
                osw.write('﻿');
            }

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
                // 勤怠記録と休暇申請の取得(一括取得結果からグルーピング済みのものを参照)
                List<AttendanceRecord> records = recordsByUser.getOrDefault(user.getUserId(), Collections.emptyList());
                List<LeaveApplication> leaveApplications = leaveApplicationsByUser.getOrDefault(user.getUserId(), Collections.emptyList());

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

                BigDecimal paidLeaveDays = BigDecimal.ZERO;
                BigDecimal unpaidLeaveDays = BigDecimal.ZERO;
                BigDecimal absenceDays = BigDecimal.ZERO;

                if (leaveApplications != null) {
                    Map<LocalDate, LeaveApplication> leaveMap = new HashMap<>();
                    for (LeaveApplication la : leaveApplications) {
                        // 月範囲(monthRange)にクリップして展開(月跨ぎ休暇の範囲外日数を計上しない)
                        LocalDate segStart = la.getLeaveStartDate().isBefore(monthRange.getStartDate())
                                ? monthRange.getStartDate() : la.getLeaveStartDate();
                        LocalDate segEnd = la.getLeaveEndDate().isAfter(monthRange.getEndDate())
                                ? monthRange.getEndDate() : la.getLeaveEndDate();
                        LocalDate d = segStart;
                        while (!d.isAfter(segEnd)) {
                            leaveMap.put(d, la);
                            d = d.plusDays(1);
                        }
                    }
                    for (LeaveApplication la : leaveMap.values()) {
                        if (la.getStatus() == LeaveStatus.APPROVED) {
                            BigDecimal consumed = leaveApplicationService.calculateDailyConsumedDays(la.getLeaveDurationType());
                            if (la.getLeaveType() == LeaveType.PAID_LEAVE) {
                                paidLeaveDays = paidLeaveDays.add(consumed);
                            } else if (la.getLeaveType() == LeaveType.UNPAID_LEAVE) {
                                unpaidLeaveDays = unpaidLeaveDays.add(consumed);
                            } else if (la.getLeaveType() == LeaveType.ABSENCE) {
                                absenceDays = absenceDays.add(consumed);
                            }
                        }
                    }
                }

                // 各行をCSVに書き込み
                csvPrinter.printRecord(
                        user.getEmpNo() != null ? user.getEmpNo() : user.getUserId().toString(),
                        user.getFullName(),
                        workingDays,
                        formatDays(absenceDays.add(unpaidLeaveDays)),
                        formatDays(paidLeaveDays),
                        com.attendance.app.util.DateTimeUtil.formatHoursToHHmm(totalWorkingHours),
                        com.attendance.app.util.DateTimeUtil.formatHoursToHHmm(totalOvertimeHours),
                        com.attendance.app.util.DateTimeUtil.formatHoursToHHmm(totalNightShiftHours),
                        com.attendance.app.util.DateTimeUtil.formatHoursToHHmm(totalHolidayWorkHours)
                );
            }
        }

        log.info("給与連携CSV(GZIP)生成完了: yearMonth={}, format={}, users={}", yearMonth, format, users.size());
        return baos.toByteArray();
    }

    /**
     * 日数を小数許容の文字列に整形します。整数なら "1"、半休を含む場合は "0.5"/"1.5" 等になります。
     */
    private String formatDays(BigDecimal days) {
        if (days == null) {
            return "0";
        }
        if (days.compareTo(BigDecimal.ZERO) == 0) {
            return "0";
        }
        return days.stripTrailingZeros().toPlainString();
    }
}
