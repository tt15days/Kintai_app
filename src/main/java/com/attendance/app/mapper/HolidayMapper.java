package com.attendance.app.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.attendance.app.entity.Holiday;

/**
 * 祝日マスタを操作する Mapper です。
 */
@Mapper
public interface HolidayMapper {

    /**
     * 祝日を1件登録します。
     *
     * @param holiday 登録する祝日情報
     * @return 登録された件数
     */
    int insert(@Param("holiday") Holiday holiday);

    /**
     * 登録済み祝日を全件取得します。
     *
     * @return 祝日情報のリスト
     */
    List<Holiday> selectAll();

    /**
     * 指定された年の祝日を取得します。
     *
     * @param year 対象年
     * @return 祝日情報のリスト
     */
    List<Holiday> selectByYear(@Param("year") int year);

    /**
     * 登録済み祝日を全件削除します。
     */
    void deleteAll();
}
