package com.fons.cloud.ai.agent.api;

import com.fons.cloud.ai.agent.model.runtime.AgentRunContext;
import com.fons.cloud.ai.tool.common.model.AgentToolResult;

/**
 * Agent 工具解析结果处理器。
 *
 * <p>只消费已经解析成功的工具结果，不负责工具解析、Agent 状态流转或运行时资源释放。</p>
 *
 * @author hongqy
 */
@FunctionalInterface
public interface AgentToolResultHandler {

    /**
     * 判断是否处理当前工具结果。
     *
     * @param result 已解析成功的工具结果
     * @return 是否处理
     */
    default boolean supports(AgentToolResult result) {
        return true;
    }

    /**
     * 消费已经解析成功的工具结果。
     *
     * @param context 当前 Run 上下文
     * @param result 已解析成功的工具结果
     * @param emitter 受限的 Agent 消息发送端口
     */
    void handle(AgentRunContext context,
                AgentToolResult result,
                AgentMessageEmitter emitter);

    /**
     * 默认空处理器。
     *
     * @return 空处理器
     */
    static AgentToolResultHandler noop() {
        return (context, result, emitter) -> {
        };
    }
}
