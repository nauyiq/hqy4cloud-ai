package com.fons.cloud.ai.agent.observability.core;

import com.fons.cloud.ai.agent.observability.model.TraceEvent;
import com.fons.cloud.ai.agent.observability.model.TraceRunContext;

import java.time.Instant;
import java.util.Objects;

/**
 * JSONL 持久化事件信封，为原始 Trace 事件补充存储级元数据。
 *
 * @param eventId 持久化事件唯一标识
 * @param sequence 当前 Trace 内从 1 开始的单调递增序号
 * @param recordedAt 事件实际写入前的记录时间
 * @param context 本次 Agent 执行的稳定上下文
 * @param event 调用方产生的原始 Trace 事件
 * @author hongqy
 */
record TraceEnvelope(
        String eventId,
        long sequence,
        Instant recordedAt,
        TraceRunContext context,
        TraceEvent event) {

    TraceEnvelope {
        if (sequence <= 0) {
            throw new IllegalArgumentException("sequence must be positive");
        }
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(recordedAt, "recordedAt cannot be null");
        Objects.requireNonNull(context, "context cannot be null");
        Objects.requireNonNull(event, "event cannot be null");
    }
}
