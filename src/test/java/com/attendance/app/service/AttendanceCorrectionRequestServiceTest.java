package com.attendance.app.service;

import com.attendance.app.entity.AttendanceCorrectionRequest;
import com.attendance.app.entity.AttendanceRecord;
import com.attendance.app.entity.AuditEventType;
import com.attendance.app.entity.User;
import com.attendance.app.entity.UserRole;
import com.attendance.app.mapper.AttendanceCorrectionRequestMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AttendanceCorrectionRequestService")
class AttendanceCorrectionRequestServiceTest {

    @Mock
    private AttendanceCorrectionRequestMapper correctionRequestMapper;
    @Mock
    private AttendanceRecordService attendanceRecordService;
    @Mock
    private AttendanceSubmissionService attendanceSubmissionService;
    @Mock
    private UserService userService;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private AttendanceApproverAssignmentService approverAssignmentService;

    @InjectMocks
    private AttendanceCorrectionRequestService service;

    private User adminUser;
    private User regularUser;
    private User approverUserClassA;
    private User applicantClassA;
    private User applicantClassB;

    @BeforeEach
    void setUp() {
        adminUser = User.builder().userId(1L).userRole(UserRole.ADMIN).isActive(true).canApproveAttendance(true).build();
        regularUser = User.builder().userId(2L).userRole(UserRole.USER).isActive(true).canApproveAttendance(false).build();
        approverUserClassA = User.builder().userId(3L).userRole(UserRole.USER).className("A").isActive(true).canApproveAttendance(true).build();
        applicantClassA = User.builder().userId(21L).userRole(UserRole.USER).className("A").department("総務部").isActive(true).build();
        applicantClassB = User.builder().userId(22L).userRole(UserRole.USER).className("B").department("開発部").isActive(true).build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // submitRequest
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("submitRequest")
    class SubmitRequest {

        @Test
        @DisplayName("正常系: 既存の勤怠がない状態で新規申請を登録する")
        void submit_noExistingRecord_createsPendingRequest() {
            LocalDate date = LocalDate.of(2026, 5, 15);
            when(attendanceSubmissionService.resolvePayrollMonth(date)).thenReturn(YearMonth.of(2026, 5));
            when(correctionRequestMapper.selectByUserAndMonth(21L, "2026-05")).thenReturn(List.of());
            when(attendanceRecordService.getRecordByUserAndDate(21L, date)).thenReturn(Optional.empty());

            AttendanceCorrectionRequest result = service.submitRequest(
                    21L, date, LocalTime.of(9, 0), LocalTime.of(18, 0), "備考", "理由修正");

            assertThat(result).isNotNull();
            assertThat(result.getUserId()).isEqualTo(21L);
            assertThat(result.getAttendanceDate()).isEqualTo(date);
            assertThat(result.getStatus()).isEqualTo(AttendanceCorrectionRequestService.STATUS_PENDING);
            assertThat(result.getReason()).isEqualTo("理由修正");
            assertThat(result.getCurrentStartTime()).isNull();
            assertThat(result.getCurrentEndTime()).isNull();

            verify(correctionRequestMapper).insert(any(AttendanceCorrectionRequest.class));
        }

        @Test
        @DisplayName("正常系: 既存の勤怠記録がある場合にスナップショットをとって登録する")
        void submit_withExistingRecord_createsSnapshot() {
            LocalDate date = LocalDate.of(2026, 5, 15);
            AttendanceRecord record = AttendanceRecord.builder()
                    .startTime(Instant.parse("2026-05-15T00:00:00Z")) // 09:00 JST (DateTimeUtil.toInstant)
                    .endTime(Instant.parse("2026-05-15T09:00:00Z"))   // 18:00 JST
                    .remarks("古い備考")
                    .nightShiftHours(0.5)
                    .build();

            when(attendanceSubmissionService.resolvePayrollMonth(date)).thenReturn(YearMonth.of(2026, 5));
            when(correctionRequestMapper.selectByUserAndMonth(21L, "2026-05")).thenReturn(List.of());
            when(attendanceRecordService.getRecordByUserAndDate(21L, date)).thenReturn(Optional.of(record));

            AttendanceCorrectionRequest result = service.submitRequest(
                    21L, date, LocalTime.of(10, 0), LocalTime.of(19, 0), "新しい備考", "修正理由");

            assertThat(result.getCurrentStartTime()).isEqualTo(LocalTime.of(9, 0));
            assertThat(result.getCurrentEndTime()).isEqualTo(LocalTime.of(18, 0));
            assertThat(result.getCurrentRemarks()).isEqualTo("古い備考");
            assertThat(result.getCurrentNightShiftHours()).isEqualTo(0.5);
            verify(correctionRequestMapper).insert(any(AttendanceCorrectionRequest.class));
        }

        @Test
        @DisplayName("バリデーション: 日付がnullの場合は例外")
        void submit_nullDate_throwsException() {
            assertThatThrownBy(() -> service.submitRequest(21L, null, null, null, null, "理由"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("修正対象日を指定してください");
        }

        @Test
        @DisplayName("バリデーション: 理由がnullまたは空文字の場合は例外")
        void submit_emptyReason_throwsException() {
            assertThatThrownBy(() -> service.submitRequest(21L, LocalDate.of(2026, 5, 15), null, null, null, "   "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("修正理由を入力してください");
        }

        @Test
        @DisplayName("バリデーション: 理由が500文字を超える場合は例外")
        void submit_longReason_throwsException() {
            String longReason = "a".repeat(501);
            assertThatThrownBy(() -> service.submitRequest(21L, LocalDate.of(2026, 5, 15), null, null, null, longReason))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("修正理由は500文字以内で入力してください");
        }

        @Test
        @DisplayName("バリデーション: 同一日に既に承認待ち（PENDING）の申請がある場合は例外")
        void submit_duplicatePendingRequest_throwsException() {
            LocalDate date = LocalDate.of(2026, 5, 15);
            AttendanceCorrectionRequest pending = AttendanceCorrectionRequest.builder()
                    .attendanceDate(date).status(AttendanceCorrectionRequestService.STATUS_PENDING).build();

            when(attendanceSubmissionService.resolvePayrollMonth(date)).thenReturn(YearMonth.of(2026, 5));
            when(correctionRequestMapper.selectByUserAndMonth(21L, "2026-05")).thenReturn(List.of(pending));

            assertThatThrownBy(() -> service.submitRequest(21L, date, null, null, null, "理由"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("この日付には既に承認待ちの修正申請があります");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // withdrawRequest
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("withdrawRequest")
    class WithdrawRequest {

        @Test
        @DisplayName("正常系: 自分の申請かつPENDING状態であれば取り下げできる")
        void withdraw_ownPendingRequest_success() {
            AttendanceCorrectionRequest pending = AttendanceCorrectionRequest.builder()
                    .requestId(100L).userId(21L).status(AttendanceCorrectionRequestService.STATUS_PENDING).build();
            when(correctionRequestMapper.selectByIdForUpdate(100L)).thenReturn(Optional.of(pending));

            service.withdrawRequest(100L, 21L);

            assertThat(pending.getStatus()).isEqualTo(AttendanceCorrectionRequestService.STATUS_WITHDRAWN);
            assertThat(pending.getActionBy()).isEqualTo(21L);
            verify(correctionRequestMapper).update(pending);
        }

        @Test
        @DisplayName("異常系: 他人の申請は取り下げできない")
        void withdraw_otherUserRequest_throwsException() {
            AttendanceCorrectionRequest pending = AttendanceCorrectionRequest.builder()
                    .requestId(100L).userId(21L).status(AttendanceCorrectionRequestService.STATUS_PENDING).build();
            when(correctionRequestMapper.selectByIdForUpdate(100L)).thenReturn(Optional.of(pending));

            assertThatThrownBy(() -> service.withdrawRequest(100L, 22L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("自分の申請のみ取り下げできます");
        }

        @Test
        @DisplayName("異常系: PENDING以外の申請は取り下げできない")
        void withdraw_nonPendingRequest_throwsException() {
            AttendanceCorrectionRequest approved = AttendanceCorrectionRequest.builder()
                    .requestId(100L).userId(21L).status(AttendanceCorrectionRequestService.STATUS_APPROVED).build();
            when(correctionRequestMapper.selectByIdForUpdate(100L)).thenReturn(Optional.of(approved));

            assertThatThrownBy(() -> service.withdrawRequest(100L, 21L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("承認待ちの申請のみ取り下げできます");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // approveRequest
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("approveRequest")
    class ApproveRequest {

        @Test
        @DisplayName("正常系: 個人・部署アサインされた一般承認者が申請を承認する")
        void approve_assignedApprover_success() {
            AttendanceCorrectionRequest pending = AttendanceCorrectionRequest.builder()
                    .requestId(300L).userId(21L).attendanceDate(LocalDate.of(2026, 5, 15))
                    .status(AttendanceCorrectionRequestService.STATUS_PENDING)
                    .requestedStartTime(LocalTime.of(9, 0)).requestedEndTime(LocalTime.of(18, 0)).requestedRemarks("修正")
                    .build();

            when(userService.getUserById(3L)).thenReturn(Optional.of(approverUserClassA));
            when(userService.isAttendanceApprover(approverUserClassA)).thenReturn(true);
            when(correctionRequestMapper.selectByIdForUpdate(300L)).thenReturn(Optional.of(pending));
            when(userService.getUserById(21L)).thenReturn(Optional.of(applicantClassA));
            when(approverAssignmentService.resolveAssignedApproverIds(21L, "総務部")).thenReturn(List.of(3L));

            service.approveRequest(300L, 3L, "承認します");

            verify(attendanceRecordService).saveRecord(21L, LocalDate.of(2026, 5, 15),
                    LocalTime.of(9, 0), LocalTime.of(18, 0), "修正", false, null);
            verify(correctionRequestMapper).update(pending);
            assertThat(pending.getStatus()).isEqualTo(AttendanceCorrectionRequestService.STATUS_APPROVED);
            assertThat(pending.getActionComment()).isEqualTo("承認します");
            verify(auditLogService).recordCorrectionRequestEvent(
                    eq(AuditEventType.CORRECTION_APPROVED), eq(3L), eq(21L), eq(300L), any());
        }

        @Test
        @DisplayName("異常系: アサイン外の一般承認者は承認できない")
        void approve_unassignedApprover_throwsException() {
            AttendanceCorrectionRequest pending = AttendanceCorrectionRequest.builder()
                    .requestId(300L).userId(21L).status(AttendanceCorrectionRequestService.STATUS_PENDING).build();

            when(userService.getUserById(3L)).thenReturn(Optional.of(approverUserClassA));
            when(userService.isAttendanceApprover(approverUserClassA)).thenReturn(true);
            when(correctionRequestMapper.selectByIdForUpdate(300L)).thenReturn(Optional.of(pending));
            when(userService.getUserById(21L)).thenReturn(Optional.of(applicantClassA));
            when(approverAssignmentService.resolveAssignedApproverIds(21L, "総務部")).thenReturn(List.of(99L));

            assertThatThrownBy(() -> service.approveRequest(300L, 3L, "承認"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("この申請を承認する権限がありません");
        }

        @Test
        @DisplayName("異常系: アサインのない一般承認者は承認できない")
        void approve_unassignedApproverWithoutAssignments_throwsException() {
            AttendanceCorrectionRequest pending = AttendanceCorrectionRequest.builder()
                    .requestId(300L).userId(22L).status(AttendanceCorrectionRequestService.STATUS_PENDING).build();

            when(userService.getUserById(3L)).thenReturn(Optional.of(approverUserClassA));
            when(userService.isAttendanceApprover(approverUserClassA)).thenReturn(true);
            when(correctionRequestMapper.selectByIdForUpdate(300L)).thenReturn(Optional.of(pending));
            when(userService.getUserById(22L)).thenReturn(Optional.of(applicantClassB));

            assertThatThrownBy(() -> service.approveRequest(300L, 3L, "承認"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("この申請を承認する権限がありません");
        }

        @Test
        @DisplayName("正常系: 時刻指定がnullで既存勤怠がある場合、備考のみが適用される")
        void approve_nullTimes_appliesOnlyRemarks() {
            AttendanceCorrectionRequest pending = AttendanceCorrectionRequest.builder()
                    .requestId(300L).userId(21L).attendanceDate(LocalDate.of(2026, 5, 15))
                    .status(AttendanceCorrectionRequestService.STATUS_PENDING)
                    .requestedStartTime(null).requestedEndTime(null).requestedRemarks("備考のみ修正")
                    .build();

            AttendanceRecord record = AttendanceRecord.builder()
                    .startTime(Instant.parse("2026-05-15T00:00:00Z")) // 09:00
                    .endTime(Instant.parse("2026-05-15T09:00:00Z"))   // 18:00
                    .remarks("古い備考").eventTypeId(5)
                    .build();

            when(userService.getUserById(1L)).thenReturn(Optional.of(adminUser));
            when(userService.isAttendanceApprover(adminUser)).thenReturn(true);
            when(correctionRequestMapper.selectByIdForUpdate(300L)).thenReturn(Optional.of(pending));
            when(attendanceRecordService.getRecordByUserAndDate(21L, LocalDate.of(2026, 5, 15))).thenReturn(Optional.of(record));

            service.approveRequest(300L, 1L, "OK");

            // 既存の開始・終了時刻が引き継がれ、備考のみ「備考のみ修正」で保存されること
            verify(attendanceRecordService).saveRecord(21L, LocalDate.of(2026, 5, 15),
                    LocalTime.of(9, 0), LocalTime.of(18, 0), "備考のみ修正", false, 5);
        }

        @Test
        @DisplayName("承認権限がないユーザーは承認できない")
        void approveRequest_withoutApprovalAuthority_throwsException() {
            when(userService.getUserById(10L)).thenReturn(Optional.of(regularUser));
            when(userService.isAttendanceApprover(regularUser)).thenReturn(false);

            assertThatThrownBy(() -> service.approveRequest(100L, 10L, "ok"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("勤怠承認権限がありません");
        }

        @Test
        @DisplayName("承認待ち以外の申請は承認できない")
        void approveRequest_whenNotPending_throwsException() {
            AttendanceCorrectionRequest approved = AttendanceCorrectionRequest.builder()
                    .requestId(200L).userId(20L).status(AttendanceCorrectionRequestService.STATUS_APPROVED).build();

            when(userService.getUserById(1L)).thenReturn(Optional.of(adminUser));
            when(userService.isAttendanceApprover(adminUser)).thenReturn(true);
            when(correctionRequestMapper.selectByIdForUpdate(200L)).thenReturn(Optional.of(approved));

            assertThatThrownBy(() -> service.approveRequest(200L, 1L, "ok"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("承認待ちの申請のみ承認できます");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // rejectRequest
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("rejectRequest")
    class RejectRequest {

        @Test
        @DisplayName("正常系: PENDINGの申請を却下し、コメントを保存する")
        void reject_pendingRequest_success() {
            AttendanceCorrectionRequest pending = AttendanceCorrectionRequest.builder()
                    .requestId(400L).userId(21L).status(AttendanceCorrectionRequestService.STATUS_PENDING).build();

            when(userService.getUserById(1L)).thenReturn(Optional.of(adminUser));
            when(userService.isAttendanceApprover(adminUser)).thenReturn(true);
            when(correctionRequestMapper.selectByIdForUpdate(400L)).thenReturn(Optional.of(pending));

            service.rejectRequest(400L, 1L, "却下します");

            assertThat(pending.getStatus()).isEqualTo(AttendanceCorrectionRequestService.STATUS_REJECTED);
            assertThat(pending.getActionComment()).isEqualTo("却下します");
            assertThat(pending.getActionBy()).isEqualTo(1L);
            verify(correctionRequestMapper).update(pending);
            verify(auditLogService).recordCorrectionRequestEvent(
                    eq(AuditEventType.CORRECTION_REJECTED), eq(1L), eq(21L), eq(400L), any());
        }

        @Test
        @DisplayName("承認待ち以外の申請は却下できない")
        void rejectRequest_whenNotPending_throwsException() {
            AttendanceCorrectionRequest approved = AttendanceCorrectionRequest.builder()
                    .requestId(400L).userId(22L).status(AttendanceCorrectionRequestService.STATUS_APPROVED).build();

            when(userService.getUserById(1L)).thenReturn(Optional.of(adminUser));
            when(userService.isAttendanceApprover(adminUser)).thenReturn(true);
            when(correctionRequestMapper.selectByIdForUpdate(400L)).thenReturn(Optional.of(approved));

            assertThatThrownBy(() -> service.rejectRequest(400L, 1L, "却下"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("承認待ちの申請のみ却下できます");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getPendingRequests
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("getPendingRequests")
    class GetPendingRequests {

        @Test
        @DisplayName("正常系: 管理者(ADMIN)は全PENDING申請を取得できる")
        void getPending_admin_returnsAll() {
            AttendanceCorrectionRequest req1 = AttendanceCorrectionRequest.builder().userId(21L).status("PENDING").build();
            AttendanceCorrectionRequest req2 = AttendanceCorrectionRequest.builder().userId(22L).status("PENDING").build();

            when(userService.isAttendanceApprover(adminUser)).thenReturn(true);
            when(correctionRequestMapper.selectByStatus("PENDING")).thenReturn(List.of(req1, req2));

            List<AttendanceCorrectionRequest> result = service.getPendingRequests(adminUser);

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("正常系: 一般承認者はクラス名が一致する申請のみ取得できる")
        void getPending_approver_filtersByClass() {
            AttendanceCorrectionRequest req1 = AttendanceCorrectionRequest.builder().userId(21L).status("PENDING").build();
            AttendanceCorrectionRequest req2 = AttendanceCorrectionRequest.builder().userId(22L).status("PENDING").build();

            when(userService.isAttendanceApprover(approverUserClassA)).thenReturn(true);
            when(correctionRequestMapper.selectByStatus("PENDING")).thenReturn(List.of(req1, req2));
            when(attendanceSubmissionService.canApproveAll(approverUserClassA, List.of(21L, 22L)))
                    .thenReturn(java.util.Map.of(21L, true, 22L, false));

            List<AttendanceCorrectionRequest> result = service.getPendingRequests(approverUserClassA);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getUserId()).isEqualTo(21L); // Class Aのみ
        }
    }
}
