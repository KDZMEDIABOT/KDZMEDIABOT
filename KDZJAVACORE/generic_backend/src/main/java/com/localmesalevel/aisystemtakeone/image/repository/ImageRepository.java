package com.localmesalevel.aisystemtakeone.image.repository;

import com.localmesalevel.aisystemtakeone.image.model.ImageData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ImageRepository extends JpaRepository<ImageData, Long> {
    List<ImageData> findByTopicId(Long topicId);
}
