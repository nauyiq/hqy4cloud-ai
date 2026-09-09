package com.fons.cloud.ai.ocr.config;

import com.fons.cloud.ai.ocr.constants.OcrProvider;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author hongqy
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "sys.orc")
public class OcrParsedConfigProperties {

    /**
     * ocr提供者
     */
    private OcrProvider provider;

    /**
     * OCR服务地址
     */
    private String baseUrl;

    /**
     * orc模型
     */
    private String model;

    /**
     * 访问令牌
     */
    private String accessToken;


}
