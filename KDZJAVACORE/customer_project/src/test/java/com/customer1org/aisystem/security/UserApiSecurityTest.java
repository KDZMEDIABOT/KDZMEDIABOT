package com.customer1org.aisystem.security;

import com.localmesalevel.aisystemtakeone.config.DefaultSecurityConfig;
import com.localmesalevel.aisystemtakeone.config.PermitAllApiSecurityConfig;
import com.localmesalevel.aisystemtakeone.config.ResourceServerSecurityConfig;
import com.localmesalevel.aisystemtakeone.user.model.UserAccount;
import com.localmesalevel.aisystemtakeone.user.repository.UserAccountRepository;
import com.localmesalevel.aisystemtakeone.user.web.UserAuthController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = UserApiSecurityTest.TestConfig.class)
@TestPropertySource(properties = {
    "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://issuer.example.test"
})
class UserApiSecurityTest {

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({
        ResourceServerSecurityConfig.class,
        PermitAllApiSecurityConfig.class,
        DefaultSecurityConfig.class
    })
    static class TestConfig {
        @Bean
        UserAccountRepository userAccountRepository() {
            return Mockito.mock(UserAccountRepository.class);
        }

        @Bean
        JwtDecoder jwtDecoder() {
            return Mockito.mock(JwtDecoder.class);
        }

        @Bean
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder();
        }

        @Bean
        UserAuthController userAuthController(UserAccountRepository userAccountRepository, PasswordEncoder passwordEncoder) {
            return new UserAuthController(userAccountRepository, passwordEncoder);
        }
    }

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserAccountRepository userAccountRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        reset(userAccountRepository);
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
            .apply(springSecurity())
            .build();
    }

    @Test
    void usersCrudRequiresAdminAuthority() throws Exception {
        mockMvc.perform(post("/api/users")
                .with(jwt().authorities(new SimpleGrantedAuthority("maintainer")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"newuser\",\"role\":\"admin\",\"password\":\"newpassword\"}"))
            .andExpect(status().is4xxClientError());

        verifyNoInteractions(userAccountRepository);
    }

    @Test
    void changePasswordAllowsAnyAuthenticatedUser() throws Exception {
        UserAccount account = new UserAccount();
        account.setId(11L);
        account.setUsername("maintainer");
        account.setRole("maintainer");
        account.setPassword(new BCryptPasswordEncoder().encode("current-pass"));

        when(userAccountRepository.findByUsername("maintainer")).thenReturn(Optional.of(account));
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/api/users/change-password")
                .with(jwt().authorities(new SimpleGrantedAuthority("maintainer")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"maintainer\",\"currentPassword\":\"current-pass\",\"newPassword\":\"new-password\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Password changed successfully"));

        ArgumentCaptor<UserAccount> savedUser = ArgumentCaptor.forClass(UserAccount.class);
        verify(userAccountRepository).save(savedUser.capture());
        assertThat(savedUser.getValue().getPassword()).isNotEqualTo("new-password");
        assertThat(savedUser.getValue().getPassword()).startsWith("$2");
    }
}
