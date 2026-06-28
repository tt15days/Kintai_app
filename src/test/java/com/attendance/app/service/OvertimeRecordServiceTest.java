package com.attendance.app.service;

import com.attendance.app.entity.OvertimeRecord;
import com.attendance.app.mapper.OvertimeRecordMapper;
import com.attendance.app.util.DateTimeUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OvertimeRecordService")
class OvertimeRecordServiceTest {

    @Mock
    private OvertimeRecordMapper overtimeRecordMapper;

    @InjectMocks
    private OvertimeRecordService service;

    @Nested
    @DisplayName("syncFromAttendance")
    class SyncFromAttendance {

        @Test
        @DisplayName("基準終了時刻を超えた場合は残業記録を新規作成する")
        void createsRecord_whenActualEndExceedsStandard() {
            Long userId = 1L;
            LocalDate date = LocalDate.of(2026, 5, 10);
            Instant actualEnd = DateTimeUtil.toInstant(date, LocalTime.of(19, 30));

            when(overtimeRecordMapper.selectByUserAndDateForUpdate(userId, date)).thenReturn(Optional.empty());

            service.syncFromAttendance(userId, date, LocalTime.of(9, 0), LocalTime.of(18, 0), actualEnd);

            ArgumentCaptor<OvertimeRecord> captor = ArgumentCaptor.forClass(OvertimeRecord.class);
            verify(overtimeRecordMapper).insert(captor.capture());

            OvertimeRecord inserted = captor.getValue();
            assertThat(inserted.getUserId()).isEqualTo(userId);
            assertThat(inserted.getOvertimeDate()).isEqualTo(date);
            assertThat(inserted.getOvertimeStart()).isEqualTo(DateTimeUtil.toInstant(date, LocalTime.of(18, 0)));
            assertThat(inserted.getOvertimeHours()).isEqualTo(1.5);
        }

        @Test
        @DisplayName("基準終了時刻以前の退勤は残業記録を削除する")
        void deletesRecord_whenNoOvertime() {
            Long userId = 2L;
            LocalDate date = LocalDate.of(2026, 5, 11);
            Instant actualEnd = DateTimeUtil.toInstant(date, LocalTime.of(18, 0));

            service.syncFromAttendance(userId, date, LocalTime.of(9, 0), LocalTime.of(18, 0), actualEnd);

            verify(overtimeRecordMapper).deleteByUserAndDate(userId, date);
        }

        @Test
        @DisplayName("日跨ぎ勤務では基準終了日時を翌日として残業算出する")
        void lateNightWork_identifiesNextDayCorrectly() {
            Long userId = 3L;
            LocalDate date = LocalDate.of(2026, 5, 12);
            Instant actualEnd = DateTimeUtil.toInstant(date.plusDays(1), LocalTime.of(7, 0));

            when(overtimeRecordMapper.selectByUserAndDateForUpdate(userId, date)).thenReturn(Optional.empty());

            service.syncFromAttendance(userId, date, LocalTime.of(22, 0), LocalTime.of(6, 0), actualEnd);

            ArgumentCaptor<OvertimeRecord> captor = ArgumentCaptor.forClass(OvertimeRecord.class);
            verify(overtimeRecordMapper).insert(captor.capture());

            OvertimeRecord inserted = captor.getValue();
            assertThat(inserted.getOvertimeStart()).isEqualTo(DateTimeUtil.toInstant(date.plusDays(1), LocalTime.of(6, 0)));
            assertThat(inserted.getOvertimeHours()).isEqualTo(1.0);
        }
    }

    @Test
    @DisplayName("開始時刻が終了時刻より後の場合の作成は残業時間0.0")
    void createRecord_withStartAfterEnd_returnsZeroHours() {
        LocalDate date = LocalDate.of(2026, 5, 20);
        Instant start = DateTimeUtil.toInstant(date, LocalTime.of(20, 0));
        Instant end = DateTimeUtil.toInstant(date, LocalTime.of(18, 0));

        OvertimeRecord result = service.createRecord(9L, date, start, end, "manual", "test");

        assertThat(result.getOvertimeHours()).isEqualTo(0.0);
        verify(overtimeRecordMapper).insert(result);
    }
}
