package com.fons.cloud.ai.agent.core;

import cn.hutool.core.util.IdUtil;
import com.fons.cloud.ai.agent.model.message.MessageContentType;
import com.fons.cloud.ai.agent.model.request.AgentInputContent;
import com.fons.cloud.ai.agent.model.request.AgentInputContentType;
import com.fons.cloud.ai.agent.model.request.AgentRequest;
import com.fons.cloud.ai.agent.model.response.AgentResultCode;
import com.fons.cloud.ai.agent.model.runtime.AgentScopeToolResultBuffer;
import com.fons.cloud.ai.agent.model.runtime.AgentScopeToolResultKey;
import com.fons.cloud.ai.agent.model.runtime.AgentRunState;
import com.fons.cloud.ai.agent.model.runtime.RuntimeActions;
import com.fons.cloud.common.base.exception.BizException;
import com.fons.cloud.common.base.exception.SystemIntervalException;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.event.RequireExternalExecutionEvent;
import io.agentscope.core.event.RequireUserConfirmEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ThinkingBlockDeltaEvent;
import io.agentscope.core.event.ToolResultDataDeltaEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.event.ToolResultStartEvent;
import io.agentscope.core.event.ToolResultTextDeltaEvent;
import io.agentscope.core.message.Base64Source;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.DataBlock;
import io.agentscope.core.message.Source;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultState;
import io.agentscope.core.message.URLSource;
import io.agentscope.core.message.UserMessage;
import io.agentscope.harness.agent.HarnessAgent;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * 按common BaseAgent契约适配AgentScope HarnessAgent。
 *
 * <p>原生HarnessAgent由下游构建并注入，本类只负责Fons Run与AgentScope单次调用之间的协议适配。
 * AgentScope负责Harness能力和ReAct循环，本类负责输入转换、流式消息以及common生命周期收口。</p>
 *
 * @author hongqy
 */
@Slf4j
@Getter
@SuperBuilder
public class AgentScopeHarnessAgent extends BaseAgent<AgentScopeRunContext> {

    /**
     * Fons运行ID在AgentScope RuntimeContext中的属性名称。
     */
    protected static final String RUN_ID_ATTRIBUTE = "fons.runId";

    /**
     * Fons消息ID在AgentScope RuntimeContext中的属性名称。
     */
    protected static final String MESSAGE_ID_ATTRIBUTE = "fons.messageId";

    /**
     * 调用方构建完成的AgentScope HarnessAgent。
     */
    @NonNull
    protected final HarnessAgent delegate;

    /**
     * 启动AgentScope事件流，并接入common运行生命周期。
     *
     * @param context 本次Run上下文
     * @param actions 本次Run行为权柄
     * @return AgentScope事件流订阅权柄
     */
    @Override
    protected Disposable streamExecute(AgentScopeRunContext context, RuntimeActions actions) {
        return Flux.defer(() -> {
                    if (context.getState() != AgentRunState.RUNNING) {
                        return Flux.empty();
                    }
                    if (context.getRequest().getHitlRequestInfo() != null) {
                        return Flux.error(SystemIntervalException.of(
                                "AgentScope resumable HITL is not supported"));
                    }
                    return delegate.streamEvents(
                            createUserMessage(context.getRequest()),
                            context.getRuntimeContext());
                })
                .subscribeOn(Schedulers.boundedElastic())
                // 串行处理原生回调，避免并发修改Context和乱序输出客户端消息。
                .publishOn(Schedulers.boundedElastic(), 1)
                .doOnNext(event -> handleEvent(context, actions, event))
                .doOnComplete(() -> finishSegment(context, actions))
                .onErrorMap(this::normalizeError)
                .doFinally(signal -> {
                    try {
                        if (signal == SignalType.CANCEL
                                && context.getState() == AgentRunState.RUNNING) {
                            cancelled(context, actions);
                        }
                    } finally {
                        // 原生流结束后清理尚未收到End事件的工具结果缓冲。
                        context.getToolResultBuffers().clear();
                    }
                })
                .subscribe(ignored -> {
                }, error -> failed(context, actions, error,
                        AgentResultCode.FAILED_EXECUTE_AGENT.getCode(),
                        AgentResultCode.FAILED_EXECUTE_AGENT.getMessage()));
    }

    /**
     * 创建AgentScope单次Run上下文。
     *
     * @param request Agent请求
     * @return AgentScope Run上下文
     */
    @Override
    protected AgentScopeRunContext createRunContext(AgentRequest request) {
        String runId = IdUtil.fastSimpleUUID();
        return AgentScopeRunContext.builder()
                .runId(runId)
                .messageId(request.getMessageId())
                .conversationId(request.getConversationId())
                .request(request)
                .runtimeContext(createRuntimeContext(request, runId))
                .build();
    }

    /**
     * 创建AgentScope单次Run行为权柄。
     *
     * @param context AgentScope Run上下文
     * @return AgentScope Run行为权柄
     */
    @Override
    protected RuntimeActions createActions(AgentScopeRunContext context) {
        return AgentScopeRuntimeActions.builder()
                .agentRunContext(context)
                .build();
    }

