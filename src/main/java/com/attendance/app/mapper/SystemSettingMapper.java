package com.attendance.app.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * system_settings テーブルを操作する Mapper です。
 */
@Mapper
public interface SystemSettingMapper {

    /**
     * 設定キーに対応する値を取得します。
     *
     * @param settingKey 設定キー
     * @return 設定値。存在しない場合は null
     */
    String selectValueByKey(@Param("settingKey") String settingKey);

    /**
     * 設定キーが存在すれば更新し、存在しなければ追加します。
     *
     * @param settingKey 設定キー
     * @param settingValue 設定値
     * @return 更新または追加された件数
     */
    int upsertValue(@Param("settingKey") String settingKey, @Param("settingValue") String settingValue);
}
