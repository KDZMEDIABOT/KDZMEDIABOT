package com.localmesalevel.aisystemtakeone.dialog.repository;

import com.localmesalevel.aisystemtakeone.dialog.model.DialogThread;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DialogThreadRepository extends JpaRepository<DialogThread, Long> {

    List<DialogThread> findByUserIdOrderByLastMessageAtDesc(Long userId);

    Optional<DialogThread> findByIdAndUserId(Long id, Long userId);

    boolean existsByIdAndUserId(Long id, Long userId);

    void deleteByIdAndUserId(Long id, Long userId);
}
