package com.fons.cloud.ai.rag.common.exception;

import com.fons.cloud.ai.exception.BsException;
import com.fons.cloud.ai.response.ResponseInfo;

import static com.fons.cloud.ai.constants.AiResultCode.FAILED_EXECUTED_READ_DOCUMENT;

/**
 * @author hongqy
 * @date 2026/3/12
 */
public class DocumentReadException extends BsException {

    public static DocumentReadException of(String code) {
        return new DocumentReadException(code);
    }

    public static DocumentReadException of(String code, String message) {
        return new DocumentReadException(code, message);
    }

    public static DocumentReadException of(String code, String message, Throwable cause) {
        return new DocumentReadException(code, message, cause);
    }

    public static DocumentReadException of(ResponseInfo responseInfo) {
        return new DocumentReadException(responseInfo);
    }

    public static DocumentReadException of(BsException bsException) {
        return new DocumentReadException(bsException.getCode(), bsException.getMessage(), bsException);
    }

    public static DocumentReadException of(ResponseInfo responseInfo, Throwable cause) {
        return new DocumentReadException(responseInfo.getCode(), responseInfo.getMessage(), cause);
    }

    public DocumentReadException() {
        super(FAILED_EXECUTED_READ_DOCUMENT.getMessage());
    }

    public DocumentReadException(String message) {
        super(FAILED_EXECUTED_READ_DOCUMENT.getCode(), message);
    }

    public DocumentReadException(Throwable cause) {
        super(FAILED_EXECUTED_READ_DOCUMENT.getCode(), FAILED_EXECUTED_READ_DOCUMENT.getMessage(), cause);
    }

    public DocumentReadException(ResponseInfo responseInfo) {
        super(responseInfo);
    }

    public DocumentReadException(String code, String message) {
        super(code, message);
    }

    public DocumentReadException(String code, String message, Throwable cause) {
        super(code, message, cause);
    }
}
