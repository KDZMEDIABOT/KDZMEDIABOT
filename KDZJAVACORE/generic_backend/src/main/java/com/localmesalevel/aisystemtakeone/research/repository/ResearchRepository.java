package com.localmesalevel.aisystemtakeone.research.repository;

import com.localmesalevel.aisystemtakeone.research.model.ResearchData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ResearchRepository extends JpaRepository<ResearchData, Long> {
    Optional<ResearchData> findByTopicId(Long topicId);
}
