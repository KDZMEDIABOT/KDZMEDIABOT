package com.localmesalevel.aisystemtakeone.llm.model;

import com.localmesalevel.aisystemtakeone.user.model.UserAccount;

import javax.persistence.*;

@Entity
@Table(name = "llm_endpoint_credentials")
public class LlmEndpointCredentials {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "llm_api_type", nullable = false, length = 50)
    private LlmApiType llmApiType;

    @Column(name = "base_url", nullable = false, length = 1000)
    private String baseURL;

    @Column(name = "api_key", nullable = false, length = 2000)
    private String apiKey;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @Column(name = "endpoint_display_name", nullable = false, length = 255)
    private String endpointDisplayName;

    @Column(name = "model_name", length = 255)
    private String modelName;

    @Column(name = "use_specified_user_agent")
    private boolean useSpecifiedUserAgent;

    @Column(name = "user_agent", length = 1000)
    private String userAgent;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LlmApiType getLlmApiType() {
        return llmApiType;
    }

    public void setLlmApiType(LlmApiType llmApiType) {
        this.llmApiType = llmApiType;
    }

    public String getBaseURL() {
        return baseURL;
    }

    public void setBaseURL(String baseURL) {
        this.baseURL = baseURL;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public UserAccount getUser() {
        return user;
    }

    public void setUser(UserAccount user) {
        this.user = user;
    }

    public String getEndpointDisplayName() {
        return endpointDisplayName;
    }

    public void setEndpointDisplayName(String endpointDisplayName) {
        this.endpointDisplayName = endpointDisplayName;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public boolean isUseSpecifiedUserAgent() {
        return useSpecifiedUserAgent;
    }

    public void setUseSpecifiedUserAgent(boolean useSpecifiedUserAgent) {
        this.useSpecifiedUserAgent = useSpecifiedUserAgent;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }
}
