package com.attendance.app.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.Instant;

/**
 * Holiday Entity - 祝日マスタ
 *
 * 祝日マスタの1件分を表します。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Holiday {
    /** 祝日日付 */
    private LocalDate holidayDate;
    /** 祝日名 */
    private String name;
    /** 作成日時（UTC） */
    private Instant createdAt;
}
