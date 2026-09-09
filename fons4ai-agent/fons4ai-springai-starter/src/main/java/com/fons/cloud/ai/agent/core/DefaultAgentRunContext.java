package com.fons.cloud.ai.agent.core;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.InterruptionMetadata;
import com.alibaba.fastjson2.JSON;
import com.fons.cloud.ai.agent.model.request.AgentRequest;
import com.fons.cloud.ai.agent.model.response.AgentCompleteInfo;
import com.fons.cloud.ai.agent.model.runtime.AgentRunContext;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * 单次 Run 的状态容器。
 * <p>只保存状态、请求快照、引擎配置与输出；不持有 Disposable 或资源释放动作。</p>
 *
 * @author hongqy
 */
@Getter
@SuperBuilder
public class DefaultAgentRunContext extends AgentRunContext {

    /**
     * 本次请求快照（含审批恢复元数据）。
     */
    private final AgentRequest request;

    /**
     * 当前 Graph 的 thread/checkpoint 配置
     */
    @Setter
    private volatile RunnableConfig runnableConfig;

    /**
     * 原生中断结果，等引擎流收尾后转换为 common 审批暂停。
     */
    @Setter
    private volatile InterruptionMetadata interruption;


    /**
     * 使用最后一轮无工具调用的模型输出确认最终答案。
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
