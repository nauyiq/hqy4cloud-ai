package com.fons.cloud.ai.ocr.model.request;

import cn.hutool.core.io.file.FileNameUtil;
import cn.hutool.core.lang.Assert;
import com.fons.cloud.common.base.exception.BusinessRuntimeException;
import com.fons.cloud.common.request.BaseRequest;
import com.fons.cloud.common.result.ResultCode;
import lombok.Getter;

import java.util.Set;

/**
 * 单个文件OCR的请求
 *
 * @author hongqy
 */
@Getter
public class OcrDocumentRequest extends BaseRequest {
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("pdf", "png", "jpg", "jpeg");

    /**
     * 文件名
     */
    private final String fileName;

    /**
     * 文件内容
     */
    private final byte[] content;

    public OcrDocumentRequest(String fileName, byte[] content) {
        Assert.notEmpty(fileName, () -> BusinessRuntimeException.of(ResultCode.PARAM_UNDEFINED));
        Assert.isTrue(content != null && content.length > 0, () -> BusinessRuntimeException.of(ResultCode.FILE_IS_EMPTY));

        if (!SUPPORTED_EXTENSIONS.contains(extractExtension(fileName))) {
            throw BusinessRuntimeException.of(ResultCode.NOT_SUPPORT_FILE_TYPE);
        }
        this.fileName = fileName;
        this.content = content;
    }

    private String extractExtension(String fileName) {
        return FileNameUtil.getSuffix(fileName);
    }


    public static OcrDocumentRequest create(String fileName, byte[] content) {
        return new OcrDocumentRequest(fileName, content);
    }


}
