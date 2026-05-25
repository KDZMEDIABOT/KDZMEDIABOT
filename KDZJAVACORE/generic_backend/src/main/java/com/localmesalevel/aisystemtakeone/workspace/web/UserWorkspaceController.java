package com.localmesalevel.aisystemtakeone.workspace.web;

import com.localmesalevel.aisystemtakeone.user.model.UserAccount;
import com.localmesalevel.aisystemtakeone.user.repository.UserAccountRepository;
import com.localmesalevel.aisystemtakeone.workspace.model.Workspace;
import com.localmesalevel.aisystemtakeone.workspace.model.WorkspaceFile;
import com.localmesalevel.aisystemtakeone.workspace.service.WorkspaceFileService;
import com.localmesalevel.aisystemtakeone.workspace.service.WorkspaceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/user/workspaces")
public class UserWorkspaceController {

    private final WorkspaceService workspaceService;
    private final WorkspaceFileService fileService;
    private final UserAccountRepository userAccountRepository;

    @Autowired
    public UserWorkspaceController(WorkspaceService workspaceService,
                                   WorkspaceFileService fileService,
                                   UserAccountRepository userAccountRepository) {
        this.workspaceService = workspaceService;
        this.fileService = fileService;
        this.userAccountRepository = userAccountRepository;
    }

    @PostMapping(value = "/default/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<WorkspaceFile> uploadFileToDefaultWorkspace(
            @RequestParam("file") MultipartFile file,
            @RequestAttribute("userId") Long userId
    ) throws IOException {
        UserAccount user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        Workspace workspace;
        if (user.getDefaultWorkspaceId() == null) {
            workspace = workspaceService.createWorkspace(userId, "Default workspace");
            user.setDefaultWorkspaceId(workspace.getId());
            userAccountRepository.save(user);
        } else {
            workspace = workspaceService.getWorkspace(user.getDefaultWorkspaceId(), userId)
                    .orElseGet(() -> {
                        Workspace newWs = workspaceService.createWorkspace(userId, "Default workspace");
                        user.setDefaultWorkspaceId(newWs.getId());
                        userAccountRepository.save(user);
                        return newWs;
                    });
        }

        byte[] data = file.getBytes();
        WorkspaceFile saved = fileService.uploadFile(
                workspace.getId(),
                file.getOriginalFilename(),
                file.getContentType(),
                data
        );
        return ResponseEntity.ok(saved);
    }
}
