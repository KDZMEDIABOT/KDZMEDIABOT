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
}
