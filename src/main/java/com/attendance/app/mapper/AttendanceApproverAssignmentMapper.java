package com.attendance.app.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 勤怠承認者の個人別・部署別割当を操作する Mapper です。
 */
@Mapper
public interface AttendanceApproverAssignmentMapper {

    /**
     * 指定申請者に個別設定された承認者ID一覧を取得します。
     *
     * @param applicantUserId 申請者ユーザーID
     * @return 承認者ユーザーIDのリスト
     */
    List<Long> selectUserApproverIds(@Param("applicantUserId") Long applicantUserId);

    /**
     * 指定申請者の個別承認者設定を全削除します。
     *
     * @param applicantUserId 申請者ユーザーID
     * @return 削除された件数
     */
    int deleteUserApprovers(@Param("applicantUserId") Long applicantUserId);

    /**
     * 指定申請者に対する個別承認者を追加します。
     *
     * @param applicantUserId 申請者ユーザーID
     * @param approverUserId 承認者ユーザーID
     * @return 追加された件数
     */
    int insertUserApprover(
            @Param("applicantUserId") Long applicantUserId,
            @Param("approverUserId") Long approverUserId);

    /**
     * 指定部署に設定された承認者ID一覧を取得します。
     *
     * @param departmentName 部署名
     * @return 承認者ユーザーIDのリスト
     */
    List<Long> selectDepartmentApproverIds(@Param("departmentName") String departmentName);

    /**
     * 指定部署の承認者設定を全削除します。
     *
     * @param departmentName 部署名
     * @return 削除された件数
     */
    int deleteDepartmentApprovers(@Param("departmentName") String departmentName);

    /**
     * 部署名変更に追従して承認者設定の部署名を更新します。
     *
     * @param oldDepartmentName 変更前の部署名
     * @param newDepartmentName 変更後の部署名
     * @return 更新された件数
     */
    int replaceDepartmentName(
            @Param("oldDepartmentName") String oldDepartmentName,
            @Param("newDepartmentName") String newDepartmentName);

    /**
     * 指定部署に対する承認者を追加します。
     *
     * @param departmentName 部署名
     * @param approverUserId 承認者ユーザーID
     * @return 追加された件数
     */
    int insertDepartmentApprover(
            @Param("departmentName") String departmentName,
            @Param("approverUserId") Long approverUserId);

    /**
     * 指定された承認者IDを持つ個人別承認者設定を削除します。
     */
    int deleteUserApproverByApprover(@Param("approverUserId") Long approverUserId);

    /**
     * 指定された承認者IDを持つ部署別承認者設定を削除します。
     */
    int deleteDepartmentApproverByApprover(@Param("approverUserId") Long approverUserId);

    /**
     * 申請者単位のトランザクションスコープ advisory lock を取得します。
     * 個人別承認者設定の delete-then-insert（TOCTOUレース）を直列化するために、更新前に呼び出します。
     * トランザクション終了時に自動的に解放されます。
     *
     * @param applicantUserId 対象の申請者ユーザーID
     */
    void acquireUserApproverLock(@Param("applicantUserId") Long applicantUserId);

    /**
     * 部署単位のトランザクションスコープ advisory lock を取得します。
     * 部署別承認者設定の delete-then-insert（TOCTOUレース）を直列化するために、更新前に呼び出します。
     * 部署名は安定したハッシュ（PostgreSQLの hashtext）でキー化します。
     * トランザクション終了時に自動的に解放されます。
     *
     * @param departmentName 対象の部署名
     */
    void acquireDepartmentApproverLock(@Param("departmentName") String departmentName);
}
