package com.localmesalevel.aisystemtakeone.llm.web;

import com.localmesalevel.aisystemtakeone.llm.model.LlmApiType;
import com.localmesalevel.aisystemtakeone.llm.model.LlmEndpointCredentials;
import com.localmesalevel.aisystemtakeone.llm.repository.LlmEndpointCredentialsRepository;
import com.localmesalevel.aisystemtakeone.user.model.UserAccount;
import com.localmesalevel.aisystemtakeone.user.repository.UserAccountRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.*;

@RestController
@RequestMapping("/api/llm-endpoints")
public class LlmEndpointCredentialsController {
	private static final Logger LOGGER = LoggerFactory.getLogger(LlmEndpointCredentialsController.class);
	
    private final LlmEndpointCredentialsRepository llmEndpointCredentialsRepository;
    private final UserAccountRepository userAccountRepository;

    public LlmEndpointCredentialsController(
        LlmEndpointCredentialsRepository llmEndpointCredentialsRepository,
        UserAccountRepository userAccountRepository
    ) {
        this.llmEndpointCredentialsRepository = llmEndpointCredentialsRepository;
        this.userAccountRepository = userAccountRepository;
    }

    @GetMapping
    @Transactional
    public ResponseEntity<?> list(@RequestParam("userId") Long userId) {
        Optional<UserAccount> maybeUser = userAccountRepository.findById(userId);
        if (maybeUser.isEmpty()) {
            return ResponseEntity.badRequest().body(new ErrorResponse("User not found"));
        }
        UserAccount user = maybeUser.get();
        if (!isMaintainerOrAdmin(user.getRole())) {
            return ResponseEntity.status(403).body(new ErrorResponse("Role is not allowed to manage LLM endpoints"));
        }

        List<LlmEndpointCredentials> entries = llmEndpointCredentialsRepository.findByUserIdOrderByIdDesc(user.getId());
        autoSelectSingleEndpointIfNeeded(user, entries);

        Long currentEndpointId = user.getCurrentLlmEndpoint() == null ? null : user.getCurrentLlmEndpoint().getId();
        List<EntryResponse> items = entries
            .stream()
            .map(it -> new EntryResponse(
                it.getId(),
                it.getLlmApiType(),
                it.getBaseURL(),
                maskApiKey(it.getApiKey()),
                it.getEndpointDisplayName(),
                it.getModelName(),
                currentEndpointId != null && currentEndpointId.equals(it.getId())
            ))
            .toList();

        return ResponseEntity.ok(new ListResponse(items, currentEndpointId));
    }

    @PostMapping
    @Transactional
    public ResponseEntity<?> create(
        @RequestParam("userId") Long userId,
        @RequestBody UpsertRequest request
    ) {
    	LOGGER.debug("create enter");
        Optional<UserAccount> maybeUser = userAccountRepository.findById(userId);
        if (maybeUser.isEmpty()) {
        	LOGGER.debug("create userId not found");
            return ResponseEntity.badRequest().body(new ErrorResponse("User not found"));
        }
        UserAccount user = maybeUser.get();
        if (!isMaintainerOrAdmin(user.getRole())) {
        	LOGGER.debug("create bad role");
            return ResponseEntity.status(403).body(new ErrorResponse("Role is not allowed to manage LLM endpoints"));
        }
        if (request == null
            || request.getLlmApiType() == null
            || isBlank(request.getBaseURL())
            || isBlank(request.getApiKey())
            || isBlank(request.getEndpointDisplayName())) {
        	LOGGER.debug("create bad body");
            return ResponseEntity.badRequest().body(new ErrorResponse("llmApiType, baseURL, apiKey and endpointDisplayName are required"));
        }
    	LOGGER.debug("create creating");

        LlmEndpointCredentials entity = new LlmEndpointCredentials();
        entity.setUser(user);
        entity.setLlmApiType(request.getLlmApiType());
        entity.setBaseURL(request.getBaseURL().trim());
        entity.setApiKey(request.getApiKey().trim());
        entity.setEndpointDisplayName(request.getEndpointDisplayName().trim());
        entity.setModelName(trimToNull(request.getModelName()));

        LlmEndpointCredentials saved = llmEndpointCredentialsRepository.save(entity);
        autoSelectSingleEndpointIfNeeded(user, llmEndpointCredentialsRepository.findByUserIdOrderByIdDesc(user.getId()));

        Long currentEndpointId = user.getCurrentLlmEndpoint() == null ? null : user.getCurrentLlmEndpoint().getId();
    	LOGGER.debug("create leaving");
        return ResponseEntity.ok(new EntryResponse(
            saved.getId(),
            saved.getLlmApiType(),
            saved.getBaseURL(),
            maskApiKey(saved.getApiKey()),
            saved.getEndpointDisplayName(),
            saved.getModelName(),
            currentEndpointId != null && currentEndpointId.equals(saved.getId())
        ));
    }

