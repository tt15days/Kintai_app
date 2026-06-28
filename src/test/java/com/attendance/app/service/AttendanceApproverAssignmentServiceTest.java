package com.attendance.app.service;

import com.attendance.app.entity.User;
import com.attendance.app.mapper.AttendanceApproverAssignmentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AttendanceApproverAssignmentServiceTest {

    @Mock
    private AttendanceApproverAssignmentMapper assignmentMapper;

    @Mock
    private UserService userService;

    @InjectMocks
    private AttendanceApproverAssignmentService assignmentService;

    private User applicant;
    private User validApprover;
    private User invalidApprover;

    @BeforeEach
    void setUp() {
        applicant = new User();
        applicant.setUserId(1L);

        validApprover = new User();
        validApprover.setUserId(2L);

        invalidApprover = new User();
        invalidApprover.setUserId(3L);
    }

    @Test
    void testGetUserApproverIds() {
        when(assignmentMapper.selectUserApproverIds(1L)).thenReturn(List.of(2L));
        List<Long> result = assignmentService.getUserApproverIds(1L);
        assertEquals(1, result.size());
        assertEquals(2L, result.get(0));
    }

    @Test
    void testGetDepartmentApproverIds() {
        when(assignmentMapper.selectDepartmentApproverIds("本社")).thenReturn(List.of(2L));
        List<Long> result = assignmentService.getDepartmentApproverIds("本社");
        assertEquals(1, result.size());
        assertEquals(2L, result.get(0));
    }

    @Test
    void testAssignUserApprovers_Success() {
        when(userService.getUserById(1L)).thenReturn(Optional.of(applicant));
        when(userService.getUserById(2L)).thenReturn(Optional.of(validApprover));
        when(userService.isAttendanceApprover(validApprover)).thenReturn(true);

        assignmentService.assignUserApprovers(1L, List.of(2L));

        verify(assignmentMapper).deleteUserApprovers(1L);
        verify(assignmentMapper).insertUserApprover(1L, 2L);
    }

    @Test
    void testAssignUserApprovers_ApplicantNotFound() {
        when(userService.getUserById(1L)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            assignmentService.assignUserApprovers(1L, List.of(2L));
        });
        assertTrue(ex.getMessage().contains("申請者ユーザーが見つかりません"));
    }

    @Test
    void testAssignUserApprovers_ApproverWithoutPermission() {
        when(userService.getUserById(1L)).thenReturn(Optional.of(applicant));
        when(userService.getUserById(3L)).thenReturn(Optional.of(invalidApprover));
        when(userService.isAttendanceApprover(invalidApprover)).thenReturn(false);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            assignmentService.assignUserApprovers(1L, List.of(3L));
        });
        assertTrue(ex.getMessage().contains("承認権限がない"));
    }

    @Test
    void testAssignUserApprovers_AssignSelfAsApprover() {
        when(userService.getUserById(1L)).thenReturn(Optional.of(applicant));
        when(userService.isAttendanceApprover(applicant)).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            assignmentService.assignUserApprovers(1L, List.of(1L));
        });
        assertTrue(ex.getMessage().contains("申請者自身"));
    }

    @Test
    void testAssignDepartmentApprovers_Success() {
        when(userService.getUserById(2L)).thenReturn(Optional.of(validApprover));
        when(userService.isAttendanceApprover(validApprover)).thenReturn(true);

        assignmentService.assignDepartmentApprovers("本社", List.of(2L));

        verify(assignmentMapper).deleteDepartmentApprovers("本社");
        verify(assignmentMapper).insertDepartmentApprover("本社", 2L);
    }

    @Test
    void testIsAssignedForApplicant() {
        when(assignmentMapper.selectUserApproverIds(1L)).thenReturn(List.of(2L));
        assertTrue(assignmentService.isAssignedForApplicant(1L, 2L));
        assertFalse(assignmentService.isAssignedForApplicant(1L, 3L));
    }

    @Test
    void testIsAssignedForDepartment() {
        when(assignmentMapper.selectDepartmentApproverIds("本社")).thenReturn(List.of(2L));
        assertTrue(assignmentService.isAssignedForDepartment("本社", 2L));
        assertFalse(assignmentService.isAssignedForDepartment("本社", 3L));
    }
}
