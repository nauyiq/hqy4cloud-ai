package com.fons.cloud.ai.ocr.model.response;

import com.fons.cloud.ai.ocr.constants.OcrProvider;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * OCR文档解析结果
 * @author hongqy
 */
@Getter
@Setter
@ToString
@SuperBuilder
public class OcrDocumentResult implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * OCR提供者
     */
    private OcrProvider provider;

    /**
     * markdown 内容
     */
    private String markdown;

    /**
     * 按官方结果顺序返回的页面及图片 URL
     */
    List<OcrDocumentPageResult> pages;


}
