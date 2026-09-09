package com.fons.cloud.ai.tool.autoconfigure;

import com.fons.cloud.ai.tool.core.ToolRegistry;
import com.fons.cloud.ai.tool.core.ToolResultProcessor;
import com.fons.cloud.ai.tool.support.tavily.TavilyProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author hongqy
 */
@Configuration
public class ToolAutoConfiguration {

    @Bean
    public ToolRegistry toolRegistry() {
        return ToolRegistry.getInstance();
    }

    @Bean
    @ConditionalOnMissingBean
    public ToolResultProcessor toolResultProcessor() {
        return ToolResultProcessor.getInstance();
    }

    @Bean
    @ConditionalOnMissingBean
    public TavilyProvider tavilyProvider() {
        return new TavilyProvider();
    }
}
