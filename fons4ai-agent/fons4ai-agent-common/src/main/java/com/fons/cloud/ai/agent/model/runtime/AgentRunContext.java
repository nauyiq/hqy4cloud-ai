package com.fons.cloud.ai.agent.model.runtime;

import com.fons.cloud.ai.agent.model.hitl.HumanInTheLoopInfo;
import com.fons.cloud.ai.agent.model.response.AgentCompleteInfo;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Agent运行时上下文（框架级基类）。
 *
 * <p>一次 Run 的请求级状态容器，只维护执行状态机、请求标识、编排参数和完成信息。
 * 事件流、完成结果与可中断资源均由 {@link RuntimeActions} 持有。</p>
 *
 * @author hongqy
 */
@Getter
@ToString
@SuperBuilder
public abstract class AgentRunContext {

    /**
     * 消息ID
     */
    protected String messageId;

    /**
     * 运行ID
     */
    @NonNull
    protected String runId;

    /**
     * 会话ID
     */
    @NonNull
    protected String conversationId;

    /**
     * WAITING_APPROVAL 时关联的审批请求标识；仅暂停分段有效，进入终态后清空。
     */
    protected HumanInTheLoopInfo humanInTheLoopInfo;

    /**
     * 执行状态机：CREATED -> RUNNING -> 终态 / WAITING_APPROVAL。
     */
    protected final AtomicReference<AgentRunState> state = new AtomicReference<>(AgentRunState.CREATED);

    /**
     * 执行启动时间戳，0 表示尚未启动。
     */
    protected final AtomicLong startedAt = new AtomicLong();

    /**
     * 执行结束时间戳，0 表示尚未结束。
     */
    protected final AtomicLong finishedAt = new AtomicLong();

    /**
     * 整个 Run 累积的思考过程。
     */
    private final StringBuilder thinking = new StringBuilder();

    /**
     * 参考资料
     */
    private final List<Object> references = new CopyOnWriteArrayList<>();

    /**
     * 最后答案
     */
    private final StringBuilder finalAnswer = new StringBuilder();

    /**
     * 工具调用记录
     */
    private final Map<String, List<Object>> toolRecords = new ConcurrentHashMap<>();

    /**
     * 追加答案
     * @param text 答案文本
     */
    public void appendAnswer(String text) {
        finalAnswer.append(text);
    }

    /**
     * 追加思考过程
     * @param text 思考过程文本
     */
    public void appendThinking(String text) {
        thinking.append(text);
    }

    /**
     * 追加参考资料
     * @param reference 来源信息
     */
    public synchronized void appendReference(Object reference) {
        if (reference != null) {
            references.add(reference);
        }
    }

    /**
     * 记录工具调用
     * @param toolName
     * @param value
     */
    public void recordToolUsed(String toolName, Object value) {
        this.toolRecords.computeIfAbsent(toolName, key -> new CopyOnWriteArrayList<>()).add(value);
    }

    /**
     * 获取当前状态。
     *
     * @return 状态
     */
    public AgentRunState getState() {
        return state.get();
    }

    /**
     * 尝试启动执行。
     *
     * @return 是否成功启动
     */
    public boolean tryStart() {
        if (!state.compareAndSet(AgentRunState.CREATED, AgentRunState.RUNNING)) {
            return false;
        }
        startedAt.compareAndSet(0, System.currentTimeMillis());
        return true;
    }

    /**
     * 原生引擎已保存可恢复的 checkpoint 后，才允许从 RUNNING 进入审批等待。
     *
     * <p>CAS 保证唯一性：并发或重复调用只有一次成功；成功后本分段的订阅、租约和
     * 事件流收口由调用方（暂停原语）负责。</p>
     *
     * @param humanInTheLoopInfo HITL信息
     * @return 是否首次成功进入审批等待
     */
    public boolean tryPauseForApproval(HumanInTheLoopInfo humanInTheLoopInfo) {
        if (humanInTheLoopInfo == null || !state.compareAndSet(AgentRunState.RUNNING, AgentRunState.WAITING_APPROVAL)) {
            return false;
        }
        this.humanInTheLoopInfo = humanInTheLoopInfo;
        return true;
    }

    /**
     * 尝试进入不可逆终态。
     *
     * <p>审批等待（WAITING_APPROVAL）只允许被取消、超时或审批拒绝终结，其他
     * 终态会被拒绝；随后释放 Run 持有的一切资源。</p>
     *
     * @param terminalState 终态状态
     * @return 是否首次成功进入终态
     */
    public boolean tryFinalize(AgentRunState terminalState) {
        if (!terminalState.isTerminal()) {
            return false;
        }

        while (true) {
            AgentRunState current = state.get();
            if (current.isTerminal()) {
                return false;
            }
            // 当前处于审批等待时，非异常/取消/超时/审批拒绝不允许设置结束状态
            if (current == AgentRunState.WAITING_APPROVAL
                    && terminalState != AgentRunState.CANCELLED
                    && terminalState != AgentRunState.TIMED_OUT
                    && terminalState != AgentRunState.APPROVAL_REJECTED) {
                return false;
            }
            if (state.compareAndSet(current, terminalState)) {
                finishedAt.compareAndSet(0, System.currentTimeMillis());
                humanInTheLoopInfo = null;
                return true;
            }
        }
    }

    /**
     * 交给子类通过自身上下文构建完成信息。
     *
     * @return 完成信息
     */
    public abstract AgentCompleteInfo buildCompleteInfo();

}
