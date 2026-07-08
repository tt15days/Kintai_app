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
    @org.springframework.cache.annotation.Cacheable(value = "workScheduleClasses", key = "'all'")
    public List<WorkScheduleClass> getAllClasses() {
        return workScheduleClassMapper.selectAll();
    }

    /**
     * 有効な勤務クラスのみ取得します。
     *
     * @return 有効な勤務クラスのリスト
     */
    @org.springframework.cache.annotation.Cacheable(value = "workScheduleClasses", key = "'active'")
    public List<WorkScheduleClass> getAllActiveClasses() {
        return workScheduleClassMapper.selectAllActive();
    }

    /**
     * 指定されたIDで勤務クラスを取得します。
     *
     * @param classId 勤務クラスID
     * @return 勤務クラスのOptional
     */
    @org.springframework.cache.annotation.Cacheable(value = "workScheduleClasses", key = "'id_' + #classId")
    public Optional<WorkScheduleClass> getClassById(Long classId) {
        return workScheduleClassMapper.selectById(classId);
    }

    /**
     * 指定された名称で勤務クラスを取得します。
     *
     * @param name 勤務クラス名
     * @return 勤務クラスのOptional
     */
    @org.springframework.cache.annotation.Cacheable(value = "workScheduleClasses", key = "'name_' + #name", condition = "#name != null")
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
     * @param breaks 休憩時間リスト
     * @return 作成された勤務クラス
     */
    @org.springframework.cache.annotation.CacheEvict(value = "workScheduleClasses", allEntries = true)
    public WorkScheduleClass createClass(String name, String workLocation,
                                         String address, String station, String telephone,
                                         String sectionName, String folderName, String tags,
                                         Boolean isActive,
                                         Short maxHours, Short minHours,
                                         LocalTime startTime, LocalTime endTime,
                                         List<com.attendance.app.entity.WorkScheduleClassBreak> breaks) {
        String normalizedName = normalizeRequiredName(name);
        if (workScheduleClassMapper.existsByName(normalizedName)) {
            throw new IllegalArgumentException("このクラス名は既に登録されています: " + normalizedName);
        }

        validateBreaks(breaks);

        String classCode = generateClassCode();

        WorkScheduleClass wsc = WorkScheduleClass.builder()
                .classCode(classCode)
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
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        workScheduleClassMapper.insert(wsc);

        if (breaks != null && !breaks.isEmpty()) {
            for (com.attendance.app.entity.WorkScheduleClassBreak b : breaks) {
                b.setClassId(wsc.getClassId());
            }
            workScheduleClassMapper.insertBreaks(breaks);
        }

        wsc.setBreaks(breaks);
        log.info("勤務クラスを作成しました: name={}, classCode={}", normalizedName, classCode);
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
     * @param breaks 休憩時間リスト
     * @return 更新された勤務クラス
     */
    @org.springframework.cache.annotation.CacheEvict(value = "workScheduleClasses", allEntries = true)
    public WorkScheduleClass updateClass(Long classId, String name, String workLocation,
                                         String address, String station, String telephone,
                                         String sectionName, String folderName, String tags,
                                         Boolean isActive,
                                         Short maxHours, Short minHours,
                                         LocalTime startTime, LocalTime endTime,
                                         List<com.attendance.app.entity.WorkScheduleClassBreak> breaks) {
        WorkScheduleClass wsc = workScheduleClassMapper.selectById(classId)
                .orElseThrow(() -> new IllegalArgumentException(CLASS_NOT_FOUND_PREFIX + classId));
        String normalizedName = normalizeRequiredName(name);
        String oldName = wsc.getName();

        if (workScheduleClassMapper.existsByNameAndNotId(normalizedName, classId)) {
            throw new IllegalArgumentException("このクラス名は既に登録されています: " + normalizedName);
        }

        validateBreaks(breaks);

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
        wsc.setUpdatedAt(Instant.now());

        workScheduleClassMapper.update(wsc);

        // 休憩時間の更新（一度削除してから全挿入）
        workScheduleClassMapper.deleteBreaksByClassId(classId);
        if (breaks != null && !breaks.isEmpty()) {
            for (com.attendance.app.entity.WorkScheduleClassBreak b : breaks) {
                b.setClassId(classId);
            }
            workScheduleClassMapper.insertBreaks(breaks);
        }
        wsc.setBreaks(breaks);

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
    @org.springframework.cache.annotation.CacheEvict(value = "workScheduleClasses", allEntries = true)
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

    private String generateClassCode() {
        int index = 1;
        while (true) {
            String code = "W" + String.format("%03d", index);
            if (!workScheduleClassMapper.existsByCode(code)) {
                return code;
            }
            index++;
        }
    }

    private void validateBreaks(List<com.attendance.app.entity.WorkScheduleClassBreak> breaks) {
        if (breaks == null || breaks.isEmpty()) {
            throw new IllegalArgumentException("休憩時間は少なくとも1つ設定してください");
        }
        for (int i = 0; i < breaks.size(); i++) {
            com.attendance.app.entity.WorkScheduleClassBreak b = breaks.get(i);
            validateBreakWindow(b.getBreakStartTime(), b.getBreakEndTime(), "休憩" + (i + 1));
        }
    }

    private void validateBreakWindow(LocalTime startTime, LocalTime endTime, String label) {
        if (startTime == null || endTime == null) {
            throw new IllegalArgumentException(label + "の開始時刻と終了時刻は両方入力してください");
        }
        if (startTime.equals(endTime)) {
            throw new IllegalArgumentException(label + "の開始時刻と終了時刻は同じにできません");
        }
    }
}
