package com.fons.cloud.ai.ocr.api;

import com.fons.cloud.ai.ocr.constants.OcrProvider;
import com.fons.cloud.ai.ocr.model.request.OcrDocumentRequest;
import com.fons.cloud.ai.ocr.model.response.OcrDocumentResult;
import com.fons.cloud.common.result.R;

/**
 * ocr解析接口
 * @author hongqy
 */
public interface OcrDocumentParser {

    /**
     * 返回该实例创建时明确选择的 Provider。
     *
     * @return Provider
     */
    OcrProvider provider();

    /**
     * ocr解析
     *
     * @param request 请求参数
     * @return 解析结果
     */
    R<OcrDocumentResult> parse(OcrDocumentRequest request);


}
