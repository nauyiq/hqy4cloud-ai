package com.fons.cloud.ai.agent.core;

import com.fons.cloud.ai.agent.model.runtime.RuntimeActions;
import lombok.experimental.SuperBuilder;

import java.io.Serial;

/**
 * AgentScope单次Run的行为权柄。
 *
 * <p>当前阶段沿用RuntimeActions默认的事件、完成和硬取消行为，仅建立AgentScope专用
 * 扩展类型；后续原生中断或Run级资源出现时，再通过父类扩展点接入。</p>
 *
 * @author hongqy
 */
@SuperBuilder
public class AgentScopeRuntimeActions extends RuntimeActions {

    @Serial
    private static final long serialVersionUID = 1L;

}
