package com.localmesalevel.aisystemtakeone.user.model;

import com.localmesalevel.aisystemtakeone.llm.model.LlmEndpointCredentials;

import javax.persistence.*;

@Entity
@Table(name = "users")
public class UserAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String username;

    @Column(nullable = false, length = 255)
    private String password;

    @Column(nullable = false, length = 50)
    private String role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_llm_endpoint_id")
    private LlmEndpointCredentials currentLlmEndpoint;

    @Column(name = "default_workspace_id")
    private Long defaultWorkspaceId;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public LlmEndpointCredentials getCurrentLlmEndpoint() {
        return currentLlmEndpoint;
    }

    public void setCurrentLlmEndpoint(LlmEndpointCredentials currentLlmEndpoint) {
        this.currentLlmEndpoint = currentLlmEndpoint;
    }

    public Long getDefaultWorkspaceId() {
        return defaultWorkspaceId;
    }

    public void setDefaultWorkspaceId(Long defaultWorkspaceId) {
        this.defaultWorkspaceId = defaultWorkspaceId;
    }
}

