package com.fons.cloud.ai.agent.model.runtime;

import com.fons.cloud.ai.agent.api.AgentType;
import com.fons.cloud.ai.agent.model.request.AgentTaskRegisterRequest;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 本地实例真正持有的任务句柄
 * @author hongqy
 */
@Slf4j
@Getter
@Setter
@ToString
public class AgentLocalTask {

    /**
     * 任务ID
     */
    private String runId;

    /**
     * 会话ID
     */
    private String conversationId;


    /**
     * Agent类型
     */
    private AgentType agentType;

    /**
     * 任务创建时间戳
     */
    private Long createTime;

    /**
     * 任务是否停止
     */
    private final AtomicBoolean stopped = new AtomicBoolean(false);

    /**
     * 本地任务取消权柄。
     */
    private final AtomicReference<Disposable> disposable = new AtomicReference<>();

    public static AgentLocalTask create(AgentTaskRegisterRequest request) {
        AgentLocalTask task = new AgentLocalTask();
        task.setRunId(request.getRunId());
        task.setConversationId(request.getConversationId());
        task.setAgentType(request.getAgentType());
        task.setCreateTime(System.currentTimeMillis());
        return task;
    }


    public String getTaskKey() {
        return conversationId + ":" + runId;
    }

    /**
     * 绑定执行任务
     * @param binding 可执行任务
     */
    public synchronized void bindDisposable(Disposable binding) {
        if (binding == null) {
            return;
        }
        if (stopped.get()) {
            log.info("Disposable task is disposed, runId:{}.", runId);
            disposeQuietly(binding);
            return;
        }
        Disposable previous = disposable.getAndSet(binding);
        if (previous != null && previous != binding && !previous.isDisposed()) {
            previous.dispose();
        }
    }

    /**
     * 断开任务
     */
    public synchronized boolean dispose() {
        stopped.set(true);
        Disposable task = disposable.getAndSet(null);
        if (task == null) {
            log.info("Disposable task is null, runId:{}.", runId);
            return true;
        }
        if (task.isDisposed()) {
            return true;
        }
        try {
            task.dispose();
            return true;
        } catch (Exception e) {
            disposable.compareAndSet(null, task);
            log.error(e.getMessage(), e);
            return false;
        }
    }

    private void disposeQuietly(Disposable task) {
        if (task == null || task.isDisposed()) {
            return;
        }
        try {
            task.dispose();
        } catch (Exception e) {
            log.warn("释放已停止任务的迟到句柄失败, runId:{}", runId, e);
        }
    }


}
