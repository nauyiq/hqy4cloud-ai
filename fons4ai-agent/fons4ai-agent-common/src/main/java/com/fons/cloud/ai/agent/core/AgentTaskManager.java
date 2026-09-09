package com.fons.cloud.ai.agent.core;

import cn.hutool.core.util.IdUtil;
import com.alibaba.fastjson2.JSON;
import com.fons.cloud.ai.agent.model.response.AgentResultCode;
import com.fons.cloud.ai.agent.model.request.AgentTaskBingRequest;
import com.fons.cloud.ai.agent.model.request.AgentTaskCancelRequest;
import com.fons.cloud.ai.agent.model.request.AgentTaskRegisterRequest;
import com.fons.cloud.ai.agent.model.request.AgentTaskReleaseRequest;
import com.fons.cloud.ai.agent.model.request.AgentStopRequest;
import com.fons.cloud.ai.agent.model.runtime.AgentLocalTask;
import com.fons.cloud.common.result.R;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RBucket;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Agent任务注册控制器
 * @author hongqy
 */
@Slf4j
public class AgentTaskManager implements InitializingBean, DisposableBean {
    private static final String TASK_KEY_PREFIX = "fons4ai-agent:task:";
    private static final String STOP_TOPIC_NAME = "fons4ai-agent:stop";
    private static final String CANCEL_KEY_PREFIX = "fons4ai-agent:cancel:";

    private static final long TASK_TTL_MINUTES = 30;
    private static final long TASK_CHECK_INTERVAL_MINUTES = 5;
    private static final long CANCEL_TASK_TTL_MINUTES = 10;

    /**
     * 当前实例真正执行的任务，按 conversationId 互斥。
     */
    private final Map<String, AgentLocalTask> localTasks = new ConcurrentHashMap<>();

    /**
     * 实例ID
     */
    private final String instanceId;

    /**
     * redisson客户端
     */
    private final RedissonClient redissonClient;

    /**
     * 停止消息的发布订阅主题
     */
    private final RTopic stopTopic;

    /**
     * 监听器ID
     */
    private final Integer listenerId;

