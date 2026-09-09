package com.fons.cloud.ai.agent.model.request;

import com.fons.cloud.ai.agent.model.hitl.HumanInTheLoopKind;
import lombok.*;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

/**
 * 审批恢复元数据。
 *
 * <p>审批批准（或带编辑批准）后，由审批服务构造携带本元数据的新请求，走一次全新的
 * {@code BaseAgent.run()}（新 runId、新分段、新任务句柄），子类在 streamExecute 中
 * 识别 resume 并从 checkpoint 继续执行。REJECT 决策不需要恢复，直接由服务层拒绝收口。</p>
 *
 * @author hongqy
 */
@Getter
@Setter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class HitlRequestInfo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 关联的原审批单 ID（审计链与幂等校验使用）。
     */
    private String hitlId;

    /**
     * HITL 类型；新的 checkpoint 恢复请求统一使用 APPROVAL。
     */
    private HumanInTheLoopKind humanInTheLoopKind;

    /**
     * 原 Run 的 runId；引擎线程键 threadId 的定位依据。
     *
     * <p>暂停时子类将本次 Run 的 runId 写入审批事件，审批服务存入票证；恢复时由本字段
     * 拼回原线程（如 {@code threadId = conversationId + ":" + originRunId}），配合
     * {@link #checkpointId} 从 Saver 精确定位中断点。恢复 Run 自身使用新的 runId，
     * 与引擎线程无关。</p>
     */
    private String originRunId;

    /**
     * 引擎 checkpoint 标识，子类在暂停前保存。
     */
    private String checkpointId;

    /**
     * 审批决策；仅 APPROVE / EDIT 会发起恢复。
     */
    private AgentApprovalAction decision;

    /**
     * 额外的参数
     */
    private Map<String, Object> params;

}
