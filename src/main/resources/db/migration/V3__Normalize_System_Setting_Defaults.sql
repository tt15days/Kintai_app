INSERT INTO system_settings (setting_key, setting_value, updated_at)
SELECT 'PAID_LEAVE_GRANT_DATE',
       LPAD(month_setting.setting_value, 2, '0') || '-' || LPAD(day_setting.setting_value, 2, '0'),
       NOW()
FROM system_settings month_setting
JOIN system_settings day_setting ON day_setting.setting_key = 'paid_leave_grant_day'
WHERE month_setting.setting_key = 'paid_leave_grant_month'
  AND month_setting.setting_value ~ '^(?:[1-9]|1[0-2])$'
  AND day_setting.setting_value ~ '^(?:[1-9]|[12][0-9]|3[01])$'
ON CONFLICT (setting_key) DO NOTHING;

INSERT INTO system_settings (setting_key, setting_value, updated_at)
VALUES
    ('PAID_LEAVE_GRANT_DATE', '04-01', NOW()),
    ('PAID_LEAVE_GRANT_DAYS', '10', NOW())
ON CONFLICT (setting_key) DO NOTHING;

DELETE FROM system_settings
WHERE setting_key IN ('paid_leave_grant_month', 'paid_leave_grant_day');
