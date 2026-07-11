package com.attendance.app.service;

import com.attendance.app.entity.User;
import com.attendance.app.entity.UserRole;
import com.attendance.app.mapper.AttendanceCorrectionRequestMapper;
import com.attendance.app.mapper.AttendanceSubmissionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

// TransactionAwareCacheManagerProxy によりキャッシュの Put/Evict はトランザクションコミット後に
// 反映されるため、テストを @Transactional（コミットしない）で囲まず、各サービス呼び出しが
// 自身のトランザクションをコミットする実運用と同じ流れで検証する。
// DBへの書き込みは Mapper / AttendanceRecordService のモックで遮断している。
@SpringBootTest(classes = com.attendance.app.AttendanceApplication.class)
@ActiveProfiles("local")
class CacheIntegrationTest {

    @Autowired
    private AttendanceSubmissionService submissionService;

    @Autowired
    private AttendanceCorrectionRequestService correctionRequestService;

    @Autowired
    private CacheManager cacheManager;

    @MockitoBean
    private AttendanceSubmissionMapper attendanceSubmissionMapper;

    @MockitoBean
    private AttendanceCorrectionRequestMapper correctionRequestMapper;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private AttendanceRecordService attendanceRecordService;

    private User adminUser;

    @BeforeEach
    void setUp() {
        if (cacheManager.getCache("pendingSubmissionsCount") != null) {
            cacheManager.getCache("pendingSubmissionsCount").clear();
        }
        if (cacheManager.getCache("pendingCorrectionsCount") != null) {
            cacheManager.getCache("pendingCorrectionsCount").clear();
        }

        adminUser = User.builder()
                .userId(1L)
                .fullName("管理者")
                .userRole(UserRole.ADMIN)
                .className("開発部")
                .isActive(true)
                .canApproveAttendance(true)
                .build();

        when(userService.isAttendanceApprover(any())).thenReturn(true);
        when(userService.getUserById(1L)).thenReturn(Optional.of(adminUser));
        when(attendanceRecordService.getRecordByUserAndDate(anyLong(), any())).thenReturn(Optional.empty());
    }

    @Test
    void testPendingSubmissionsCachingAndEviction() {
        // 1回目の呼び出し
        when(attendanceSubmissionMapper.selectByStatus("PENDING")).thenReturn(new ArrayList<>());
        List<?> list1 = submissionService.getPendingSubmissions(adminUser);

        // 2回目の呼び出し (キャッシュされているため、Mapperメソッドは呼ばれず、結果が返る)
        List<?> list2 = submissionService.getPendingSubmissions(adminUser);

        assertThat(list1).isSameAs(list2);
        verify(attendanceSubmissionMapper, times(1)).selectByStatus("PENDING");

        // キャッシュクリアのトリガー (例: approveメソッド呼び出し)
        com.attendance.app.entity.AttendanceSubmission sub = com.attendance.app.entity.AttendanceSubmission.builder()
                .submissionId(10L)
                .userId(2L)
                .status("PENDING")
                .build();
        when(attendanceSubmissionMapper.selectByIdForUpdate(10L)).thenReturn(Optional.of(sub));

        submissionService.approve(10L, 1L, "OK");

        // 承認後はキャッシュがクリアされているはずなので、再度Mapperが呼ばれる
        reset(attendanceSubmissionMapper);
        when(attendanceSubmissionMapper.selectByStatus("PENDING")).thenReturn(new ArrayList<>());
        submissionService.getPendingSubmissions(adminUser);

        verify(attendanceSubmissionMapper, times(1)).selectByStatus("PENDING");
    }

    @Test
    void testPendingCorrectionsCachingAndEviction() {
        // 1回目の呼び出し
        when(correctionRequestMapper.selectByStatus("PENDING")).thenReturn(new ArrayList<>());
        List<?> list1 = correctionRequestService.getPendingRequests(adminUser);

        // 2回目の呼び出し
        List<?> list2 = correctionRequestService.getPendingRequests(adminUser);

        assertThat(list1).isSameAs(list2);
        verify(correctionRequestMapper, times(1)).selectByStatus("PENDING");

        // キャッシュクリアのトリガー (例: approveRequestメソッド呼び出し)
        com.attendance.app.entity.AttendanceCorrectionRequest req = com.attendance.app.entity.AttendanceCorrectionRequest.builder()
                .requestId(20L)
                .userId(2L)
                .attendanceDate(LocalDate.now())
                .status("PENDING")
                .build();
        when(correctionRequestMapper.selectByIdForUpdate(20L)).thenReturn(Optional.of(req));

        correctionRequestService.approveRequest(20L, 1L, "OK");

        // キャッシュクリア後の呼び出し
        reset(correctionRequestMapper);
        when(correctionRequestMapper.selectByStatus("PENDING")).thenReturn(new ArrayList<>());
        correctionRequestService.getPendingRequests(adminUser);

        verify(correctionRequestMapper, times(1)).selectByStatus("PENDING");
    }
}
