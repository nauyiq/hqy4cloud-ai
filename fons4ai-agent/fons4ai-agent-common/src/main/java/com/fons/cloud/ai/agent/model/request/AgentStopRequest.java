package com.fons.cloud.ai.agent.model.request;

/**
 * 跨实例主动停止命令。
 *
 * @param version 版本号
 * @param conversationId 会话标识
 * @param runId 目标执行唯一标识
 */
public record AgentStopRequest(int version, String conversationId, String runId) {
    public static final int CURRENT_VERSION = 1;

    public String getTaskKey() {
        return conversationId + ":" + runId;
    }

}
