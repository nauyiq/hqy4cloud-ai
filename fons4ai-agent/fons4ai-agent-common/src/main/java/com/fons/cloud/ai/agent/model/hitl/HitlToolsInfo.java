package com.fons.cloud.ai.agent.model.hitl;

/**
 * 人工交互时需要审批的工具信息
 * @param id       工具id, 可能为空
 * @param toolName 工具名称
 * @param desc     工具描述
 * @param args     工具参数
 * @author hongqy
 */
public record HitlToolsInfo(String id, String toolName, String desc, String args) {




}
