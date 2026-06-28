package com.attendance.app.controller;

import com.attendance.app.security.SecurityUtil;
import com.attendance.app.service.OvertimeRecordService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ExtendedModelMap;

import java.time.YearMonth;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class OvertimeRecordControllerTest {

    @Mock
    private OvertimeRecordService overtimeRecordService;

    @Mock
    private SecurityUtil securityUtil;

    @InjectMocks
    private OvertimeRecordController controller;

    @BeforeEach
    void setUp() {
        when(securityUtil.getCurrentUserId()).thenReturn(1L);
    }

    @Test
    void testShowOvertimeForm_Success() {
        when(overtimeRecordService.getRecordsByUserAndMonth(anyLong(), anyInt(), anyInt()))
                .thenReturn(Collections.emptyList());
        when(overtimeRecordService.getOvertimeHoursSumByUserAndMonth(anyLong(), anyInt(), anyInt()))
                .thenReturn(10.5);

        ExtendedModelMap model = new ExtendedModelMap();
        String view = controller.showOvertimeForm(null, model);

        assertEquals("user/overtime", view);
        assertEquals(10.5, model.getAttribute("monthlyOvertime"));
        assertEquals(1L, model.getAttribute("userId"));
    }

    @Test
    void testShowOvertimeForm_WithYearMonth() {
        when(overtimeRecordService.getRecordsByUserAndMonth(anyLong(), anyInt(), anyInt()))
                .thenReturn(Collections.emptyList());
        when(overtimeRecordService.getOvertimeHoursSumByUserAndMonth(anyLong(), anyInt(), anyInt()))
                .thenReturn(0.0);

        ExtendedModelMap model = new ExtendedModelMap();
        String view = controller.showOvertimeForm("2026-05", model);

        assertEquals("user/overtime", view);
        assertEquals(YearMonth.of(2026, 5), model.getAttribute("yearMonth"));
    }

    @Test
    void testShowOvertimeForm_ExceptionHandling() {
        when(overtimeRecordService.getRecordsByUserAndMonth(anyLong(), anyInt(), anyInt()))
                .thenThrow(new RuntimeException("DB Connection Error"));

        ExtendedModelMap model = new ExtendedModelMap();
        String view = controller.showOvertimeForm(null, model);

        assertEquals("user/overtime", view);
        assertEquals("残業管理画面の表示に失敗しました", model.getAttribute("error"));
    }
}
