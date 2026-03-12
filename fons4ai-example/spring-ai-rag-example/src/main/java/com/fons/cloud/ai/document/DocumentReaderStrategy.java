package com.fons.cloud.ai.document;

import org.springframework.ai.document.Document;

import java.io.File;
import java.util.List;

/**
 * @author hongqy
 * @date 2026/3/10
 */
public interface DocumentReaderStrategy {

    /**
     * Get the type of the document reader.
     *
     * @return the type of the document reader
     */
    DocumentReaderType getType();

    /**
     * Read a file and return a list of documents.
     *
     * @param file the file to read
     * @return a list of documents
     */
    List<Document> read(File file);

}
