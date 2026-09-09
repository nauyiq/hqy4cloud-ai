package com.fons.cloud.ai.agent.infrastructure.handler;

import com.fons.cloud.ai.agent.api.AgentMessageEmitter;
import com.fons.cloud.ai.agent.api.AgentToolResultHandler;
import com.fons.cloud.ai.agent.model.runtime.AgentRunContext;
import com.fons.cloud.ai.tool.common.model.AgentToolResult;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Objects;

/**
 * 组合式 Agent 工具结果处理器。
 *
 * <p>按照注册顺序调用所有支持当前结果的处理器。单个处理器失败不会阻断后续处理器，
 * 也不会反向影响工具与 LLM 的主执行链路。</p>
 *
 * @author hongqy
 */
@Slf4j
public final class CompositeAgentToolResultHandler implements AgentToolResultHandler {

    private final List<AgentToolResultHandler> handlers;

    public CompositeAgentToolResultHandler(List<AgentToolResultHandler> handlers) {
        this.handlers = handlers == null
                ? List.of()
                : handlers.stream().filter(Objects::nonNull).toList();
    }

    @Override
    public void handle(AgentRunContext context,
                       AgentToolResult result,
                       AgentMessageEmitter emitter) {
        for (AgentToolResultHandler handler : handlers) {
            try {
                if (handler.supports(result)) {
                    handler.handle(context, result, emitter);
                }
            } catch (RuntimeException exception) {
                log.warn("Failed to handle tool result, handler:{}, toolName:{}",
                        handler.getClass().getName(), result.toolName(), exception);
            }
        }
    }
}
