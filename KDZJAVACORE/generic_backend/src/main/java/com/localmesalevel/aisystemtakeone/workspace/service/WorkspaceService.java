package com.localmesalevel.aisystemtakeone.workspace.service;

import com.localmesalevel.aisystemtakeone.workspace.model.Workspace;
import com.localmesalevel.aisystemtakeone.workspace.repository.WorkspaceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;

    @Autowired
    public WorkspaceService(WorkspaceRepository workspaceRepository) {
        this.workspaceRepository = workspaceRepository;
    }

    public Workspace createWorkspace(Long userId, String name) {
        Workspace workspace = new Workspace();
        workspace.setUserId(userId);
        workspace.setName(name != null ? name.trim() : "Untitled Workspace");
        return workspaceRepository.save(workspace);
    }

    public List<Workspace> listWorkspaces(Long userId) {
        return workspaceRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public Optional<Workspace> getWorkspace(Long id, Long userId) {
        return workspaceRepository.findByIdAndUserId(id, userId);
    }

    public Workspace renameWorkspace(Long id, Long userId, String newName) {
        Workspace workspace = workspaceRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("Workspace not found: " + id));
        workspace.setName(newName != null ? newName.trim() : workspace.getName());
        return workspaceRepository.save(workspace);
    }

    public void deleteWorkspace(Long id, Long userId) {
        workspaceRepository.deleteByIdAndUserId(id, userId);
    }
}
