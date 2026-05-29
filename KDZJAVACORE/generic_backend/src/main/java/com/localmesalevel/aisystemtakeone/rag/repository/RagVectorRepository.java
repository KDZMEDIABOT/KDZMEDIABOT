package com.localmesalevel.aisystemtakeone.rag.repository;

import com.localmesalevel.aisystemtakeone.rag.model.RagVector;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RagVectorRepository extends JpaRepository<RagVector, Long> {

    List<RagVector> findByUserIdAndSourceTypeAndSourceId(Long userId, String sourceType, Long sourceId);

    void deleteBySourceTypeAndSourceId(String sourceType, Long sourceId);

    @Modifying
    @Query("DELETE FROM RagVector v WHERE v.sourceType = :sourceType AND v.threadId = :threadId")
    void deleteBySourceTypeAndThreadId(@Param("sourceType") String sourceType, @Param("threadId") Long threadId);

    void deleteByThreadId(Long threadId);

    List<RagVector> findByUserId(Long userId);
}
