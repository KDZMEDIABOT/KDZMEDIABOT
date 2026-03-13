package com.localmesalevel.aisystemtakeone.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

/**
 * Matches when spring.security.oauth2.resourceserver.jwt.issuer-uri is not set or blank.
 */
public class JwtIssuerNotConfiguredCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String uri = context.getEnvironment()
            .getProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri");
        return !StringUtils.hasText(uri);
    }
}
