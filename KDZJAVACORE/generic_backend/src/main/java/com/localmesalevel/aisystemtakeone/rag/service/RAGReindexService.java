package com.localmesalevel.aisystemtakeone.rag.service;

import com.localmesalevel.aisystemtakeone.dialog.model.DialogMessage;
import com.localmesalevel.aisystemtakeone.dialog.repository.DialogMessageRepository;
import com.localmesalevel.aisystemtakeone.rag.adapter.RAGAdapter;
import com.localmesalevel.aisystemtakeone.rag.adapter.PostgresVectorRAGAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class RAGReindexService {

    private static final Logger logger = LoggerFactory.getLogger(RAGReindexService.class);

    private final RAGAdapter ragAdapter;
    private final DialogMessageRepository messageRepository;

    @Autowired
    public RAGReindexService(RAGAdapter ragAdapter, DialogMessageRepository messageRepository) {
        this.ragAdapter = ragAdapter;
        this.messageRepository = messageRepository;
    }

    @Scheduled(fixedDelayString = "${rag.reindex.interval:300000}")
    @Transactional
    public void reindexAllMessages() {
    	try {
	        logger.info("RAG re-indexing all messages started");
	        List<DialogMessage> messages = messageRepository.findAll();
	        int successCount = 0;
	        int total = messages.size();
	        for (DialogMessage msg : messages) {
	            if (msg.getThreadId() == null) {
	                continue;
	            }
	            Long userId = msg.getThread() != null ? msg.getThread().getUserId() : null;
	            if (userId != null) {
	                try {
	                    ragAdapter.indexDialogMessage(msg, userId);
	                    successCount++;
	                } catch (Exception e) {
	                    logger.error("Failed to re-index message {}: {}", msg.getId(), e.toString(), e);
	                }
	            }
	        }
	        logger.info("RAG re-indexing finished: {}/{} messages re-indexed to version {}", successCount, total, PostgresVectorRAGAdapter.RAG_INDEXER_VERSION);
    	} catch (Throwable e) {
            logger.error("", e);
        }
    }
}
