package com.mkwang.backend.common.dto;

import com.mkwang.backend.common.sse.SseEventType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SseEvent {

    private SseEventType event;
    private Object data;
}
