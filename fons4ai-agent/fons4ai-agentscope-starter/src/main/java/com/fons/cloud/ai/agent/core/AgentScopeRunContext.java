package com.fons.cloud.ai.agent.core;

import com.alibaba.fastjson2.JSON;
import com.fons.cloud.ai.agent.model.request.AgentRequest;
import com.fons.cloud.ai.agent.model.response.AgentCompleteInfo;
import com.fons.cloud.ai.agent.model.runtime.AgentRunContext;
import com.fons.cloud.ai.agent.model.runtime.AgentScopeToolResultBuffer;
import com.fons.cloud.ai.agent.model.runtime.AgentScopeToolResultKey;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.message.Msg;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.SuperBuilder;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AgentScope单次Run的状态容器。
 *
 * <p>只保存请求快照、AgentScope调用上下文、运行结果与事件聚合数据，不持有
 * 原生流、HarnessAgent或资源释放动作。</p>
 *
 * @author hongqy
 */
@Getter
@SuperBuilder
public class AgentScopeRunContext extends AgentRunContext {

    /**
     * 本次请求快照。
     */
    @NonNull
    private final AgentRequest request;

    /**
     * AgentScope单次调用上下文。
     */
    @NonNull
    private final RuntimeContext runtimeContext;

    /**
     * AgentScope本次执行的最终结果消息。
     */
    private volatile Msg result;

    /**
     * 当前阶段尚未接入的原生交互类型。
     */
    private volatile AgentEventType unsupportedInteraction;

    /**
     * 当前Run尚未结束的工具结果事件缓冲，使用事件来源、回复ID和工具调用ID精确关联。
     */
    private final Map<AgentScopeToolResultKey, AgentScopeToolResultBuffer> toolResultBuffers =
            new ConcurrentHashMap<>();

    /**
     * 使用AgentScope最终结果确认本次运行的最终答案。
     *
     * @param result AgentScope最终结果
     */
    public void recordResult(Msg result) {
        this.result = result;
        getFinalAnswer().setLength(0);
        if (result != null) {
            getFinalAnswer().append(result.getTextContent());
        }
    }

    /**
     * 记录当前适配器尚未接入的原生交互。
     *
     * @param interaction 原生事件类型
     */
    public void recordUnsupportedInteraction(AgentEventType interaction) {
        if (this.unsupportedInteraction == null) {
            this.unsupportedInteraction = interaction;
        }
    }

    /**
     * 构建common Agent完成信息。
     *
     * @return Agent完成信息
     */
    @Override
    public AgentCompleteInfo buildCompleteInfo() {
        return AgentCompleteInfo.builder()
                .finalAnswer(getFinalAnswer().toString())
                .thinking(getThinking().toString())
                .references(getReferences().isEmpty() ? null : JSON.toJSONString(getReferences()))
                .tools(getToolRecords().keySet())
                .build();
    }

}
