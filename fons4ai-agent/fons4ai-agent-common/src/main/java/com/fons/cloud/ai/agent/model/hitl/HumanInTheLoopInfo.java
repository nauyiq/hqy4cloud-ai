package com.fons.cloud.ai.agent.model.hitl;

import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

/**
 * 人工交互
 * @author hongqy
 */
@Getter
@ToString
@SuperBuilder
public class HumanInTheLoopInfo implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 本次人工交互的id，默认是UUID
     */
    private String id;

    /**
     * 检查点ID, 对于不需要中断恢复的请求是可以不要求从检查点恢复的， 可以依赖于消息列表等
     */
    private String checkpointId;

    /**
     * 原始的runId
     */
    private String originRunId;

    /**
     * 人工交互的类型
     */
    private HumanInTheLoopKind kind;

    /**
     * 人工交互的数据
     */
    private Map<String, Object> data;



}
