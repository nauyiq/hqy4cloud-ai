package com.fons.cloud.ai.agent.model.request;

import com.fons.cloud.ai.agent.api.AgentType;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * Agent任务注册请求
 * @author hongqy
 */
@Getter
@Setter
@ToString
@SuperBuilder
public class AgentTaskRegisterRequest extends BaseAgentTaskRequest {

    /**
     * Agent类型
     */
    private AgentType agentType;

    public static AgentTaskRegisterRequest create(String runId, String conversationId, AgentType agentType) {
        return AgentTaskRegisterRequest.builder()
                .agentType(agentType)
                .runId(runId)
                .conversationId(conversationId)
                .build();
    }


}
