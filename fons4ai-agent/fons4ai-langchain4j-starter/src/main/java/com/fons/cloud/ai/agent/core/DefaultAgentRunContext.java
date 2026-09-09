package com.fons.cloud.ai.agent.core;

import com.alibaba.fastjson2.JSON;
import com.fons.cloud.ai.agent.model.request.AgentRequest;
import com.fons.cloud.ai.agent.model.response.AgentCompleteInfo;
import com.fons.cloud.ai.agent.model.runtime.AgentRunContext;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

/**
 * LangChain4j单次Run的状态容器。
 *
 * <p>只保存请求快照和运行结果，不持有原生流或资源释放动作。</p>
 *
 * @author hongqy
 */
@Getter
@SuperBuilder
public class DefaultAgentRunContext extends AgentRunContext {

    /**
     * 本次请求快照
     */
    private final AgentRequest request;

    /**
     * 使用LangChain4j最终响应确认本次运行的最终答案。
     *
     * @param answer 最终答案
     */
    public void replaceFinalAnswer(String answer) {
        getFinalAnswer().setLength(0);
        if (answer != null) {
            getFinalAnswer().append(answer);
        }
    }

    @Override
    public synchronized AgentCompleteInfo buildCompleteInfo() {
        return AgentCompleteInfo.builder()
                .finalAnswer(getFinalAnswer().toString())
                .thinking(getThinking().toString())
                .references(getReferences().isEmpty() ? null : JSON.toJSONString(getReferences()))
                .build();
    }
}