    /**
     * 将common多模态输入转换为AgentScope用户消息。
     *
     * @param request Agent请求
     * @return AgentScope用户消息
     */
    protected UserMessage createUserMessage(AgentRequest request) {
        try {
            List<ContentBlock> blocks = new ArrayList<>(request.getContents().size());
            for (AgentInputContent content : request.getContents()) {
                blocks.add(createContentBlock(content));
            }
            return new UserMessage(blocks);
        } catch (SystemIntervalException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.warn("Failed to convert common input to AgentScope UserMessage, runMessageId:{}",
                    request.getMessageId(), exception);
            throw SystemIntervalException.of("Failed to convert Agent multimodal input");
        }
    }

    /**
     * 将单个common输入内容转换为AgentScope内容块。
     *
     * @param content common输入内容
     * @return AgentScope内容块
     */
    private ContentBlock createContentBlock(AgentInputContent content) {
        if (content.getType() == AgentInputContentType.TEXT) {
            return TextBlock.builder()
                    .text(content.getText())
                    .build();
        }

        DataBlock.Builder builder = DataBlock.builder()
                .source(createDataSource(content));
        if (StringUtils.isNotBlank(content.getName())) {
            builder.name(content.getName());
        }
        return builder.build();
    }

    /**
     * 创建AgentScope多模态数据源。
     *
     * @param content common多模态输入内容
     * @return AgentScope数据源
     */
    private Source createDataSource(AgentInputContent content) {
        if (content.getUri() != null) {
            return URLSource.builder()
                    .url(content.getUri().toString())
                    .mimeType(content.getMimeType())
                    .build();
        }
        return Base64Source.builder()
                .mediaType(content.getMimeType())
                .data(Base64.getEncoder().encodeToString(content.getData()))
                .build();
    }

    /**
     * 处理AgentScope顶层Agent事件。
     *
     * @param context 当前Run上下文
     * @param actions 当前Run行为权柄
     * @param event   原生事件
     */
    protected void handleEvent(AgentScopeRunContext context,
                               RuntimeActions actions,
                               AgentEvent event) {
        if (context.getState() != AgentRunState.RUNNING
                || StringUtils.isNotBlank(event.getSource())) {
            return;
        }

        switch (event) {
            case TextBlockDeltaEvent textEvent -> {
                String text = textEvent.getDelta();
                if (StringUtils.isNotEmpty(text)) {
                    context.appendAnswer(text);
                    emit(actions, text, MessageContentType.TEXT);
                }
                return;
            }
            case ThinkingBlockDeltaEvent thinkingEvent -> {
                String thinking = thinkingEvent.getDelta();
                if (StringUtils.isNotEmpty(thinking)) {
                    context.appendThinking(thinking);
                    emit(actions, thinking, MessageContentType.THINKING);
                }
                return;
            }
            case AgentResultEvent resultEvent -> {
                context.recordResult(resultEvent.getResult());
                return;
            }
            case ToolResultStartEvent toolResultStartEvent -> {
                startToolResult(context, toolResultStartEvent);
                return;
            }
            case ToolResultTextDeltaEvent toolResultTextDeltaEvent -> {
                appendToolResult(context, toolResultTextDeltaEvent);
                return;
            }
            case ToolResultDataDeltaEvent toolResultDataDeltaEvent -> {
                markToolDataResult(context, toolResultDataDeltaEvent);
                return;
            }
            case ToolResultEndEvent toolResultEndEvent -> {
                finishToolResult(context, actions, toolResultEndEvent);
                return;
            }
            default -> {
            }
        }

        if (event instanceof RequireUserConfirmEvent
                || event instanceof RequireExternalExecutionEvent) {
            // 等原生流自然结束，确保AgentScope有机会保存当前会话状态。
            context.recordUnsupportedInteraction(event.getType());
        }
    }

    /**
     * 创建一次工具结果聚合缓冲。
     *
     * @param context 当前Run上下文
     * @param event   工具结果开始事件
     */
    private void startToolResult(AgentScopeRunContext context, ToolResultStartEvent event) {
        getOrCreateToolResultBuffer(context, event,
                event.getReplyId(), event.getToolCallId(), event.getToolCallName());
    }

    /**
     * 追加一次工具调用的文本结果片段。
     *
     * @param context 当前Run上下文
     * @param event   工具文本结果事件
     */
    private void appendToolResult(AgentScopeRunContext context, ToolResultTextDeltaEvent event) {
        AgentScopeToolResultBuffer buffer = getOrCreateToolResultBuffer(context, event,
                event.getReplyId(), event.getToolCallId(), event.getToolCallName());
        buffer.appendText(event.getDelta());
    }

    /**
     * 标记一次工具调用包含非文本结果。
     *
     * @param context 当前Run上下文
     * @param event   工具非文本结果事件
     */
    private void markToolDataResult(AgentScopeRunContext context, ToolResultDataDeltaEvent event) {
        AgentScopeToolResultBuffer buffer = getOrCreateToolResultBuffer(context, event,
                event.getReplyId(), event.getToolCallId(), event.getToolCallName());
        buffer.markDataOutput();
    }

