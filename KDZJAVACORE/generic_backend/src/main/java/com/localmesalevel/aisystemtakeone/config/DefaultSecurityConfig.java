package com.localmesalevel.aisystemtakeone.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Fallback: permit all non-API paths (e.g. error page, actuator).
 */
@Configuration
public class DefaultSecurityConfig {

    @Bean
    @Order(100)
    public SecurityFilterChain defaultChain(HttpSecurity http) throws Exception {
        http
            .antMatcher("/**")
            .authorizeRequests(auth -> auth.anyRequest().permitAll())
            .csrf(csrf -> csrf.disable());
        return http.build();
    }
}
