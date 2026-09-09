package com.fons.cloud.ai.agent.model.runtime;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

/**
 * AgentScope单次工具调用的文本结果缓冲。
 *
 * @author hongqy
 */
@Getter
@Setter
public class AgentScopeToolResultBuffer {

    /**
     * 工具名称。
     * -- GETTER --
     *  获取工具名称。
     */
    private String toolName;

    /**
     * 工具文本结果。
     */
    private final StringBuilder text = new StringBuilder();

    /**
     * 是否包含当前common工具契约无法表达的非文本结果。
     */
    private boolean dataOutput;

    /**
     * 更新工具名称，空名称不会覆盖已有值。
     *
     * @param toolName 工具名称
     */
    public void updateToolName(String toolName) {
        if (StringUtils.isNotBlank(toolName)) {
            this.toolName = toolName;
        }
    }

    /**
     * 追加文本结果片段。
     *
     * @param delta 文本结果片段
     */
    public void appendText(String delta) {
        if (StringUtils.isNotEmpty(delta)) {
            text.append(delta);
        }
    }

    /**
     * 标记当前工具结果包含非文本内容。
     */
    public void markDataOutput() {
        this.dataOutput = true;
    }

    /**
     * 获取已经聚合完成的文本结果。
     *
     * @return 工具文本结果
     */
    public String getText() {
        return text.toString();
    }

    /**
     * 判断是否包含非文本结果。
     *
     * @return true表示包含非文本结果
     */
    public boolean hasDataOutput() {
        return dataOutput;
    }

}