    @PutMapping("/current")
    @Transactional
    public ResponseEntity<?> selectCurrent(@RequestParam("userId") Long userId, @RequestBody SelectCurrentRequest request) {
        if (request == null || request.getEndpointId() == null) {
            return ResponseEntity.badRequest().body(new ErrorResponse("endpointId is required"));
        }

        Optional<UserAccount> maybeUser = userAccountRepository.findById(userId);
        if (maybeUser.isEmpty()) {
            return ResponseEntity.badRequest().body(new ErrorResponse("User not found"));
        }
        UserAccount user = maybeUser.get();
        if (!isMaintainerOrAdmin(user.getRole())) {
            return ResponseEntity.status(403).body(new ErrorResponse("Role is not allowed to manage LLM endpoints"));
        }

        Optional<LlmEndpointCredentials> maybeEndpoint = llmEndpointCredentialsRepository.findByIdAndUserId(request.getEndpointId(), userId);
        if (maybeEndpoint.isEmpty()) {
            return ResponseEntity.badRequest().body(new ErrorResponse("Endpoint does not exist for this user"));
        }

        user.setCurrentLlmEndpoint(maybeEndpoint.get());
        userAccountRepository.save(user);
        return ResponseEntity.ok(new SelectCurrentResponse(user.getId(), request.getEndpointId()));
    }

