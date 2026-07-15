package com.attendance.app.service;

import com.attendance.app.mapper.SystemSettingMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SystemSettingService")
class SystemSettingServiceTest {

    @Mock
    private SystemSettingMapper systemSettingMapper;

    @InjectMocks
    private SystemSettingService service;

    @Test
    @DisplayName("getSettingValue: 設定キーに対応する値をそのまま返す")
    void getSettingValue_returnsValueFromMapper() {
        when(systemSettingMapper.selectValueByKey("MAIL_HOST")).thenReturn("smtp.example.com");

        String value = service.getSettingValue("MAIL_HOST");

        assertThat(value).isEqualTo("smtp.example.com");
    }

    @Test
    @DisplayName("getSettingValue: 存在しないキーの場合はnullを返す")
    void getSettingValue_withUnknownKey_returnsNull() {
        when(systemSettingMapper.selectValueByKey("UNKNOWN")).thenReturn(null);

        String value = service.getSettingValue("UNKNOWN");

        assertThat(value).isNull();
    }

    @Test
    @DisplayName("updateSettingValue: mapperのupsertValueへ委譲し件数を返す")
    void updateSettingValue_delegatesToMapperUpsert() {
        when(systemSettingMapper.upsertValue("MAIL_HOST", "smtp.new.com")).thenReturn(1);

        int result = service.updateSettingValue("MAIL_HOST", "smtp.new.com");

        assertThat(result).isEqualTo(1);
        verify(systemSettingMapper).upsertValue("MAIL_HOST", "smtp.new.com");
    }

    @Test
    @DisplayName("updatePaidLeaveGrantDate: 付与日を更新する")
    void updatePaidLeaveGrantDate_updatesDate() {
        service.updatePaidLeaveGrantDate("04-01");

        verify(systemSettingMapper).upsertValue(SystemSettingService.PAID_LEAVE_GRANT_DATE_KEY, "04-01");
    }
}
