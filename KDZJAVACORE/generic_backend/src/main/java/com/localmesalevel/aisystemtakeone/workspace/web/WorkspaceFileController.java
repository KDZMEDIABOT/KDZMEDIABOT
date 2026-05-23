package com.localmesalevel.aisystemtakeone.workspace.web;

import com.localmesalevel.aisystemtakeone.workspace.model.WorkspaceFile;
import com.localmesalevel.aisystemtakeone.workspace.service.WorkspaceFileService;
import com.localmesalevel.aisystemtakeone.workspace.service.WorkspaceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/files")
public class WorkspaceFileController {

    private final WorkspaceFileService fileService;
    private final WorkspaceService workspaceService;

    @Autowired
    public WorkspaceFileController(WorkspaceFileService fileService,
                                   WorkspaceService workspaceService) {
        this.fileService = fileService;
        this.workspaceService = workspaceService;
    }

    @GetMapping
    public ResponseEntity<List<WorkspaceFile>> listFiles(@PathVariable Long workspaceId,
                                                          @RequestAttribute("userId") Long userId) {
        workspaceService.getWorkspace(workspaceId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Workspace not found: " + workspaceId));
        return ResponseEntity.ok(fileService.listFiles(workspaceId));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<WorkspaceFile> uploadFile(@PathVariable Long workspaceId,
                                                   @RequestParam("file") MultipartFile file,
                                                   @RequestAttribute("userId") Long userId) throws IOException {
        workspaceService.getWorkspace(workspaceId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Workspace not found: " + workspaceId));

        byte[] data = file.getBytes();
        WorkspaceFile saved = fileService.uploadFile(workspaceId, file.getOriginalFilename(), file.getContentType(), data);
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/{fileId}")
    public ResponseEntity<byte[]> downloadFile(@PathVariable Long workspaceId,
                                                @PathVariable Long fileId,
                                                @RequestAttribute("userId") Long userId) {
        workspaceService.getWorkspace(workspaceId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Workspace not found: " + workspaceId));

        Optional<WorkspaceFile> fileOpt = fileService.getFile(fileId);
        if (fileOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        WorkspaceFile file = fileOpt.get();
        // Basic check that the file belongs to the requested workspace
        if (!workspaceId.equals(file.getWorkspace() != null ? file.getWorkspace().getId() : null)) {
            return ResponseEntity.notFound().build();
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(file.getMimeType() != null ? MediaType.parseMediaType(file.getMimeType()) : MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", file.getFileName());
        headers.setContentLength(file.getFileData().length);
        return ResponseEntity.ok().headers(headers).body(file.getFileData());
    }

    @DeleteMapping("/{fileId}")
    public ResponseEntity<Void> deleteFile(@PathVariable Long workspaceId,
                                           @PathVariable Long fileId,
                                           @RequestAttribute("userId") Long userId) {
        workspaceService.getWorkspace(workspaceId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Workspace not found: " + workspaceId));
        fileService.deleteFile(fileId);
        return ResponseEntity.ok().build();
    }
}
