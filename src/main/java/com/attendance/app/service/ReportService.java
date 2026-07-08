package com.attendance.app.service;

import com.attendance.app.entity.AttendanceRecord;
import com.attendance.app.entity.LeaveApplication;
import com.attendance.app.entity.LeaveStatus;
import com.attendance.app.entity.User;
import com.attendance.app.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * レポート出力（CSV/ZIP）に関する業務ロジックを提供するサービスです。
 *
 * 管理者向け月次勤怠CSVおよびZIPダウンロードを提供します。
 * CSV形式: UTF-8 (BOMなし)、CRLF改行、各値はダブルクォートで囲む
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private static final String CSV_HEADER =
            "\"氏名\",\"メールアドレス\",\"日付\",\"曜日\",\"出勤時刻\",\"退勤時刻\","
            + "\"勤務時間\",\"残業時間\",\"土曜休出時間\",\"日祝休出時間\",\"備考\",\"休暇種別\"";
    private static final String CRLF = "\r\n";
    private static final int SATURDAY = 6;

    private final AttendanceRecordService attendanceRecordService;
    private final LeaveApplicationService leaveApplicationService;
    private final UserService userService;
    private final CsvFilenamePatternService csvFilenamePatternService;

    /**
     * 指定ユーザーの月次勤怠CSVをバイト配列で返します。
     *
     * @param user 対象ユーザー
     * @param yearMonth 対象年月
     * @return CSV バイト配列（UTF-8 BOMなし）
     */
    public byte[] generateUserAttendanceCsv(User user, YearMonth yearMonth) {
        AttendanceRecordService.MonthRange monthRange = attendanceRecordService.getMonthRange(yearMonth);
        List<AttendanceRecord> records = attendanceRecordService.getRecordsByUserAndMonth(user.getUserId(), yearMonth);
        List<LeaveApplication> leaves = leaveApplicationService.getApplicationsByUserAndDateRange(
                user.getUserId(), monthRange.getStartDate(), monthRange.getEndDate());

        Map<LocalDate, AttendanceRecord> recordMap = buildRecordMap(records);
        Map<LocalDate, String> leaveMap = buildLeaveMap(leaves);

        StringBuilder sb = new StringBuilder();
        sb.append(CSV_HEADER).append(CRLF);

        LocalDate current = monthRange.getStartDate();
        while (!current.isAfter(monthRange.getEndDate())) {
            AttendanceRecord rec = recordMap.get(current);
            String leaveType = leaveMap.getOrDefault(current, "");
            sb.append(buildCsvRow(user, current, rec, leaveType));
            sb.append(CRLF);
            current = current.plusDays(1);
        }

        log.info("月次勤怠CSV生成: userId={}, yearMonth={}, rows={}", user.getUserId(), yearMonth,
                monthRange.getDaysInMonth());
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * アクティブな全ユーザーの月次勤怠CSVをまとめたZIPをバイト配列で返します。
     *
     *
     * @param yearMonth 対象年月
     * @param downloadedAt ダウンロード日時
     * @return ZIPバイト配列
     * @throws IOException ZIP生成に失敗した場合
     */
    public byte[] generateAllUsersAttendanceZip(YearMonth yearMonth, OffsetDateTime downloadedAt) throws IOException {
        List<User> users = userService.getActiveUsers();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try (ZipOutputStream zos = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
            for (User user : users) {
                byte[] csvBytes = generateUserAttendanceCsv(user, yearMonth);
                String entryName = csvFilenamePatternService.buildCsvFilename(user, yearMonth, downloadedAt);
                zos.putNextEntry(new ZipEntry(entryName));
                zos.write(csvBytes);
                zos.closeEntry();
            }
        }

        log.info("月次勤怠ZIP生成: yearMonth={}, userCount={}", yearMonth, users.size());
        return baos.toByteArray();
    }

    /**
     * 勤怠記録のリストから、日付をキーとしたマップを構築します。
     *
     * @param records 勤怠記録リスト
     * @return 勤怠記録のマップ
     */
    private Map<LocalDate, AttendanceRecord> buildRecordMap(List<AttendanceRecord> records) {
        Map<LocalDate, AttendanceRecord> map = new HashMap<>();
        if (records == null) {
            return map;
        }
        for (AttendanceRecord rec : records) {
            LocalDate date = DateTimeUtil.toLocalDate(rec.getAttendanceDate());
            if (date != null) {
                map.put(date, rec);
            }
        }
        return map;
    }

    /**
     * 休暇申請のリストから、日付をキーとした休暇種別名のマップを構築します。
     *
     * @param leaves 休暇申請リスト
     * @return 休暇種別名のマップ
     */
    private Map<LocalDate, String> buildLeaveMap(List<LeaveApplication> leaves) {
        Map<LocalDate, String> map = new HashMap<>();
        if (leaves == null) {
            return map;
        }
        for (LeaveApplication la : leaves) {
            if (la.getStatus() != LeaveStatus.APPROVED) {
                continue;
            }
            LocalDate d = la.getLeaveStartDate();
            while (!d.isAfter(la.getLeaveEndDate())) {
                map.put(d, la.getLeaveType().getDisplayName());
                d = d.plusDays(1);
            }
        }
        return map;
    }

    /**
     * CSVの1行分の文字列を構築します。
     *
     * @param user 対象ユーザー
     * @param date 対象日付
     * @param rec 勤怠記録
     * @param leaveType 休暇種別
     * @return CSV形式の行文字列
     */
    private String buildCsvRow(User user, LocalDate date, AttendanceRecord rec, String leaveType) {
        String fullName = user.getFullName() != null ? user.getFullName() : "";
        String email = user.getEmail() != null ? user.getEmail() : "";
        String dateStr = date.toString();
        String dayOfWeek = date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.JAPANESE);

        String startTime = "";
        String endTime = "";
        String workingHours = "";
        String overtimeHours = "";
        String saturdayWorkHours = "";
        String holidayWorkHours = "";
        String remarks = "";

        if (rec != null) {
            startTime = rec.getStartTime() != null
                    ? formatTime(DateTimeUtil.toLocalTime(rec.getStartTime()).toString())
                    : "";
            endTime = rec.getEndTime() != null
                    ? formatTime(DateTimeUtil.toLocalTime(rec.getEndTime()).toString())
                    : "";
            workingHours = rec.getWorkingHours() != null
                    ? com.attendance.app.util.DateTimeUtil.formatHoursToHHmm(rec.getWorkingHours())
                    : "";
            overtimeHours = rec.getOvertimeHours() != null
                    ? com.attendance.app.util.DateTimeUtil.formatHoursToHHmm(rec.getOvertimeHours())
                    : "";

            double hwHours = rec.getHolidayWorkHours() != null ? rec.getHolidayWorkHours() : 0.0;
            if (hwHours > 0) {
                if (date.getDayOfWeek().getValue() == SATURDAY) {
                    saturdayWorkHours = com.attendance.app.util.DateTimeUtil.formatHoursToHHmm(hwHours);
                    holidayWorkHours = "0時間0分";
                } else {
                    saturdayWorkHours = "0時間0分";
                    holidayWorkHours = com.attendance.app.util.DateTimeUtil.formatHoursToHHmm(hwHours);
                }
            }

            remarks = rec.getRemarks() != null ? rec.getRemarks() : "";
        }

        return csvQuote(fullName) + ","
                + csvQuote(email) + ","
                + csvQuote(dateStr) + ","
                + csvQuote(dayOfWeek) + ","
                + csvQuote(startTime) + ","
                + csvQuote(endTime) + ","
                + csvQuote(workingHours) + ","
                + csvQuote(overtimeHours) + ","
                + csvQuote(saturdayWorkHours) + ","
                + csvQuote(holidayWorkHours) + ","
                + csvQuote(remarks) + ","
                + csvQuote(leaveType);
    }

    /**
     * "HH:mm:ss" または "HH:mm" 形式から "HH:mm" の先頭5文字を返します。
     *
     * @param timeStr 時刻文字列
     * @return 先頭5文字の時刻文字列
     */
    private String formatTime(String timeStr) {
        if (timeStr == null || timeStr.length() < 5) {
            return timeStr != null ? timeStr : "";
        }
        return timeStr.substring(0, 5);
    }

    /**
     * 文字列をCSV用ダブルクォートで囲み、値中のダブルクォートをエスケープします。
     *
     * @param value 対象文字列
     * @return エスケープおよびクォートされた文字列
     */
    private String csvQuote(String value) {
        if (value == null) {
            return "\"\"";
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
