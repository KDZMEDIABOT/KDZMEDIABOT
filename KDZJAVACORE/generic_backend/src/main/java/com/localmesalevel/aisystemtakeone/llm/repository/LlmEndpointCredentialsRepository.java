package com.localmesalevel.aisystemtakeone.llm.repository;

import com.localmesalevel.aisystemtakeone.llm.model.LlmEndpointCredentials;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LlmEndpointCredentialsRepository extends JpaRepository<LlmEndpointCredentials, Long> {
    List<LlmEndpointCredentials> findByUserIdOrderByIdDesc(Long userId);
    Optional<LlmEndpointCredentials> findByIdAndUserId(Long id, Long userId);
}
