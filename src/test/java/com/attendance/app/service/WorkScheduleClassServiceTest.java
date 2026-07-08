package com.attendance.app.service;

import com.attendance.app.entity.WorkScheduleClass;
import com.attendance.app.mapper.AttendanceApproverAssignmentMapper;
import com.attendance.app.mapper.UserMapper;
import com.attendance.app.mapper.WorkScheduleClassMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class WorkScheduleClassServiceTest {

    @Mock
    private WorkScheduleClassMapper workScheduleClassMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private AttendanceApproverAssignmentMapper attendanceApproverAssignmentMapper;

    @InjectMocks
    private WorkScheduleClassService workScheduleClassService;

    private WorkScheduleClass defaultClass;

    @BeforeEach
    void setUp() {
        defaultClass = WorkScheduleClass.builder()
                .classId(1L)
                .classCode("W001")
                .name("標準勤務")
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(18, 0))
                .breaks(List.of(
                        com.attendance.app.entity.WorkScheduleClassBreak.builder()
                                .classId(1L)
                                .breakStartTime(LocalTime.of(12, 0))
                                .breakEndTime(LocalTime.of(13, 0))
                                .build()
                ))
                .build();
    }

    @Test
    void testGetAllClasses() {
        when(workScheduleClassMapper.selectAll()).thenReturn(List.of(defaultClass));
        List<WorkScheduleClass> result = workScheduleClassService.getAllClasses();
        assertEquals(1, result.size());
        assertEquals("標準勤務", result.get(0).getName());
    }

    @Test
    void testGetClassById() {
        when(workScheduleClassMapper.selectById(1L)).thenReturn(Optional.of(defaultClass));
        Optional<WorkScheduleClass> result = workScheduleClassService.getClassById(1L);
        assertTrue(result.isPresent());
        assertEquals("標準勤務", result.get().getName());
    }

    @Test
    void testGetClassByName() {
        when(workScheduleClassMapper.selectByName("標準勤務")).thenReturn(Optional.of(defaultClass));
        Optional<WorkScheduleClass> result = workScheduleClassService.getClassByName("標準勤務");
        assertTrue(result.isPresent());
        
        // Blank name should return empty without calling DB
        assertTrue(workScheduleClassService.getClassByName(" ").isEmpty());
        verify(workScheduleClassMapper, times(1)).selectByName(anyString());
    }

    @Test
    void testCreateClass_Success() {
        when(workScheduleClassMapper.existsByName("新規シフト")).thenReturn(false);
        when(workScheduleClassMapper.existsByCode(anyString())).thenReturn(false);

        WorkScheduleClass result = workScheduleClassService.createClass(
                "新規シフト", "本社",
                null, null, null, null, "開発フォルダ", "夜勤,シフト",
                true, null, null,
                LocalTime.of(10, 0), LocalTime.of(19, 0),
                List.of(
                        com.attendance.app.entity.WorkScheduleClassBreak.builder()
                                .breakStartTime(LocalTime.of(13, 0))
                                .breakEndTime(LocalTime.of(14, 0))
                                .build()
                )
        );

        assertNotNull(result);
        assertEquals("新規シフト", result.getName());
        assertEquals("本社", result.getWorkLocation());
        assertEquals("開発フォルダ", result.getFolderName());
        assertEquals("夜勤,シフト", result.getTags());
        assertEquals(LocalTime.of(10, 0), result.getStartTime());
        verify(workScheduleClassMapper).insert(any(WorkScheduleClass.class));
    }

    @Test
    void testCreateClass_DuplicateName() {
        when(workScheduleClassMapper.existsByName("標準勤務")).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            workScheduleClassService.createClass(
                    "標準勤務", null,
                    null, null, null, null, null, null,
                    true, null, null,
                    LocalTime.of(9, 0), LocalTime.of(18, 0),
                    List.of(
                            com.attendance.app.entity.WorkScheduleClassBreak.builder()
                                    .breakStartTime(LocalTime.of(12, 0))
                                    .breakEndTime(LocalTime.of(13, 0))
                                    .build()
                    )
            );
        });
        assertTrue(ex.getMessage().contains("既に登録されています"));
        verify(workScheduleClassMapper, never()).insert(any());
    }

    @Test
    void testCreateClass_InvalidBreak() {
        when(workScheduleClassMapper.existsByName("テスト")).thenReturn(false);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            workScheduleClassService.createClass(
                    "テスト", null,
                    null, null, null, null, null, null,
                    true, null, null,
                    LocalTime.of(9, 0), LocalTime.of(18, 0),
                    List.of(
                            com.attendance.app.entity.WorkScheduleClassBreak.builder()
                                    .breakStartTime(LocalTime.of(12, 0))
                                    .breakEndTime(null)
                                    .build()
                    )
            );
        });
        assertTrue(ex.getMessage().contains("開始時刻と終了時刻は両方入力"));
    }

    @Test
    void testUpdateClass_Success() {
        when(workScheduleClassMapper.selectById(1L)).thenReturn(Optional.of(defaultClass));
        when(workScheduleClassMapper.existsByNameAndNotId("変更後シフト", 1L)).thenReturn(false);

        WorkScheduleClass result = workScheduleClassService.updateClass(
                1L, "変更後シフト", "支店",
                null, null, null, null, "新フォルダ", "変更タグ",
                true, null, null,
                LocalTime.of(8, 0), LocalTime.of(17, 0),
                List.of(
                        com.attendance.app.entity.WorkScheduleClassBreak.builder()
                                .classId(1L)
                                .breakStartTime(LocalTime.of(11, 0))
                                .breakEndTime(LocalTime.of(12, 0))
                                .build()
                )
        );

        assertEquals("変更後シフト", result.getName());
        assertEquals("新フォルダ", result.getFolderName());
        assertEquals("変更タグ", result.getTags());
        verify(workScheduleClassMapper).update(any(WorkScheduleClass.class));
        verify(userMapper).replaceClassName("標準勤務", "変更後シフト");
        verify(attendanceApproverAssignmentMapper).replaceDepartmentName("標準勤務", "変更後シフト");
    }

    @Test
    void testUpdateClass_DuplicateName() {
        when(workScheduleClassMapper.selectById(1L)).thenReturn(Optional.of(defaultClass));
        when(workScheduleClassMapper.existsByNameAndNotId("既存シフト", 1L)).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> {
            workScheduleClassService.updateClass(
                    1L, "既存シフト", null,
                    null, null, null, null, null, null,
                    true, null, null,
                    LocalTime.of(9, 0), LocalTime.of(18, 0),
                    List.of(
                            com.attendance.app.entity.WorkScheduleClassBreak.builder()
                                    .classId(1L)
                                    .breakStartTime(LocalTime.of(12, 0))
                                    .breakEndTime(LocalTime.of(13, 0))
                                    .build()
                    )
            );
        });
        verify(workScheduleClassMapper, never()).update(any());
    }

    @Test
    void testDeleteClass_Success() {
        when(workScheduleClassMapper.selectById(1L)).thenReturn(Optional.of(defaultClass));

        workScheduleClassService.deleteClass(1L);

        verify(workScheduleClassMapper).update(argThat(wsc -> !wsc.getIsActive()));
    }
}
