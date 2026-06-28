package com.attendance.app.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class Article36AlertDto {
    private Long userId;
    private BigDecimal totalOvertimeHours;
}
