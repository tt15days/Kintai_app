package com.attendance.app.mapper;

import com.attendance.app.entity.Department;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * Department Mapper - 部署マスタに関するDB操作
 */
@Mapper
public interface DepartmentMapper {

    /**
     * すべての部署を取得
     *
     * @return 部署のリスト
     */
    List<Department> selectAll();

    /**
     * 有効な部署のみ取得
     *
     * @return 有効な部署のリスト
     */
    List<Department> selectAllActive();

    /**
     * IDで部署を取得
     *
     * @param departmentId 部署ID
     * @return 部署。存在しない場合は Optional.empty()
     */
    Optional<Department> selectById(@Param("departmentId") Long departmentId);

    /**
     * 名称で部署を取得
     *
     * @param name 部署名
     * @return 部署。存在しない場合は Optional.empty()
     */
    Optional<Department> selectByName(@Param("name") String name);

    /**
     * 名称の存在チェック
     *
     * @param name 部署名
     * @return 存在する場合は true
     */
    boolean existsByName(@Param("name") String name);

    /**
     * 名称の重複チェック（同一IDを除く）
     *
     * @param name 部署名
     * @param departmentId 除外する部署ID
     * @return 重複する場合は true
     */
    boolean existsByNameAndNotId(@Param("name") String name, @Param("departmentId") Long departmentId);

    /**
     * 部署を新規作成
     *
     * @param department 登録する部署情報
     * @return 登録された件数
     */
    int insert(Department department);

    /**
     * 部署を更新
     *
     * @param department 更新する部署情報
     * @return 更新された件数
     */
    int update(Department department);

    /**
     * 指定部署に所属する有効ユーザー数を取得（廃止可否の判定用）
     *
     * @param name 部署名
     * @return 有効ユーザー数
     */
    int countActiveUsersByDepartment(@Param("name") String name);

    /**
     * 部署改名に伴い users.department を一括更新
     *
     * @param oldName 旧部署名
     * @param newName 新部署名
     * @return 更新された件数
     */
    int renameUsersDepartment(@Param("oldName") String oldName, @Param("newName") String newName);

    /**
     * 部署改名に伴い attendance_department_approvers.department_name を一括更新
     *
     * @param oldName 旧部署名
     * @param newName 新部署名
     * @return 更新された件数
     */
    int renameDepartmentApprovers(@Param("oldName") String oldName, @Param("newName") String newName);

    /**
     * 部署改名に伴い work_schedule_classes.section_name を一括更新
     *
     * @param oldName 旧部署名
     * @param newName 新部署名
     * @return 更新された件数
     */
    int renameWorkScheduleClassSection(@Param("oldName") String oldName, @Param("newName") String newName);
}
