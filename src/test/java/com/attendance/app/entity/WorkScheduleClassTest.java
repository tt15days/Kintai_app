package com.attendance.app.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("WorkScheduleClass")
class WorkScheduleClassTest {

    @Test
    @DisplayName("getTotalBreakMinutes: 4スロットの時間帯を合計する")
    void getTotalBreakMinutes_sumsAllSlots() {
        WorkScheduleClass workScheduleClass = WorkScheduleClass.builder()
                .breakStartTime(LocalTime.of(12, 0))
                .breakEndTime(LocalTime.of(13, 0))
                .breakStartTime2(LocalTime.of(15, 0))
                .breakEndTime2(LocalTime.of(15, 15))
                .breakStartTime3(LocalTime.of(18, 30))
                .breakEndTime3(LocalTime.of(18, 40))
                .breakStartTime4(LocalTime.of(20, 0))
                .breakEndTime4(LocalTime.of(20, 5))
                .build();

        assertThat(workScheduleClass.getTotalBreakMinutes()).isEqualTo(90);
    }

    @Test
    @DisplayName("getTotalBreakMinutes: 日またぎの休憩時間帯を計算する")
    void getTotalBreakMinutes_handlesOvernightBreakWindow() {
        WorkScheduleClass workScheduleClass = WorkScheduleClass.builder()
                .breakStartTime(LocalTime.of(23, 30))
                .breakEndTime(LocalTime.of(1, 30))
                .breakStartTime2(null)
                .breakEndTime2(null)
                .breakStartTime3(null)
                .breakEndTime3(null)
                .breakStartTime4(null)
                .breakEndTime4(null)
                .build();

        assertThat(workScheduleClass.getTotalBreakMinutes()).isEqualTo(120);
    }
}