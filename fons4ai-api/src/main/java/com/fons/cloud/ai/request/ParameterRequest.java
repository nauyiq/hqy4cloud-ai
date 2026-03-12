package com.fons.cloud.ai.request;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * @author hongqy
 * @date 2026/3/11
 */
@Getter
public class ParameterRequest extends BaseRequest {

    private final Map<String, Object> parameters = new HashMap<>();

    public void addParameter(String key, Object value) {
        this.parameters.put(key, value);
    }

    public void addParameters(Map<String, Object> parameters) {
        this.parameters.putAll(parameters);
    }

    public Object getString(String key) {
        return getString(key, null);
    }

    public String getString(String key, String defaultValue) {
        return (String) this.parameters.getOrDefault(key, defaultValue);
    }

    @SuppressWarnings("unchecked")
    public <T> T getObj(String key, T defaultValue) {
        return (T) this.parameters.getOrDefault(key, defaultValue);
    }

}
