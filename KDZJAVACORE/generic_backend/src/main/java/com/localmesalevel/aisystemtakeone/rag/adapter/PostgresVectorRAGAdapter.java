package com.localmesalevel.aisystemtakeone.rag.adapter;

import com.localmesalevel.aisystemtakeone.dialog.model.DialogMessage;
import com.localmesalevel.aisystemtakeone.llm.model.LlmEndpointCredentials;
import com.localmesalevel.aisystemtakeone.rag.EmbeddingService;
import com.localmesalevel.aisystemtakeone.rag.TextChunker;
import com.localmesalevel.aisystemtakeone.rag.model.RAGResult;
import com.localmesalevel.aisystemtakeone.rag.model.RagVector;
import com.localmesalevel.aisystemtakeone.rag.repository.RagVectorRepository;
import com.localmesalevel.aisystemtakeone.user.repository.UserAccountRepository;
import com.localmesalevel.aisystemtakeone.workspace.model.WorkspaceFile;
import com.localmesalevel.aisystemtakeone.workspace.model.Workspace;
import com.localmesalevel.aisystemtakeone.workspace.service.WorkspaceFileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Component
@Primary
public class PostgresVectorRAGAdapter implements RAGAdapter {

    private static final Logger logger = LoggerFactory.getLogger(PostgresVectorRAGAdapter.class);

    public static final int RAG_INDEXER_VERSION = 3;

    private final RagVectorRepository ragVectorRepository;
    private final EmbeddingService embeddingService;
    private final TextChunker textChunker;
    private final UserAccountRepository userAccountRepository;
    private final WorkspaceFileService workspaceFileService;

    @Autowired
    public PostgresVectorRAGAdapter(RagVectorRepository ragVectorRepository,
                                    EmbeddingService embeddingService,
                                    TextChunker textChunker,
                                    UserAccountRepository userAccountRepository,
                                    WorkspaceFileService workspaceFileService) {
        this.ragVectorRepository = ragVectorRepository;
        this.embeddingService = embeddingService;
        this.textChunker = textChunker;
        this.userAccountRepository = userAccountRepository;
        this.workspaceFileService = workspaceFileService;
    }

    @Override
    public String getName() {
        return "postgres-vector";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    private LlmEndpointCredentials getCredentials(Long userId) {
        var userOpt = userAccountRepository.findById(userId);
        if (userOpt.isPresent()) {
            var user = userOpt.get();
            var creds = user.getCurrentLlmEndpoint();
            if (creds != null) {
                return creds;
            }
        }
        return null;
    }

    @Override
    @Transactional
    public void indexDialogMessage(DialogMessage message, Long userId) {
        if (message == null || message.getId() == null || message.getThreadId() == null) {
            return;
        }
        String text = message.getContent();
        if (text == null || text.trim().isEmpty()) {
            return;
        }

        LlmEndpointCredentials credentials = getCredentials(userId);
        if (credentials == null) {
            logger.warn("No LLM credentials for user {}, skipping vector indexing", userId);
            return;
        }

        // Deduplication: remove old vectors for this message before re-indexing
        List<RagVector> existing = ragVectorRepository.findByUserIdAndSourceTypeAndSourceId(
            userId, "dialog_message", message.getId());
        if (!existing.isEmpty()) {
            ragVectorRepository.deleteAll(existing);
        }

        // Index message content
        List<String> chunks = textChunker.chunk(text);
        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            float[] embedding = embeddingService.embed(chunk, credentials);
            if (embedding == null) {
                continue;
            }
            RagVector vec = new RagVector();
            vec.setSourceType("dialog_message");
            vec.setSourceId(message.getId());
            vec.setThreadId(message.getThreadId());
            vec.setUserId(userId);
            vec.setChunkText(chunk);
            vec.setEmbedding(toDoubleArray(embedding));
            vec.setChunkIndex(i);
            vec.setVersion(RAG_INDEXER_VERSION);
            ragVectorRepository.save(vec);
        }

        // Index attached files
        if (message.getAttachedFiles() != null) {
            for (WorkspaceFile attachedFile : message.getAttachedFiles()) {
                if (attachedFile.getFileData() != null && attachedFile.getFileData().length > 0) {
                    try {
                        String fileText = workspaceFileService.readFileAsText(attachedFile);
                        indexTextWithVersion(fileText, "file", attachedFile.getId(), message.getThreadId(), userId, credentials);
                    } catch (Exception e) {
                        logger.error("Failed to index attached file {} for RAG: {}", attachedFile.getId(), e.getMessage());
                    }
                }
            }
        }

        // Index attached workspaces (all files in each workspace)
        if (message.getAttachedWorkspaces() != null) {
            for (Workspace workspace : message.getAttachedWorkspaces()) {
                if (workspace.getFiles() != null) {
                    for (WorkspaceFile wf : workspace.getFiles()) {
                        if (wf.getFileData() != null && wf.getFileData().length > 0) {
                            try {
                                String fileText = workspaceFileService.readFileAsText(wf);
                                indexTextWithVersion(fileText, "workspace_file", wf.getId(), message.getThreadId(), userId, credentials);
                            } catch (Exception e) {
                                logger.error("Failed to index workspace file {} for RAG: {}", wf.getId(), e.getMessage());
                            }
                        }
                    }
                }
            }
        }

        logger.trace("Indexed message {} for user {} into {} chunks with attachments", message.getId(), userId, chunks.size());
    }

