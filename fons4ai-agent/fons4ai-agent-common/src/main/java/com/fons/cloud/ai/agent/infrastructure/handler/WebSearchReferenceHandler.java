package com.fons.cloud.ai.agent.infrastructure.handler;

import com.alibaba.fastjson2.JSON;
import com.fons.cloud.ai.agent.api.AgentMessageEmitter;
import com.fons.cloud.ai.agent.api.AgentToolResultHandler;
import com.fons.cloud.ai.agent.model.message.MessageContentType;
import com.fons.cloud.ai.agent.model.runtime.AgentRunContext;
import com.fons.cloud.ai.tool.common.model.AgentToolResult;
import com.fons.cloud.ai.tool.common.model.ToolInfo;
import com.fons.cloud.ai.tool.common.model.web.WebBaseResult;

import java.util.List;

/**
 * 将网络搜索与网页提取工具的解析结果投影为 Agent Reference 消息。
 *
 * <p>本处理器不执行工具、不解析原始响应，也不参与 Agent 生命周期流转，
 * 可作为任意 Agent 的工具结果处理器单独使用或加入组合处理器。</p>
 *
 * @author hongqy
 */
public final class WebSearchReferenceHandler implements AgentToolResultHandler {

    @Override
    public boolean supports(AgentToolResult result) {
        ToolInfo toolInfo = result.toolInfo();
        return toolInfo != null && (toolInfo.isSearch() || toolInfo.isExtract());
    }

    @Override
    public void handle(AgentRunContext context,
                       AgentToolResult result,
                       AgentMessageEmitter emitter) {
        List<WebBaseResult> references = result.parsedResults().stream()
                .filter(WebBaseResult.class::isInstance)
                .map(WebBaseResult.class::cast)
                .toList();
        if (references.isEmpty()) {
            return;
        }

        references.forEach(context::appendReference);
        emitter.emit(JSON.toJSONString(references), MessageContentType.REFERENCE);
    }
}
