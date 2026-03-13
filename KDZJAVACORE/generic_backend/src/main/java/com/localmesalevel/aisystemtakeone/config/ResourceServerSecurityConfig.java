package com.localmesalevel.aisystemtakeone.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * When JWT issuer is configured: permit health/error, require JWT for other /api.
 */
@EnableWebSecurity
@Configuration
public class ResourceServerSecurityConfig {

    @Bean
    @Order(1)
    @Conditional(JwtIssuerConfiguredCondition.class)
    public SecurityFilterChain resourceServerChain(HttpSecurity http) throws Exception {
        http
            .antMatcher("/api/**")
            .authorizeRequests(auth -> auth
                .antMatchers(
                    "/api/**/health",
                    "/api/workflow/health",
                    "/api/users/authenticate",
                    "/error"
                ).permitAll()
                .antMatchers(HttpMethod.POST, "/api/users/change-password")
                    .authenticated()
                .antMatchers(HttpMethod.GET, "/api/users", "/api/users/**")
                    .hasAnyAuthority("ROLE_admin", "SCOPE_admin", "admin")
                .antMatchers(HttpMethod.POST, "/api/users", "/api/users/**")
                    .hasAnyAuthority("ROLE_admin", "SCOPE_admin", "admin")
                .antMatchers(HttpMethod.PUT, "/api/users", "/api/users/**")
                    .hasAnyAuthority("ROLE_admin", "SCOPE_admin", "admin")
                .antMatchers(HttpMethod.DELETE, "/api/users", "/api/users/**")
                    .hasAnyAuthority("ROLE_admin", "SCOPE_admin", "admin")
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}))
            .csrf(csrf -> csrf.disable());
        return http.build();
    }
}
