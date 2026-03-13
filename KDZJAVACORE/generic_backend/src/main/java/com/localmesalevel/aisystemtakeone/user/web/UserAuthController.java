package com.localmesalevel.aisystemtakeone.user.web;

import com.localmesalevel.aisystemtakeone.user.model.UserAccount;
import com.localmesalevel.aisystemtakeone.user.repository.UserAccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;
import java.util.Optional;

@RestController
@RequestMapping("/api/users")
public class UserAuthController {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;

    public UserAuthController(UserAccountRepository userAccountRepository, PasswordEncoder passwordEncoder) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/authenticate")
    public ResponseEntity<?> authenticate(@RequestBody LoginRequest request) {
        if (request == null || isBlank(request.getUsername()) || isBlank(request.getPassword())) {
            return ResponseEntity.badRequest().body(new ErrorResponse("Username and password are required"));
        }

        Optional<UserAccount> maybeUser = userAccountRepository.findByUsername(request.getUsername());
        if (maybeUser.isEmpty()) {
            return ResponseEntity.status(401).body(new ErrorResponse("Invalid credentials"));
        }

        UserAccount user = maybeUser.get();
        if (!passwordMatchesAndMigrateIfNeeded(request.getPassword(), user)) {
            return ResponseEntity.status(401).body(new ErrorResponse("Invalid credentials"));
        }

        String role = user.getRole() == null ? "" : user.getRole().toLowerCase(Locale.ROOT);
        return ResponseEntity.ok(new AuthenticatedUserResponse(
            user.getId(),
            user.getUsername(),
            role
        ));
    }

    @GetMapping
    public ResponseEntity<UserListResponse> listUsers(
        @RequestParam(name = "username", defaultValue = "") String username,
        @RequestParam(name = "page", defaultValue = "0") int page,
        @RequestParam(name = "size", defaultValue = "10") int size
    ) {
        int normalizedPage = Math.max(page, 0);
        int normalizedSize = Math.min(Math.max(size, 1), 100);
        Page<UserAccount> usersPage = userAccountRepository.findByUsernameContainingIgnoreCase(
            username == null ? "" : username.trim(),
            PageRequest.of(normalizedPage, normalizedSize)
        );

        return ResponseEntity.ok(new UserListResponse(
            usersPage.getContent().stream()
                .map(user -> new UserSummaryResponse(user.getId(), user.getUsername(), normalizeRole(user.getRole())))
                .toList(),
            usersPage.getNumber(),
            usersPage.getSize(),
            usersPage.getTotalElements(),
            usersPage.getTotalPages()
        ));
    }

