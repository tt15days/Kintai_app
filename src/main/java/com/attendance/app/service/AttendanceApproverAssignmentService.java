package com.attendance.app.service;

import com.attendance.app.entity.User;
import com.attendance.app.mapper.AttendanceApproverAssignmentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 勤怠承認者の割り当てと妥当性検証を行うサービスです。
 * 
 * 申請者個人および部署単位での承認者設定に関するロジックを提供します。
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AttendanceApproverAssignmentService {

    private final AttendanceApproverAssignmentMapper assignmentMapper;
    private final UserService userService;

    /**
     * 申請者個人に設定された承認者IDの一覧を取得します。
     *
     * @param applicantUserId 申請者のユーザーID
     * @return 承認者IDのリスト
     */
    public List<Long> getUserApproverIds(Long applicantUserId) {
        return assignmentMapper.selectUserApproverIds(applicantUserId);
    }

    /**
     * 指定された部署に設定された承認者IDの一覧を取得します。
     *
     * @param departmentName 部署名
     * @return 承認者IDのリスト。部署名が空の場合は空のリストを返します。
     */
    public List<Long> getDepartmentApproverIds(String departmentName) {
        if (departmentName == null || departmentName.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return assignmentMapper.selectDepartmentApproverIds(departmentName.trim());
    }

    /**
     * 申請者個人に対する承認者の一覧を設定・更新します。
     * 既存の設定は削除され、新しい設定に置き換わります。
     *
     * @param applicantUserId 申請者のユーザーID
     * @param approverUserIds 新しく設定する承認者IDのリスト
     * @throws IllegalArgumentException 申請者が見つからない場合、または承認者IDが不正な場合
     */
    public void assignUserApprovers(Long applicantUserId, List<Long> approverUserIds) {
        User applicant = userService.getUserById(applicantUserId)
                .orElseThrow(() -> new IllegalArgumentException("申請者ユーザーが見つかりません"));

        Set<Long> normalizedIds = normalizeAndValidateApproverIds(approverUserIds, applicant.getUserId());

        // 申請者単位のadvisory lockを取得し、delete-then-insertを直列化する（TOCTOUレース対策）
        // トランザクション終了時に自動解放される
        assignmentMapper.acquireUserApproverLock(applicantUserId);

        assignmentMapper.deleteUserApprovers(applicantUserId);
        for (Long approverUserId : normalizedIds) {
            assignmentMapper.insertUserApprover(applicantUserId, approverUserId);
        }
    }

    /**
     * 部署に対する承認者の一覧を設定・更新します。
     * 既存の設定は削除され、新しい設定に置き換わります。
     *
     * @param departmentName 部署名
     * @param approverUserIds 新しく設定する承認者IDのリスト
     * @throws IllegalArgumentException 部署名が空の場合、または承認者IDが不正な場合
     */
    public void assignDepartmentApprovers(String departmentName, List<Long> approverUserIds) {
        if (departmentName == null || departmentName.trim().isEmpty()) {
            throw new IllegalArgumentException("部署名が設定されていません");
        }

        Set<Long> normalizedIds = normalizeAndValidateApproverIds(approverUserIds, null);
        String normalizedDepartment = departmentName.trim();

        // 部署単位のadvisory lockを取得し、delete-then-insertを直列化する（TOCTOUレース対策）
        // トランザクション終了時に自動解放される
        assignmentMapper.acquireDepartmentApproverLock(normalizedDepartment);

        assignmentMapper.deleteDepartmentApprovers(normalizedDepartment);
        for (Long approverUserId : normalizedIds) {
            assignmentMapper.insertDepartmentApprover(normalizedDepartment, approverUserId);
        }
    }

    /**
     * 指定された承認者が、申請者個人の承認者として割り当て済みであるか判定します。
     *
     * @param applicantUserId 申請者のユーザーID
     * @param approverUserId 判定対象の承認者ID
     * @return 割り当て済みであれば {@code true}、そうでなければ {@code false}
     */
    public boolean isAssignedForApplicant(Long applicantUserId, Long approverUserId) {
        List<Long> userApprovers = getUserApproverIds(applicantUserId);
        return userApprovers.contains(approverUserId);
    }

    /**
     * 指定された承認者が、部署の承認者として割り当て済みであるか判定します。
     *
     * @param departmentName 部署名
     * @param approverUserId 判定対象の承認者ID
     * @return 割り当て済みであれば {@code true}、そうでなければ {@code false}
     */
    public boolean isAssignedForDepartment(String departmentName, Long approverUserId) {
        List<Long> departmentApprovers = getDepartmentApproverIds(departmentName);
        return departmentApprovers.contains(approverUserId);
    }

    /**
     * 申請者に対して管理者が明示的にアサインした承認者ID一覧（個人アサイン＋部署アサインの合算）を返します。
     * 個人・部署いずれのアサインも1件も無い場合は空リストを返します。呼び出し側は、
     * 空リストの場合のみ勤務クラス一致等の既定ルールにフォールバックしてください
     * （1件でもアサインが存在する場合は、それ以外の承認者を許可してはいけません）。
     *
     * @param applicantUserId 申請者のユーザーID
     * @param applicantClassName 申請者の所属勤務クラス名（部署アサインの判定に使用）
     * @return アサイン済み承認者IDのリスト（アサインが無い場合は空リスト）
     */
    public List<Long> resolveAssignedApproverIds(Long applicantUserId, String applicantClassName) {
        List<Long> userApprovers = getUserApproverIds(applicantUserId);
        List<Long> departmentApprovers = getDepartmentApproverIds(applicantClassName);
        if (userApprovers.isEmpty() && departmentApprovers.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> combined = new LinkedHashSet<>(userApprovers);
        combined.addAll(departmentApprovers);
        return List.copyOf(combined);
    }

    /**
     * 承認者IDリストの正規化（重複排除、null除去）と妥当性検証を行います。
     *
     * @param approverUserIds 検証対象の承認者IDリスト
     * @param applicantUserId 申請者のユーザーID（個人設定の場合）。部署設定の場合は {@code null}
     * @return 正規化された承認者IDのセット
     * @throws IllegalArgumentException 承認者が存在しない、承認権限がない、または申請者自身が設定されている場合
     */
    private Set<Long> normalizeAndValidateApproverIds(List<Long> approverUserIds, Long applicantUserId) {
        if (approverUserIds == null || approverUserIds.isEmpty()) {
            return Collections.emptySet();
        }

        Set<Long> normalized = new LinkedHashSet<>(approverUserIds);
        normalized.remove(null);

        for (Long approverUserId : normalized) {
            User approver = userService.getUserById(approverUserId)
                    .orElseThrow(() -> new IllegalArgumentException("承認者ユーザーが見つかりません: userId=" + approverUserId));
            
            if (!userService.isAttendanceApprover(approver)) {
                throw new IllegalArgumentException("承認権限がないユーザーが含まれています: userId=" + approverUserId);
            }
            
            if (applicantUserId != null && applicantUserId.equals(approverUserId)) {
                throw new IllegalArgumentException("申請者自身を承認者に設定することはできません");
            }
        }

        return normalized;
    }
}
