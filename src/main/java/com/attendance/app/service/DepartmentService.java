package com.attendance.app.service;

import com.attendance.app.entity.Department;
import com.attendance.app.mapper.DepartmentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 部署マスタの業務ロジックを提供するサービスです。
 *
 * users.department / attendance_department_approvers.department_name /
 * work_schedule_classes.section_name は部署名の名前参照のため、
 * 改名時は同一トランザクションで参照箇所を一括更新します。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class DepartmentService {

    private static final String DEPARTMENT_NOT_FOUND_PREFIX = "部署が見つかりません: departmentId=";
    private static final int MAX_NAME_LENGTH = 100;

    private final DepartmentMapper departmentMapper;

    /**
     * すべての部署を取得します。
     *
     * @return 部署のリスト
     */
    @Transactional(readOnly = true)
    public List<Department> getAllDepartments() {
        return departmentMapper.selectAll();
    }

    /**
     * 有効な部署のみ取得します。
     *
     * @return 有効な部署のリスト
     */
    @Transactional(readOnly = true)
    public List<Department> getActiveDepartments() {
        return departmentMapper.selectAllActive();
    }

    /**
     * 部署を新規作成します。
     *
     * @param name 部署名
     * @return 作成された部署
     */
    public Department createDepartment(String name) {
        String normalizedName = normalizeRequiredName(name);
        if (departmentMapper.existsByName(normalizedName)) {
            throw new IllegalArgumentException("この部署名は既に登録されています: " + normalizedName);
        }
        Department department = Department.builder()
                .name(normalizedName)
                .isActive(true)
                .build();
        departmentMapper.insert(department);
        log.info("部署を作成しました: departmentId={}, name={}", department.getDepartmentId(), normalizedName);
        return department;
    }

    /**
     * 部署を更新します。改名時は参照している
     * users / attendance_department_approvers / work_schedule_classes も一括更新します。
     * 廃止（無効化）は所属する有効ユーザーがいない場合のみ可能です。
     *
     * @param departmentId 部署ID
     * @param name 部署名
     * @param isActive 有効フラグ
     * @return 更新された部署
     */
    @CacheEvict(value = "workScheduleClasses", allEntries = true)
    public Department updateDepartment(Long departmentId, String name, Boolean isActive) {
        Department department = departmentMapper.selectById(departmentId)
                .orElseThrow(() -> new IllegalArgumentException(DEPARTMENT_NOT_FOUND_PREFIX + departmentId));

        String normalizedName = normalizeRequiredName(name);
        if (departmentMapper.existsByNameAndNotId(normalizedName, departmentId)) {
            throw new IllegalArgumentException("この部署名は既に登録されています: " + normalizedName);
        }

        boolean deactivating = Boolean.TRUE.equals(department.getIsActive()) && !Boolean.TRUE.equals(isActive);
        if (deactivating) {
            int activeUsers = departmentMapper.countActiveUsersByDepartment(department.getName());
            if (activeUsers > 0) {
                throw new IllegalArgumentException(
                        "所属中の有効なユーザーがいるため廃止できません（" + activeUsers + "名）。先にユーザーの部署を変更してください");
            }
        }

        String oldName = department.getName();
        department.setName(normalizedName);
        department.setIsActive(Boolean.TRUE.equals(isActive));
        departmentMapper.update(department);

        if (!oldName.equals(normalizedName)) {
            int users = departmentMapper.renameUsersDepartment(oldName, normalizedName);
            int approvers = departmentMapper.renameDepartmentApprovers(oldName, normalizedName);
            int classes = departmentMapper.renameWorkScheduleClassSection(oldName, normalizedName);
            log.info("部署名変更を反映しました: {} -> {} (users={}, approvers={}, classes={})",
                    oldName, normalizedName, users, approvers, classes);
        }

        log.info("部署を更新しました: departmentId={}, name={}, isActive={}",
                departmentId, normalizedName, department.getIsActive());
        return department;
    }

    private String normalizeRequiredName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("部署名を入力してください");
        }
        String normalized = name.trim();
        if (normalized.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("部署名は" + MAX_NAME_LENGTH + "文字以内で入力してください");
        }
        return normalized;
    }
}
