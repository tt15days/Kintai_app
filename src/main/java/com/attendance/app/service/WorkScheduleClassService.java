package com.attendance.app.service;

import com.attendance.app.entity.WorkScheduleClass;
import com.attendance.app.mapper.AttendanceApproverAssignmentMapper;
import com.attendance.app.mapper.UserMapper;
import com.attendance.app.mapper.WorkScheduleClassMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/**
 * 勤務クラス（所定時間マスタ）の業務ロジックを提供するサービスです。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class WorkScheduleClassService {

    private static final String CLASS_NOT_FOUND_PREFIX = "勤務クラスが見つかりません: classId=";
    private static final LocalTime DEFAULT_WORK_START_TIME = LocalTime.of(9, 0);
    private static final LocalTime DEFAULT_WORK_END_TIME = LocalTime.of(18, 0);

    private final WorkScheduleClassMapper workScheduleClassMapper;
    private final UserMapper userMapper;
    private final AttendanceApproverAssignmentMapper attendanceApproverAssignmentMapper;

    /**
     * すべての勤務クラスを取得します。
     *
     * @return 勤務クラスのリスト
     */
    public List<WorkScheduleClass> getAllClasses() {
        return workScheduleClassMapper.selectAll();
    }

    /**
     * 有効な勤務クラスのみ取得します。
     *
     * @return 有効な勤務クラスのリスト
     */
    public List<WorkScheduleClass> getAllActiveClasses() {
        return workScheduleClassMapper.selectAllActive();
    }

    /**
     * 指定されたIDで勤務クラスを取得します。
     *
     * @param classId 勤務クラスID
     * @return 勤務クラスのOptional
     */
    public Optional<WorkScheduleClass> getClassById(Long classId) {
        return workScheduleClassMapper.selectById(classId);
    }

    /**
     * 指定された名称で勤務クラスを取得します。
     *
     * @param name 勤務クラス名
     * @return 勤務クラスのOptional
     */
    public Optional<WorkScheduleClass> getClassByName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return Optional.empty();
        }
        return workScheduleClassMapper.selectByName(name.trim());
    }

    /**
     * 新しい勤務クラスを作成します。
     *
     * @param name クラス名
     * @param workLocation 勤務地
     * @param startTime 始業時刻
     * @param endTime 終業時刻
     * @param breakStartTime 休憩開始時刻
     * @param breakEndTime 休憩終了時刻
     * @param breakStartTime2 休憩開始時刻2
     * @param breakEndTime2 休憩終了時刻2
     * @param breakStartTime3 休憩開始時刻3
     * @param breakEndTime3 休憩終了時刻3
     * @param breakStartTime4 休憩開始時刻4
     * @param breakEndTime4 休憩終了時刻4
     * @return 作成された勤務クラス
     */
    public WorkScheduleClass createClass(String name, String workLocation,
                                         String address, String station, String telephone,
                                         String sectionName, String folderName, String tags,
                                         Boolean isActive,
                                         Short maxHours, Short minHours,
                                         LocalTime startTime, LocalTime endTime,
                                         LocalTime breakStartTime, LocalTime breakEndTime,
                                         LocalTime breakStartTime2, LocalTime breakEndTime2,
                                         LocalTime breakStartTime3, LocalTime breakEndTime3,
                                         LocalTime breakStartTime4, LocalTime breakEndTime4) {
        String normalizedName = normalizeRequiredName(name);
        if (workScheduleClassMapper.existsByName(normalizedName)) {
            throw new IllegalArgumentException("このクラス名は既に登録されています: " + normalizedName);
        }

        BreakWindow primaryBreak = normalizePrimaryBreakWindow(breakStartTime, breakEndTime);
        BreakWindow break2 = normalizeOptionalBreakWindow(breakStartTime2, breakEndTime2, "休憩2");
        BreakWindow break3 = normalizeOptionalBreakWindow(breakStartTime3, breakEndTime3, "休憩3");
        BreakWindow break4 = normalizeOptionalBreakWindow(breakStartTime4, breakEndTime4, "休憩4");

        WorkScheduleClass wsc = WorkScheduleClass.builder()
                .name(normalizedName)
                .workLocation(normalizeOptionalText(workLocation))
                .address(normalizeOptionalText(address))
                .station(normalizeOptionalText(station))
                .telephone(normalizeOptionalText(telephone))
                .sectionName(normalizeOptionalText(sectionName))
                .folderName(normalizeOptionalText(folderName))
                .tags(normalizeOptionalText(tags))
                .isActive(isActive != null ? isActive : Boolean.TRUE)
                .maxHours(maxHours)
                .minHours(minHours)
                .startTime(startTime != null ? startTime : DEFAULT_WORK_START_TIME)
                .endTime(endTime != null ? endTime : DEFAULT_WORK_END_TIME)
                .breakStartTime(primaryBreak.startTime())
                .breakEndTime(primaryBreak.endTime())
                .breakStartTime2(break2.startTime())
                .breakEndTime2(break2.endTime())
                .breakStartTime3(break3.startTime())
                .breakEndTime3(break3.endTime())
                .breakStartTime4(break4.startTime())
                .breakEndTime4(break4.endTime())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        workScheduleClassMapper.insert(wsc);
        log.info("勤務クラスを作成しました: name={}", normalizedName);
        return wsc;
    }

    /**
     * 既存の勤務クラスを更新します。
     *
     * @param classId 勤務クラスID
     * @param name クラス名
     * @param workLocation 勤務地
     * @param startTime 始業時刻
     * @param endTime 終業時刻
     * @param breakStartTime 休憩開始時刻
     * @param breakEndTime 休憩終了時刻
     * @param breakStartTime2 休憩開始時刻2
     * @param breakEndTime2 休憩終了時刻2
     * @param breakStartTime3 休憩開始時刻3
     * @param breakEndTime3 休憩終了時刻3
     * @param breakStartTime4 休憩開始時刻4
     * @param breakEndTime4 休憩終了時刻4
     * @return 更新された勤務クラス
     */
    public WorkScheduleClass updateClass(Long classId, String name, String workLocation,
                                         String address, String station, String telephone,
                                         String sectionName, String folderName, String tags,
                                         Boolean isActive,
                                         Short maxHours, Short minHours,
                                         LocalTime startTime, LocalTime endTime,
                                         LocalTime breakStartTime, LocalTime breakEndTime,
                                         LocalTime breakStartTime2, LocalTime breakEndTime2,
                                         LocalTime breakStartTime3, LocalTime breakEndTime3,
                                         LocalTime breakStartTime4, LocalTime breakEndTime4) {
        WorkScheduleClass wsc = workScheduleClassMapper.selectById(classId)
                .orElseThrow(() -> new IllegalArgumentException(CLASS_NOT_FOUND_PREFIX + classId));
        String normalizedName = normalizeRequiredName(name);
        String oldName = wsc.getName();

        if (workScheduleClassMapper.existsByNameAndNotId(normalizedName, classId)) {
            throw new IllegalArgumentException("このクラス名は既に登録されています: " + normalizedName);
        }

        wsc.setName(normalizedName);
        wsc.setWorkLocation(normalizeOptionalText(workLocation));
        wsc.setAddress(normalizeOptionalText(address));
        wsc.setStation(normalizeOptionalText(station));
        wsc.setTelephone(normalizeOptionalText(telephone));
        wsc.setSectionName(normalizeOptionalText(sectionName));
        wsc.setFolderName(normalizeOptionalText(folderName));
        wsc.setTags(normalizeOptionalText(tags));
        wsc.setIsActive(isActive != null ? isActive : Boolean.TRUE);
        wsc.setMaxHours(maxHours);
        wsc.setMinHours(minHours);
        wsc.setStartTime(startTime != null ? startTime : DEFAULT_WORK_START_TIME);
        wsc.setEndTime(endTime != null ? endTime : DEFAULT_WORK_END_TIME);

        BreakWindow primaryBreak = normalizePrimaryBreakWindow(breakStartTime, breakEndTime);
        BreakWindow break2 = normalizeOptionalBreakWindow(breakStartTime2, breakEndTime2, "休憩2");
        BreakWindow break3 = normalizeOptionalBreakWindow(breakStartTime3, breakEndTime3, "休憩3");
        BreakWindow break4 = normalizeOptionalBreakWindow(breakStartTime4, breakEndTime4, "休憩4");

        wsc.setBreakStartTime(primaryBreak.startTime());
        wsc.setBreakEndTime(primaryBreak.endTime());
        wsc.setBreakStartTime2(break2.startTime());
        wsc.setBreakEndTime2(break2.endTime());
        wsc.setBreakStartTime3(break3.startTime());
        wsc.setBreakEndTime3(break3.endTime());
        wsc.setBreakStartTime4(break4.startTime());
        wsc.setBreakEndTime4(break4.endTime());
        wsc.setUpdatedAt(Instant.now());

        workScheduleClassMapper.update(wsc);
        if (!oldName.equals(normalizedName)) {
            userMapper.replaceClassName(oldName, normalizedName);
            attendanceApproverAssignmentMapper.replaceDepartmentName(oldName, normalizedName);
        }
        log.info("勤務クラスを更新しました: classId={}, name={}", classId, normalizedName);
        return wsc;
    }

    /**
     * 指定されたIDの勤務クラスを削除します（論理削除）。
     *
     * @param classId 削除対象의勤務クラスID
     */
    public void deleteClass(Long classId) {
        WorkScheduleClass workScheduleClass = workScheduleClassMapper.selectById(classId)
                .orElseThrow(() -> new IllegalArgumentException(CLASS_NOT_FOUND_PREFIX + classId));
        workScheduleClass.setIsActive(false);
        workScheduleClass.setUpdatedAt(Instant.now());
        workScheduleClassMapper.update(workScheduleClass);
        log.info("勤務クラスを論理削除しました: classId={}", classId);
    }

    private String normalizeRequiredName(String value) {
        String normalized = normalizeOptionalText(value);
        if (normalized == null) {
            throw new IllegalArgumentException("クラス名称は必須です");
        }
        return normalized;
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 休憩時間帯を正規化します。
     */
    private BreakWindow normalizePrimaryBreakWindow(LocalTime startTime, LocalTime endTime) {
        if (startTime == null || endTime == null) {
            throw new IllegalArgumentException("休憩1の開始時刻と終了時刻は両方入力してください");
        }
        validateBreakWindow(startTime, endTime, "休憩1");
        return new BreakWindow(startTime, endTime);
    }

    private BreakWindow normalizeOptionalBreakWindow(LocalTime startTime, LocalTime endTime, String label) {
        if (startTime == null && endTime == null) {
            return new BreakWindow(null, null);
        }
        validateBreakWindow(startTime, endTime, label);
        return new BreakWindow(startTime, endTime);
    }

    private void validateBreakWindow(LocalTime startTime, LocalTime endTime, String label) {
        if (startTime == null || endTime == null) {
            throw new IllegalArgumentException(label + "の開始時刻と終了時刻は両方入力してください");
        }
        if (startTime.equals(endTime)) {
            throw new IllegalArgumentException(label + "の開始時刻と終了時刻は同じにできません");
        }
    }

    private record BreakWindow(LocalTime startTime, LocalTime endTime) {
    }
}
