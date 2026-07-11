package com.attendance.app.service;

import com.attendance.app.entity.EventType;
import com.attendance.app.mapper.EventTypeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 勤怠事由マスタの業務ロジックを提供するサービスです。
 */
@Service
@RequiredArgsConstructor
public class EventTypeService {

    private final EventTypeMapper eventTypeMapper;

    /**
     * 有効な勤怠事由の一覧を取得します。
     *
     * @return 有効な勤怠事由のリスト（表示順）
     */
    @org.springframework.cache.annotation.Cacheable("eventTypes")
    public List<EventType> getAllActiveEventTypes() {
        return eventTypeMapper.selectAllActive();
    }
}
