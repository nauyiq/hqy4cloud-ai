package com.fons.cloud.ai.tool.common.constants;

import com.fons.cloud.common.result.Result;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 工具结果处理错误码。
 *
 * @author hongqy
 */
@Getter
@AllArgsConstructor
public enum ToolResultCode implements Result {

    TOOL_NOT_REGISTERED("TO200001", "工具未注册"),
    TOOL_RESULT_PARSER_NOT_FOUND("TO200002", "工具结果解析器不存在"),
    FAILED_PARSE_TOOL_RESULT("TO999991", "工具结果解析失败"),
    ;

    private final String code;
    private final String message;

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
