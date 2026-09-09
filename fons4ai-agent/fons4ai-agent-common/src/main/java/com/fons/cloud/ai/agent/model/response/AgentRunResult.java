package com.fons.cloud.ai.agent.model.response;

import com.fons.cloud.ai.agent.model.hitl.HumanInTheLoopInfo;
import com.fons.cloud.ai.agent.model.runtime.AgentRunState;
import lombok.Builder;
import lombok.Getter;

/**
 * 一次智能体执行分段的结构化结果，可表示不可逆终态或 checkpoint 审批等待快照。
 *
 * @author hongqy
 */
@Getter
@Builder
public final class AgentRunResult {

    /**
     * 执行唯一标识。
     */
    private final String runId;

    /**
     * 会话标识。
     */
    private final String conversationId;

    /**
     * 可选的消息标识。
     */
    private final String messageId;

    /**
     * 不可逆终态
     */
    private final AgentRunState state;

    /**
     * 可选的安全错误码。
     */
    private final String errorCode;

    /**
     * 可选的安全错误信息。
     */
    private final String errorMessage;

    /**
     * 可选的完成信息。
     */
    private AgentCompleteInfo completeInfo;

    /**
     * 可选的 HITL 信息。
     */
    private HumanInTheLoopInfo humanInTheLoopInfo;

}
