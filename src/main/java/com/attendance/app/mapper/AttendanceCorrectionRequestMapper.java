package com.attendance.app.mapper;

import com.attendance.app.entity.AttendanceCorrectionRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * 勤怠修正申請を永続化する Mapper です。
 */
@Mapper
public interface AttendanceCorrectionRequestMapper {

    /**
     * 申請IDで修正申請を取得します。
     *
     * @param requestId 修正申請ID
     * @return 修正申請情報。存在しない場合は Optional.empty()
     */
    Optional<AttendanceCorrectionRequest> selectById(@Param("requestId") Long requestId);

    /**
     * 申請IDで修正申請を取得し、排他ロックを獲得します。
     *
     * @param requestId 修正申請ID
     * @return 修正申請情報。存在しない場合は Optional.empty()
     */
    Optional<AttendanceCorrectionRequest> selectByIdForUpdate(@Param("requestId") Long requestId);

    /**
     * ユーザー単位で修正申請一覧を取得します。
     *
     * @param userId ユーザーID
     * @return 修正申請情報のリスト
     */
    List<AttendanceCorrectionRequest> selectByUserId(@Param("userId") Long userId);

    /**
     * 指定ユーザー・対象月の修正申請一覧を取得します。
     *
     * @param userId ユーザーID
     * @param targetYearMonth 対象年月
     * @return 修正申請情報のリスト
     */
    List<AttendanceCorrectionRequest> selectByUserAndMonth(
            @Param("userId") Long userId,
            @Param("targetYearMonth") String targetYearMonth);

    /**
     * ステータス単位で修正申請一覧を取得します。
     *
     * @param status ステータス
     * @return 修正申請情報のリスト
     */
    List<AttendanceCorrectionRequest> selectByStatus(@Param("status") String status);

    /**
     * 修正申請を登録します。
     *
     * @param request 修正申請情報
     * @return 登録された件数
     */
    int insert(AttendanceCorrectionRequest request);

    /**
     * 修正申請を更新します。
     *
     * @param request 修正申請情報
     * @return 更新された件数
     */
    int update(AttendanceCorrectionRequest request);
}
