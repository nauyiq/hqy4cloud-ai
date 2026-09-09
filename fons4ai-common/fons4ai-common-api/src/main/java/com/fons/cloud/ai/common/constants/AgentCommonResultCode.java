package com.fons.cloud.ai.common.constants;

import com.fons.cloud.common.result.Result;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author hongqy
 */
@Getter
@AllArgsConstructor
public enum AgentCommonResultCode implements Result {

    RECOGNIZE_IMAGE_FILE_IS_EMPTY("RA100002", "识别图片文件为空"),
    PADDLEOCR_DOCUMENT_REQUEST_INVALID("RA100003", "PaddleOCR 文档解析请求参数异常"),

    NOT_FOUND_OCR_PROVIDER("AG200001", "OCR提供者不存在，请检查"),
    NOT_SUPPORT_IMAGE_GEN_PROVIDER("AG200003", "不支持的图片生成提供者"),



    FAILED_EXECUTE_MULTIMODAL_IMAGE_RECOGNITION("RA999993", "多模态图片识别异常"),
    OCR_DOCUMENT_PARSE_TIMEOUT("RA999994", "OCR文档解析超时"),
    OCR_DOCUMENT_PARSE_FAILED("RA999995", "OCR文档解析异常"),
    OCR_DOCUMENT_RESPONSE_INVALID("RA999996", "OCR文档解析响应异常");


    ;

    private final String code;
    private final String message;


}
