package com.fons.cloud.ai.ocr.model.response;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

/**
 * 单页解析结果及其官方图片 URL。
 * @author hongqy
 */
@Getter
@Setter
@ToString
@SuperBuilder
public class OcrDocumentPageResult implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * markdown 内容
     */
    private String markdown;

    /**
     * markdown 图片, 相对图片路径到官方图片 URL 的映射
     */
    private Map<String, String> markdownImages;

    /**
     * 可视化结果图片名称到官方图片 URL 的映射, 可视化结果图片名称到官方图片 URL 的映射
     */
    private Map<String, String> outputImages;


}
