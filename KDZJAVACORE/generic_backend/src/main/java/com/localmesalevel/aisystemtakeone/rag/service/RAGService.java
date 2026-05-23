package com.localmesalevel.aisystemtakeone.rag.service;

import com.localmesalevel.aisystemtakeone.dialog.model.DialogMessage;
import com.localmesalevel.aisystemtakeone.rag.adapter.RAGAdapter;
import com.localmesalevel.aisystemtakeone.rag.model.RAGResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RAGService {

    private static final Logger logger = LoggerFactory.getLogger(RAGService.class);

    private final RAGAdapter ragAdapter;

    @Autowired
    public RAGService(RAGAdapter ragAdapter) {
        this.ragAdapter = ragAdapter;
    }

    public String enhancePromptWithRAG(String userQuery, Long userId) {
        if (!ragAdapter.isAvailable()) {
            logger.trace("RAG adapter not available, skipping context enhancement");
            return userQuery;
        }

        try {
            List<RAGResult> results = ragAdapter.retrieveRelevantContext(userQuery, userId, 5);
            if (results == null || results.isEmpty()) {
                return userQuery;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("[Relevant context from previous conversations:\n");
            for (RAGResult result : results) {
                String content = result.getContent();
                if (content != null && !content.isEmpty()) {
                    sb.append("- \"").append(truncate(content, 300))
                      .append("\" (").append(result.getCitation()).append(")\n");
                }
            }
            sb.append("]\n\n");
            sb.append("User query: ").append(userQuery);
            return sb.toString();
        } catch (Exception e) {
            logger.warn("RAG context retrieval failed, falling back to plain query: {}", e.getMessage());
            return userQuery;
        }
    }

    public void indexMessage(DialogMessage message, Long userId) {
        if (message == null) {
            return;
        }
        try {
            ragAdapter.indexDialogMessage(message, userId);
        } catch (Exception e) {
            logger.warn("Failed to index message {} for RAG: {}", message.getId(), e.getMessage());
        }
    }

    public boolean isAvailable() {
        return ragAdapter != null && ragAdapter.isAvailable();
    }

    public void indexFile(String fileContent, Long fileId, Long userId) {
        if (fileContent == null || fileId == null || userId == null) {
            return;
        }
        try {
            ragAdapter.indexFileContent(fileContent, fileId, userId);
        } catch (Exception e) {
            logger.warn("Failed to index file {} for RAG: {}", fileId, e.getMessage());
        }
    }

    public List<RAGResult> retrieveRelevantContext(String query, Long userId, int maxResults) {
        if (!isAvailable()) {
            logger.trace("RAG adapter not available, skipping context retrieval");
            return List.of();
        }
        try {
            return ragAdapter.retrieveRelevantContext(query, userId, maxResults);
        } catch (Exception e) {
            logger.warn("RAG context retrieval failed: {}", e.getMessage());
            return List.of();
        }
    }

    private String truncate(String s, int maxLen) {
        if (s == null || s.length() <= maxLen) {
            return s;
        }
        return s.substring(0, maxLen) + "...";
    }
}
