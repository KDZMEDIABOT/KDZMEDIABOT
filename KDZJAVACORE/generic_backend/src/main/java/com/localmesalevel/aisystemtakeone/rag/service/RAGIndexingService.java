package com.localmesalevel.aisystemtakeone.rag.service;

import com.localmesalevel.aisystemtakeone.dialog.model.DialogMessage;
import com.localmesalevel.aisystemtakeone.dialog.repository.DialogMessageRepository;
import com.localmesalevel.aisystemtakeone.rag.adapter.RAGAdapter;
import com.localmesalevel.aisystemtakeone.rag.adapter.PostgresVectorRAGAdapter;
import com.localmesalevel.aisystemtakeone.rag.model.RagVector;
import com.localmesalevel.aisystemtakeone.rag.repository.RagVectorRepository;
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
    private final RagVectorRepository ragVectorRepository;

    @Autowired
    public RAGIndexingService(RAGAdapter ragAdapter,
                              DialogMessageRepository messageRepository,
                              RagVectorRepository ragVectorRepository) {
        this.ragAdapter = ragAdapter;
        this.messageRepository = messageRepository;
        this.ragVectorRepository = ragVectorRepository;
    }

    @Scheduled(fixedDelayString = "${rag.indexing.interval:60000}")
    @Transactional
    public void indexUnindexedMessages() {
        Instant cutoff = Instant.now().minus(5, ChronoUnit.MINUTES);
        List<Object[]> rows = messageRepository.findUnindexedMessages(cutoff, 100);
        if (!rows.isEmpty()) {
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
                        logger.error("Failed to index message {} for user {}: {}", msgId, userId, e.toString(), e);
                    }
                }
            }
            logger.info("RAG indexing job finished: {}/{} messages indexed", successCount, total);
        }
        reindexOldVersions();
    }

    private void reindexOldVersions() {
        try {
            List<Long> oldMessageIds = ragVectorRepository.findDialogMessageSourceIdsByVersionLessThanEqual(2);
            if (oldMessageIds.isEmpty()) {
                return;
            }
            logger.info("RAG re-indexing job started: {} messages with version <= 2 to re-index", oldMessageIds.size());
            int successCount = 0;
            int total = oldMessageIds.size();
            for (Long msgId : oldMessageIds) {
                try {
                    Optional<DialogMessage> opt = messageRepository.findById(msgId);
                    if (opt.isPresent()) {
                        DialogMessage msg = opt.get();
                        Long userId = msg.getThread() != null ? msg.getThread().getUserId() : null;
                        if (userId != null) {
                            ragAdapter.indexDialogMessage(msg, userId);
                            successCount++;
                        }
                    }
                } catch (Exception e) {
                    logger.error("Failed to re-index message {}: {}", msgId, e.toString(), e);
                }
            }
            logger.info("RAG re-indexing job finished: {}/{} messages re-indexed to version {}", successCount, total, PostgresVectorRAGAdapter.RAG_INDEXER_VERSION);
        } catch (Throwable e) {
            logger.error("RAG re-indexing job failed: {}", e.toString(), e);
        }
    }
}
