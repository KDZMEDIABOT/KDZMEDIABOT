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
}
