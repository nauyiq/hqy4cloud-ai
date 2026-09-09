package com.fons.cloud.ai.agent.model.message;

import lombok.*;

import java.io.Serial;
import java.io.Serializable;

/**
 * Agent消息载体
 * @author hongqy
 */
@Getter
@Setter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class AgentMessage implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 消息ID
     */
    private String messageId;

    /**
     * 会话ID
     */
    private String conversationId;

    /**
     * 本次Agent消息类型
     */
    private AgentMessageType messageType;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 消息内容类型
     */
    private MessageContentType contentType;

    /**
     * 时间戳
     */
    private Long timestamp;

}
