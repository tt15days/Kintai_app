package com.attendance.app.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * User Entity - ユーザー情報
 *
 * タイムゾーン対応: すべてのタイムスタンプは Instant 型（UTC）で管理
 * - createdAt: ユーザー作成日時（UTC、TIMESTAMP WITH TIME ZONE）
 * - updatedAt: ユーザー更新日時（UTC、TIMESTAMP WITH TIME ZONE）
 * - lastLoginAt: 最後のログイン日時（UTC、TIMESTAMP WITH TIME ZONE）
 *
 * フロントエンド表示時は JavaScript で日本時間に変換
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
    /** ユーザーID（PK） */
    private Long userId;
    /** 社員番号（企業向け） */
    private String empNo;
    /** 部署名（企業向け） */
    private String department;
    /** 雇用形態（企業向け） */
    private String employmentType;
    /** メールアドレス */
    private String email;
    /** パスワード（ハッシュ化済み） */
    private String password;
    /** 初回ログイン等のパスワードリセット要求フラグ */
    private Boolean passwordResetRequired;
    /** 氏名 */
    private String fullName;
    /** 役職名 */
    private String positionTitle;
    /** 電話番号 */
    private String phoneNumber;
    /** 所属クラス名（勤務形態などの区分用） */
    private String className;
    /** 有給休暇の残日数 */
    private BigDecimal paidLeaveDays;
    /** 備考・特記事項 */
    private String notes;
    /** ユーザーロール（管理者・一般ユーザー） */
    private UserRole userRole;
    /** 勤怠の承認権限の有無 */
    private Boolean canApproveAttendance;
    /** アカウントの有効・無効状態 */
    private Boolean isActive;
    /** 廃止日時（UTC） */
    private Instant deletedAt;
    /** 作成日時（UTC） */
    private Instant createdAt;
    /** 更新日時（UTC） */
    private Instant updatedAt;
    /** 最終ログイン日時（UTC） */
    private Instant lastLoginAt;
    /** 次回年次有給付与日数（管理者が変更可、付与後に自動増加） */
    private Integer annualLeaveGrantDays;
    /** 毎年の付与日数自動増加量（デフォルト 1.0） */
    private BigDecimal annualLeaveIncrement;
    /** 有給残日数の上限（ユーザー別設定、デフォルト 40） */
    private Integer maxPaidLeaveDays;
    /** ログイン連続失敗回数 */
    private Integer failedLoginCount;
    /** 一時ロック解除日時（NULLは一時ロックなし） */
    private Instant lockedUntil;
    /** アカウント永久ロックフラグ */
    private Boolean accountLocked;
    /** 入社日 */
    private java.time.LocalDate hireDate;
}