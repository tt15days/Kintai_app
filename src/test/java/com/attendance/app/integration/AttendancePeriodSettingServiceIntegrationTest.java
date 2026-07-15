package com.attendance.app.integration;

import com.attendance.app.mapper.SystemSettingMapper;
import com.attendance.app.service.AttendancePeriodSettingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = com.attendance.app.AttendanceApplication.class)
@ActiveProfiles("integration")
@DisplayName("AttendancePeriodSettingService Integration")
class AttendancePeriodSettingServiceIntegrationTest {

    private static final String TRIGGER_NAME = "trg_test_reject_period_end";
    private static final String FUNCTION_NAME = "test_reject_period_end";

    @Autowired
    private AttendancePeriodSettingService service;

    @Autowired
    private SystemSettingMapper systemSettingMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("終了日の保存失敗時は開始日の更新もロールバックする")
    void updatePeriod_secondUpsertFailure_rollsBackFirstUpsert() {
        String originalStart = systemSettingMapper.selectValueByKey(
                AttendancePeriodSettingService.SETTING_KEY_START_DAY);
        String originalEnd = systemSettingMapper.selectValueByKey(
                AttendancePeriodSettingService.SETTING_KEY_END_DAY);
        systemSettingMapper.upsertValue(AttendancePeriodSettingService.SETTING_KEY_START_DAY, "21");
        systemSettingMapper.upsertValue(AttendancePeriodSettingService.SETTING_KEY_END_DAY, "20");

        try {
            dropFailureTrigger();
            jdbcTemplate.execute("""
                    CREATE FUNCTION test_reject_period_end() RETURNS trigger AS $$
                    BEGIN
                        IF NEW.setting_key = 'attendance_period_end_day' THEN
                            RAISE EXCEPTION 'forced second upsert failure';
                        END IF;
                        RETURN NEW;
                    END;
                    $$ LANGUAGE plpgsql
                    """);
            jdbcTemplate.execute("""
                    CREATE TRIGGER trg_test_reject_period_end
                    BEFORE INSERT OR UPDATE ON system_settings
                    FOR EACH ROW EXECUTE FUNCTION test_reject_period_end()
                    """);
            assertThatThrownBy(() -> service.updatePeriod(15, 14))
                    .isInstanceOf(RuntimeException.class);
            assertThat(systemSettingMapper.selectValueByKey(
                    AttendancePeriodSettingService.SETTING_KEY_START_DAY)).isEqualTo("21");
            assertThat(systemSettingMapper.selectValueByKey(
                    AttendancePeriodSettingService.SETTING_KEY_END_DAY)).isEqualTo("20");
        } finally {
            dropFailureTrigger();
            restoreSetting(AttendancePeriodSettingService.SETTING_KEY_START_DAY, originalStart);
            restoreSetting(AttendancePeriodSettingService.SETTING_KEY_END_DAY, originalEnd);
        }
    }

    private void dropFailureTrigger() {
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS " + TRIGGER_NAME + " ON system_settings");
        jdbcTemplate.execute("DROP FUNCTION IF EXISTS " + FUNCTION_NAME + "()");
    }

    private void restoreSetting(String key, String value) {
        if (value == null) {
            jdbcTemplate.update("DELETE FROM system_settings WHERE setting_key = ?", key);
        } else {
            systemSettingMapper.upsertValue(key, value);
        }
    }
}
