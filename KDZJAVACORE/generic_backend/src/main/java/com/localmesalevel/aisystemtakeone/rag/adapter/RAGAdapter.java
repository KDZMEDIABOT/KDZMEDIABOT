package com.localmesalevel.aisystemtakeone.rag.adapter;

import com.localmesalevel.aisystemtakeone.dialog.model.DialogMessage;
import com.localmesalevel.aisystemtakeone.rag.model.RAGResult;

import java.util.List;

public interface RAGAdapter {

    String getName();

    boolean isAvailable();

    void indexDialogMessage(DialogMessage message, Long userId);

    void indexAllThreadMessages(Long threadId, Long userId);

    List<RAGResult> retrieveRelevantContext(String query, Long userId, int maxResults);

    void deleteThreadIndex(Long threadId);

    /**
     * Index file content for RAG retrieval.
     *
     * @param content the text content extracted from the file
     * @param fileId  the unique file identifier
     * @param userId  the owning user
     */
    default void indexFileContent(String content, Long fileId, Long userId) {
        // No-op by default; adapters may override
    }
}
