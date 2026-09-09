package com.fons.cloud.ai.ocr.core.paddle;

import cn.hutool.core.io.file.FileNameUtil;
import cn.hutool.core.lang.Assert;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.fons.cloud.ai.common.constants.AgentCommonResultCode;
import com.fons.cloud.ai.ocr.api.OcrDocumentParser;
import com.fons.cloud.ai.ocr.config.OcrParsedConfigProperties;
import com.fons.cloud.ai.ocr.constants.OcrProvider;
import com.fons.cloud.ai.ocr.model.request.OcrDocumentRequest;
import com.fons.cloud.ai.ocr.model.response.OcrDocumentPageResult;
import com.fons.cloud.ai.ocr.model.response.OcrDocumentResult;
import com.fons.cloud.common.base.exception.SystemIntervalException;
import com.fons.cloud.common.result.R;
import com.fons.cloud.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

/**
 * 调用方自部署 PaddleOCR-VL layout-parsing 服务的协议适配器。
 * <p>
 * 所有参数固定，避免调用方在无版本约束下改变 Markdown 输出语义。
 *
 * @author hongqy
 */
@Slf4j
@RequiredArgsConstructor
public class PaddleOcrLocalDocumentParser implements OcrDocumentParser {

    private static final String LAYOUT_PARSING_PATH = "/layout-parsing";

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);

    private final OcrParsedConfigProperties properties;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(REQUEST_TIMEOUT)
            .build();

    @Override
    public OcrProvider provider() {
        return OcrProvider.PADDLE_OCR_LOCAL;
    }

    @Override
    public R<OcrDocumentResult> parse(OcrDocumentRequest request) {
        if (request == null) {
            return R.failed(ResultCode.PARAM_UNDEFINED);
        }
        String baseUrl = properties.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            return R.failed(ResultCode.PARAM_UNDEFINED);
        }
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(parsedUri(baseUrl))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestPayload(request), StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(httpRequest,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return parseResponse(response);
        } catch (HttpTimeoutException exception) {
            log.error("Failed execute ocr local parse, timeout, cause: {}", exception.getMessage());
            return R.failed(AgentCommonResultCode.OCR_DOCUMENT_PARSE_TIMEOUT);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.error("Failed execute ocr local parse, interrupted, cause: {}", exception.getMessage());
            return R.failed(AgentCommonResultCode.OCR_DOCUMENT_PARSE_FAILED);
        } catch (IOException exception) {
            log.error("Failed execute ocr local parse, cause: {}", exception.getMessage(), exception);
            return R.failed(AgentCommonResultCode.OCR_DOCUMENT_PARSE_FAILED);
        }
    }

    private R<OcrDocumentResult> parseResponse(HttpResponse<String> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return R.failed(AgentCommonResultCode.OCR_DOCUMENT_PARSE_FAILED);
        }
        try {
            JSONObject root = JSON.parseObject(response.body());
            JSONObject result = root.getJSONObject("result");
            JSONArray pages = result == null ? null : result.getJSONArray("layoutParsingResults");
            if (pages == null || pages.isEmpty()) {
                log.warn("响应缺少 layoutParsingResults 页面");
                return R.failed(AgentCommonResultCode.OCR_DOCUMENT_RESPONSE_INVALID);
            }
            String markdown = extractMarkdown(pages.getJSONObject(0));
            if (StringUtils.isBlank(markdown)) {
                log.warn("响应 markdown.text 不可为空");
                return R.failed(AgentCommonResultCode.OCR_DOCUMENT_RESPONSE_INVALID);
            }
            return R.ok(toResult(markdown));
        } catch (RuntimeException exception) {
            log.error("Failed to parse ocr local response, cause: {}", exception.getMessage(), exception);
            return R.failed(AgentCommonResultCode.OCR_DOCUMENT_RESPONSE_INVALID.getCode(), exception.getMessage());
        }
    }

    private String extractMarkdown(JSONObject page) {
        Assert.notNull(page, () -> new SystemIntervalException("响应 layoutParsingResults 页面不可为空"));
        JSONObject markdown = page.getJSONObject("markdown");
        if (markdown == null) {
            return null;
        }
        return markdown.getString("text");
    }

    private String requestPayload(OcrDocumentRequest request) {
        JSONObject payload = new JSONObject();
        payload.put("file", Base64.getEncoder().encodeToString(request.getContent()));
        payload.put("fileType", isPdf(request.getFileName()) ? 0 : 1);
        payload.put("returnMarkdownImages", false);
        payload.put("visualize", false);
        payload.put("restructurePages", true);
        payload.put("concatenatePages", true);
        return payload.toJSONString();
    }

    private boolean isPdf(String fileName) {
        return "pdf".equalsIgnoreCase(FileNameUtil.getSuffix(fileName));
    }

    private URI parsedUri(String baseUrl) {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return URI.create(base + LAYOUT_PARSING_PATH);
    }

    private OcrDocumentResult toResult(String markdown) {
        OcrDocumentPageResult page = OcrDocumentPageResult.builder()
                .markdown(markdown)
                .markdownImages(Collections.emptyMap())
                .outputImages(Collections.emptyMap())
                .build();
        return OcrDocumentResult.builder()
                .provider(OcrProvider.PADDLE_OCR_LOCAL)
                .markdown(markdown)
                .pages(List.of(page))
                .build();
    }
}
