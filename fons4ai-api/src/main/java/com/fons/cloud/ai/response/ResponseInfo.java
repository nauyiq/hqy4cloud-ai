package com.fons.cloud.ai.response;

/**
 * @author hongqy
 * @date 2026/3/10
 */
public interface ResponseInfo {

    /**
     * get response message.
     * @return message.
     */
    String getMessage();

    /**
     * get response code
     * @return code
     */
    String getCode();

}
