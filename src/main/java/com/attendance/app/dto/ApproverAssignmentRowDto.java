package com.attendance.app.dto;

import lombok.Data;

@Data
public class ApproverAssignmentRowDto {
    private Long applicantUserId;
    private String applicantName;
    private String applicantDepartment;
    private String departmentName;
    private Long approverUserId;
    private String approverName;
}