    /**
     * 任务检查定时器
     */
    private final ScheduledExecutorService taskCheckScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "agent-task-check");
        t.setDaemon(true);
        return t;
    });


    public AgentTaskManager(RedissonClient redissonClient) {
        this(IdUtil.fastSimpleUUID(), redissonClient);
    }

    public AgentTaskManager(String instanceId, RedissonClient redissonClient) {
        this.instanceId = instanceId;
        this.redissonClient = redissonClient;
        // 获取发布订阅主题
        this.stopTopic = redissonClient.getTopic(STOP_TOPIC_NAME);
        // 设置监听器
        this.listenerId = this.stopTopic.addListener(String.class, (channel, payload) -> {
            onReceiveStopMessage(payload);
        });
        log.info("AgentTaskManager 初始化, instanceId: {}", instanceId);
    }

    /**
     * 注册任务
     * @param request
     * @return
     */
    public R<Boolean> registerTask(AgentTaskRegisterRequest request) {
        String conversationId = request.getConversationId();
        String taskId = request.getTaskId();

        // 同一会话只允许一个任务执行。
        AgentLocalTask task = localTasks.get(conversationId);
        if (task != null) {
            log.warn("会话 {} 本地已有任务 {} 在执行，拒绝注册新任务 {}",
                    conversationId, task.getTaskKey(), taskId);
            return R.failed(AgentResultCode.AGENT_TASK_ALREADY_EXIST);
        }

        // 判断是否接受到停止请求
        RBucket<String> cancelBucket = getTaskCancelBucket(taskId);
        if (cancelBucket.isExists()) {
            log.warn("任务 {} 正在关闭中，拒绝注册该任务", taskId);
            return R.failed(AgentResultCode.AGENT_TASK_ALREADY_CLOSE);
        }

        // Redis 租约按会话注册，租约值同时记录实例和 runId，保证释放时可以精确校验。
        RBucket<String> taskBucket = getTaskBucket(conversationId);
        String leaseValue = getTaskLeaseValue(request.getRunId());
        if (!taskBucket.setIfAbsent(leaseValue, Duration.ofMinutes(TASK_TTL_MINUTES))) {
            String holder = taskBucket.get();
            log.warn("会话 {} 已被任务租约 {} 占用，当前任务 {} 拒绝注册", conversationId, holder, taskId);
            return R.failed(AgentResultCode.AGENT_TASK_ALREADY_EXIST);
        }

        // 注册到本地
        AgentLocalTask localTask = AgentLocalTask.create(request);
        AgentLocalTask previous = localTasks.putIfAbsent(conversationId, localTask);
        if (previous != null) {
            taskBucket.compareAndSet(leaseValue, null);
            log.warn("会话 {} 本地已有任务 {} 在执行，当前任务 {} 释放租约并拒绝注册",
                    conversationId, previous.getTaskKey(), taskId);
            return R.failed(AgentResultCode.AGENT_TASK_ALREADY_EXIST);
        }

        // 处理“Redis 租约注册成功 -> 本地任务注册成功”窗口内到达的取消请求。
        if (cancelBucket.isExists()) {
            doStopTask(localTask);
            log.warn("任务 {} 注册期间收到取消请求，拒绝继续启动", taskId);
            return R.failed(AgentResultCode.AGENT_TASK_ALREADY_CLOSE);
        }
        log.info("注册任务成功: taskId={}, agentType={}, instanceId={}", localTask.getTaskKey(), localTask.getAgentType(), instanceId);
        return R.success(true);
    }

    /**
     * 绑定任务句柄
     * @param request
     * @return
     */
    public R<Boolean> bindingTaskDisposable(AgentTaskBingRequest request) {
        String taskId = request.getTaskId();
        AgentLocalTask task = localTasks.get(request.getConversationId());
        if (task == null || !StringUtils.equals(task.getRunId(), request.getRunId())) {
            // 任务可能在“注册成功 -> 真实句柄绑定”窗口内被取消。此时本地任务已清理，
            // 但取消标记仍在 TTL 内，必须释放迟到的句柄，避免取消后仍继续执行。
            if (getTaskCancelBucket(taskId).isExists()) {
                disposeQuietly(request.getDisposable(), taskId);
                return R.success();
            }
            log.warn("当前实例不持有该任务, instanceId:{}, taskId:{}", instanceId, taskId);
            return R.failed(AgentResultCode.AGENT_TASK_NOT_EXIST);
        }

        // 绑定任务句柄
        task.bindDisposable(request.getDisposable());

        // 再次检查是否存在迟来的取消请求
        if (getTaskCancelBucket(taskId).isExists()) {
            disposeQuietly(request.getDisposable(), taskId);
        }
        return R.success();
    }

    /**
     * 全局唯一的主动停止任务入口
     * @param request
     * @return
     */
    public R<Boolean> stopTask(AgentTaskCancelRequest request) {
        String taskId = request.getTaskId();
        RBucket<String> cancelBucket = getTaskCancelBucket(taskId);
        // 先判断本地实例是否存在任务
        AgentLocalTask task = localTasks.get(request.getConversationId());
        if (task != null && StringUtils.equals(task.getRunId(), request.getRunId())) {
            // 本地快速路径也必须先留下取消事实，用于处理随后才绑定的真实句柄。
            cancelBucket.setIfAbsent(instanceId, Duration.ofMinutes(CANCEL_TASK_TTL_MINUTES));
            boolean stop = doStopTask(task);
            return stop ? R.success() : R.failed(AgentResultCode.FAILED_EXECUTE_STOP_AGENT_TASK);
        }

        // 同一取消请求幂等返回成功。
        if (cancelBucket.isExists()) {
            // 任务已经存在， 则直接返回成功
            return R.success();
        }
        // 取消只接受当前会话中精确匹配的 run，避免旧 run 的迟到取消误伤新任务。
        String leaseValue = getTaskBucket(request.getConversationId()).get();
        if (!isTaskLeaseForRun(leaseValue, request.getRunId())) {
            return R.failed(AgentResultCode.AGENT_TASK_NOT_EXIST);
        }
        // 设置取消任务标识 默认10分钟 这10分钟尽最大努力通知
        cancelBucket.setIfAbsent(instanceId, Duration.ofMinutes(CANCEL_TASK_TTL_MINUTES));
        // 发送一次停止消息
        sendStopMessage(request);
        return R.success();
    }

    /**
     * 释放任务
     * @param request
     * @return
     */
    public R<Boolean> releaseTask(AgentTaskReleaseRequest request) {
        String conversationId = request.getConversationId();
        AgentLocalTask task = localTasks.get(conversationId);
        if (task != null && StringUtils.equals(task.getRunId(), request.getRunId())) {
            localTasks.remove(conversationId, task);
        }

        // 只删除本实例当前 run 持有的会话租约；迟到的旧 run 释放不会影响新 run。
        RBucket<String> bucket = getTaskBucket(conversationId);
        bucket.compareAndSet(getTaskLeaseValue(request.getRunId()), null);
        return R.success();
    }


    /**
     * 发送停止消息
     * @param request
     */
    private void sendStopMessage(AgentTaskCancelRequest request) {
        AgentStopRequest stopRequest = new AgentStopRequest(
                AgentStopRequest.CURRENT_VERSION, request.getConversationId(), request.getRunId());
        long receivers = stopTopic.publish(JSON.toJSONString(stopRequest));
        log.info("发布停止广播: taskId:{}, 订阅者数量={}", request.getTaskId(), receivers);
    }


    /**
     * 接受到停止消息
     */
    private void onReceiveStopMessage(String payload) {
        AgentStopRequest request = parseStopRequest(payload);
        if (request == null || request.version() != AgentStopRequest.CURRENT_VERSION) {
            log.warn("忽略无法识别的停止任务消息: {}", payload);
            return;
        }

        AgentLocalTask task = localTasks.get(request.conversationId());
        if (task == null) {
            log.debug("本实例不持有该会话任务: conversationId={}", request.conversationId());
            return;
        }
        if (!StringUtils.equals(task.getRunId(), request.runId())) {
            log.debug("忽略旧运行的停止任务消息: conversationId={}, targetRunId={}, currentRunId={}",
                    request.conversationId(), request.runId(), task.getRunId());
            return;
        }

        log.info("接收停止任务请求, instanceId:{}, taskKey:{}.", instanceId, task.getTaskKey());
        doStopTask(task);
    }

    /**
     * 执行本地任务
     * @param task
     */
    private boolean doStopTask(AgentLocalTask task) {
        try {
            if (!task.dispose()) {
                return false;
            }

            // 释放全局任务租约；取消标记保留到 TTL 结束，以处理迟到的句柄绑定。
            RBucket<String> bucket = getTaskBucket(task.getConversationId());
            if (bucket.compareAndSet(getTaskLeaseValue(task.getRunId()), null)) {
                log.debug("已删除Redis任务标识, instanceId:{}, conversationId:{}.", instanceId, task.getConversationId());
            }

            // 删除本地实例持有的缓存
            localTasks.remove(task.getConversationId(), task);
            return true;
        } catch (Exception e) {
            log.warn("Failed to stop task, taskInfo: {}", JSON.toJSONString(task), e);
            return false;
        }
    }


    @Override
    public void afterPropertiesSet() throws Exception {
        // 启动任务检查线程池
        taskCheckScheduler.scheduleAtFixedRate(
                this::taskCheck,
                TASK_CHECK_INTERVAL_MINUTES,
                TASK_CHECK_INTERVAL_MINUTES,
                TimeUnit.MINUTES
        );
        log.info("AgentTaskManager 启动完成, 已订阅停止主题, TASK检查刷新间隔={}分钟", TASK_CHECK_INTERVAL_MINUTES);
    }




    /**
     * <p>
     *  1. 尽最大努力通知取消任务
     *  2. 定时刷新本地所有运行中任务的 Redis TTL,防止长任务的 key 过期
     * </p>
     */
    private void taskCheck() {
        if (localTasks.isEmpty()) {
            return;
        }
        log.debug("开始进行任务检查, 本地任务数={}", localTasks.size());
        for (Map.Entry<String, AgentLocalTask> entry : localTasks.entrySet()) {
            String conversationId = entry.getKey();
            AgentLocalTask task = entry.getValue();
            String taskId = task.getTaskKey();
            // 检查本地任务是否接受到取消任务通知
            try {
                RBucket<String> bucket = getTaskCancelBucket(taskId);
                if (bucket.isExists()) {
                    if (localTasks.get(conversationId) == task) {
                        log.info("检测到待处理的取消任务，执行本地停止: taskId={}", taskId);
                        doStopTask(task);
                    }
                    continue;
                }
            } catch (Exception e) {
                log.error("通知取消任务失败: taskId={}", taskId, e);
            }

            // 刷新本地TTL
            try {
                RBucket<String> bucket = getTaskBucket(conversationId);
                String holder = bucket.get();
                if (getTaskLeaseValue(task.getRunId()).equals(holder)) {
                    bucket.expire(Duration.ofMinutes(TASK_TTL_MINUTES));
                } else {
                    // Redis 中的 holder 不是本实例，说明 key 已被其他实例持有或已过期
                    log.warn("TTL刷新发现会话租约归属变化: conversationId={}, runId={}, 期望={}, 实际={}",
                            conversationId, task.getRunId(), getTaskLeaseValue(task.getRunId()), holder);
                    if (!task.dispose()) {
                        log.warn("会话租约丢失后释放本地任务失败: taskId={}", taskId);
                    }
                    localTasks.remove(conversationId, task);
                }
            } catch (Exception e) {
                log.error("TTL刷新失败: taskId={}", taskId, e);
            }
        }
    }


    @Override
    public void destroy() throws Exception {
        // 移除发布订阅监听器
        try {
            stopTopic.removeListener(listenerId);
        } catch (Exception e) {
            log.warn("移除发布订阅监听器失败", e);
        }

        // 关闭定时任务
        taskCheckScheduler.shutdown();

        // 清理所有本地任务（释放 Redis key）
        for (Map.Entry<String, AgentLocalTask> entry : localTasks.entrySet()) {
            doRemoveTask(entry.getKey(), entry.getValue());
        }
        log.info("AgentTaskManager 销毁完成, instanceId={}", instanceId);
    }


    /**
     * 内部移除：从本地 map 删除 + 删除 Redis key
     */
    private void doRemoveTask(String conversationId, AgentLocalTask task) {
        if (!task.dispose()) {
            log.warn("AgentTaskManager 销毁时释放本地任务失败: taskId={}", task.getTaskKey());
        }
        localTasks.remove(conversationId, task);
        RBucket<String> bucket = getTaskBucket(conversationId);
        if (bucket.compareAndSet(getTaskLeaseValue(task.getRunId()), null)) {
            log.debug("删除 Redis 任务key: conversationId={}, runId={}", conversationId, task.getRunId());
        }
    }

    private void disposeQuietly(reactor.core.Disposable disposable, String taskId) {
        if (disposable == null || disposable.isDisposed()) {
            return;
        }
        try {
            disposable.dispose();
            log.info("已释放取消后迟到绑定的任务句柄: taskId={}", taskId);
        } catch (Exception e) {
            log.warn("释放取消后迟到绑定的任务句柄失败: taskId={}", taskId, e);
        }
    }

    /**
     * 获取任务 RBucket
     */
    private RBucket<String> getTaskBucket(String conversationId) {
        return redissonClient.getBucket(TASK_KEY_PREFIX + conversationId, StringCodec.INSTANCE);
    }

    /**
     * 获取任务 RBucket
     */
    private RBucket<String> getTaskCancelBucket(String taskId) {
        return redissonClient.getBucket(CANCEL_KEY_PREFIX + taskId, StringCodec.INSTANCE);
    }

    /**
     * 解析停止消息
     * @param payload
     * @return
     */
    private AgentStopRequest parseStopRequest(String payload) {
        if (StringUtils.isBlank(payload)) {
            return null;
        }
        try {
            return JSON.parseObject(payload, AgentStopRequest.class);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 会话租约值同时包含实例和 runId，既能标识持有实例，也能防止迟到释放删除新任务租约。
     */
    private String getTaskLeaseValue(String runId) {
        return instanceId + ":" + runId;
    }

    /**
     * 判断会话租约是否属于目标 run。runId 由框架生成，不包含冒号。
     */
    private boolean isTaskLeaseForRun(String leaseValue, String runId) {
        return StringUtils.isNotBlank(leaseValue)
                && StringUtils.isNotBlank(runId)
                && leaseValue.endsWith(":" + runId);
    }


}
