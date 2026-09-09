package com.fons.cloud.ai.agent.infrastructure.utils;

import java.util.ArrayList;
import java.util.List;

/**
 * <think/>标签解析器。
 * 无状态工具类，将 LLM 流式输出的文本 chunk 拆分为思考内容和正常文本。
 * 支持跨 chunk 的标签状态追踪（通过 inThink 参数）。
 * @author hongqy
 */
public class ThinkMessageParser {

    /**
     * 思考内容开始标记
     */
    private static final String THINK_START = "<think";

    /**
     * 思考内容结束标记
     */
    private static final String THINK_END = "</think";


    /**
     * 去除文本中的 think标签及其内容。
     * @param input 可能包含 think 标签的文本
     * @return 去除 think 标签后的文本
     */
    public static String stripThinkTags(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        // 通用正则：匹配 <think...>...</think...>（兼容空格、属性、自闭合等变体）
        return input.replaceAll("(?s)<think[^>]*>.*?</think[^>]*>", "").trim();
    }

    /**
     * 内容段，标识是思考内容还是正常文本。
     */
    public record Segment(boolean thinking, String content) {
    }

    /**
     * 解析结果。
     */
    public record ParseResult(List<Segment> segments, boolean inThink) {
    }

    /**
     * 流式解析结果。pendingTag 保存 chunk 末尾尚未组成完整标签的字符。
     */
    public record StreamingParseResult(List<Segment> segments, boolean inThink, String pendingTag) {
    }


    /**
     * 解析一个文本 chunk。
     *
     * @param chunk   当前文本 chunk
     * @param inThink 上一个 chunk 结束时的 think 标签内状态
     * @return 解析结果，包含拆分后的内容段和更新后的 inThink 状态
     */
    public static ParseResult parse(String chunk, boolean inThink) {
        if (chunk == null || chunk.isEmpty()) {
            return new ParseResult(List.of(), inThink);
        }

        List<Segment> segments = new ArrayList<>();
        boolean currentInThink = inThink;
        int index = 0;

        while (index < chunk.length()) {
            int thinkStartIdx = chunk.indexOf(THINK_START, index);
            int thinkEndIdx = chunk.indexOf(THINK_END, index);

            int nextTagPos;
            boolean isStartTag;

            if (thinkStartIdx == -1 && thinkEndIdx == -1) {
                String remaining = chunk.substring(index);
                if (!remaining.isEmpty()) {
                    segments.add(new Segment(currentInThink, remaining));
                }
                break;
            }

            if (thinkStartIdx != -1 && (thinkEndIdx == -1 || thinkStartIdx < thinkEndIdx)) {
                nextTagPos = thinkStartIdx;
                isStartTag = true;
            } else {
                nextTagPos = thinkEndIdx;
                isStartTag = false;
            }

            if (nextTagPos > index) {
                String beforeTag = chunk.substring(index, nextTagPos);
                if (!beforeTag.isEmpty()) {
                    segments.add(new Segment(currentInThink, beforeTag));
                }
            }

            int tagEnd = chunk.indexOf('>', nextTagPos);
            if (tagEnd == -1) {
                currentInThink = isStartTag;
                break;
            }

            currentInThink = isStartTag;
            index = tagEnd + 1;
        }

        return new ParseResult(segments, currentInThink);
    }

    /**
     * 支持标签本身跨 chunk 的解析入口，例如 {@code "<thi" + "nk>"}。
     *
     * @param chunk 当前文本 chunk
     * @param inThink 上一个 chunk 是否位于 think 标签内
     * @param pendingTag 上一个 chunk 尚未确认的标签前缀
     */
    public static StreamingParseResult parseStreaming(String chunk, boolean inThink, String pendingTag) {
        String input = (pendingTag == null ? "" : pendingTag) + (chunk == null ? "" : chunk);
        if (input.isEmpty()) {
            return new StreamingParseResult(List.of(), inThink, "");
        }

        List<Segment> segments = new ArrayList<>();
        boolean currentInThink = inThink;
        int index = 0;

        while (index < input.length()) {
            int thinkStartIdx = input.indexOf(THINK_START, index);
            int thinkEndIdx = input.indexOf(THINK_END, index);

            if (thinkStartIdx == -1 && thinkEndIdx == -1) {
                String remaining = input.substring(index);
                int pendingLength = markerPrefixSuffixLength(remaining);
                int contentEnd = remaining.length() - pendingLength;
                if (contentEnd > 0) {
                    segments.add(new Segment(currentInThink, remaining.substring(0, contentEnd)));
                }
                return new StreamingParseResult(
                        segments,
                        currentInThink,
                        pendingLength == 0 ? "" : remaining.substring(contentEnd));
            }

            boolean startTag = thinkStartIdx != -1
                    && (thinkEndIdx == -1 || thinkStartIdx < thinkEndIdx);
            int tagStart = startTag ? thinkStartIdx : thinkEndIdx;
            if (tagStart > index) {
                segments.add(new Segment(currentInThink, input.substring(index, tagStart)));
            }

            int tagEnd = input.indexOf('>', tagStart);
            if (tagEnd == -1) {
                return new StreamingParseResult(segments, currentInThink, input.substring(tagStart));
            }

            currentInThink = startTag;
            index = tagEnd + 1;
        }

        return new StreamingParseResult(segments, currentInThink, "");
    }

    private static int markerPrefixSuffixLength(String text) {
        int maxLength = Math.min(text.length(), THINK_END.length() - 1);
        for (int length = maxLength; length > 0; length--) {
            String suffix = text.substring(text.length() - length);
            if (THINK_START.startsWith(suffix) || THINK_END.startsWith(suffix)) {
                return length;
            }
        }
        return 0;
    }



}
