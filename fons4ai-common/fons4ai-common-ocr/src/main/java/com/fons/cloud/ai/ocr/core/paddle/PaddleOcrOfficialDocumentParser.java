package com.fons.cloud.ai.ocr.core.paddle;

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
import com.fons.cloud.common.base.exception.BusinessRuntimeException;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * PaddleOCR 官方异步文档解析协议适配器。
 * <p>
 * 协议为提交任务、轮询状态、下载 JSONL 结果三步；不持久化 jobId，不在失败时改派本地 Provider。
 *
 * @author hongqy
 */
@Slf4j
@RequiredArgsConstructor
public class PaddleOcrOfficialDocumentParser implements OcrDocumentParser {

    private static final String JOBS_PATH = "/api/v2/ocr/jobs";

    private static final String DEFAULT_MODEL = "PaddleOCR-VL-1.6";

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);

    private static final Duration POLL_TIMEOUT = Duration.ofMinutes(10);

    private static final Duration POLL_INTERVAL = Duration.ofSeconds(2);

    private final OcrParsedConfigProperties properties;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(REQUEST_TIMEOUT)
            .build();

    @Override
    public OcrProvider provider() {
        return OcrProvider.PADDLE_OCR_OFFICIAL;
    }

    @Override
    public R<OcrDocumentResult> parse(OcrDocumentRequest request) {
        if (request == null) {
            return R.failed(ResultCode.PARAM_UNDEFINED);
        }
        if (StringUtils.isBlank(properties.getBaseUrl())) {
            return R.failed(ResultCode.PARAM_UNDEFINED);
        }
        try {
            String jobId = submit(request);
            return fetchResult(jobId);
        } catch (BusinessRuntimeException exception) {
            log.error("Failed execute ocr official parse, code: {}, cause: {}", exception.getCode(), exception.getMessage());
            return R.failed(exception.getCode(), exception.getMessage());
        } catch (RuntimeException exception) {
            log.error("Failed to parse ocr official response, cause: {}", exception.getMessage(), exception);
            return R.failed(AgentCommonResultCode.OCR_DOCUMENT_RESPONSE_INVALID.getCode(), exception.getMessage());
        }
    }

    private String submit(OcrDocumentRequest request) {
        String boundary = "----fons4aiPaddleOcr" + UUID.randomUUID().toString().replace("-", "");
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(pathUri(JOBS_PATH))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + accessToken())
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(multipartBody(boundary, request)))
                .build();
        JSONObject data = unwrap(send(httpRequest, "提交官方解析任务"));
        String jobId = data.getString("jobId");
        Assert.notBlank(jobId, () -> new SystemIntervalException("提交结果缺少 jobId"));
        return jobId;
    }

    private R<OcrDocumentResult> fetchResult(String jobId) {
        String resultUrl = pollForResultUrl(jobId);
        List<OcrDocumentPageResult> pages = parsePages(downloadResult(resultUrl));
        String markdown = pages.stream().map(OcrDocumentPageResult::getMarkdown).collect(Collectors.joining("\n\n"));
        OcrDocumentResult result = OcrDocumentResult.builder()
                .provider(OcrProvider.PADDLE_OCR_OFFICIAL)
                .markdown(markdown)
                .pages(pages)
                .build();
        return R.ok(result);
    }

    private String pollForResultUrl(String jobId) {
        Instant deadline = Instant.now().plus(POLL_TIMEOUT);
        while (Instant.now().isBefore(deadline)) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(pathUri(JOBS_PATH + "/" + jobId))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Authorization", "Bearer " + accessToken())
                    .GET()
                    .build();
            JSONObject data = unwrap(send(request, "查询官方解析任务"));
            String state = data.getString("state");
            if ("done".equals(state)) {
                JSONObject resultUrl = data.getJSONObject("resultUrl");
                Assert.notNull(resultUrl, () -> new SystemIntervalException("查询结果缺少 resultUrl"));
                String jsonUrl = resultUrl.getString("jsonUrl");
                Assert.notBlank(jsonUrl, () -> new SystemIntervalException("查询结果缺少 resultUrl.jsonUrl"));
                return jsonUrl;
            }
            if ("failed".equals(state)) {
                throw BusinessRuntimeException.of(AgentCommonResultCode.OCR_DOCUMENT_PARSE_FAILED);
            }
            if (!("pending".equals(state) || "running".equals(state))) {
                throw BusinessRuntimeException.of(AgentCommonResultCode.OCR_DOCUMENT_RESPONSE_INVALID);
            }
            sleepBeforeNextPoll(deadline);
        }
        throw BusinessRuntimeException.of(AgentCommonResultCode.OCR_DOCUMENT_PARSE_TIMEOUT);
    }

    private String downloadResult(String resultUrl) {
        URI uri = URI.create(resultUrl);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
        HttpResponse<String> response = send(request, "下载官方解析结果");
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw BusinessRuntimeException.of(AgentCommonResultCode.OCR_DOCUMENT_PARSE_FAILED);
        }
        return response.body();
    }

    private List<OcrDocumentPageResult> parsePages(String jsonl) {
        List<OcrDocumentPageResult> pages = new ArrayList<>();
        for (String line : jsonl.split("\\R")) {
            if (line.isBlank()) {
                continue;
            }
            JSONObject root = JSON.parseObject(line);
            Assert.notNull(root, () -> new SystemIntervalException("JSONL 行必须是对象"));
            JSONObject result = root.getJSONObject("result");
            Assert.notNull(result, () -> new SystemIntervalException("JSONL 行缺少 result"));
            JSONArray layoutResults = result.getJSONArray("layoutParsingResults");
            Assert.notNull(layoutResults, () -> new SystemIntervalException("JSONL 行缺少 layoutParsingResults"));
            for (int index = 0; index < layoutResults.size(); index++) {
                JSONObject page = layoutResults.getJSONObject(index);
                Assert.notNull(page, () -> new SystemIntervalException("layoutParsingResults 页面不可为空"));
                JSONObject markdown = page.getJSONObject("markdown");
                Assert.notNull(markdown, () -> new SystemIntervalException("页面缺少 markdown"));
                String text = markdown.getString("text");
                Assert.notBlank(text, () -> new SystemIntervalException("页面 markdown.text 不可为空"));
                pages.add(OcrDocumentPageResult.builder()
                        .markdown(text)
                        .markdownImages(optionalImageUrls(markdown.getJSONObject("images")))
                        .outputImages(optionalImageUrls(page.getJSONObject("outputImages")))
                        .build());
            }
        }
        if (pages.isEmpty()) {
            throw new SystemIntervalException("解析结果不包含任何页面");
        }
        return pages;
    }

    private Map<String, String> optionalImageUrls(JSONObject images) {
        if (images == null) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : images.entrySet()) {
            if (!(entry.getValue() instanceof String url) || url.isBlank()) {
                throw new SystemIntervalException("图片地址必须是非空字符串");
            }
            result.put(entry.getKey(), url);
        }
        return result;
    }

    private JSONObject unwrap(HttpResponse<String> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw BusinessRuntimeException.of(AgentCommonResultCode.OCR_DOCUMENT_PARSE_FAILED);
        }
        JSONObject root = JSON.parseObject(response.body());
        Assert.notNull(root, () -> new SystemIntervalException("响应顶层必须是对象"));
        JSONObject data = root.getJSONObject("data");
        Assert.notNull(data, () -> new SystemIntervalException("响应缺少 data"));
        return data;
    }

    private HttpResponse<String> send(HttpRequest request, String operation) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (HttpTimeoutException exception) {
            throw BusinessRuntimeException.of(AgentCommonResultCode.OCR_DOCUMENT_PARSE_TIMEOUT.getCode(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw BusinessRuntimeException.of(AgentCommonResultCode.OCR_DOCUMENT_PARSE_FAILED.getCode(), exception);
        } catch (IOException exception) {
            throw BusinessRuntimeException.of(AgentCommonResultCode.OCR_DOCUMENT_PARSE_FAILED.getCode(), exception);
        }
    }

    private void sleepBeforeNextPoll(Instant deadline) {
        Duration remaining = Duration.between(Instant.now(), deadline);
        Duration sleep = POLL_INTERVAL.compareTo(remaining) < 0 ? POLL_INTERVAL : remaining;
        if (sleep.isZero() || sleep.isNegative()) {
            return;
        }
        try {
            Thread.sleep(sleep);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw BusinessRuntimeException.of(AgentCommonResultCode.OCR_DOCUMENT_PARSE_FAILED.getCode(), exception);
        }
    }

    private byte[] multipartBody(String boundary, OcrDocumentRequest request) {
        String prefix = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"model\"\r\n\r\n" + model() + "\r\n"
                + "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + safeFileName(request.getFileName()) + "\"\r\n"
                + "Content-Type: application/octet-stream\r\n\r\n";
        byte[] prefixBytes = prefix.getBytes(StandardCharsets.UTF_8);
        byte[] content = request.getContent();
        byte[] suffix = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[prefixBytes.length + content.length + suffix.length];
        System.arraycopy(prefixBytes, 0, result, 0, prefixBytes.length);
        System.arraycopy(content, 0, result, prefixBytes.length, content.length);
        System.arraycopy(suffix, 0, result, prefixBytes.length + content.length, suffix.length);
        return result;
    }

    private String model() {
        String model = properties.getModel();
        return StringUtils.isBlank(model) ? DEFAULT_MODEL : model;
    }

    private String accessToken() {
        String accessToken = properties.getAccessToken();
        return accessToken == null ? "" : accessToken;
    }

    private URI pathUri(String path) {
        String base = properties.getBaseUrl();
        String normalized = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        return URI.create(normalized + path);
    }

    private String safeFileName(String fileName) {
        String name = fileName.replace('\\', '/');
        int separator = name.lastIndexOf('/');
        String baseName = separator >= 0 ? name.substring(separator + 1) : name;
        String cleaned = baseName.replaceAll("[\\r\\n\"]", "");
        return cleaned.isBlank() ? "document" : cleaned;
    }
}