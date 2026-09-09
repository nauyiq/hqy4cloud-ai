package com.fons.cloud.ai.agent.model.request;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import reactor.core.Disposable;

/**
 * @author hongqy
 */
@Getter
@Setter
@SuperBuilder
@ToString
public class AgentTaskBingRequest extends BaseAgentTaskRequest {

    /**
     * 本地任务取消权柄。该对象可以是底层订阅，也可以是 RuntimeActions 提供的取消代理。
     */
    private Disposable disposable;

}
