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
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
    private final HolidayService holidayService;

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

        String resolvedDurationType = leaveDurationType != null ? leaveDurationType : "FULL_DAY";
        if (!"FULL_DAY".equals(resolvedDurationType) && !startDate.isEqual(endDate)) {
            throw new IllegalArgumentException("半休は開始日と終了日が同じ単日でのみ申請できます");
        }

        // 同一期間に既存の有効な休暇申請（却下以外）が重複していないか確認する
        List<LeaveApplication> overlapping = getApplicationsByUserAndDateRange(userId, startDate, endDate);
        boolean hasOverlap = overlapping.stream()
                .anyMatch(a -> a.getStatus() != LeaveStatus.REJECTED);
        if (hasOverlap) {
            throw new IllegalArgumentException("指定期間に既に休暇申請が存在します");
        }

        if (LeaveType.PAID_LEAVE == leaveType) {
            BigDecimal requestedDays = calculateConsumedDays(startDate, endDate, resolvedDurationType);
            BigDecimal remainingDays = paidLeaveBalanceService.getTotalRemainingDays(userId);
            if (requestedDays.compareTo(remainingDays) > 0) {
                BigDecimal shortage = requestedDays.subtract(remainingDays);
                throw new IllegalArgumentException("有給休暇の残日数が不足しています（不足: " + shortage.stripTrailingZeros().toPlainString() + "日）");
            }
        }

        LeaveApplication application = LeaveApplication.builder()
                .userId(userId)
                .leaveStartDate(startDate)
                .leaveEndDate(endDate)
                .leaveDurationType(resolvedDurationType)
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
     * 休暇申請の作成と即時承認を単一トランザクションで実行します（自動承認仕様）。
     * 承認時の例外（有給残高不足等）が発生した場合は作成も含めてロールバックされ、
     * PENDING 申請が残留しません。
     *
     * @param userId 申請ユーザーID
     * @param startDate 休暇の開始日
     * @param endDate 休暇の終了日
     * @param leaveType 休暇の種類
     * @param reason 休暇の理由
     * @param approvedBy 承認者のユーザーID
     * @return 作成・承認された休暇申請情報
     */
    public LeaveApplication createAndApproveApplication(Long userId, LocalDate startDate, LocalDate endDate, LeaveType leaveType, String reason, Long approvedBy) {
        return createAndApproveApplication(userId, startDate, endDate, "FULL_DAY", leaveType, reason, approvedBy);
    }

    public LeaveApplication createAndApproveApplication(Long userId, LocalDate startDate, LocalDate endDate, String leaveDurationType, LeaveType leaveType, String reason, Long approvedBy) {
        LeaveApplication application = createApplication(userId, startDate, endDate, leaveDurationType, leaveType, reason);
        approveApplication(application.getApplicationId(), approvedBy);
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

        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("開始日は終了日より前である必要があります");
        }

        if (!"FULL_DAY".equals(application.getLeaveDurationType()) && !startDate.isEqual(endDate)) {
            throw new IllegalArgumentException("半休は開始日と終了日が同じ単日でのみ申請できます");
        }

        // 同一期間に既存の有効な休暇申請（却下以外で自身以外）が重複していないか確認する
        List<LeaveApplication> overlapping = getApplicationsByUserAndDateRange(application.getUserId(), startDate, endDate);
        boolean hasOverlap = overlapping.stream()
                .anyMatch(a -> !a.getApplicationId().equals(applicationId) && a.getStatus() != LeaveStatus.REJECTED);
        if (hasOverlap) {
            throw new IllegalArgumentException("指定期間に既に休暇申請が存在します");
        }

        if (LeaveType.PAID_LEAVE == leaveType) {
            BigDecimal requestedDays = calculateConsumedDays(startDate, endDate, application.getLeaveDurationType());
            BigDecimal remainingDays = paidLeaveBalanceService.getTotalRemainingDays(application.getUserId());
            if (requestedDays.compareTo(remainingDays) > 0) {
                BigDecimal shortage = requestedDays.subtract(remainingDays);
                throw new IllegalArgumentException("有給休暇の残日数が不足しています（不足: " + shortage.stripTrailingZeros().toPlainString() + "日）");
            }
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

        // 有給休暇承認時は年次残高から使用日数を減算する（残高不足の場合は例外により本メソッド全体がロールバックされる）
        if (LeaveType.PAID_LEAVE == application.getLeaveType()) {
            BigDecimal days = calculateConsumedDays(application.getLeaveStartDate(), application.getLeaveEndDate(), application.getLeaveDurationType());
            paidLeaveBalanceService.deductBalance(application.getUserId(), days, application.getLeaveStartDate());
        }

        application.setStatus(LeaveStatus.APPROVED);
        application.setApprovedAt(DateTimeUtil.now());
        application.setApprovedBy(approvedBy);
        application.setUpdatedAt(DateTimeUtil.now());
        leaveApplicationMapper.update(application);

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
            BigDecimal days = calculateConsumedDays(application.getLeaveStartDate(), application.getLeaveEndDate(), application.getLeaveDurationType());
            paidLeaveBalanceService.refundBalance(application.getUserId(), days, application.getLeaveStartDate());
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
     * 休暇申請の実消化日数を計算します。
     * 半休（AM_HALF/PM_HALF）は開始日＝終了日の単日申請であることを前提とし、0.5日として扱います。
     * それ以外（FULL_DAY）は所定労働日（土日および holidays マスタの祝日を除外）のみを日数として計上します。
     *
     * @param startDate          開始日
     * @param endDate            終了日
     * @param leaveDurationType  休暇期間種別（FULL_DAY, AM_HALF, PM_HALF）
     * @return 実消化日数
     */
    public BigDecimal calculateConsumedDays(LocalDate startDate, LocalDate endDate, String leaveDurationType) {
        if (isHalfDay(leaveDurationType)) {
            return new BigDecimal("0.5");
        }
        long days = countWorkingDays(startDate, endDate);
        return BigDecimal.valueOf(days);
    }

    /**
     * 指定期間内の所定労働日数を計算します（両端を含む）。
     * 土曜・日曜および holidays マスタに登録された祝日を除外します。
     *
     * @param startDate 開始日
     * @param endDate   終了日
     * @return 所定労働日数
     */
    private long countWorkingDays(LocalDate startDate, LocalDate endDate) {
        Set<LocalDate> holidays = new HashSet<>();
        for (int year = startDate.getYear(); year <= endDate.getYear(); year++) {
            holidays.addAll(holidayService.getHolidaysByYear(year));
        }
        return startDate.datesUntil(endDate.plusDays(1))
                .filter(date -> {
                    DayOfWeek dow = date.getDayOfWeek();
                    return dow != DayOfWeek.SATURDAY
                            && dow != DayOfWeek.SUNDAY
                            && !holidays.contains(date);
                })
                .count();
    }

    /**
     * 休暇1日あたりの消化日数を返します（半休は0.5日、それ以外は1.0日）。
     * 半休は単日申請のみ許可されるため、申請単位の消化日数と一致します。
     *
     * @param leaveDurationType 休暇期間種別（FULL_DAY, AM_HALF, PM_HALF）
     * @return 1日あたりの消化日数
     */
    public BigDecimal calculateDailyConsumedDays(String leaveDurationType) {
        return isHalfDay(leaveDurationType) ? new BigDecimal("0.5") : BigDecimal.ONE;
    }

    private boolean isHalfDay(String leaveDurationType) {
        return "AM_HALF".equals(leaveDurationType) || "PM_HALF".equals(leaveDurationType);
    }

    /**
     * 指定された年の承認済み有給休暇の使用日数を集計します。
     * 半休（AM_HALF/PM_HALF）は0.5日として集計されます。
     *
     * @param userId 対象ユーザーID
     * @param year 集計対象の年（例: 2026）
     * @return 当該年の有給休暇使用日数の合計
     */
    public BigDecimal calculateYearlyUsedPaidLeaveDays(Long userId, int year) {
        return leaveApplicationMapper.countApprovedPaidLeaveDays(userId, year);
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
