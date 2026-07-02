package com.attendance.app.service;

import com.attendance.app.entity.AuditEventType;
import com.attendance.app.entity.User;
import com.attendance.app.entity.UserRole;
import com.attendance.app.mapper.SystemSettingMapper;
import com.attendance.app.mapper.UserMapper;
import com.attendance.app.mapper.WorkScheduleClassMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * ユーザー管理に関する業務ロジックを提供するサービスです。
 *
 * タイムゾーン対応: すべてのタイムスタンプは Instant 型（UTC）で管理
 * フロントエンドで日本時間に変換する
 *
 * <p>主要な更新操作（作成・更新・削除・パスワード初期化）は呼び出し元から actorUserId を受取り、
 * {@link AuditLogService} を通じて操作監査ログを記録します。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private static final String USER_NOT_FOUND_PREFIX = "ユーザーが見つかりません: userId=";
    private static final BigDecimal DEFAULT_PAID_LEAVE_DAYS = new BigDecimal("10.0");
    /** デフォルトの有給残日数上限（ユーザー別 max_paid_leave_days が未設定の場合に使用） */
    private static final int DEFAULT_MAX_PAID_LEAVE_DAYS = 40;
    /** デフォルトの初回有給付与日数 */
    private static final int DEFAULT_ANNUAL_LEAVE_GRANT_DAYS = 10;
    /** パスワードポリシー: 英字と数字をそれぞれ1文字以上含む8文字以上 */
    private static final java.util.regex.Pattern PASSWORD_PATTERN =
            java.util.regex.Pattern.compile("^(?=.*[a-zA-Z])(?=.*[0-9]).{8,}$");

    private final UserMapper userMapper;
    private final WorkScheduleClassMapper workScheduleClassMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final SystemSettingMapper systemSettingMapper;

    /**
     * 指定されたユーザーIDでユーザー情報を取得します。
     *
     * @param userId ユーザーID
     * @return ユーザー情報のOptional
     */
    public Optional<User> getUserById(Long userId) {
        return userMapper.selectById(userId);
    }

    /**
     * 指定されたメールアドレスでユーザー情報を取得します。
     *
     * @param email メールアドレス
     * @return ユーザー情報のOptional
     */
    public Optional<User> getUserByEmail(String email) {
        return userMapper.selectByEmail(email);
    }

    /**
     * アクティブなユーザー一覧を取得します。
     *
     * @return アクティブユーザーのリスト
     */
    public List<User> getActiveUsers() {
        return userMapper.selectAllActive();
    }

    /**
     * すべてのユーザー一覧を取得します。
     *
     * @return 全ユーザーのリスト
     */
    public List<User> getAllUsers() {
        return userMapper.selectAll();
    }

    /**
     * 指定されたロールを持つユーザー一覧を取得します。
     *
     * @param role ユーザーロール
     * @return 該当ロールのユーザーリスト
     */
    public List<User> getUsersByRole(UserRole role) {
        return userMapper.selectByRole(role.name());
    }

    /**
     * ユーザーを作成する。
     *
     * @param email       メールアドレス（重複不可）
     * @param password    初期パスワード（BCrypt でエンコードして保存）
     * @param fullName    氏名
     * @param role        ロール
     * @param actorUserId 操作を実行した管理者のユーザー ID（監査ログ用）
     * @return 作成したユーザーエンティティ
     */
    public User createUser(String email, String password, String fullName, UserRole role, java.time.LocalDate hireDate, Long actorUserId) {
        validatePassword(password);
        if (userMapper.existsByEmail(email)) {
            throw new IllegalArgumentException("このメールアドレスは既に登録されています: " + email);
        }

        Long nextUserId = userMapper.selectNextUserId();
        String prefix = systemSettingMapper.selectValueByKey("EMP_NO_PREFIX");
        if (prefix == null) {
            prefix = "";
        }
        String empNo = prefix + String.format("%03d", nextUserId);

        User user = User.builder()
                .empNo(empNo)
                .email(email)
                .password(passwordEncoder.encode(password))
                .passwordResetRequired(false)
                .fullName(fullName)
                .paidLeaveDays(DEFAULT_PAID_LEAVE_DAYS)
                .userRole(role)
                .canApproveAttendance(false)
                .isActive(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .hireDate(hireDate)
                .build();

        userMapper.insert(user);
        log.info("ユーザーを作成しました: fullName={}, role={}", fullName, role);

        auditLogService.recordUserEvent(
                AuditEventType.USER_CREATED,
                actorUserId,
                user.getUserId(),
                "ロール: " + role.name());
        return user;
    }

    /**
     * ユーザー情報を更新する。
     *
     * @param userId               更新対象のユーザー ID
     * @param email                新しいメールアドレス
     * @param fullName             新しい氏名
     * @param role                 新しいロール
     * @param positionTitle        役職名称（任意）
     * @param phoneNumber          電話番号（任意）
     * @param className            勤務クラス名（任意）
     * @param paidLeaveDays        有給休暨残日数
     * @param notes                備考（任意）
     * @param canApproveAttendance 勤怠承認権限フラグ
     * @param actorUserId          操作を実行したユーザー ID（監査ログ用）
     * @return 更新後のユーザーエンティティ
     */
    public User updateUser(
            Long userId,
            String email,
            String fullName,
            UserRole role,
            String positionTitle,
            String phoneNumber,
            String className,
            BigDecimal paidLeaveDays,
            String notes,
            boolean canApproveAttendance,
            java.time.LocalDate hireDate,
            boolean isActive,
            Long actorUserId) {
        User user = findUserOrThrow(userId);

        if (userId.equals(actorUserId) && user.getUserRole() == UserRole.ADMIN && role == UserRole.USER) {
            throw new IllegalArgumentException("自分自身の管理者ロールを一般ユーザーに降格することはできません。");
        }

        if (userId.equals(actorUserId) && !isActive) {
            throw new IllegalArgumentException("自分自身のアカウントを無効にすることはできません。");
        }

        user.setEmail(email);
        user.setFullName(fullName);
        user.setUserRole(role);
        user.setPositionTitle(normalizeOptionalText(positionTitle));
        user.setPhoneNumber(normalizeOptionalText(phoneNumber));
        user.setClassName(validateWorkScheduleClassName(className));
        user.setPaidLeaveDays(paidLeaveDays != null ? paidLeaveDays : DEFAULT_PAID_LEAVE_DAYS);
        user.setNotes(normalizeOptionalText(notes));
        user.setCanApproveAttendance(role == UserRole.USER && canApproveAttendance);
        user.setHireDate(hireDate);
        user.setIsActive(isActive);
        user.setUpdatedAt(Instant.now());

        userMapper.update(user);
        log.info("ユーザー情報を更新しました: userId={}", userId);

        auditLogService.recordUserEvent(
                AuditEventType.USER_UPDATED,
                actorUserId,
                userId,
                "ロール: " + role.name());
        return user;
    }

    /**
     * ユーザー本人のパスワードを変更します。
     * 現在のパスワードが一致しない場合は例外を送出します。
     *
     * @param userId ユーザーID
     * @param oldPassword 現在のパスワード
     * @param newPassword 新しいパスワード
     */
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        validatePassword(newPassword);
        User user = findUserOrThrow(userId);

        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new IllegalArgumentException("現在のパスワードが正しくありません");
        }

        userMapper.updatePassword(userId, passwordEncoder.encode(newPassword), false);
        log.info("パスワードを変更しました: userId={}", userId);
    }

    /**
     * 管理者によるパスワード初期化。
     *
     * @param userId      初期化対象のユーザー ID
     * @param actorUserId 操作を実行した管理者のユーザー ID（監査ログ用）
     * @return 初期パスワード文字列（画面表示用）
     */
    public String resetPasswordByAdmin(Long userId, Long actorUserId) {
        findUserOrThrow(userId);
        String randomPassword = generateRandomPassword();
        userMapper.updatePassword(userId, passwordEncoder.encode(randomPassword), true);
        log.info("管理者がパスワードを初期化しました: userId={}", userId);

        auditLogService.recordUserEvent(
                AuditEventType.USER_PASSWORD_RESET,
                actorUserId,
                userId,
                null);
        return randomPassword;
    }

    /**
     * ユーザーを削除（ソフトデリート）する。
     *
     * @param userId      削除対象のユーザー ID
     * @param actorUserId 操作を実行した管理者のユーザー ID（監査ログ用）
     */
    public void deleteUser(Long userId, Long actorUserId) {
        if (userId.equals(actorUserId)) {
            throw new IllegalArgumentException("自分自身を削除することはできません。");
        }
        userMapper.softDeleteById(userId);
        log.info("ユーザーを削除しました: userId={}", userId);

        auditLogService.recordUserEvent(
                AuditEventType.USER_DELETED,
                actorUserId,
                userId,
                null);
    }

    /**
     * ユーザーをハードデリート（物理削除）します。
     *
     * @param userId 削除対象のユーザーID
     */
    public void hardDeleteUser(Long userId) {
        userMapper.deleteById(userId);
        log.info("ユーザーをハードデリートしました: userId={}", userId);
    }

    /**
     * ユーザーの最終ログイン日時を現在時刻に更新します。
     *
     * @param userId 対象ユーザーID
     */
    public void updateLastLogin(Long userId) {
        findUserOrThrow(userId);
        userMapper.updateLastLogin(userId, Instant.now());
    }

    /**
     * 登録されている全ユーザーの総数を取得します。
     *
     * @return ユーザー総数
     */
    public long getUserCount() {
        return userMapper.countAll();
    }

    /**
     * メールアドレスとパスワードを使用してユーザーを認証します。
     *
     * @param email メールアドレス
     * @param password パスワード
     * @return 認証成功時はユーザー情報のOptional、失敗時は空のOptional
     */
    public Optional<User> authenticate(String email, String password) {
        Optional<User> user = userMapper.selectByEmail(email);
        if (user.isEmpty()) {
            return Optional.empty();
        }

        log.debug("Stored password: [REDACTED]");

        if (user.get().getIsActive() && passwordEncoder.matches(password, user.get().getPassword())) {
            updateLastLogin(user.get().getUserId());
            return user;
        }
        return Optional.empty();
    }

    /**
     * ユーザーIDからユーザーを取得し、存在しない場合は例外を送出します。
     *
     * @param userId ユーザーID
     * @return ユーザー
     * @throws IllegalArgumentException ユーザーが存在しない場合
     */
    private User findUserOrThrow(Long userId) {
        return userMapper.selectById(userId)
                .orElseThrow(() -> new IllegalArgumentException(USER_NOT_FOUND_PREFIX + userId));
    }

    /**
     * ユーザーIDからユーザーを取得し（排他ロックを獲得）、存在しない場合は例外を送出します。
     *
     * @param userId ユーザーID
     * @return ユーザー
     * @throws IllegalArgumentException ユーザーが存在しない場合
     */
    private User findUserForUpdateOrThrow(Long userId) {
        return userMapper.selectByIdForUpdate(userId)
                .orElseThrow(() -> new IllegalArgumentException(USER_NOT_FOUND_PREFIX + userId));
    }

    /**
     * ランダムなパスワードを生成します。
     * 英大文字、英小文字、数字、記号をそれぞれ最低1文字含む12文字の文字列です。
     */
    private String generateRandomPassword() {
        String upper = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String lower = "abcdefghijklmnopqrstuvwxyz";
        String digits = "0123456789";
        String specials = "!@#$%^&*()_+-=[]{}|;:,.<>?";
        String allChars = upper + lower + digits + specials;
        
        java.security.SecureRandom random = new java.security.SecureRandom();
        StringBuilder password = new StringBuilder();
        
        password.append(upper.charAt(random.nextInt(upper.length())));
        password.append(lower.charAt(random.nextInt(lower.length())));
        password.append(digits.charAt(random.nextInt(digits.length())));
        password.append(specials.charAt(random.nextInt(specials.length())));
        
        for (int i = 4; i < 12; i++) {
            password.append(allChars.charAt(random.nextInt(allChars.length())));
        }
        
        List<Character> charList = new ArrayList<>();
        for (char c : password.toString().toCharArray()) {
            charList.add(c);
        }
        Collections.shuffle(charList, random);
        
        StringBuilder shuffled = new StringBuilder();
        for (char c : charList) {
            shuffled.append(c);
        }
        return shuffled.toString();
    }

    /**
     * \u30d1\u30b9\u30ef\u30fc\u30c9\u30dd\u30ea\u30b7\u30fc\u691c\u8a3c: \u82f1\u5b57\u3068\u6570\u5b57\u3092\u305d\u308c\u305e\u308c1\u6587\u5b57\u4ee5\u4e0a\u542b\u30808\u6587\u5b57\u4ee5\u4e0a\u3067\u3042\u308b\u3053\u3068\u3092\u691c\u8a3c\u3059\u308b\u3002
     *
     * @param password \u691c\u8a3c\u5bfe\u8c61\u30d1\u30b9\u30ef\u30fc\u30c9
     * @throws IllegalArgumentException \u30dd\u30ea\u30b7\u30fc\u4e0d\u6e80\u306e\u5834\u5408
     */
    private void validatePassword(String password) {
        if (password == null || !PASSWORD_PATTERN.matcher(password).matches()) {
            throw new IllegalArgumentException(
                    "\u30d1\u30b9\u30ef\u30fc\u30c9\u306f\u82f1\u5b57\u3068\u6570\u5b57\u3092\u305d\u308c\u305e\u308c1\u6587\u5b57\u4ee5\u4e0a\u542b\u30808\u6587\u5b57\u4ee5\u4e0a\u3067\u5165\u529b\u3057\u3066\u304f\u3060\u3055\u3044");
        }
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 指定ユーザーが勤怠承認権限を持つか判定します。
     * 管理者は常に true、それ以外は有効な一般ユーザーかつ承認フラグが必要です。
     */
    public boolean isAttendanceApprover(User user) {
        if (user == null) {
            return false;
        }
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            return false;
        }

        if (user.getUserRole() == UserRole.ADMIN) {
            return true;
        }

        return user.getUserRole() == UserRole.USER
                && Boolean.TRUE.equals(user.getCanApproveAttendance());
    }

    /**
     * 勤怠承認者として選択可能な有効ユーザー一覧を返します。
     */
    public List<User> getAttendanceApproverCandidates() {
        return getActiveUsers().stream()
                .filter(u -> this.isAttendanceApprover(u))
                .toList();
    }

    /**
     * ユーザーの勤務クラスを更新します。
     * className に null を渡すと勤務クラスの割当を解除します。
     */
    public void updateWorkScheduleClass(Long userId, String className) {
        findUserOrThrow(userId);
        String normalizedClassName = validateWorkScheduleClassName(className);
        userMapper.updateWorkScheduleClass(userId, normalizedClassName);
        log.info("勤務クラスを更新しました: userId={}, className={}", userId, normalizedClassName);
    }

    private String validateWorkScheduleClassName(String className) {
        String normalizedClassName = normalizeOptionalText(className);
        if (normalizedClassName == null) {
            return null;
        }
        if (workScheduleClassMapper.selectByName(normalizedClassName).isEmpty()) {
            throw new IllegalArgumentException("指定された勤務クラスが存在しません: " + normalizedClassName);
        }
        return normalizedClassName;
    }

    /**
     * ユーザーに年次有給休暇を付与します。
     * ユーザー別の次回付与日数を現在の残日数へ加算し、上限日数で打ち止めにします。
     * 付与後は次年度用の付与日数も自動更新します。
     *
     * @param userId 付与対象のユーザーID
     */
    @Transactional
    public void grantAnnualPaidLeave(Long userId) {
        User user = findUserForUpdateOrThrow(userId);

        int grantDays = user.getAnnualLeaveGrantDays() != null
                ? user.getAnnualLeaveGrantDays() : DEFAULT_ANNUAL_LEAVE_GRANT_DAYS;
        BigDecimal increment = user.getAnnualLeaveIncrement() != null
                ? user.getAnnualLeaveIncrement() : BigDecimal.ONE;
        int maxDays = user.getMaxPaidLeaveDays() != null
                ? user.getMaxPaidLeaveDays() : DEFAULT_MAX_PAID_LEAVE_DAYS;

        BigDecimal current = user.getPaidLeaveDays() != null ? user.getPaidLeaveDays() : BigDecimal.ZERO;
        BigDecimal newDays = current.add(BigDecimal.valueOf(grantDays))
                .min(BigDecimal.valueOf(maxDays));

        userMapper.updatePaidLeaveDays(userId, newDays);

        // 来年度向けに annual_leave_grant_days を自動増加（20日上限）
        int nextGrantDays = (int) Math.min(grantDays + increment.doubleValue(), 20.0);
        userMapper.updatePaidLeaveSettings(userId, nextGrantDays, increment, maxDays);

        log.info("有給付与: userId={}, grantDays={}, before={}, after={}, nextGrantDays={}",
                userId, grantDays, current, newDays, nextGrantDays);
    }

    /**
     * ユーザー別年次有給設定を管理者が更新します。
     *
     * @param userId               対象ユーザーID
     * @param annualLeaveGrantDays 次回付与日数（0〜30）
     * @param annualLeaveIncrement 毎年自動増加量（0.0〜30.0）
     * @param maxPaidLeaveDays    有給残日数の上限（1〜100）
     */
    @Transactional
    public void updatePaidLeaveSettings(Long userId, int annualLeaveGrantDays,
                                        BigDecimal annualLeaveIncrement, int maxPaidLeaveDays) {
        findUserOrThrow(userId);
        if (annualLeaveGrantDays < 0 || annualLeaveGrantDays > 30) {
            throw new IllegalArgumentException("次回付与日数は0〜30の範囲で設定してください");
        }
        if (annualLeaveIncrement == null
                || annualLeaveIncrement.compareTo(BigDecimal.ZERO) < 0
                || annualLeaveIncrement.compareTo(BigDecimal.valueOf(30)) > 0) {
            throw new IllegalArgumentException("年間増加量は0.0〜30.0の範囲で設定してください");
        }
        if (maxPaidLeaveDays < 1 || maxPaidLeaveDays > 100) {
            throw new IllegalArgumentException("最大有給日数は1〜100の範囲で設定してください");
        }
        userMapper.updatePaidLeaveSettings(userId, annualLeaveGrantDays, annualLeaveIncrement, maxPaidLeaveDays);
        log.info("ユーザー別有給設定を更新: userId={}, grantDays={}, increment={}, maxDays={}",
                userId, annualLeaveGrantDays, annualLeaveIncrement, maxPaidLeaveDays);
    }
}