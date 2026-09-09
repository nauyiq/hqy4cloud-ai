package com.fons.cloud.ai.agent.model.response;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.io.Serial;
import java.io.Serializable;
import java.util.Set;

/**
 * Agent结束时信息， 提供最常见的结果数据 当前类不满足时建议拓展该类
 * @author hongqy
 */
@Getter
@Setter
@ToString
@SuperBuilder
public class AgentCompleteInfo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * LLM本轮对话输出的最终答案
     */
    private String finalAnswer;

    /**
     * LLM本轮对话的思考过程
     */
    private String thinking;

    /**
     * LLM本轮对话的推荐答案
     */
    private String recommendations;

    /**
     * Agent本轮对话引用的来源信息
     */
    private String references;

    /**
     * Agent本轮对话使用的工具集合
     */
    private Set<String> tools;




}
