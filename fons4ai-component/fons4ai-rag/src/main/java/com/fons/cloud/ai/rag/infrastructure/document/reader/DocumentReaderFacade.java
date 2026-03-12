package com.fons.cloud.ai.rag.infrastructure.document.reader;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.map.MapUtil;
import com.fons.cloud.ai.constants.AiResultCode;
import com.fons.cloud.ai.constants.DocumentType;
import com.fons.cloud.ai.exception.BsException;
import com.fons.cloud.ai.rag.common.exception.DocumentReadException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * @author hongqy
 * @date 2026/3/12
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentReaderFacade {

    private final Map<DocumentType, DocumentReaderStrategy> strategies;

    private DocumentReaderFacade(final List<DocumentReaderStrategy> strategiesList) {
        strategies = MapUtil.newHashMap(strategiesList.size());
        strategiesList.forEach(strategy -> strategies.put(strategy.documentType(), strategy));
    }

    public List<Document> read(DocumentReaderContext context) throws DocumentReadException {
        try {
            // 1. 选择策略
            DocumentReaderStrategy usingStrategy = null;
            DocumentType documentType = context.getDocumentType();
            if (documentType != null) {
                usingStrategy = strategies.get(documentType);
            } else {
                for (DocumentReaderStrategy strategy : strategies.values()) {
                    if (strategy.isSupport(context)) {
                        usingStrategy = strategy;
                        break;
                    }
                }
            }
            // 2. 执行策略
            Assert.notNull(usingStrategy, "Not found strategy for document type: " + documentType);
            log.info("Using strategy [{}] to read document, context:{}", documentType, context);
            return usingStrategy.read(context);
        } catch (BsException e) {
            log.warn("Failed execute to read document, code:{}, message:{}", e.getCode(), e.getMessage(), e);
            throw DocumentReadException.of(e);
        } catch (Exception e) {
            log.warn("Failed execute to read document, message:{}", e.getMessage(), e);
            throw DocumentReadException.of(AiResultCode.FAILED_EXECUTED_READ_DOCUMENT, e);
        }
    }



}
