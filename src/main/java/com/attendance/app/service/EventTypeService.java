package com.attendance.app.service;

import com.attendance.app.entity.EventType;
import com.attendance.app.mapper.EventTypeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 勤怠事由マスタの業務ロジックを提供するサービスです。
 */
@Service
@RequiredArgsConstructor
public class EventTypeService {

    private static final String DEFAULT_EVENT_CODE = "通常";

    private final EventTypeMapper eventTypeMapper;

    /**
     * 有効な勤怠事由の一覧を取得します。
     *
     * @return 有効な勤怠事由のリスト（表示順）
     */
    public List<EventType> getAllActiveEventTypes() {
        return eventTypeMapper.selectAllActive();
    }

    /**
     * デフォルト事由（通常）のIDを取得します。
     *
     * @return デフォルト事由のID。存在しない場合は null
     */
    public Integer getDefaultEventTypeId() {
        return eventTypeMapper.selectByCode(DEFAULT_EVENT_CODE)
                .map(e -> e.getEventTypeId())
                .orElse(null);
    }

    /**
     * IDで事由を取得します。
     *
     * @param eventTypeId 事由ID
     * @return 勤怠事由のOptional
     */
    public Optional<EventType> getEventTypeById(Integer eventTypeId) {
        if (eventTypeId == null) {
            return Optional.empty();
        }
        return eventTypeMapper.selectById(eventTypeId);
    }
}
