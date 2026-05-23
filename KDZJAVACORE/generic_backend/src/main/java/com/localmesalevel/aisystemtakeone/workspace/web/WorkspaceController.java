package com.localmesalevel.aisystemtakeone.workspace.web;

import com.localmesalevel.aisystemtakeone.workspace.model.Workspace;
import com.localmesalevel.aisystemtakeone.workspace.service.WorkspaceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/workspaces")
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    @Autowired
    public WorkspaceController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @GetMapping
    public ResponseEntity<List<Workspace>> listWorkspaces(@RequestAttribute("userId") Long userId) {
        return ResponseEntity.ok(workspaceService.listWorkspaces(userId));
    }

    @PostMapping
    public ResponseEntity<Workspace> createWorkspace(@RequestBody CreateWorkspaceRequest request,
                                                      @RequestAttribute("userId") Long userId) {
        Workspace workspace = workspaceService.createWorkspace(userId, request.name);
        return ResponseEntity.ok(workspace);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Workspace> renameWorkspace(@PathVariable Long id,
                                                     @RequestBody RenameWorkspaceRequest request,
                                                     @RequestAttribute("userId") Long userId) {
        Workspace workspace = workspaceService.renameWorkspace(id, userId, request.name);
        return ResponseEntity.ok(workspace);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteWorkspace(@PathVariable Long id,
                                                @RequestAttribute("userId") Long userId) {
        workspaceService.deleteWorkspace(id, userId);
        return ResponseEntity.ok().build();
    }

    public static class CreateWorkspaceRequest {
        public String name;
    }

    public static class RenameWorkspaceRequest {
        public String name;
    }
}
