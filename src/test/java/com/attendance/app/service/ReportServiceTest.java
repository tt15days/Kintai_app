package com.attendance.app.service;

import com.attendance.app.entity.AttendanceRecord;
import com.attendance.app.entity.LeaveApplication;
import com.attendance.app.entity.LeaveStatus;
import com.attendance.app.entity.LeaveType;
import com.attendance.app.entity.User;
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
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ReportServiceTest {

    @Mock
    private AttendanceRecordService attendanceRecordService;

    @Mock
    private LeaveApplicationService leaveApplicationService;

    @Mock
    private UserService userService;

    @Mock
    private CsvFilenamePatternService csvFilenamePatternService;

    @Mock
    private AttendanceRecordMapper attendanceRecordMapper;

    @Mock
    private LeaveApplicationMapper leaveApplicationMapper;

    @Mock
    private AttendanceSubmissionService attendanceSubmissionService;

    @InjectMocks
    private ReportService reportService;

    private User testUser;
    private YearMonth targetMonth;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setUserId(1L);
        testUser.setFullName("テスト ユーザー");
        testUser.setEmail("test@example.com");

        targetMonth = YearMonth.of(2026, 6);

        lenient().when(attendanceSubmissionService.getSubmission(anyLong(), any())).thenReturn(java.util.Optional.empty());
        lenient().when(attendanceSubmissionService.getSubmissionsByTargetYearMonth(any())).thenReturn(Collections.emptyList());
    }

    @Test
    void testGenerateUserAttendanceCsv() {
        AttendanceRecordService.MonthRange monthRange = new AttendanceRecordService.MonthRange(
                targetMonth,
                21,
                20
        );

        when(attendanceRecordService.getMonthRange(targetMonth)).thenReturn(monthRange);

        AttendanceRecord record = new AttendanceRecord();
        record.setAttendanceDate(Instant.parse("2026-06-01T00:00:00Z")); // Matches 2026-06-01
        record.setStartTime(Instant.parse("2026-06-01T00:00:00Z")); // 09:00 JST
        record.setEndTime(Instant.parse("2026-06-01T09:00:00Z")); // 18:00 JST
        record.setWorkingHours(8.0);
        record.setOvertimeHours(1.5);
        record.setRemarks("通常勤務");

        when(attendanceRecordService.getRecordsByUserAndMonth(1L, targetMonth))
                .thenReturn(List.of(record));

        LeaveApplication leave = new LeaveApplication();
        leave.setLeaveStartDate(LocalDate.of(2026, 6, 2));
        leave.setLeaveEndDate(LocalDate.of(2026, 6, 2));
        leave.setLeaveType(LeaveType.PAID_LEAVE);
        leave.setStatus(LeaveStatus.APPROVED);

        when(leaveApplicationService.getApplicationsByUserAndDateRange(eq(1L), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(leave));

        byte[] csvBytes = reportService.generateUserAttendanceCsv(testUser, targetMonth);
        String csvContent = new String(csvBytes, StandardCharsets.UTF_8);

        assertTrue(csvContent.contains("テスト ユーザー"));
        assertTrue(csvContent.contains("test@example.com"));
        assertTrue(csvContent.contains("2026-06-01"));
        assertTrue(csvContent.contains("通常勤務"));
        assertTrue(csvContent.contains("有給休暇"));
        assertTrue(csvContent.contains("\"8時間0分\""));
        assertTrue(csvContent.contains("\"1時間30分\""));
    }

    @Test
    void testGenerateAllUsersAttendanceZip() throws IOException {
        User user1 = new User();
        user1.setUserId(1L);
        user1.setFullName("User1");

        User user2 = new User();
        user2.setUserId(2L);
        user2.setFullName("User2");

        when(userService.getActiveUsers()).thenReturn(List.of(user1, user2));
        
        AttendanceRecordService.MonthRange monthRange = new AttendanceRecordService.MonthRange(
                targetMonth,
                21,
                20
        );
        when(attendanceRecordService.getMonthRange(targetMonth)).thenReturn(monthRange);
        when(attendanceRecordMapper.selectAllByDateRange(any(), any())).thenReturn(Collections.emptyList());
        when(leaveApplicationMapper.selectAllByDateRange(any(), any())).thenReturn(Collections.emptyList());

        when(csvFilenamePatternService.buildCsvFilename(eq(user1), eq(targetMonth), any(OffsetDateTime.class)))
                .thenReturn("user1.csv");
        when(csvFilenamePatternService.buildCsvFilename(eq(user2), eq(targetMonth), any(OffsetDateTime.class)))
                .thenReturn("user2.csv");

        byte[] zipBytes = reportService.generateAllUsersAttendanceZip(targetMonth, OffsetDateTime.now());
        
        assertNotNull(zipBytes);
        assertTrue(zipBytes.length > 0);

        // Verify ZIP contents
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry1 = zis.getNextEntry();
            assertNotNull(entry1);
            assertEquals("user1.csv", entry1.getName());

            ZipEntry entry2 = zis.getNextEntry();
            assertNotNull(entry2);
            assertEquals("user2.csv", entry2.getName());

            assertNull(zis.getNextEntry());
        }
    }
}
