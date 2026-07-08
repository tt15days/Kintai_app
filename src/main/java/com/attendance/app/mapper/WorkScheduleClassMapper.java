package com.attendance.app.mapper;

import com.attendance.app.entity.WorkScheduleClass;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * WorkScheduleClass Mapper - 勤務クラス（所定時間マスタ）に関するDB操作
 */
@Mapper
public interface WorkScheduleClassMapper {

    /**
     * すべての勤務クラスを取得
     *
     * @return 勤務クラスのリスト
     */
    List<WorkScheduleClass> selectAll();

    /**
     * 有効な勤務クラスのみ取得
     *
     * @return 有効な勤務クラスのリスト
     */
    List<WorkScheduleClass> selectAllActive();

    /**
     * IDで勤務クラスを取得
     *
     * @param classId 勤務クラスID
     * @return 勤務クラス。存在しない場合は Optional.empty()
     */
    Optional<WorkScheduleClass> selectById(@Param("classId") Long classId);

    /**
     * 名称で勤務クラスを取得
     *
     * @param name クラス名
     * @return 勤務クラス。存在しない場合は Optional.empty()
     */
    Optional<WorkScheduleClass> selectByName(@Param("name") String name);

    /**
     * 勤務クラスを新規作成
     *
     * @param workScheduleClass 登録する勤務クラス情報
     * @return 登録された件数
     */
    int insert(WorkScheduleClass workScheduleClass);

    /**
     * 勤務クラスを更新
     *
     * @param workScheduleClass 更新する勤務クラス情報
     * @return 更新された件数
     */
    int update(WorkScheduleClass workScheduleClass);

    /**
     * 勤務クラスを削除
     *
     * @param classId 勤務クラスID
     * @return 削除された件数
     */
    int deleteById(@Param("classId") Long classId);

    /**
     * 名称の重複チェック（同一IDを除く）
     *
     * @param name クラス名
     * @param classId 除外する勤務クラスID
     * @return 重複する場合は true
     */
    boolean existsByNameAndNotId(@Param("name") String name, @Param("classId") Long classId);

    /**
     * 名称の存在チェック
     *
     * @param name クラス名
     * @return 存在する場合は true
     */
    /**
     * 名称の存在チェック
     *
     * @param name クラス名
     * @return 存在する場合は true
     */
    boolean existsByName(@Param("name") String name);

    /**
     * クラスコードの存在チェック
     *
     * @param classCode クラスコード
     * @return 存在する場合は true
     */
    boolean existsByCode(@Param("classCode") String classCode);

    /**
     * クラスコードの重複チェック（同一IDを除く）
     *
     * @param classCode クラスコード
     * @param classId 除外する勤務クラスID
     * @return 重複する場合は true
     */
    boolean existsByCodeAndNotId(@Param("classCode") String classCode, @Param("classId") Long classId);

    /**
     * クラスコードで勤務クラスを取得
     *
     * @param classCode クラスコード
     * @return 勤務クラス。存在しない場合は Optional.empty()
     */
    Optional<WorkScheduleClass> selectByCode(@Param("classCode") String classCode);

    /**
     * 休憩時間リストを一括登録
     *
     * @param breaks 休憩時間リスト
     */
    void insertBreaks(@Param("breaks") List<com.attendance.app.entity.WorkScheduleClassBreak> breaks);

    /**
     * 勤務クラスIDに紐付く休憩時間を削除
     *
     * @param classId 勤務クラスID
     */
    void deleteBreaksByClassId(@Param("classId") Long classId);
}
