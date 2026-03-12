package com.fons.cloud.ai.document;

import org.springframework.ai.document.Document;

import java.io.File;
import java.util.List;

/**
 * @author hongqy
 * @date 2026/3/10
 */
public abstract class AbstractDocumentReaderStrategy implements DocumentReaderStrategy {

    @Override
    public List<Document> read(File file) {



        return List.of();
    }


    /**
     * Check if the file is supported by the document reader.
     *
     * @param file the file to check
     * @return true if the file is supported, false otherwise
     */
    protected abstract boolean supports(File file);




}
