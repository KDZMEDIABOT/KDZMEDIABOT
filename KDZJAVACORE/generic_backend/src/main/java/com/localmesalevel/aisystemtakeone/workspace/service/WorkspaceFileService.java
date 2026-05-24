package com.localmesalevel.aisystemtakeone.workspace.service;

import com.localmesalevel.aisystemtakeone.workspace.model.WorkspaceFile;
import com.localmesalevel.aisystemtakeone.workspace.repository.WorkspaceFileRepository;
import com.localmesalevel.aisystemtakeone.workspace.repository.WorkspaceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class WorkspaceFileService {

    private final WorkspaceFileRepository fileRepository;
    private final WorkspaceRepository workspaceRepository;

    @Autowired
    public WorkspaceFileService(WorkspaceFileRepository fileRepository,
                                WorkspaceRepository workspaceRepository) {
        this.fileRepository = fileRepository;
        this.workspaceRepository = workspaceRepository;
    }

    public WorkspaceFile uploadFile(Long workspaceId, String fileName, String mimeType, byte[] data) {
        com.localmesalevel.aisystemtakeone.workspace.model.Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Workspace not found: " + workspaceId));

        WorkspaceFile file = new WorkspaceFile();
        file.setWorkspace(workspace);
        file.setFileName(fileName != null ? fileName : "unnamed");
        file.setMimeType(mimeType);
        file.setFileData(data);
        file.setFileSize((long) (data != null ? data.length : 0));
        file.setCreatedAt(Instant.now());
        return fileRepository.save(file);
    }

    public Optional<WorkspaceFile> getFile(Long fileId) {
        return fileRepository.findById(fileId);
    }

    public List<WorkspaceFile> listFiles(Long workspaceId) {
        return fileRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId);
    }

    public void deleteFile(Long fileId) {
        fileRepository.deleteById(fileId);
    }

    public void markIndexed(Long fileId) {
        fileRepository.findById(fileId).ifPresent(file -> {
            file.setIndexedAt(Instant.now());
            fileRepository.save(file);
        });
    }

    public String readFileAsText(WorkspaceFile file) {
        if (file == null || file.getFileData() == null || file.getFileData().length == 0) {
            return "";
        }
        return FileFormatToTextConverterHelper.convertToText(file.getFileData(), file.getMimeType());
    }
}
