package com.localmesalevel.aisystemtakeone.auth;

import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Filter that validates session cookies against the auth service
 * and sets the userId as a request attribute for downstream controllers.
 */
@Component
@Order(10)
public class SessionAuthFilter extends OncePerRequestFilter {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final HttpClient httpClient;
    private final String authServiceUrl;

    public SessionAuthFilter() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        String envUrl = System.getenv("AUTH_SERVICE_URL");
        if (envUrl != null && !envUrl.isEmpty()) {
            this.authServiceUrl = envUrl;
        } else {
            this.authServiceUrl = "http://localhost:3002";
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                      HttpServletResponse response,
                                      FilterChain filterChain) throws ServletException, IOException {
        String requestUri = request.getRequestURI();
        System.out.println("[SESSION_AUTH] Request: " + requestUri + " method=" + request.getMethod());

        // Only protect API endpoints that aren't public
        if (!isProtectedPath(requestUri)) {
            System.out.println("[SESSION_AUTH] Path not protected, skipping");
            filterChain.doFilter(request, response);
            return;
        }

        System.out.println("[SESSION_AUTH] Path protected, getting cookie");
        String sessionCookie = extractSessionCookie(request);
        System.out.println("[SESSION_AUTH] sessionCookie=" + (sessionCookie != null ? "present" : "null"));
        if (sessionCookie == null) {
            System.out.println("[SESSION_AUTH] Missing session cookie -> 401");
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing session cookie");
            return;
        }

        try {
            Long userId = validateSession(sessionCookie);
            System.out.println("[SESSION_AUTH] validateSession returned userId=" + userId);
            if (userId == null) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired session");
                return;
            }
            request.setAttribute("userId", userId);
            filterChain.doFilter(request, response);
        } catch (Exception e) {
            System.err.println("[SESSION_AUTH] validation error: " + e.getMessage());
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Auth validation error: " + e.getMessage());
        }
    }

    private boolean isProtectedPath(String uri) {
        // Protect dialog and workspace endpoints
        return uri.startsWith("/api/dialogs") || uri.startsWith("/api/workspaces");
    }

    private String extractSessionCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if ("connect.sid".equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private Long validateSession(String sessionCookieValue) throws Exception {
        java.net.http.HttpRequest httpRequest = java.net.http.HttpRequest.newBuilder()
                .uri(URI.create(authServiceUrl + "/api/auth/me"))
                .timeout(Duration.ofSeconds(5))
                .header("Cookie", "connect.sid=" + sessionCookieValue)
                .GET()
                .build();

        java.net.http.HttpResponse<String> httpResponse = httpClient.send(httpRequest,
                java.net.http.HttpResponse.BodyHandlers.ofString());

        if (httpResponse.statusCode() != 200) {
            return null;
        }

        JsonNode body = MAPPER.readTree(httpResponse.body());
        if (body.has("id")) {
            return body.get("id").asLong();
        }
        return null;
    }
}
