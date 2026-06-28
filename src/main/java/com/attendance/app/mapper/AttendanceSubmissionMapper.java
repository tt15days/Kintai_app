package com.attendance.app.mapper;

import com.attendance.app.entity.AttendanceSubmission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * 月次勤怠申請を永続化する Mapper です。
 */
@Mapper
public interface AttendanceSubmissionMapper {

    /**
     * 申請IDで勤怠申請を取得します。
     *
     * @param submissionId 申請ID
     * @return 勤怠申請情報。存在しない場合は Optional.empty()
     */
    Optional<AttendanceSubmission> selectById(@Param("submissionId") Long submissionId);

    /**
     * 申請IDで勤怠申請を取得し、排他ロックを獲得します。
     *
     * @param submissionId 申請ID
     * @return 勤怠申請情報。存在しない場合は Optional.empty()
     */
    Optional<AttendanceSubmission> selectByIdForUpdate(@Param("submissionId") Long submissionId);

    /**
     * 指定ユーザー・対象月の勤怠申請を取得します。
     *
     * @param userId ユーザーID
     * @param targetYearMonth 対象年月
     * @return 勤怠申請情報。存在しない場合は Optional.empty()
     */
    Optional<AttendanceSubmission> selectByUserAndMonth(
            @Param("userId") Long userId,
            @Param("targetYearMonth") String targetYearMonth);

    /**
     * ステータスで勤怠申請一覧を取得します。
     *
     * @param status ステータス
     * @return 勤怠申請情報のリスト
     */
    List<AttendanceSubmission> selectByStatus(@Param("status") String status);

    /**
     * 指定ユーザーの勤怠申請一覧を取得します。
     *
     * @param userId ユーザーID
     * @return 勤怠申請情報のリスト
     */
    List<AttendanceSubmission> selectByUserId(@Param("userId") Long userId);

    /**
     * 対象年月に一致する勤怠申請一覧を取得します。
     *
     * @param targetYearMonth 対象年月
     * @return 勤怠申請情報のリスト
     */
    List<AttendanceSubmission> selectByTargetYearMonth(@Param("targetYearMonth") String targetYearMonth);

    /**
     * 勤怠申請を登録します。
     *
     * @param submission 登録する勤怠申請情報
     * @return 登録された件数
     */
    int insert(AttendanceSubmission submission);

    /**
     * 勤怠申請を更新します。
     *
     * @param submission 更新する勤怠申請情報
     * @return 更新された件数
     */
    int update(AttendanceSubmission submission);

    /**
     * 指定ユーザー・対象月の勤怠申請を削除します。
     *
     * @param userId ユーザーID
     * @param targetYearMonth 対象年月
     * @return 削除された件数
     */
    int deleteByUserAndMonth(
            @Param("userId") Long userId,
            @Param("targetYearMonth") String targetYearMonth);
}
