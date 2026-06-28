package com.attendance.app.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class PaidLeaveAlertDto {
    private Long userId;
    private LocalDate grantDate;
    private BigDecimal usedDays;
}
