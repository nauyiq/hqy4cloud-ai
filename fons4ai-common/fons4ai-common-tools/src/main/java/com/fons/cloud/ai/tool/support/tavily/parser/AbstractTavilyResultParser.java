package com.fons.cloud.ai.tool.support.tavily.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fons.cloud.ai.tool.api.ToolResultParser;

import java.util.List;

/**
 * Tavily 结果解析器公共基类。
 *
 * @param <T> 解析结果类型
 * @author hongqy
 */
public abstract class AbstractTavilyResultParser<T> implements ToolResultParser<T> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public List<T> parse(String result) {
        try {
            JsonNode root = MAPPER.readTree(result);
            if (!root.isArray() || root.isEmpty()) {
                throw new IllegalArgumentException("Tavily response must be a non-empty array");
            }
            JsonNode textNode = root.get(0).get("text");
            if (textNode == null || textNode.isNull()) {
                throw new IllegalArgumentException("Tavily response does not contain text");
            }
            JsonNode textJson = textNode.isTextual() ? MAPPER.readTree(textNode.asText()) : textNode;
            JsonNode resultsNode = textJson.get("results");
            if (resultsNode == null || !resultsNode.isArray()) {
                throw new IllegalArgumentException("Tavily response does not contain a results array");
            }
            return parseResult(resultsNode);
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException illegalArgumentException) {
                throw illegalArgumentException;
            }
            throw new IllegalArgumentException("Failed to parse Tavily result", e);
        }
    }

    protected abstract List<T> parseResult(JsonNode resultsNode);

    protected String getSafe(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
