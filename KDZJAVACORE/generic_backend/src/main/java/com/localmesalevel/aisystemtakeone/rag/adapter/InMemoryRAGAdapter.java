package com.localmesalevel.aisystemtakeone.rag.adapter;

import com.localmesalevel.aisystemtakeone.dialog.model.DialogMessage;
import com.localmesalevel.aisystemtakeone.rag.model.RAGResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public class InMemoryRAGAdapter implements RAGAdapter {

    private static final Logger logger = LoggerFactory.getLogger(InMemoryRAGAdapter.class);

    // userId -> messageId -> IndexedMessage
    private final Map<Long, Map<Long, IndexedMessage>> userMessageIndex = new ConcurrentHashMap<>();
    // userId -> term -> Set<messageId>
    private final Map<Long, Map<String, Set<Long>>> userTermDocFreq = new ConcurrentHashMap<>();

    @Override
    public String getName() {
        return "in-memory-tfidf";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public void indexDialogMessage(DialogMessage message, Long userId) {
        if (message == null || message.getId() == null || message.getContent() == null || message.getContent().trim().isEmpty()) {
            return;
        }

        String text = message.getContent().trim();
        List<String> terms = tokenize(text);
        if (terms.isEmpty()) {
            return;
        }

        Long messageId = message.getId();
        Long threadId = message.getThreadId();

        Map<String, Integer> termFreq = new HashMap<>();
        for (String term : terms) {
            termFreq.merge(term, 1, Integer::sum);
        }

        int maxFreq = termFreq.values().stream().max(Integer::compare).orElse(1);
        Map<String, Double> tfScores = new HashMap<>();
        for (Map.Entry<String, Integer> entry : termFreq.entrySet()) {
            tfScores.put(entry.getKey(), (double) entry.getValue() / maxFreq);
        }

        IndexedMessage indexed = new IndexedMessage(messageId, threadId, text, tfScores);

        userMessageIndex.computeIfAbsent(userId, k -> new ConcurrentHashMap<>())
                        .put(messageId, indexed);

        Map<String, Set<Long>> termDocFreq = userTermDocFreq.computeIfAbsent(userId, k -> new ConcurrentHashMap<>());
        for (String term : new HashSet<>(terms)) {
            termDocFreq.computeIfAbsent(term, k -> ConcurrentHashMap.newKeySet()).add(messageId);
        }

        logger.trace("Indexed message {} for user {} thread {}", messageId, userId, threadId);
    }

    @Override
    public void indexAllThreadMessages(Long threadId, Long userId) {
        // No-op for in-memory adapter; messages are indexed individually as they arrive
    }

    @Override
    public List<RAGResult> retrieveRelevantContext(String query, Long userId, int maxResults) {
        Map<Long, IndexedMessage> userMessages = userMessageIndex.get(userId);
        if (userMessages == null || userMessages.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> queryTerms = tokenize(query.toLowerCase());
        if (queryTerms.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, Set<Long>> termDocFreq = userTermDocFreq.getOrDefault(userId, Collections.emptyMap());
        int totalDocs = userMessages.size();
        if (totalDocs == 0) {
            return Collections.emptyList();
        }

        Map<Long, Double> messageScores = new HashMap<>();
        for (IndexedMessage message : userMessages.values()) {
            double score = 0.0;
            for (String term : queryTerms) {
                Double tf = message.tfScores.get(term);
                if (tf == null) {
                    continue;
                }
                int df = termDocFreq.getOrDefault(term, Set.of()).size();
                if (df == 0) {
                    df = 1;
                }
                double idf = Math.log((double) totalDocs / df);
                score += tf * idf;
            }
            if (score > 0) {
                messageScores.put(message.messageId, score);
            }
        }

        return messageScores.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(maxResults)
                .map(e -> {
                    IndexedMessage msg = userMessages.get(e.getKey());
                    return new RAGResult(
                            msg.content,
                            "dialog_message",
                            msg.messageId,
                            e.getValue(),
                            "From thread " + msg.threadId
                    );
                })
                .collect(Collectors.toList());
    }

    @Override
    public void deleteThreadIndex(Long threadId) {
        for (Map<Long, IndexedMessage> messageMap : userMessageIndex.values()) {
            Set<Long> toRemove = new HashSet<>();
            for (Map.Entry<Long, IndexedMessage> entry : messageMap.entrySet()) {
                if (threadId.equals(entry.getValue().threadId)) {
                    toRemove.add(entry.getKey());
                }
            }
            for (Long messageId : toRemove) {
                messageMap.remove(messageId);
            }
        }
    }

    @Override
    public void indexFileContent(String content, Long fileId, Long userId) {
        if (content == null || content.trim().isEmpty() || fileId == null || userId == null) {
            return;
        }
        String text = content.trim();
        List<String> terms = tokenize(text);
        if (terms.isEmpty()) {
            return;
        }
        Map<String, Integer> termFreq = new HashMap<>();
        for (String term : terms) {
            termFreq.merge(term, 1, Integer::sum);
        }
        int maxFreq = termFreq.values().stream().max(Integer::compare).orElse(1);
        Map<String, Double> tfScores = new HashMap<>();
        for (Map.Entry<String, Integer> entry : termFreq.entrySet()) {
            tfScores.put(entry.getKey(), (double) entry.getValue() / maxFreq);
        }
        IndexedMessage indexed = new IndexedMessage(fileId, -1L, text, tfScores);
        userMessageIndex.computeIfAbsent(userId, k -> new ConcurrentHashMap<>()).put(fileId, indexed);
        Map<String, Set<Long>> termDocFreq = userTermDocFreq.computeIfAbsent(userId, k -> new ConcurrentHashMap<>());
        for (String term : new HashSet<>(terms)) {
            termDocFreq.computeIfAbsent(term, k -> ConcurrentHashMap.newKeySet()).add(fileId);
        }
        logger.trace("Indexed file content {} for user {}", fileId, userId);
    }

    private List<String> tokenize(String text) {
        String[] tokens = text.split("[^a-z0-9\\u0400-\\u04ff]+");
        List<String> result = new ArrayList<>();
        for (String t : tokens) {
            if (t.length() > 2) {
                result.add(t);
            }
        }
        return result;
    }

    private static class IndexedMessage {
        final Long messageId;
        final Long threadId;
        final String content;
        final Map<String, Double> tfScores;

        IndexedMessage(Long messageId, Long threadId, String content, Map<String, Double> tfScores) {
            this.messageId = messageId;
            this.threadId = threadId;
            this.content = content;
            this.tfScores = tfScores;
        }
    }
}
