package com.localmesalevel.aisystemtakeone.workspace.repository;

import com.localmesalevel.aisystemtakeone.workspace.model.Workspace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkspaceRepository extends JpaRepository<Workspace, Long> {

    List<Workspace> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<Workspace> findByIdAndUserId(Long id, Long userId);

    void deleteByIdAndUserId(Long id, Long userId);
}
