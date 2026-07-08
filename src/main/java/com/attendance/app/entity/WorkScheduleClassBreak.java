package com.attendance.app.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

/**
2.  * WorkScheduleClassBreak Entity - 勤務クラス休憩時間情報（縦持ち）
3.  */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkScheduleClassBreak {
    /** 休憩時間ID (PK) */
    private Long breakId;
    /** 勤務クラスID */
    private Long classId;
    /** 休憩開始時刻 */
    private LocalTime breakStartTime;
    /** 休憩終了時刻 */
    private LocalTime breakEndTime;
}
