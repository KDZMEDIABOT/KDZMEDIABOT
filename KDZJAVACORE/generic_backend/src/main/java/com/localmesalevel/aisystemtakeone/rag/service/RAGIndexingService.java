package com.localmesalevel.aisystemtakeone.rag.service;

import com.localmesalevel.aisystemtakeone.dialog.model.DialogMessage;
import com.localmesalevel.aisystemtakeone.dialog.repository.DialogMessageRepository;
import com.localmesalevel.aisystemtakeone.rag.adapter.RAGAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Service
public class RAGIndexingService {

    private static final Logger logger = LoggerFactory.getLogger(RAGIndexingService.class);

    private final RAGAdapter ragAdapter;
    private final DialogMessageRepository messageRepository;

    @Autowired
    public RAGIndexingService(RAGAdapter ragAdapter, DialogMessageRepository messageRepository) {
        this.ragAdapter = ragAdapter;
        this.messageRepository = messageRepository;
    }

    @Scheduled(fixedDelayString = "${rag.indexing.interval:60000}")
    @Transactional
    public void indexUnindexedMessages() {
        Instant cutoff = Instant.now().minus(5, ChronoUnit.MINUTES);
        List<Object[]> rows = messageRepository.findUnindexedMessages(cutoff, 100);
        if (rows.isEmpty()) {
            return;
        }
        logger.info("RAG indexing job started: {} messages to index", rows.size());
        int successCount = 0;
        int total = rows.size();
        for (Object[] row : rows) {
            Long msgId = ((Number) row[0]).longValue();
            Long userId = ((Number) row[1]).longValue();
            Optional<DialogMessage> opt = messageRepository.findById(msgId);
            if (opt.isPresent()) {
                try {
                    ragAdapter.indexDialogMessage(opt.get(), userId);
                    successCount++;
                } catch (Exception e) {
                    logger.error("Failed to index message {} for user {}: {}", msgId, userId, e.getMessage());
                }
            }
        }
        logger.info("RAG indexing job finished: {}/{} messages indexed", successCount, total);
    }
}
