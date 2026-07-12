package com.attendance.app.service;

import com.attendance.app.entity.Holiday;
import com.attendance.app.mapper.HolidayMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("HolidayService")
class HolidayServiceTest {

    @Mock
    private HolidayMapper holidayMapper;

    @InjectMocks
    private HolidayService service;

    @Test
    @DisplayName("有効なCSVを正しくパースする")
    void parseFromCsv_parsesValidRows() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "holidays.csv",
                "text/csv",
                "2026-01-01,元日\n2026-02-11,建国記念の日\n".getBytes(StandardCharsets.UTF_8));

        List<Holiday> parsed = service.parseFromCsv(file);

        assertThat(parsed).hasSize(2);
        assertThat(parsed.get(0).getHolidayDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(parsed.get(0).getName()).isEqualTo("元日");
        assertThat(parsed.get(1).getHolidayDate()).isEqualTo(LocalDate.of(2026, 2, 11));
    }

    @Test
    @DisplayName("不正な日付形式を含むCSVは例外")
    void parseFromCsv_rejectsInvalidFormat() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "holidays.csv",
                "text/csv",
                "2026/01/01,元日\n".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.parseFromCsv(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1行目の日付形式が不正です");
    }

    @Test
    @DisplayName("CSV保存時は既存データを削除後に全件挿入する")
    void saveFromCsv_replacesAllAndInsertsParsedRows() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "holidays.csv",
                "text/csv",
                "2026-03-20,春分の日\n2026-11-03,文化の日\n".getBytes(StandardCharsets.UTF_8));

        service.saveFromCsv(file);

        InOrder inOrder = inOrder(holidayMapper);
        inOrder.verify(holidayMapper).deleteAll();
        ArgumentCaptor<Holiday> captor = ArgumentCaptor.forClass(Holiday.class);
        verify(holidayMapper, times(2)).insert(captor.capture());

        List<Holiday> inserted = captor.getAllValues();
        assertThat(inserted.get(0).getHolidayDate()).isEqualTo(LocalDate.of(2026, 3, 20));
        assertThat(inserted.get(1).getHolidayDate()).isEqualTo(LocalDate.of(2026, 11, 3));
    }

    @Test
    @DisplayName("うるう年（2月29日）を含むCSVを正しくパースして登録できること")
    void parseFromCsv_leapYear_parsesValidLeapDay() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "holidays.csv",
                "text/csv",
                "2024-02-29,うるう日祝日\n".getBytes(StandardCharsets.UTF_8));

        List<Holiday> parsed = service.parseFromCsv(file);

        assertThat(parsed).hasSize(1);
        assertThat(parsed.get(0).getHolidayDate()).isEqualTo(LocalDate.of(2024, 2, 29));
        assertThat(parsed.get(0).getName()).isEqualTo("うるう日祝日");
    }
}
