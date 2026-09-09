package com.fons.cloud.ai.tool.common.model;

import java.util.List;

/**
 * Agent 可消费的工具解析结果。
 *
 * <p>仅表示解析成功的结果；解析失败通过 {@code R<AgentToolResult>} 表达。</p>
 *
 * @param callId 工具调用ID
 * @param toolName 工具名称
 * @param toolInfo 工具注册信息
 * @param rawResult 工具原始响应
 * @param parsedResults 解析后的结果列表，合法空结果使用空列表表示
 * @author hongqy
 */
public record AgentToolResult(
        String callId,
        String toolName,
        ToolInfo toolInfo,
        String rawResult,
        List<?> parsedResults) {

    public AgentToolResult {
        parsedResults = parsedResults == null ? List.of() : List.copyOf(parsedResults);
    }
}
