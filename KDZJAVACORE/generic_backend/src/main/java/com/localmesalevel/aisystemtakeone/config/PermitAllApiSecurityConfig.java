package com.localmesalevel.aisystemtakeone.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * When JWT issuer is NOT configured (e.g. dev without IdP): permit all /api for local/test use.
 */
@EnableWebSecurity
@Configuration
public class PermitAllApiSecurityConfig {

    @Bean
    @Order(1)
    //@Conditional(JwtIssuerNotConfiguredCondition.class)
    public SecurityFilterChain permitAllApiChain(HttpSecurity http) throws Exception {
        http
            .antMatcher("/api/**")
            .authorizeRequests(auth -> auth.anyRequest().permitAll())
            .csrf(csrf -> csrf.disable());
        return http.build();
    }
}
