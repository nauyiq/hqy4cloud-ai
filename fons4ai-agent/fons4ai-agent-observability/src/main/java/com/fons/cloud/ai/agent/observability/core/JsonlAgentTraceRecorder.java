package com.fons.cloud.ai.agent.observability.core;

import com.alibaba.fastjson2.JSON;
import com.fons.cloud.ai.agent.observability.api.AgentTraceRecorder;
import com.fons.cloud.ai.agent.observability.api.AgentTraceRun;
import com.fons.cloud.ai.agent.observability.model.TraceRunContext;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 将每次 Agent Trace 按 JSON Lines 格式写入独立文件的记录器。
 *
 * <p>该实现只负责本地文件持久化，不依赖 Spring 或具体 Agent 框架。序列化和文件写入异常
 * 会被记录为告警并被忽略，避免影响 Agent 主执行链路。</p>
 *
 * @author hongqy
 */
public final class JsonlAgentTraceRecorder implements AgentTraceRecorder {

    /** 可直接作为安全文件名使用的 Run 标识格式。 */
    private static final Pattern SAFE_FILE_NAME = Pattern.compile("[A-Za-z0-9._-]{1,128}");

    /** JSONL 文件保存目录的绝对规范路径。 */
    private final Path storageDirectory;

    /** 用于生成记录时间，支持调用方注入统一时钟。 */
    private final Clock clock;

    /** JDK 系统日志记录器，写入失败时仅输出告警。 */
    private final System.Logger logger = System.getLogger(JsonlAgentTraceRecorder.class.getName());

    /**
     * 使用默认 JSON 配置创建记录器。
     *
    * @param storageDirectory JSONL 文件保存目录
     */
    public JsonlAgentTraceRecorder(Path storageDirectory) {
        this(storageDirectory, Clock.systemUTC());
    }

    /**
     * 创建可注入时钟的记录器，主要供框架集成时统一时间来源。
     *
     * @param storageDirectory JSONL 文件保存目录
     * @param clock 记录时间使用的时钟
     */
    JsonlAgentTraceRecorder(Path storageDirectory, Clock clock) {
        this.storageDirectory = Objects.requireNonNull(storageDirectory, "storageDirectory cannot be null")
                .toAbsolutePath()
                .normalize();
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    @Override
    public AgentTraceRun prepare(TraceRunContext context) {
        TraceRunContext requiredContext = Objects.requireNonNull(context, "context cannot be null");
        Path traceFile = storageDirectory.resolve(toFileName(requiredContext.runId()));
        return new JsonlAgentTraceRun(requiredContext, traceFile, this);
    }

    /**
     * 将一个事件信封追加为独立 JSON 行。
     *
     * @param traceFile 当前 Trace 对应的文件
     * @param envelope 待持久化事件信封
     * @return 是否成功写入
     */
    boolean append(Path traceFile, TraceEnvelope envelope) {
        try {
            String jsonLine = JSON.toJSONString(envelope) + "\n";
            Files.createDirectories(storageDirectory);
            Files.writeString(
                    traceFile,
                    jsonLine,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
            return true;
        } catch (Exception exception) {
            logger.log(System.Logger.Level.WARNING,
                    "Failed to append agent trace event to " + traceFile, exception);
            return false;
        }
    }

    /**
     * 返回当前记录时间。
     *
     * @return UTC 时间点
     */
    Instant now() {
        return clock.instant();
    }

    /**
     * 将 Run 标识转换为不可越过保存目录的文件名。
     *
     * @param runId Run 唯一标识
     * @return JSONL 文件名
     */
    private static String toFileName(String runId) {
        if (SAFE_FILE_NAME.matcher(runId).matches()) {
            return runId + ".jsonl";
        }
        return UUID.nameUUIDFromBytes(runId.getBytes(StandardCharsets.UTF_8)) + ".jsonl";
    }
}
