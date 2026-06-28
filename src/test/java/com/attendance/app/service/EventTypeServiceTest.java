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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
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

    @Nested
    @DisplayName("getDefaultEventTypeId")
    class GetDefaultEventTypeId {

        @Test
        @DisplayName("デフォルトコード通常が存在する場合はそのIDを返す")
        void defaultCodeExists_returnsId() {
            EventType defaultType = EventType.builder().eventTypeId(10).code("通常").displayName("通常勤務").build();
            when(eventTypeMapper.selectByCode("通常")).thenReturn(Optional.of(defaultType));

            Integer result = service.getDefaultEventTypeId();

            assertThat(result).isEqualTo(10);
            verify(eventTypeMapper).selectByCode("通常");
        }

        @Test
        @DisplayName("デフォルトコード通常が存在しない場合はnullを返す")
        void defaultCodeNotExists_returnsNull() {
            when(eventTypeMapper.selectByCode("通常")).thenReturn(Optional.empty());

            Integer result = service.getDefaultEventTypeId();

            assertThat(result).isNull();
            verify(eventTypeMapper).selectByCode("通常");
        }
    }

    @Nested
    @DisplayName("getEventTypeById")
    class GetEventTypeById {

        @Test
        @DisplayName("引数がnullの場合はMapperを呼ばずにemptyを返す")
        void nullId_returnsEmpty() {
            Optional<EventType> result = service.getEventTypeById(null);

            assertThat(result).isEmpty();
            verify(eventTypeMapper, never()).selectById(anyInt());
        }

        @Test
        @DisplayName("存在するIDの場合は対応する事由を返す")
        void existingId_returnsEventType() {
            EventType type = EventType.builder().eventTypeId(1).code("通常").build();
            when(eventTypeMapper.selectById(1)).thenReturn(Optional.of(type));

            Optional<EventType> result = service.getEventTypeById(1);

            assertThat(result).isPresent().contains(type);
            verify(eventTypeMapper).selectById(1);
        }

        @Test
        @DisplayName("存在しないIDの場合はemptyを返す")
        void notFoundId_returnsEmpty() {
            when(eventTypeMapper.selectById(99)).thenReturn(Optional.empty());

            Optional<EventType> result = service.getEventTypeById(99);

            assertThat(result).isEmpty();
            verify(eventTypeMapper).selectById(99);
        }
    }
}
