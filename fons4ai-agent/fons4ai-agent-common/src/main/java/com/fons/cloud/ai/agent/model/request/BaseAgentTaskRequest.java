package com.fons.cloud.ai.agent.model.request;

import com.fons.cloud.common.request.BaseRequest;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * @author hongqy
 */
@Getter
@Setter
@SuperBuilder
@ToString
public class BaseAgentTaskRequest extends BaseRequest {

    /**
     * 运行ID
     */
    private String runId;

    /**
     * 会话ID
     */
    private String conversationId;


    /**
     * 获取精确运行标识。会话互斥由 conversationId 单独承担，该标识用于绑定、取消和释放校验。
     */
    public String getTaskId() {
        return conversationId + ":" + runId;
    }

}
