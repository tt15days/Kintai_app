package com.attendance.app.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Department Entity - 部署マスタ
 *
 * users.department / attendance_department_approvers.department_name /
 * work_schedule_classes.section_name から名前参照される部署のマスタです。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Department {
    /** 部署ID（PK） */
    private Long departmentId;
    /** 部署名（一意） */
    private String name;
    /** 有効フラグ（false=廃止） */
    private Boolean isActive;
    /** 作成日時（UTC） */
    private Instant createdAt;
    /** 更新日時（UTC） */
    private Instant updatedAt;
}
