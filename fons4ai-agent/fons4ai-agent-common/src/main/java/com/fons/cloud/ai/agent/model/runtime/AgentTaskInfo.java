package com.fons.cloud.ai.agent.model.runtime;

import com.fons.cloud.common.base.exception.SystemIntervalException;
import org.apache.commons.lang3.StringUtils;

/**
 * 精确标识一次运行任务，防止旧运行的迟到终态影响同会话的新运行。
 *
 * @param conversationId 会话标识
 * @param runId 执行唯一标识
 */
public record AgentTaskInfo(String conversationId, String runId) {
    public AgentTaskInfo {
        if (StringUtils.isBlank(conversationId) || StringUtils.isBlank(runId)) {
            throw new SystemIntervalException("conversationId and runId are required");
        }
    }
}
