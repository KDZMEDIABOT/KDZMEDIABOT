package com.localmesalevel.aisystemtakeone.dialog.repository;

import com.localmesalevel.aisystemtakeone.dialog.model.DialogMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DialogMessageRepository extends JpaRepository<DialogMessage, Long> {

    List<DialogMessage> findByThreadIdOrderByCreatedAtAsc(Long threadId);

    long countByThreadId(Long threadId);

    void deleteByThreadId(Long threadId);
}
