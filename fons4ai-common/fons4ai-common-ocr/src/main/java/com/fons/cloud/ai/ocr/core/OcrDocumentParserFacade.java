package com.fons.cloud.ai.ocr.core;

import cn.hutool.extra.spring.SpringUtil;
import com.fons.cloud.ai.common.constants.AgentCommonResultCode;
import com.fons.cloud.ai.ocr.api.OcrDocumentParser;
import com.fons.cloud.ai.ocr.config.OcrParsedConfigProperties;
import com.fons.cloud.ai.ocr.constants.OcrProvider;
import com.fons.cloud.ai.ocr.model.request.OcrDocumentRequest;
import com.fons.cloud.ai.ocr.model.response.OcrDocumentResult;
import com.fons.cloud.common.result.R;
import com.fons.cloud.common.result.ResultCode;
import com.google.common.collect.Maps;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.springframework.beans.factory.SmartInitializingSingleton;

import java.util.Map;

/**
 * ORC解析门面
 * @author hongqy
 */
@Slf4j
@RequiredArgsConstructor
public class OcrDocumentParserFacade implements SmartInitializingSingleton {
    private final OcrParsedConfigProperties properties;
    private final Map<OcrProvider, OcrDocumentParser> parserMap = Maps.newHashMapWithExpectedSize(8);


    /**
     * 解析文档
     * @param request
     * @return
     */
    public R<OcrDocumentResult> parse(OcrDocumentRequest request) {
        if (request == null) {
            return R.failed(ResultCode.PARAM_UNDEFINED);
        }
        try {
            OcrProvider provider = properties.getProvider();
            OcrDocumentParser ocrDocumentParser = parserMap.get(provider);
            if (ocrDocumentParser == null) {
                return R.failed(AgentCommonResultCode.NOT_FOUND_OCR_PROVIDER);
            }
            return ocrDocumentParser.parse(request);
        } catch (Exception cause) {
            log.error("Failed execute to orc parse, cause: {}", cause.getMessage(), cause);
            return R.failed(AgentCommonResultCode.OCR_DOCUMENT_PARSE_FAILED);
        }
    }

    @Override
    public void afterSingletonsInstantiated() {
        Map<String, OcrDocumentParser> beans = SpringUtil.getBeansOfType(OcrDocumentParser.class);
        if (MapUtils.isEmpty(beans)) {
            log.warn("Not found any OcrDocumentParser bean.");
        } else {
            beans.forEach((name, bean) -> parserMap.put(bean.provider(), bean));
        }
    }
}
