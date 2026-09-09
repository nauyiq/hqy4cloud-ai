package com.fons.cloud.ai.agent.model.runtime;

import java.util.Objects;

/**
 * AgentScope工具结果事件关联键。
 *
 * @author hongqy
 */
public final class AgentScopeToolResultKey {

    /**
     * 原生事件来源，null表示顶层Agent。
     */
    private final String source;

    /**
     * 模型回复ID。
     */
    private final String replyId;

    /**
     * 工具调用ID。
     */
    private final String toolCallId;

    /**
     * 创建工具结果事件关联键。
     *
     * @param source 原生事件来源，null表示顶层Agent
     * @param replyId 模型回复ID
     * @param toolCallId 工具调用ID
     */
    public AgentScopeToolResultKey(String source, String replyId, String toolCallId) {
        this.source = source;
        this.replyId = replyId;
        this.toolCallId = toolCallId;
    }

    /**
     * 获取原生事件来源。
     *
     * @return 原生事件来源
     */
    public String getSource() {
        return source;
    }

    /**
     * 获取模型回复ID。
     *
     * @return 模型回复ID
     */
    public String getReplyId() {
        return replyId;
    }

    /**
     * 获取工具调用ID。
     *
     * @return 工具调用ID
     */
    public String getToolCallId() {
        return toolCallId;
    }

    /**
     * 判断两个工具结果事件关联键是否相同。
     *
     * @param object 待比较对象
     * @return true表示三个关联字段全部相同
     */
    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof AgentScopeToolResultKey that)) {
            return false;
        }
        return Objects.equals(source, that.source)
                && Objects.equals(replyId, that.replyId)
                && Objects.equals(toolCallId, that.toolCallId);
    }

    /**
     * 计算工具结果事件关联键哈希值。
     *
     * @return 关联键哈希值
     */
    @Override
    public int hashCode() {
        return Objects.hash(source, replyId, toolCallId);
    }

}
