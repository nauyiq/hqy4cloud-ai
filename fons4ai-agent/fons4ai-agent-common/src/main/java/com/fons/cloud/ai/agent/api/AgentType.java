package com.fons.cloud.ai.agent.api;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author hongqy
 */
@Getter
@AllArgsConstructor
public enum AgentType {

    /**
     * React模式的智能体
     */
    REACT("react"),

    /**
     * 可驾驭模式Agent
     */
    HARNESS("harness"),

    /**
     * 自定义模式的智能体
     */
    CUSTOM("custom"),

    ;

    private final String type;



}
