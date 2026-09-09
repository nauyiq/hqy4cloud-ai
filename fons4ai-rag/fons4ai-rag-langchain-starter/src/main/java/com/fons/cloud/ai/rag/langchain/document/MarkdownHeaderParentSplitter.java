package com.fons.cloud.ai.rag.langchain.document;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Markdown 标题层级分块器（父子模式）。
 * <p>
 * 参考 know-engine 的 MarkdownHeaderParentTextSplitter 实现，适配 fons4ai 包结构。
 * <ul>
 *   <li>按 Markdown 标题层级（{@code #}~{@code ######}）切分文档</li>
 *   <li>维护标题栈处理层级回退（如从 {@code ###} 回到 {@code ##}）</li>
 *   <li>识别代码块标记（``` 和 ~~~），代码块内不检测标题</li>
 *   <li>超长片段（超出 chunkSize）二次切割为父子模式：
 *     <ul>
 *       <li>保留完整父块，标记 {@code skipEmbedding=1}（不向量化）</li>
 *       <li>生成多个子块，携带 {@code parentChunkId} 指向父块</li>
 *       <li>子块之间有 overlap 字符重叠</li>
 *     </ul>
 *   </li>
 *   <li>每个分块携带标题元数据（title/subtitle/.../headerLevel）</li>
 * </ul>
 *
 * @author hongqy
 */
public class MarkdownHeaderParentSplitter implements DocumentSplitter {

    private static final String[] HEADER_NAMES = {
            "title", "subtitle", "subsubtitle", "subsubsubtitle",
            "subsubsubsubtitle", "subsubsubsubsubtitle"
    };

    /** 标题分割映射表，按标记长度倒序排列 */
    private final List<Map.Entry<String, String>> headersToSplitOn;

    /** 是否按行返回结果，false 时聚合相同元数据的行 */
    private final boolean returnEachLine;

    /** 是否剥离标题行本身 */
    private final boolean stripHeaders;

    /** 每个分片的最大字符数，0 表示不限制 */
    private final int chunkSize;

    /** 相邻分片之间的重叠字符数 */
    private final int overlap;

    /**
     * 通过标题级别构造。
     *
     * @param titleLevel 标题级别（1-6），表示按 1 到 titleLevel 级标题分割
     * @param chunkSize  每个分片最大字符数，0 表示不限制
     * @param overlap    相邻分片重叠字符数
     * @throws IllegalArgumentException titleLevel 不在 1-6 范围时抛出
     */
    public MarkdownHeaderParentSplitter(int titleLevel, int chunkSize, int overlap) {
        this(buildHeadersMap(titleLevel), false, false, chunkSize, overlap);
    }

    /**
     * 通过标题映射表构造。
     *
     * @param headersToSplitOn 标题分割映射表，key 为标记（如 "#"），value 为元数据键名
     * @param returnEachLine   是否按行返回，false 时聚合相同元数据的行
     * @param stripHeaders     是否移除标题行
     * @param chunkSize        每个分片最大字符数，0 表示不限制
     * @param overlap          相邻分片重叠字符数
     */
    public MarkdownHeaderParentSplitter(Map<String, String> headersToSplitOn,
                                        boolean returnEachLine, boolean stripHeaders,
                                        int chunkSize, int overlap) {
        this.headersToSplitOn = headersToSplitOn.entrySet().stream()
                .sorted(Comparator.comparingInt(e -> -e.getKey().length()))
                .collect(Collectors.toList());
        this.returnEachLine = returnEachLine;
        this.stripHeaders = stripHeaders;
        this.chunkSize = chunkSize;
        this.overlap = overlap;
    }

    /**
     * 根据标题级别生成标题分割映射表。
     *
     * @param titleLevel 标题级别（1-6）
     * @return 标题分割映射表
     * @throws IllegalArgumentException titleLevel 不在 1-6 范围时抛出
     */
    private static Map<String, String> buildHeadersMap(int titleLevel) {
        if (titleLevel < 1 || titleLevel > 6) {
            throw new IllegalArgumentException("titleLevel 必须在 1-6 范围内，当前值: " + titleLevel);
        }
        Map<String, String> headers = new LinkedHashMap<>();
        for (int i = 1; i <= titleLevel; i++) {
            headers.put("#".repeat(i), HEADER_NAMES[i - 1]);
        }
        return headers;
    }

    @Override
    public List<TextSegment> split(Document document) {
        List<DocumentWithMetadata> segments = splitWithMetadata(document.text(), document.metadata().toMap(), List.of());
        return segments.stream()
                .map(seg -> new TextSegment(seg.content, Metadata.from(seg.metadata)))
                .toList();
    }

    /**
     * 分块并为每个输出片段返回输入文本范围对应的来源标识。
     *
     * <p>该方法只补充来源追溯，不改变 Markdown 标题、父子关系或 SDK 二次分块算法。超长
     * 标题范围产生的 Parent/Child 均继承该标题范围内的完整来源集合。</p>
     *
     * @param document 输入 Markdown 文档
     * @param sourceRanges 输入文本中按字符偏移标记的来源范围
     * @return 带来源标识的分块结果
     */
    public List<SourcedTextSegment> split(Document document, List<SourceRange> sourceRanges) {
        Objects.requireNonNull(document, "document 不可为空");
        List<SourceRange> validatedRanges = validateSourceRanges(document.text(), sourceRanges);
        return splitWithMetadata(document.text(), document.metadata().toMap(), validatedRanges).stream()
                .map(segment -> new SourcedTextSegment(
                        new TextSegment(segment.content, Metadata.from(segment.metadata)), segment.sourceIds))
                .toList();
    }

    /**
     * 核心分割逻辑，保留元数据。
     *
     * @param text         待分割的文本
     * @param baseMetadata 基础元数据
     * @return 带元数据的文档片段列表
     */
    private List<DocumentWithMetadata> splitWithMetadata(
            String text, Map<String, Object> baseMetadata, List<SourceRange> sourceRanges) {
        List<String> lines = List.of(text.split("\n"));
        List<Line> linesWithMetadata = new ArrayList<>();
        List<String> currentContent = new ArrayList<>();
        Set<String> currentSourceIds = new LinkedHashSet<>();
        Map<String, Object> currentMetadata = new HashMap<>(baseMetadata);
        List<Header> headerStack = new ArrayList<>();
        Map<String, Object> initialMetadata = new HashMap<>(baseMetadata);

        boolean inCodeBlock = false;
        String openingFence = "";
        int lineOffset = 0;

        for (String line : lines) {
            String strippedLine = line.trim();
            List<String> lineSourceIds = sourceIdsFor(sourceRanges, lineOffset, lineOffset + line.length());

            // 代码块标记检测
            if (!inCodeBlock) {
                if (strippedLine.startsWith("```")) {
                    inCodeBlock = true;
                    openingFence = "```";
                } else if (strippedLine.startsWith("~~~")) {
                    inCodeBlock = true;
                    openingFence = "~~~";
                }
            } else {
                if (strippedLine.startsWith(openingFence)) {
                    inCodeBlock = false;
                    openingFence = "";
                }
            }

            if (inCodeBlock) {
                currentContent.add(line);
                currentSourceIds.addAll(lineSourceIds);
                lineOffset += line.length() + 1;
                continue;
            }

            // 标题检测
            boolean isHeader = false;
            for (Map.Entry<String, String> header : headersToSplitOn) {
                String sep = header.getKey();
                String name = header.getValue();

                if (strippedLine.startsWith(sep) &&
                        (strippedLine.length() == sep.length() || strippedLine.charAt(sep.length()) == ' ')) {

                    int currentHeaderLevel = sep.length();

                    // 维护标题栈：移除所有级别 >= 当前的标题
                    while (!headerStack.isEmpty() &&
                            headerStack.get(headerStack.size() - 1).level >= currentHeaderLevel) {
                        Header popped = headerStack.remove(headerStack.size() - 1);
                        initialMetadata.remove(popped.name);
                    }

                    Header newHeader = new Header(currentHeaderLevel, name,
                            strippedLine.substring(sep.length()).trim());
                    headerStack.add(newHeader);
                    initialMetadata.put(name, newHeader.data);
                    initialMetadata.put(MetadataKeyConstants.HEADER_LEVEL, currentHeaderLevel);
                    initialMetadata.put(MetadataKeyConstants.CHUNK_ID, UUID.randomUUID().toString());

                    // 遇到新标题时保存之前累积的内容
                    if (hasMeaningfulContent(currentContent)) {
                        linesWithMetadata.add(new Line(String.join("\n", currentContent), currentMetadata,
                                currentSourceIds));
                    }
                    currentContent.clear();
                    currentSourceIds.clear();

                    if (!stripHeaders) {
                        currentContent.add(line);
                        currentSourceIds.addAll(lineSourceIds);
                    }

                    isHeader = true;
                    break;
                }
            }

            if (!isHeader) {
                currentContent.add(line);
                currentSourceIds.addAll(lineSourceIds);
            }

            currentMetadata = new HashMap<>(initialMetadata);
            lineOffset += line.length() + 1;
        }

        // 处理最后累积的内容
        if (hasMeaningfulContent(currentContent)) {
            linesWithMetadata.add(new Line(String.join("\n", currentContent), currentMetadata, currentSourceIds));
        }

        // 聚合模式
        List<DocumentWithMetadata> segments;
        if (!returnEachLine) {
            segments = aggregateLinesToChunks(linesWithMetadata);
        } else {
            segments = linesWithMetadata.stream()
                    .map(line -> new DocumentWithMetadata(line.content, line.metadata, line.sourceIds))
                    .toList();
        }

        // chunkSize 二次切割
        if (chunkSize > 0) {
            segments = splitByChunkSize(segments);
        }

        return segments;
    }

    /**
     * 聚合相同元数据的行为一个分块。
     *
     * @param lines 行列表
     * @return 聚合后的分块列表
     */
    private List<DocumentWithMetadata> aggregateLinesToChunks(List<Line> lines) {
        List<Line> aggregated = new ArrayList<>();
        for (Line line : lines) {
            if (!aggregated.isEmpty() &&
                    aggregated.get(aggregated.size() - 1).metadata.equals(line.metadata)) {
                Line last = aggregated.get(aggregated.size() - 1);
                last.content = last.content + "\n" + line.content;
                last.sourceIds.addAll(line.sourceIds);
            } else {
                aggregated.add(new Line(line.content, new HashMap<>(line.metadata), line.sourceIds));
            }
        }
        return aggregated.stream()
                .map(chunk -> new DocumentWithMetadata(chunk.content, chunk.metadata, chunk.sourceIds))
                .toList();
    }

    /**
     * 对超出 chunkSize 的分片进行父子模式二次切割。
     * <p>
     * 保留完整父块（skipEmbedding=1），生成子块（parentChunkId 关联）。
     *
     * @param segments 原始分片列表
     * @return 切割后的分片列表
     */
    private List<DocumentWithMetadata> splitByChunkSize(List<DocumentWithMetadata> segments) {
        List<DocumentWithMetadata> result = new ArrayList<>();
        for (DocumentWithMetadata segment : segments) {
            String content = segment.content;
            if (content.length() <= chunkSize) {
                result.add(segment);
            } else {
                ParentChildDocumentSplitter splitter =
                        new ParentChildDocumentSplitter(content.length(), chunkSize, overlap);
                Document childDocument = Document.from(content, Metadata.from(segment.metadata));
                splitter.split(childDocument).forEach(textSegment ->
                        result.add(new DocumentWithMetadata(
                                textSegment.text(), textSegment.metadata().toMap(), segment.sourceIds)));
            }
        }
        return result;
    }

    /** 空行保留在相邻内容中，但不单独形成无语义片段。 */
    private boolean hasMeaningfulContent(List<String> lines) {
        return lines.stream().anyMatch(line -> !line.isBlank());
    }

    private List<SourceRange> validateSourceRanges(String text, List<SourceRange> sourceRanges) {
        Objects.requireNonNull(sourceRanges, "sourceRanges 不可为空");
        int previousEnd = 0;
        for (SourceRange range : sourceRanges) {
            if (range.startInclusive < previousEnd || range.endExclusive > text.length()) {
                throw new IllegalArgumentException("来源范围必须按文本偏移有序且不重叠");
            }
            previousEnd = range.endExclusive;
        }
        return List.copyOf(sourceRanges);
    }

    private List<String> sourceIdsFor(List<SourceRange> sourceRanges, int startInclusive, int endExclusive) {
        return sourceRanges.stream()
                .filter(range -> range.startInclusive < endExclusive && range.endExclusive > startInclusive)
                .map(SourceRange::sourceId)
                .distinct()
                .toList();
    }

    /** 带元数据的文本行 */
    private static class Line {
        String content;
        final Map<String, Object> metadata;
        final Set<String> sourceIds;

        Line(String content, Map<String, Object> metadata, Set<String> sourceIds) {
            this.content = content;
            this.metadata = metadata;
            this.sourceIds = new LinkedHashSet<>(sourceIds);
        }
    }

    /** Markdown 标题 */
    private static class Header {
        final int level;
        final String name;
        final String data;

        Header(int level, String name, String data) {
            this.level = level;
            this.name = name;
            this.data = data;
        }
    }

    /** 携带元数据的文档片段 */
    private record DocumentWithMetadata(String content, Map<String, Object> metadata, List<String> sourceIds) {
        DocumentWithMetadata(String content, Map<String, Object> metadata, List<String> sourceIds) {
            this.content = content;
            this.metadata = new HashMap<>(metadata);
            this.sourceIds = List.copyOf(sourceIds);
        }

        DocumentWithMetadata(String content, Map<String, Object> metadata, Set<String> sourceIds) {
            this(content, metadata, List.copyOf(sourceIds));
        }
    }

    /** 输入文本中一个来源片段的字符范围。 */
    public record SourceRange(int startInclusive, int endExclusive, String sourceId) {

        /** 校验字符范围和来源标识。 */
        public SourceRange {
            if (startInclusive < 0 || endExclusive <= startInclusive) {
                throw new IllegalArgumentException("来源范围必须为非空的有效字符区间");
            }
            if (sourceId == null || sourceId.isBlank()) {
                throw new IllegalArgumentException("来源标识不可为空");
            }
        }
    }

    /** 带输入来源标识的 LangChain4j 输出片段。 */
    public record SourcedTextSegment(TextSegment segment, List<String> sourceIds) {

        /** 校验不可为空的 SDK 片段及其来源集合。 */
        public SourcedTextSegment {
            Objects.requireNonNull(segment, "segment 不可为空");
            if (sourceIds == null || sourceIds.isEmpty()) {
                throw new IllegalArgumentException("来源标识不可为空");
            }
            sourceIds = List.copyOf(sourceIds);
        }
    }
}
