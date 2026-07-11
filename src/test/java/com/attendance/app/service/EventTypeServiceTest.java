package com.attendance.app.service;

import com.attendance.app.entity.EventType;
import com.attendance.app.mapper.EventTypeMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EventTypeService")
class EventTypeServiceTest {

    @Mock
    private EventTypeMapper eventTypeMapper;

    @InjectMocks
    private EventTypeService service;

    @Nested
    @DisplayName("getAllActiveEventTypes")
    class GetAllActiveEventTypes {

        @Test
        @DisplayName("有効な勤怠事由の一覧をそのまま返す")
        void returnsActiveEventTypes() {
            EventType type1 = EventType.builder().eventTypeId(1).code("通常").displayName("通常勤務").isActive(true).build();
            EventType type2 = EventType.builder().eventTypeId(2).code("有休").displayName("有給休暇").isActive(true).build();
            when(eventTypeMapper.selectAllActive()).thenReturn(List.of(type1, type2));

            List<EventType> result = service.getAllActiveEventTypes();

            assertThat(result).hasSize(2);
            assertThat(result).extracting(e -> e.getCode()).containsExactly("通常", "有休");
            verify(eventTypeMapper).selectAllActive();
        }
    }
}
