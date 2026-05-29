package com.localmesalevel.aisystemtakeone.dialog.repository;

import com.localmesalevel.aisystemtakeone.dialog.model.DialogMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DialogMessageRepository extends JpaRepository<DialogMessage, Long> {

    List<DialogMessage> findByThreadIdOrderByCreatedAtAsc(Long threadId);

    @org.springframework.data.jpa.repository.Query("SELECT m FROM DialogMessage m WHERE m.isReplyTo = :isReplyTo AND m.error = true")
    List<DialogMessage> findErrorRepliesTo(@org.springframework.data.repository.query.Param("isReplyTo") Long isReplyTo);

    long countByThreadId(Long threadId);

    void deleteByThreadId(Long threadId);

    @org.springframework.data.jpa.repository.Query(
            value = "SELECT m.id, t.user_id FROM dialog_messages m " +
                    "JOIN dialog_threads t ON m.thread_id = t.id " +
                    "WHERE m.id NOT IN (SELECT CAST(source_id AS bigint) FROM rag_vectors WHERE source_type = 'dialog_message') " +
                    "AND m.role IN ('user', 'assistant') " +
                    "AND m.created_at < ?1 " +
                    "ORDER BY m.id LIMIT ?2",
            nativeQuery = true)
    List<Object[]> findUnindexedMessages(java.time.Instant cutoff, int limit);
}
