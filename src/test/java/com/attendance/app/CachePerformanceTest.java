package com.attendance.app;

import com.attendance.app.mapper.SystemSettingMapper;
import com.attendance.app.service.EventTypeService;
import com.attendance.app.service.WorkScheduleClassService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class CachePerformanceTest {

    @Autowired
    private SystemSettingMapper systemSettingMapper;

    @Autowired
    private EventTypeService eventTypeService;

    @Autowired
    private WorkScheduleClassService workScheduleClassService;

    @Test
    public void testPerformance() {
        int iterations = 1000;

        // Warm up
        systemSettingMapper.selectValueByKey("attendance_period_start_day");
        eventTypeService.getAllActiveEventTypes();
        workScheduleClassService.getAllActiveClasses();

        // Measure SystemSettingMapper
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < iterations; i++) {
            systemSettingMapper.selectValueByKey("attendance_period_start_day");
        }
        long endTime = System.currentTimeMillis();
        System.out.println("MEASUREMENT_RESULT: SystemSettingMapper (1000 calls): " + (endTime - startTime) + " ms");

        // Measure EventTypeService
        startTime = System.currentTimeMillis();
        for (int i = 0; i < iterations; i++) {
            eventTypeService.getAllActiveEventTypes();
        }
        endTime = System.currentTimeMillis();
        System.out.println("MEASUREMENT_RESULT: EventTypeService (1000 calls): " + (endTime - startTime) + " ms");

        // Measure WorkScheduleClassService
        startTime = System.currentTimeMillis();
        for (int i = 0; i < iterations; i++) {
            workScheduleClassService.getAllActiveClasses();
        }
        endTime = System.currentTimeMillis();
        System.out.println("MEASUREMENT_RESULT: WorkScheduleClassService (1000 calls): " + (endTime - startTime) + " ms");
    }
}