    @PutMapping("/{endpointId}")
    @Transactional
    public ResponseEntity<?> update(
        @RequestParam("userId") Long userId,
        @PathVariable("endpointId") Long endpointId,
        @RequestBody UpsertRequest request
    ) {
        Optional<UserAccount> maybeUser = userAccountRepository.findById(userId);
        if (maybeUser.isEmpty()) {
            return ResponseEntity.badRequest().body(new ErrorResponse("User not found"));
        }
        UserAccount user = maybeUser.get();
        if (!isMaintainerOrAdmin(user.getRole())) {
            return ResponseEntity.status(403).body(new ErrorResponse("Role is not allowed to manage LLM endpoints"));
        }
        if (request == null
            || request.getLlmApiType() == null
            || isBlank(request.getBaseURL())
            || isBlank(request.getEndpointDisplayName())) {
            return ResponseEntity.badRequest().body(new ErrorResponse("llmApiType, baseURL and endpointDisplayName are required"));
        }

        Optional<LlmEndpointCredentials> maybeEndpoint = llmEndpointCredentialsRepository.findByIdAndUserId(endpointId, userId);
        if (maybeEndpoint.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        LlmEndpointCredentials entity = maybeEndpoint.get();
        entity.setLlmApiType(request.getLlmApiType());
        entity.setBaseURL(request.getBaseURL().trim());
        entity.setEndpointDisplayName(request.getEndpointDisplayName().trim());
        entity.setModelName(trimToNull(request.getModelName()));
        if (!isBlank(request.getApiKey())) {
            entity.setApiKey(request.getApiKey().trim());
        }

        LlmEndpointCredentials saved = llmEndpointCredentialsRepository.save(entity);
        Long currentEndpointId = user.getCurrentLlmEndpoint() == null ? null : user.getCurrentLlmEndpoint().getId();
        return ResponseEntity.ok(new EntryResponse(
            saved.getId(),
            saved.getLlmApiType(),
            saved.getBaseURL(),
            maskApiKey(saved.getApiKey()),
            saved.getEndpointDisplayName(),
            saved.getModelName(),
            currentEndpointId != null && currentEndpointId.equals(saved.getId())
        ));
    }

    @DeleteMapping("/{endpointId}")
    @Transactional
    public ResponseEntity<?> delete(
        @RequestParam("userId") Long userId,
        @PathVariable("endpointId") Long endpointId
    ) {
        Optional<UserAccount> maybeUser = userAccountRepository.findById(userId);
        if (maybeUser.isEmpty()) {
            return ResponseEntity.badRequest().body(new ErrorResponse("User not found"));
        }
        UserAccount user = maybeUser.get();
        if (!isMaintainerOrAdmin(user.getRole())) {
            return ResponseEntity.status(403).body(new ErrorResponse("Role is not allowed to manage LLM endpoints"));
        }

        Optional<LlmEndpointCredentials> maybeEndpoint = llmEndpointCredentialsRepository.findByIdAndUserId(endpointId, userId);
        if (maybeEndpoint.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Long currentEndpointId = user.getCurrentLlmEndpoint() == null ? null : user.getCurrentLlmEndpoint().getId();
        if (currentEndpointId != null && currentEndpointId.equals(endpointId)) {
            user.setCurrentLlmEndpoint(null);
            userAccountRepository.save(user);
        }

        llmEndpointCredentialsRepository.delete(maybeEndpoint.get());
        return ResponseEntity.noContent().build();
    }

    private boolean isMaintainerOrAdmin(String role) {
        String normalized = role == null ? "" : role.toLowerCase(Locale.ROOT).trim();
        return "maintainer".equals(normalized) || "admin".equals(normalized);
    }

    private void autoSelectSingleEndpointIfNeeded(UserAccount user, List<LlmEndpointCredentials> entries) {
        if (entries.size() != 1) {
            return;
        }
        Long currentEndpointId = user.getCurrentLlmEndpoint() == null ? null : user.getCurrentLlmEndpoint().getId();
        LlmEndpointCredentials onlyEndpoint = entries.get(0);
        if (currentEndpointId != null && currentEndpointId.equals(onlyEndpoint.getId())) {
            return;
        }
        user.setCurrentLlmEndpoint(onlyEndpoint);
        userAccountRepository.save(user);
    }

    private String maskApiKey(String apiKey) {
        if (isBlank(apiKey)) {
            return "";
        }
        String trimmed = apiKey.trim();
        if (trimmed.length() <= 4) {
            return "****";
        }
        return "****" + trimmed.substring(trimmed.length() - 4);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static class UpsertRequest {
        private LlmApiType llmApiType;
        private String baseURL;
        private String apiKey;
        private String endpointDisplayName;
        private String modelName;

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
    }

    public static class SelectCurrentRequest {
        private Long endpointId;

        public Long getEndpointId() {
            return endpointId;
        }

        public void setEndpointId(Long endpointId) {
            this.endpointId = endpointId;
        }
    }

    public static class EntryResponse {
        private final Long id;
        private final LlmApiType llmApiType;
        private final String baseURL;
        private final String maskedApiKey;
        private final String endpointDisplayName;
        private final String modelName;
        private final boolean current;

        public EntryResponse(
            Long id,
            LlmApiType llmApiType,
            String baseURL,
            String maskedApiKey,
            String endpointDisplayName,
            String modelName,
            boolean current
        ) {
            this.id = id;
            this.llmApiType = llmApiType;
            this.baseURL = baseURL;
            this.maskedApiKey = maskedApiKey;
            this.endpointDisplayName = endpointDisplayName;
            this.modelName = modelName;
            this.current = current;
        }

        public Long getId() {
            return id;
        }

        public LlmApiType getLlmApiType() {
            return llmApiType;
        }

        public String getBaseURL() {
            return baseURL;
        }

        public String getMaskedApiKey() {
            return maskedApiKey;
        }

        public String getEndpointDisplayName() {
            return endpointDisplayName;
        }

        public String getModelName() {
            return modelName;
        }

        public boolean isCurrent() {
            return current;
        }
    }

    public static class ListResponse {
        private final List<EntryResponse> items;
        private final Long currentEndpointId;

        public ListResponse(List<EntryResponse> items, Long currentEndpointId) {
            this.items = items;
            this.currentEndpointId = currentEndpointId;
        }

        public List<EntryResponse> getItems() {
            return items;
        }

        public Long getCurrentEndpointId() {
            return currentEndpointId;
        }
    }

    public static class SelectCurrentResponse {
        private final Long userId;
        private final Long endpointId;

        public SelectCurrentResponse(Long userId, Long endpointId) {
            this.userId = userId;
            this.endpointId = endpointId;
        }

        public Long getUserId() {
            return userId;
        }

        public Long getEndpointId() {
            return endpointId;
        }
    }

    public static class ErrorResponse {
        private final String error;

        public ErrorResponse(String error) {
            this.error = error;
        }

        public String getError() {
            return error;
        }
    }
}
