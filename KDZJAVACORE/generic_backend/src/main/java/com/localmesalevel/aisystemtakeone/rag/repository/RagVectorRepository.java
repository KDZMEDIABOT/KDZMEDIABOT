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

    @Query("SELECT v FROM RagVector v WHERE v.version <= :version ORDER BY v.id")
    List<RagVector> findByVersionLessThanEqual(@Param("version") int version);

    @Query("SELECT v FROM RagVector v WHERE v.sourceType = 'dialog_message' AND v.version <= :version ORDER BY v.id")
    List<RagVector> findDialogMessageVectorsByVersionLessThanEqual(@Param("version") int version);

    @Query("SELECT DISTINCT v.sourceId FROM RagVector v WHERE v.sourceType = 'dialog_message' AND v.version <= :version")
    List<Long> findDialogMessageSourceIdsByVersionLessThanEqual(@Param("version") int version);

    @Query(value = "SELECT v.*, (1 - (v.embedding <=> CAST(:queryVec AS vector))) AS similarity " +
            "FROM rag_vectors v " +
            "WHERE v.user_id = :userId " +
            "ORDER BY v.embedding <=> CAST(:queryVec AS vector) " +
            "LIMIT :maxResults",
            nativeQuery = true)
    List<Object[]> findTopBySimilarity(@Param("queryVec") String queryVec, @Param("userId") Long userId, @Param("maxResults") int maxResults);
}
