package com.localmesalevel.aisystemtakeone.user.config;

import com.localmesalevel.aisystemtakeone.user.model.UserAccount;
import com.localmesalevel.aisystemtakeone.user.repository.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class UserBootstrapConfig {

    private static final Logger log = LoggerFactory.getLogger(UserBootstrapConfig.class);

    @Bean
    CommandLineRunner ensureAdminUser(UserAccountRepository userAccountRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            UserAccount admin = userAccountRepository.findByUsername("admin")
                .orElseGet(UserAccount::new);

            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode("admin")); // NOTE: dev-only default, not for production use
            admin.setRole("admin");

            if (admin.getId() == null) {
                userAccountRepository.save(admin);
                log.info("Created default admin user with username 'admin'. PLEASE CHANGE THE PASSWORD IN PROD.");
            } else {
                userAccountRepository.save(admin);
                log.info("Ensured default admin user credentials and role are set.");
            }
        };
    }
}

