package com.example.rmss3.repository;

import com.example.rmss3.entity.ResourceAccess;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ResourceAccessRepository extends JpaRepository<ResourceAccess, UUID> {
    List<ResourceAccess> findByResourceIdAndDeletedAtIsNull(UUID resourceId);
    Optional<ResourceAccess> findByResourceIdAndUserIdAndDeletedAtIsNull(UUID resourceId, UUID userId);
    List<ResourceAccess> findByUserIdAndDeletedAtIsNull(UUID userId);
}