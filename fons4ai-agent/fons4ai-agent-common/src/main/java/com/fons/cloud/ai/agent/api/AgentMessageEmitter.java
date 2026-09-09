package com.fons.cloud.ai.agent.api;

import com.fons.cloud.ai.agent.model.message.MessageContentType;

/**
 * Agent 消息发送端口。
 *
 * <p>向扩展组件开放受限的消息发送能力，避免其直接操作事件流、完成结果或运行时资源。</p>
 *
 * @author hongqy
 */
@FunctionalInterface
public interface AgentMessageEmitter {

    /**
     * 发送一条 Agent 消息。
     *
     * @param content 消息内容
     * @param type 消息类型
     */
    void emit(String content, MessageContentType type);
}
