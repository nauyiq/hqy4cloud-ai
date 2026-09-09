package com.fons.cloud.ai.agent.model.hitl;

/**
 * 人工交互的类型, 与Agent交互过程中 通常存在需要将控制权转移给用户的行为
 *
 * @author hongqy
 */
public enum HumanInTheLoopKind {

    /**
     * 需要从检查点恢复的审批
     */
    APPROVAL,

    /**
     * 用户补充信息。当前 Run 正常结束，用户通过同一 conversation 的普通新请求继续，
     * 不使用检查点恢复
     */
    INPUT_REQUIRED

}
