package com.fons.cloud.ai.exception;

import com.fons.cloud.ai.response.ResponseInfo;

import static com.fons.cloud.ai.constants.AiResultCode.SYSTEM_INTERVAL_ERROR;

/**
 * @author hongqy
 * @date 2026/3/11
 */
public class BsException extends RuntimeException implements ResponseInfo {

    private final String code;

    @Override
    public String getCode() {
        return this.code;
    }

    public static BsException of(String code) {
        return new BsException(code);
    }

    public static BsException of(String code, String message) {
        return new BsException(code, message);
    }

    public static BsException of(String code, String message, Throwable cause) {
        return new BsException(code, message, cause);
    }

    public static BsException of(ResponseInfo responseInfo) {
        return new BsException(responseInfo);
    }

    public static BsException of(ResponseInfo responseInfo, Throwable cause) {
        return new BsException(responseInfo.getCode(), responseInfo.getMessage(), cause);
    }


    public BsException() {
        super(SYSTEM_INTERVAL_ERROR.getMessage());
        this.code = SYSTEM_INTERVAL_ERROR.getCode();
    }

    public BsException(String message) {
        super(message);
        this.code = SYSTEM_INTERVAL_ERROR.getCode();
    }

    public BsException(Throwable cause) {
        super(cause);
        this.code = SYSTEM_INTERVAL_ERROR.getCode();
    }

    public BsException(ResponseInfo responseInfo) {
        super(responseInfo.getMessage());
        this.code = responseInfo.getCode();
    }

    public BsException(String code, String message) {
        super(message);
        this.code = code;
    }

    public BsException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

}
