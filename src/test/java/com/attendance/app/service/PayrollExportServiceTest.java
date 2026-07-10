package com.attendance.app.service;

import com.attendance.app.entity.*;
import com.attendance.app.mapper.AttendanceRecordMapper;
import com.attendance.app.mapper.LeaveApplicationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.time.*;
import java.util.Collections;
import java.util.List;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class PayrollExportServiceTest {

    @Mock
    private UserService userService;

    @Mock
    private AttendanceRecordService attendanceRecordService;

    @Mock
    private LeaveApplicationService leaveApplicationService;

    @Mock
    private AttendanceRecordMapper attendanceRecordMapper;

    @Mock
    private LeaveApplicationMapper leaveApplicationMapper;

    @InjectMocks
    private PayrollExportService payrollExportService;

    private User testUser;
    private YearMonth testYearMonth;
    private AttendanceRecordService.MonthRange monthRange;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setUserId(1L);
        testUser.setEmpNo("EMP001");
        testUser.setFullName("テスト 太郎");

        testYearMonth = YearMonth.of(2023, 10);
        monthRange = AttendanceRecordService.MonthRange.of(testYearMonth, 1, 31);
    }

    @Test
    void testGeneratePayrollCsvGzip_EncodingAndLineSeparator() throws IOException {
        // Mock setup
        when(userService.getActiveUsers()).thenReturn(List.of(testUser));
        when(attendanceRecordService.getMonthRange(testYearMonth)).thenReturn(monthRange);

        AttendanceRecord record = new AttendanceRecord();
        record.setUserId(1L);
        record.setWorkingHours(8.0);
        record.setOvertimeHours(1.5);
        when(attendanceRecordMapper.selectAllByDateRange(any(), any()))
                .thenReturn(List.of(record));

        when(leaveApplicationMapper.selectAllByDateRange(any(), any()))
                .thenReturn(Collections.emptyList());

        // Execute (Shift_JIS)
        byte[] gzipBytes = payrollExportService.generatePayrollCsvGzip(testYearMonth, PayrollExportFormat.MONEYFORWARD, Charset.forName("Shift_JIS"));

        // Verify GZIP and CSV content
        assertThat(gzipBytes).isNotNull();
        assertThat(gzipBytes.length).isGreaterThan(0);

        // Decode GZIP and read as Shift_JIS
        StringBuilder decodedContent = new StringBuilder();
        try (ByteArrayInputStream bais = new ByteArrayInputStream(gzipBytes);
             GZIPInputStream gzipIn = new GZIPInputStream(bais);
             InputStreamReader isr = new InputStreamReader(gzipIn, Charset.forName("Shift_JIS"))) {
            char[] buffer = new char[1024];
            int read;
            while ((read = isr.read(buffer)) != -1) {
                decodedContent.append(buffer, 0, read);
            }
        }

        String csvString = decodedContent.toString();

        // 1. Check encoding (Shift_JIS) by ensuring characters are correctly decoded (e.g., Japanese header)
        assertThat(csvString).contains("従業員コード");
        assertThat(csvString).contains("テスト 太郎");

        // 2. Check Line Separator (CRLF expected from CSVFormat.EXCEL)
        // Ensure there's a CRLF after the header and after the data row
        assertThat(csvString).contains("\r\n");
        // Ensure it does not only contain \n without \r
        String[] lines = csvString.split("\r\n");
        assertThat(lines).hasSize(2); // 1 header + 1 data row
        
        assertThat(lines[0]).isEqualTo("従業員コード,氏名,出勤日数,欠勤日数,有休消化日数,総労働時間,時間外労働時間,深夜労働時間,休日労働時間");
        assertThat(lines[1]).isEqualTo("EMP001,テスト 太郎,1,0,0,8時間0分,1時間30分,0時間0分,0時間0分");
    }

    @Test
    void testGeneratePayrollCsvGzip_UTF8() throws IOException {
        // Mock setup
        when(userService.getActiveUsers()).thenReturn(List.of(testUser));
        when(attendanceRecordService.getMonthRange(testYearMonth)).thenReturn(monthRange);

        AttendanceRecord record = new AttendanceRecord();
        record.setUserId(1L);
        record.setWorkingHours(8.0);
        record.setOvertimeHours(1.5);
        when(attendanceRecordMapper.selectAllByDateRange(any(), any()))
                .thenReturn(List.of(record));

        when(leaveApplicationMapper.selectAllByDateRange(any(), any()))
                .thenReturn(Collections.emptyList());

        // Execute (UTF-8)
        byte[] gzipBytes = payrollExportService.generatePayrollCsvGzip(testYearMonth, PayrollExportFormat.MONEYFORWARD, java.nio.charset.StandardCharsets.UTF_8);

        // Decode GZIP and read as UTF-8
        StringBuilder decodedContent = new StringBuilder();
        try (ByteArrayInputStream bais = new ByteArrayInputStream(gzipBytes);
             GZIPInputStream gzipIn = new GZIPInputStream(bais);
             InputStreamReader isr = new InputStreamReader(gzipIn, java.nio.charset.StandardCharsets.UTF_8)) {
            char[] buffer = new char[1024];
            int read;
            while ((read = isr.read(buffer)) != -1) {
                decodedContent.append(buffer, 0, read);
            }
        }

        String csvString = decodedContent.toString();
        assertThat(csvString).contains("従業員コード");
        assertThat(csvString).contains("テスト 太郎");
    }

    @Test
    void testGeneratePayrollCsvGzip_IntegrityAndDifferentPatterns() throws IOException {
        // Mock setup for two users
        User user1 = new User();
        user1.setUserId(1L);
        user1.setEmpNo("EMP001");
        user1.setFullName("テスト 太郎");

        User user2 = new User();
        user2.setUserId(2L);
        user2.setEmpNo("EMP002");
        user2.setFullName("テスト 次郎");

        when(userService.getActiveUsers()).thenReturn(List.of(user1, user2));
        when(attendanceRecordService.getMonthRange(testYearMonth)).thenReturn(monthRange);

        // User1 attendance records
        AttendanceRecord r1 = new AttendanceRecord();
        r1.setUserId(1L);
        r1.setWorkingHours(8.0);
        r1.setOvertimeHours(1.0);
        r1.setNightShiftHours(1.0);
        r1.setHolidayWorkHours(0.0);

        AttendanceRecord r2 = new AttendanceRecord();
        r2.setUserId(1L);
        r2.setWorkingHours(7.5);
        r2.setOvertimeHours(0.0);
        r2.setNightShiftHours(0.0);
        r2.setHolidayWorkHours(0.0);

        // User1 has attendance records, User2 has none (bulk fetch)
        when(attendanceRecordMapper.selectAllByDateRange(any(), any()))
                .thenReturn(List.of(r1, r2));

        // User2 leave applications (Approved and Rejected)
        LeaveApplication paidLeave = new LeaveApplication();
        paidLeave.setUserId(2L);
        paidLeave.setLeaveType(LeaveType.PAID_LEAVE);
        paidLeave.setStatus(LeaveStatus.APPROVED);
        paidLeave.setLeaveStartDate(LocalDate.of(2023, 10, 2));
        paidLeave.setLeaveEndDate(LocalDate.of(2023, 10, 2));

        LeaveApplication unpaidLeave = new LeaveApplication();
        unpaidLeave.setUserId(2L);
        unpaidLeave.setLeaveType(LeaveType.UNPAID_LEAVE);
        unpaidLeave.setStatus(LeaveStatus.APPROVED);
        unpaidLeave.setLeaveStartDate(LocalDate.of(2023, 10, 3));
        unpaidLeave.setLeaveEndDate(LocalDate.of(2023, 10, 3));

        LeaveApplication absence = new LeaveApplication();
        absence.setUserId(2L);
        absence.setLeaveType(LeaveType.ABSENCE);
        absence.setStatus(LeaveStatus.APPROVED);
        absence.setLeaveStartDate(LocalDate.of(2023, 10, 4));
        absence.setLeaveEndDate(LocalDate.of(2023, 10, 4));

        LeaveApplication rejectedPaidLeave = new LeaveApplication();
        rejectedPaidLeave.setUserId(2L);
        rejectedPaidLeave.setLeaveType(LeaveType.PAID_LEAVE);
        rejectedPaidLeave.setStatus(LeaveStatus.REJECTED); // Rejected, should not count
        rejectedPaidLeave.setLeaveStartDate(LocalDate.of(2023, 10, 5));
        rejectedPaidLeave.setLeaveEndDate(LocalDate.of(2023, 10, 5));

        when(leaveApplicationMapper.selectAllByDateRange(any(), any()))
                .thenReturn(List.of(paidLeave, unpaidLeave, absence, rejectedPaidLeave));

        when(leaveApplicationService.calculateDailyConsumedDays(any()))
                .thenAnswer(inv -> {
                    String dt = inv.getArgument(0);
                    return ("AM_HALF".equals(dt) || "PM_HALF".equals(dt)) ? new java.math.BigDecimal("0.5") : java.math.BigDecimal.ONE;
                });

        // Execute
        byte[] gzipBytes = payrollExportService.generatePayrollCsvGzip(testYearMonth, PayrollExportFormat.MONEYFORWARD, java.nio.charset.StandardCharsets.UTF_8);

        // Decode
        StringBuilder decodedContent = new StringBuilder();
        try (ByteArrayInputStream bais = new ByteArrayInputStream(gzipBytes);
             GZIPInputStream gzipIn = new GZIPInputStream(bais);
             InputStreamReader isr = new InputStreamReader(gzipIn, java.nio.charset.StandardCharsets.UTF_8)) {
            char[] buffer = new char[1024];
            int read;
            while ((read = isr.read(buffer)) != -1) {
                decodedContent.append(buffer, 0, read);
            }
        }

        String csvString = decodedContent.toString();
        // UTF-8出力では先頭にBOMが付与される(#20)ため除去してからヘッダを検証する
        if (!csvString.isEmpty() && csvString.charAt(0) == '﻿') {
            csvString = csvString.substring(1);
        }
        String[] lines = csvString.split("\r\n");
        assertThat(lines).hasSize(3); // Header + User1 + User2

        // Header check
        assertThat(lines[0]).isEqualTo("従業員コード,氏名,出勤日数,欠勤日数,有休消化日数,総労働時間,時間外労働時間,深夜労働時間,休日労働時間");

        // User1 data row check
        // EMP001,テスト 太郎, 出勤日数:2, 欠勤日数:0, 有休消化日数:0, 総労働:15.50, 残業:1.00, 深夜:1.00, 休日:0.00
        assertThat(lines[1]).isEqualTo("EMP001,テスト 太郎,2,0,0,15時間30分,1時間0分,1時間0分,0時間0分");

        // User2 data row check
        // EMP002,テスト 次郎, 出勤日数:0, 欠勤日数:2 (unpaid + absence), 有休消化日数:1, 総労働:0.00, 残業:0.00, 深夜:0.00, 休日:0.00
        assertThat(lines[2]).isEqualTo("EMP002,テスト 次郎,0,2,1,0時間0分,0時間0分,0時間0分,0時間0分");
    }
}
