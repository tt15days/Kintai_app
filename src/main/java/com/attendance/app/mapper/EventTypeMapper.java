package com.attendance.app.mapper;

import com.attendance.app.entity.EventType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * EventType Mapper - 勤怠事由マスタに関するDB操作
 */
@Mapper
public interface EventTypeMapper {

    /**
     * 有効な事由を表示順で取得
     *
     * @return 有効な勤怠事由のリスト
     */
    List<EventType> selectAllActive();

    /**
     * すべての事由を表示順で取得
     *
     * @return 全勤怠事由のリスト
     */
    List<EventType> selectAll();

    /**
     * IDで事由を取得
     *
     * @param eventTypeId 事由ID
     * @return 勤怠事由。存在しない場合は Optional.empty()
     */
    Optional<EventType> selectById(@Param("eventTypeId") Integer eventTypeId);

    /**
     * コードで事由を取得
     *
     * @param code 事由コード
     * @return 勤怠事由。存在しない場合は Optional.empty()
     */
    Optional<EventType> selectByCode(@Param("code") String code);
}
