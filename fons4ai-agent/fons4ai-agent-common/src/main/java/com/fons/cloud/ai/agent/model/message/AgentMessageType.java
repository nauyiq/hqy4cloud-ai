package com.fons.cloud.ai.agent.model.message;

/**
 * 框架无关的会话消息角色，由具体模型适配层转换为供应商消息类型。
 * @author hongqy
 */
public enum AgentMessageType {
    USER,

    ASSISTANT,

    SYSTEM,

    TOOL
}
