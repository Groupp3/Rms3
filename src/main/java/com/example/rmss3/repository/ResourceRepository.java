package com.example.rmss3.repository;

import com.example.rmss3.entity.Resource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ResourceRepository extends JpaRepository<Resource, UUID> {
    List<Resource> findByUserIdAndDeletedAtIsNull(UUID userId);
    Optional<Resource> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);
    Optional<Resource> findByIdAndDeletedAtIsNull(UUID id);
    List<Resource> findByUserIdAndTitleContaining(UUID userId, String profilePicture);
    List<Resource> findByVisibilityAndDeletedAtIsNull(String visibility);

    List<Resource> findByDeletedAtIsNull();
}