    /**
     * 完成一次工具结果聚合。
     *
     * <p>只有成功结束的纯文本结果才进入common工具结果处理链路。失败、拒绝、
     * 中断以及非文本结果继续由AgentScope交给LLM处理。</p>
     *
     * @param context 当前Run上下文
     * @param actions 当前Run行为权柄
     * @param event   工具结果结束事件
     */
    private void finishToolResult(AgentScopeRunContext context,
                                  RuntimeActions actions,
                                  ToolResultEndEvent event) {
        AgentScopeToolResultKey key = createToolResultKey(
                event, event.getReplyId(), event.getToolCallId());
        AgentScopeToolResultBuffer buffer = context.getToolResultBuffers().remove(key);
        if (buffer == null || event.getState() != ToolResultState.SUCCESS) {
            return;
        }

        buffer.updateToolName(event.getToolCallName());
        if (buffer.hasDataOutput()) {
            log.debug("Ignore non-text AgentScope tool result, toolName:{}, toolCallId:{}",
                    buffer.getToolName(), event.getToolCallId());
            return;
        }
        if (StringUtils.isBlank(buffer.getToolName())) {
            log.warn("Ignore AgentScope tool result without tool name, toolCallId:{}",
                    event.getToolCallId());
            return;
        }

        toolFinished(context, actions, event.getToolCallId(), buffer.getToolName(), buffer.getText());
    }

    /**
     * 获取或创建一次工具调用的结果缓冲。
     *
     * @param context    当前Run上下文
     * @param event      原生工具结果事件
     * @param replyId    模型回复ID
     * @param toolCallId 工具调用ID
     * @param toolName   工具名称
     * @return 工具结果缓冲
     */
    private AgentScopeToolResultBuffer getOrCreateToolResultBuffer(AgentScopeRunContext context,
                                                                   AgentEvent event,
                                                                   String replyId,
                                                                   String toolCallId,
                                                                   String toolName) {
        AgentScopeToolResultKey key = createToolResultKey(event, replyId, toolCallId);
        AgentScopeToolResultBuffer buffer = context.getToolResultBuffers()
                .computeIfAbsent(key, ignored -> new AgentScopeToolResultBuffer());
        buffer.updateToolName(toolName);
        return buffer;
    }

    /**
     * 创建工具结果事件关联键。
     *
     * @param event      原生工具结果事件
     * @param replyId    模型回复ID
     * @param toolCallId 工具调用ID
     * @return 工具结果事件关联键
     */
    private AgentScopeToolResultKey createToolResultKey(AgentEvent event,
                                                        String replyId,
                                                        String toolCallId) {
        return new AgentScopeToolResultKey(event.getSource(), replyId, toolCallId);
    }

    /**
     * AgentScope流结束后收口当前执行分段。
     *
     * @param context 当前Run上下文
     * @param actions 当前Run行为权柄
     */
    private void finishSegment(AgentScopeRunContext context, RuntimeActions actions) {
        if (context.getState() != AgentRunState.RUNNING) {
            return;
        }

        if (context.getUnsupportedInteraction() != null) {
            String message = "AgentScope interaction is not supported: "
                    + context.getUnsupportedInteraction().name();
            failed(context, actions, SystemIntervalException.of(message),
                    AgentResultCode.FAILED_EXECUTE_AGENT.getCode(), message);
            return;
        }

        if (context.getResult() == null) {
            String message = "AgentScope execution completed without AgentResultEvent";
            failed(context, actions, SystemIntervalException.of(message),
                    AgentResultCode.FAILED_EXECUTE_AGENT.getCode(), message);
            return;
        }

        complete(context, actions);
    }

    /**
     * 将技术栈异常收敛为框架允许向外暴露的异常类型。
     *
     * @param error 原始异常
     * @return 框架异常
     */
    private Throwable normalizeError(Throwable error) {
        if (error instanceof BizException) {
            return error;
        }
        log.error("Failed execute AgentScope agent, agentName:{}", agentName, error);
        return SystemIntervalException.of(StringUtils.defaultIfBlank(
                error.getMessage(), "Failed execute AgentScope agent"));
    }

    /**
     * 创建AgentScope单次调用上下文。
     *
     * @param request Agent请求
     * @param runId   本次运行ID
     * @return AgentScope运行上下文
     */
    protected RuntimeContext createRuntimeContext(AgentRequest request, String runId) {
        var builder = RuntimeContext.builder()
                .sessionId(request.getConversationId())
                .put(RUN_ID_ATTRIBUTE, runId);
        if (StringUtils.isNotBlank(request.getUserId())) {
            builder.userId(request.getUserId());
        }
        if (StringUtils.isNotBlank(request.getMessageId())) {
            builder.put(MESSAGE_ID_ATTRIBUTE, request.getMessageId());
        }
        return builder.build();
    }

}
