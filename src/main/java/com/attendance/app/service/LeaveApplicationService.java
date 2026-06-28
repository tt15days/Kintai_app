package com.attendance.app.service;

import com.attendance.app.entity.LeaveApplication;
import com.attendance.app.entity.LeaveStatus;
import com.attendance.app.entity.LeaveType;
import com.attendance.app.mapper.LeaveApplicationMapper;
import com.attendance.app.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 休暇申請に関する業務ロジックを提供するサービスです。
 * 
 * 休暇申請の作成、更新、承認、却下、削除などのライフサイクル管理と
 * 休暇日数の計算機能を提供します。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class LeaveApplicationService {

    private static final String APPLICATION_NOT_FOUND_MESSAGE = "申請が見つかりません";

    private final LeaveApplicationMapper leaveApplicationMapper;
    private final PaidLeaveBalanceService paidLeaveBalanceService;

    /**
     * 指定された申請IDに該当する休暇申請を取得します。
     *
     * @param applicationId 取得対象の休暇申請ID
     * @return 休暇申請情報。存在しない場合は empty
     */
    public Optional<LeaveApplication> getApplicationById(Long applicationId) {
        return leaveApplicationMapper.selectById(applicationId);
    }

    /**
     * 指定されたユーザーの休暇申請一覧を取得します。
     *
     * @param userId 対象ユーザーID
     * @return 休暇申請のリスト
     */
    public List<LeaveApplication> getApplicationsByUserId(Long userId) {
        return leaveApplicationMapper.selectByUserId(userId);
    }

    /**
     * 指定されたユーザーの特定期間内の休暇申請を取得します。
     *
     * @param userId 対象ユーザーID
     * @param startDate 期間の開始日
     * @param endDate 期間の終了日
     * @return 指定期間の休暇申請リスト
     */
    public List<LeaveApplication> getApplicationsByUserAndDateRange(Long userId, LocalDate startDate, LocalDate endDate) {
        return leaveApplicationMapper.selectByUserAndDateRange(userId, startDate, endDate);
    }

    /**
     * 指定されたユーザーとステータスに合致する休暇申請を取得します。
     *
     * @param userId 対象ユーザーID
     * @param status 検索対象のステータス
     * @return 該当する休暇申請のリスト
     */
    public List<LeaveApplication> getApplicationsByUserIdAndStatus(Long userId, LeaveStatus status) {
        return leaveApplicationMapper.selectByUserIdAndStatus(userId, status.name());
    }

    /**
     * 現在承認待ち（PENDING）となっているすべての休暇申請を取得します。
     * 主に管理者や承認者の画面表示用として利用されます。
     *
     * @return 承認待ちの休暇申請リスト
     */
    public List<LeaveApplication> getPendingApplications() {
        return leaveApplicationMapper.selectPendingApplications();
    }

    /**
     * 新しい休暇申請を作成し、システムに登録します。
     * 開始日は終了日以前である必要があります。
     *
     * @param userId 申請ユーザーID
     * @param startDate 休暇の開始日
     * @param endDate 休暇の終了日
     * @param leaveType 休暇の種類
     * @param reason 休暇の理由
     * @return 作成された休暇申請情報
     * @throws IllegalArgumentException 開始日が終了日より後である場合
     */
    public LeaveApplication createApplication(Long userId, LocalDate startDate, LocalDate endDate, LeaveType leaveType, String reason) {
        return createApplication(userId, startDate, endDate, "FULL_DAY", leaveType, reason);
    }

    public LeaveApplication createApplication(Long userId, LocalDate startDate, LocalDate endDate, String leaveDurationType, LeaveType leaveType, String reason) {
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("開始日は終了日より前である必要があります");
        }

        LeaveApplication application = LeaveApplication.builder()
                .userId(userId)
                .leaveStartDate(startDate)
                .leaveEndDate(endDate)
                .leaveDurationType(leaveDurationType != null ? leaveDurationType : "FULL_DAY")
                .leaveType(leaveType)
                .reason(reason)
                .status(LeaveStatus.PENDING)
                .createdAt(DateTimeUtil.now())
                .updatedAt(DateTimeUtil.now())
                .build();

        leaveApplicationMapper.insert(application);
        log.info("休暇申請を作成しました: userId={}, type={}, durationType={}, period={}-{}", userId, leaveType, leaveDurationType, startDate, endDate);
        return application;
    }

    /**
     * 既存の休暇申請を更新します。
     * 既に承認済みの申請は更新できません。
     *
     * @param applicationId 更新対象の申請ID
     * @param startDate 新しい開始日
     * @param endDate 新しい終了日
     * @param leaveType 新しい休暇種類
     * @param reason 新しい理由
     * @return 更新された休暇申請情報
     * @throws IllegalArgumentException 申請が存在しない、または既に承認済みの場合
     */
    public LeaveApplication updateApplication(Long applicationId, LocalDate startDate, LocalDate endDate, LeaveType leaveType, String reason) {
        LeaveApplication application = findApplicationForUpdateOrThrow(applicationId);

        if (application.getStatus() == LeaveStatus.APPROVED) {
            throw new IllegalArgumentException("承認済みの申請は更新できません");
        }

        application.setLeaveStartDate(startDate);
        application.setLeaveEndDate(endDate);
        application.setLeaveType(leaveType);
        application.setReason(reason);
        application.setUpdatedAt(DateTimeUtil.now());

        leaveApplicationMapper.update(application);
        log.info("休暇申請を更新しました: applicationId={}", applicationId);
        return application;
    }

    /**
     * 申請中の休暇申請を承認します。
     * 有給休暇の場合は、年次有給残高から使用日数を差し引きます。
     *
     * @param applicationId 承認対象の申請ID
     * @param approvedBy 承認者のユーザーID
     * @throws IllegalArgumentException 申請が存在しない、またはステータスが申請中（PENDING）でない場合
     */
    public void approveApplication(Long applicationId, Long approvedBy) {
        LeaveApplication application = findApplicationForUpdateOrThrow(applicationId);

        if (application.getStatus() != LeaveStatus.PENDING) {
            throw new IllegalArgumentException("申請中のステータスのみ承認できます");
        }

        application.setStatus(LeaveStatus.APPROVED);
        application.setApprovedAt(DateTimeUtil.now());
        application.setApprovedBy(approvedBy);
        application.setUpdatedAt(DateTimeUtil.now());
        leaveApplicationMapper.update(application);

        // 有給休暇承認時は年次残高から使用日数を減算する
        if (LeaveType.PAID_LEAVE == application.getLeaveType()) {
            long days = calculateLeaveDays(application.getLeaveStartDate(), application.getLeaveEndDate());
            paidLeaveBalanceService.deductBalance(application.getUserId(), BigDecimal.valueOf(days), application.getLeaveStartDate());
        }

        log.info("休暇申請を承認しました: applicationId={}, approvedBy={}", applicationId, approvedBy);
    }

    /**
     * 申請中の休暇申請を却下します。
     *
     * @param applicationId 却下対象の申請ID
     * @throws IllegalArgumentException 申請が存在しない、またはステータスが申請中（PENDING）でない場合
     */
    public void rejectApplication(Long applicationId) {
        LeaveApplication application = findApplicationForUpdateOrThrow(applicationId);

        if (application.getStatus() != LeaveStatus.PENDING) {
            throw new IllegalArgumentException("申請中のステータスのみ却下できます");
        }

        application.setStatus(LeaveStatus.REJECTED);
        application.setUpdatedAt(DateTimeUtil.now());
        leaveApplicationMapper.update(application);
        log.info("休暇申請を却下しました: applicationId={}", applicationId);
    }

    /**
     * 指定された休暇申請をシステムから削除します。
     *
     * @param applicationId 削除対象の申請ID
     */
    public void deleteApplication(Long applicationId) {
        LeaveApplication application = findApplicationForUpdateOrThrow(applicationId);
        if (LeaveStatus.APPROVED == application.getStatus() && LeaveType.PAID_LEAVE == application.getLeaveType()) {
            long days = calculateLeaveDays(application.getLeaveStartDate(), application.getLeaveEndDate());
            paidLeaveBalanceService.refundBalance(application.getUserId(), BigDecimal.valueOf(days), application.getLeaveStartDate());
        }
        leaveApplicationMapper.deleteById(applicationId);
        log.info("休暇申請を削除しました: applicationId={}", applicationId);
    }

    /**
     * 指定された期間の休暇日数を計算します（両端の日付を含む）。
     *
     * @param startDate 開始日
     * @param endDate 終了日
     * @return 休暇日数
     */
    public long calculateLeaveDays(LocalDate startDate, LocalDate endDate) {
        return startDate.datesUntil(endDate.plusDays(1)).count();
    }

    /**
     * 指定された年の承認済み有給休暇の使用日数を集計します。
     *
     * @param userId 対象ユーザーID
     * @param year 集計対象の年（例: 2026）
     * @return 当該年の有給休暇使用日数の合計
     */
    public long calculateYearlyUsedPaidLeaveDays(Long userId, int year) {
        return leaveApplicationMapper.countApprovedPaidLeaveDays(userId, year);
    }

    /**
     * 申請IDから休暇申請を取得し、存在しない場合は例外を送出します。
     *
     * @param applicationId 休暇申請ID
     * @return 休暇申請情報
     * @throws IllegalArgumentException 申請が存在しない場合
     */
    private LeaveApplication findApplicationOrThrow(Long applicationId) {
        return leaveApplicationMapper.selectById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException(APPLICATION_NOT_FOUND_MESSAGE));
    }

    /**
     * 申請IDから休暇申請を取得し（排他ロックを獲得）、存在しない場合は例外を送出します。
     *
     * @param applicationId 休暇申請ID
     * @return 休暇申請情報
     * @throws IllegalArgumentException 申請が存在しない場合
     */
    private LeaveApplication findApplicationForUpdateOrThrow(Long applicationId) {
        return leaveApplicationMapper.selectByIdForUpdate(applicationId)
                .orElseThrow(() -> new IllegalArgumentException(APPLICATION_NOT_FOUND_MESSAGE));
    }
}
