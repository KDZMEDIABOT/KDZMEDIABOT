package com.localmesalevel.aisystemtakeone.rag;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class TextChunker {

    private final int maxChunkSize;
    private final int overlap;

    public TextChunker() {
        this(1000, 200);
    }

    public TextChunker(int maxChunkSize, int overlap) {
        this.maxChunkSize = maxChunkSize;
        this.overlap = overlap;
    }

    public List<String> chunk(String text) {
        if (text == null || text.trim().isEmpty()) {
            return java.util.Collections.emptyList();
        }

        String trimmed = text.trim();
        if (trimmed.length() <= maxChunkSize) {
            return List.of(trimmed);
        }

        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < trimmed.length()) {
            int end = Math.min(start + maxChunkSize, trimmed.length());
            if (end < trimmed.length()) {
                int lastSentence = findLastSentenceEnd(trimmed, start, end);
                if (lastSentence > start) {
                    end = lastSentence;
                } else {
                    int lastSpace = findLastSpace(trimmed, start, end);
                    if (lastSpace > start) {
                        end = lastSpace;
                    }
                }
            }
            chunks.add(trimmed.substring(start, end));
            if (end >= trimmed.length()) {
                break; // Last chunk processed
            }
            start = end - overlap;
            if (start >= end) {
                start = end;
            }
        }

        return chunks;
    }

    public List<String> chunk(String text, int maxChunkSize, int overlap) {
        return new TextChunker(maxChunkSize, overlap).chunk(text);
    }

    private static int findLastSentenceEnd(String text, int start, int end) {
        for (int i = end - 1; i > start; i--) {
            char c = text.charAt(i);
            if (c == '.' || c == '!' || c == '?' || c == '\n') {
                return i + 1;
            }
        }
        return -1;
    }

    private static int findLastSpace(String text, int start, int end) {
        for (int i = end - 1; i > start; i--) {
            if (text.charAt(i) == ' ') {
                return i;
            }
        }
        return -1;
    }
}