    private void indexTextWithVersion(String text, String sourceType, Long sourceId, Long threadId, Long userId, LlmEndpointCredentials credentials) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        // Deduplication
        List<RagVector> existing = ragVectorRepository.findByUserIdAndSourceTypeAndSourceId(userId, sourceType, sourceId);
        if (!existing.isEmpty()) {
            ragVectorRepository.deleteAll(existing);
        }
        List<String> chunks = textChunker.chunk(text);
        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            float[] embedding = embeddingService.embed(chunk, credentials);
            if (embedding == null) {
                continue;
            }
            RagVector vec = new RagVector();
            vec.setSourceType(sourceType);
            vec.setSourceId(sourceId);
            vec.setThreadId(threadId);
            vec.setUserId(userId);
            vec.setChunkText(chunk);
            vec.setEmbedding(toDoubleArray(embedding));
            vec.setChunkIndex(i);
            vec.setVersion(RAG_INDEXER_VERSION);
            ragVectorRepository.save(vec);
        }
    }

    @Override
    public void indexAllThreadMessages(Long threadId, Long userId) {
        // Messages are indexed individually as they arrive; no-op here
    }

    @Override
    public List<RAGResult> retrieveRelevantContext(String query, Long userId, int maxResults) {
        LlmEndpointCredentials credentials = getCredentials(userId);
        if (credentials == null) {
            logger.warn("No LLM credentials for user {}, skipping RAG retrieval", userId);
            return Collections.emptyList();
        }

        float[] queryEmbedding = embeddingService.embed(query, credentials);
        if (queryEmbedding == null) {
            return Collections.emptyList();
        }

        List<RagVector> candidates = ragVectorRepository.findByUserId(userId);
        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }

        double[] queryVec = toDoubleArray(queryEmbedding);
        List<ScoredVector> scored = new ArrayList<>();
        for (RagVector vec : candidates) {
            double similarity = cosineSimilarity(queryVec, vec.getEmbedding());
            scored.add(new ScoredVector(vec, similarity));
        }

        return scored.stream()
                .sorted((a, b) -> Double.compare(b.score, a.score))
                .limit(maxResults)
                .map(sv -> {
                    RagVector v = sv.vec;
                    return new RAGResult(
                            v.getChunkText(),
                            v.getSourceType(),
                            v.getSourceId(),
                            sv.score,
                            "From " + v.getSourceType() + " " + v.getSourceId()
                    );
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteThreadIndex(Long threadId) {
        ragVectorRepository.deleteByThreadId(threadId);
    }

    @Override
    public void indexFileContent(String content, Long fileId, Long userId) {
        if (content == null || content.trim().isEmpty() || fileId == null || userId == null) {
            return;
        }

        LlmEndpointCredentials credentials = getCredentials(userId);
        if (credentials == null) {
            logger.warn("No LLM credentials for user {}, skipping file indexing", userId);
            return;
        }

        indexTextWithVersion(content, "file", fileId, null, userId, credentials);
    }

    private static double cosineSimilarity(double[] a, double[] b) {
        if (a == null || b == null || a.length != b.length) {
            return 0.0;
        }
        double dot = 0.0, normA = 0.0, normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) {
            return 0.0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private static double[] toDoubleArray(float[] arr) {
        double[] result = new double[arr.length];
        for (int i = 0; i < arr.length; i++) {
            result[i] = arr[i];
        }
        return result;
    }

    private static class ScoredVector {
        final RagVector vec;
        final double score;

        ScoredVector(RagVector vec, double score) {
            this.vec = vec;
            this.score = score;
        }
    }
}
