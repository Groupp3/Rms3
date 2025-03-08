package com.example.rmss3.repository;

import com.example.rmss3.entity.Resource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ResourceRepository extends JpaRepository<Resource, Long> {
    List<Resource> findByUserIdAndDeletedAtIsNull(UUID userId);
    Optional<Resource> findByIdAndUserIdAndDeletedAtIsNull(Long id, UUID userId);
    Optional<Resource> findByObjectKeyAndDeletedAtIsNull(String objectKey);

    List<Resource> findByUserIdAndTitleContaining(UUID userId, String profilePicture);
}