package com.fons.cloud.ai.tool.core;

import com.fons.cloud.ai.tool.api.ToolResultParser;
import com.fons.cloud.ai.tool.common.constants.ToolResultCode;
import com.fons.cloud.ai.tool.common.model.AgentToolResult;
import com.fons.cloud.ai.tool.common.model.ToolInfo;
import com.fons.cloud.common.result.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 工具原始响应的统一解析入口。
 *
 * <p>解析成功与失败只通过 {@link R} 表达；合法的空结果属于解析成功。</p>
 *
 * @author hongqy
 */
@Slf4j
@RequiredArgsConstructor
public class ToolResultProcessor {

    private final ToolRegistry toolRegistry;

    private static final ToolResultProcessor INSTANCE = new ToolResultProcessor(ToolRegistry.getInstance());
    public static ToolResultProcessor getInstance() {
        return INSTANCE;
    }

    /**
     * 判断工具是否由 common-tools 注册并管理。
     *
     * <p>未注册的框架内部工具不需要进入业务结果解析链路。</p>
     *
     * @param toolName 工具名称
     * @return 是否属于受管理工具
     */
    public boolean supports(String toolName) {
        return toolRegistry.getToolInfo(toolName) != null;
    }

    /**
     * 解析工具原始响应。
     *
     * @param callId 工具调用ID
     * @param toolName 工具名称
     * @param rawResult 工具原始响应
     * @return 解析结果
     */
    public R<AgentToolResult> process(String callId, String toolName, String rawResult) {
        ToolInfo toolInfo = toolRegistry.getToolInfo(toolName);
        if (toolInfo == null) {
            return R.failed(ToolResultCode.TOOL_NOT_REGISTERED);
        }

        ToolResultParser<?> parser = toolRegistry.getToolResultParser(toolName);
        if (parser == null) {
            return R.failed(ToolResultCode.TOOL_RESULT_PARSER_NOT_FOUND);
        }

        try {
            List<?> parsedResults = parser.parse(rawResult);
            AgentToolResult result = new AgentToolResult(
                    callId,
                    toolName,
                    toolInfo,
                    rawResult,
                    parsedResults);
            return R.success(result);
        } catch (RuntimeException exception) {
            // 工具响应可能包含外部正文或敏感数据，日志不输出 rawResult。
            log.warn("Failed to parse tool result, toolName:{}", toolName, exception);
            return R.failed(ToolResultCode.FAILED_PARSE_TOOL_RESULT);
        }
    }
}
