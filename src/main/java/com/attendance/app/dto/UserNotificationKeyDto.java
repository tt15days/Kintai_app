package com.attendance.app.dto;

import lombok.Data;

@Data
public class UserNotificationKeyDto {
    private Long userId;
    private String notificationType;
}