    @PostMapping
    public ResponseEntity<?> createUser(@RequestBody UpsertUserRequest request) {
        if (request == null || isBlank(request.getUsername()) || isBlank(request.getRole()) || isBlank(request.getPassword())) {
            return ResponseEntity.badRequest().body(new ErrorResponse("Username, role and password are required"));
        }
        if (request.getPassword().length() < 3) {
            return ResponseEntity.badRequest().body(new ErrorResponse("Password must be at least 3 characters"));
        }

        String username = request.getUsername().trim();
        if (userAccountRepository.findByUsername(username).isPresent()) {
            return ResponseEntity.badRequest().body(new ErrorResponse("Username already exists"));
        }

        UserAccount user = new UserAccount();
        user.setUsername(username);
        user.setRole(normalizeRole(request.getRole()));
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        UserAccount saved = userAccountRepository.save(user);
        return ResponseEntity.ok(new UserSummaryResponse(saved.getId(), saved.getUsername(), normalizeRole(saved.getRole())));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateUser(@PathVariable Long id, @RequestBody UpsertUserRequest request) {
        if (request == null || isBlank(request.getUsername()) || isBlank(request.getRole())) {
            return ResponseEntity.badRequest().body(new ErrorResponse("Username and role are required"));
        }

        Optional<UserAccount> maybeUser = userAccountRepository.findById(id);
        if (maybeUser.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        String username = request.getUsername().trim();
        Optional<UserAccount> userWithSameUsername = userAccountRepository.findByUsername(username);
        if (userWithSameUsername.isPresent() && !id.equals(userWithSameUsername.get().getId())) {
            return ResponseEntity.badRequest().body(new ErrorResponse("Username already exists"));
        }

        UserAccount user = maybeUser.get();
        user.setUsername(username);
        user.setRole(normalizeRole(request.getRole()));
        if (!isBlank(request.getPassword())) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        UserAccount saved = userAccountRepository.save(user);
        return ResponseEntity.ok(new UserSummaryResponse(saved.getId(), saved.getUsername(), normalizeRole(saved.getRole())));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        Optional<UserAccount> maybeUser = userAccountRepository.findById(id);
        if (maybeUser.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        userAccountRepository.delete(maybeUser.get());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestBody ChangePasswordRequest request) {
        if (request == null
            || isBlank(request.getUsername())
            || isBlank(request.getCurrentPassword())
            || isBlank(request.getNewPassword())) {
            return ResponseEntity.badRequest().body(new ErrorResponse("Username, current password and new password are required"));
        }

        if (request.getNewPassword().length() < 8) {
            return ResponseEntity.badRequest().body(new ErrorResponse("New password must be at least 8 characters"));
        }

        Optional<UserAccount> maybeUser = userAccountRepository.findByUsername(request.getUsername().trim());
        if (maybeUser.isEmpty()) {
            return ResponseEntity.status(401).body(new ErrorResponse("Invalid credentials"));
        }

        UserAccount user = maybeUser.get();
        if (!passwordMatchesAndMigrateIfNeeded(request.getCurrentPassword(), user)) {
            return ResponseEntity.status(401).body(new ErrorResponse("Current password is incorrect"));
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userAccountRepository.save(user);
        return ResponseEntity.ok(new SuccessResponse("Password changed successfully"));
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String normalizeRole(String role) {
        return role == null ? "" : role.trim().toLowerCase(Locale.ROOT);
    }

    private boolean passwordMatchesAndMigrateIfNeeded(String rawPassword, UserAccount user) {
        String stored = user.getPassword();
        if (isBlank(stored)) {
            return false;
        }

        if (isBcryptHash(stored)) {
            return passwordEncoder.matches(rawPassword, stored);
        }

        // Legacy plaintext migration path: allow once, then replace with bcrypt hash.
        if (rawPassword.equals(stored)) {
            user.setPassword(passwordEncoder.encode(rawPassword));
            userAccountRepository.save(user);
            return true;
        }
        return false;
    }

    private boolean isBcryptHash(String value) {
        return value.startsWith("$2a$") || value.startsWith("$2b$") || value.startsWith("$2y$");
    }

    public static class LoginRequest {
        private String username;
        private String password;

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
    }

    public static class AuthenticatedUserResponse {
        private final Long id;
        private final String username;
        private final String role;

        public AuthenticatedUserResponse(Long id, String username, String role) {
            this.id = id;
            this.username = username;
            this.role = role;
        }

        public Long getId() {
            return id;
        }

        public String getUsername() {
            return username;
        }

        public String getRole() {
            return role;
        }
    }

    public static class UserSummaryResponse {
        private final Long id;
        private final String username;
        private final String role;

        public UserSummaryResponse(Long id, String username, String role) {
            this.id = id;
            this.username = username;
            this.role = role;
        }

        public Long getId() {
            return id;
        }

        public String getUsername() {
            return username;
        }

        public String getRole() {
            return role;
        }
    }

    public static class UserListResponse {
        private final java.util.List<UserSummaryResponse> items;
        private final int page;
        private final int size;
        private final long totalElements;
        private final int totalPages;

        public UserListResponse(java.util.List<UserSummaryResponse> items, int page, int size, long totalElements, int totalPages) {
            this.items = items;
            this.page = page;
            this.size = size;
            this.totalElements = totalElements;
            this.totalPages = totalPages;
        }

        public java.util.List<UserSummaryResponse> getItems() {
            return items;
        }

        public int getPage() {
            return page;
        }

        public int getSize() {
            return size;
        }

        public long getTotalElements() {
            return totalElements;
        }

        public int getTotalPages() {
            return totalPages;
        }
    }

    public static class UpsertUserRequest {
        private String username;
        private String role;
        private String password;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    public static class ChangePasswordRequest {
        private String username;
        private String currentPassword;
        private String newPassword;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getCurrentPassword() {
            return currentPassword;
        }

        public void setCurrentPassword(String currentPassword) {
            this.currentPassword = currentPassword;
        }

        public String getNewPassword() {
            return newPassword;
        }

        public void setNewPassword(String newPassword) {
            this.newPassword = newPassword;
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

    public static class SuccessResponse {
        private final String message;

        public SuccessResponse(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }
}
