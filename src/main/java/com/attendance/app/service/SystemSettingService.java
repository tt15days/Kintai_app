package com.attendance.app.service;

import com.attendance.app.mapper.SystemSettingMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * システム設定に関する業務ロジックを提供するサービスです。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class SystemSettingService {

    public static final String PAID_LEAVE_GRANT_DATE_KEY = "PAID_LEAVE_GRANT_DATE";
    public static final String PAID_LEAVE_GRANT_DAYS_KEY = "PAID_LEAVE_GRANT_DAYS";
    public static final String DEFAULT_PAID_LEAVE_GRANT_DATE = "04-01";
    public static final String DEFAULT_PAID_LEAVE_GRANT_DAYS = "10";

    private final SystemSettingMapper systemSettingMapper;

    /**
     * 設定キーに対応する値を取得します。
     *
     * @param settingKey 設定キー
     * @return 設定値。存在しない場合は null
     */
    @Transactional(readOnly = true)
    public String getSettingValue(String settingKey) {
        return systemSettingMapper.selectValueByKey(settingKey);
    }

    /**
     * 設定キーが存在すれば更新し、存在しなければ追加します。
     *
     * @param settingKey   設定キー
     * @param settingValue 設定値
     * @return 更新または追加された件数
     */
    public int updateSettingValue(String settingKey, String settingValue) {
        log.info("システム設定を更新します: key={}, value={}", settingKey, settingValue);
        return systemSettingMapper.upsertValue(settingKey, settingValue);
    }

    public void updatePaidLeaveGrantSettings(String grantDate, int grantDays) {
        systemSettingMapper.upsertValue(PAID_LEAVE_GRANT_DATE_KEY, grantDate);
        systemSettingMapper.upsertValue(PAID_LEAVE_GRANT_DAYS_KEY, String.valueOf(grantDays));
    }
}
