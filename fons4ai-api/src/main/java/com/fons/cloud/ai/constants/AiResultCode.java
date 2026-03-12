package com.fons.cloud.ai.constants;

import com.fons.cloud.ai.response.ResponseInfo;

/**
 * @author hongqy
 * @date 2026/3/11
 */
public enum AiResultCode implements ResponseInfo {

    //  ==================== 成功 ====================
    SUCCESS("000000", "成功"),


    //  ==================== 参数异常 ====================


    //  ==================== 数据异常 ====================
    FAILED_EXECUTED_READ_DOCUMENT("200100", "文档读取失败"),
    INVALID_DOCUMENT_FILES("200101", "无效的文档文件"),
    INVALID_DOCUMENT_TYPE("200102", "无效的文档类型"),




    //  ==================== 系统异常 ====================

    SYSTEM_INTERVAL_ERROR("999999", "系统内部错误"),



    ;

    AiResultCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String getMessage() {
        return this.message;
    }

    @Override
    public String getCode() {
        return this.code;
    }

    ;

    private final String code;

    private final String message;
}
