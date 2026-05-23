package com.localmesalevel.aisystemtakeone.workspace.repository;

import com.localmesalevel.aisystemtakeone.workspace.model.WorkspaceFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WorkspaceFileRepository extends JpaRepository<WorkspaceFile, Long> {

    List<WorkspaceFile> findByWorkspaceIdOrderByCreatedAtDesc(Long workspaceId);

    long countByWorkspaceId(Long workspaceId);
}
