package com.example.rmss3.repository;

import com.example.rmss3.entity.Resource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ResourceRepository extends JpaRepository<Resource, UUID> {

    // Find resources by userId where deletedAt is null
    List<Resource> findByUserIdAndDeletedAtIsNull(UUID userId);

    // Find resource by id and userId where deletedAt is null
    Optional<Resource> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);

    // Find resource by id where deletedAt is null
    Optional<Resource> findByIdAndDeletedAtIsNull(UUID id);

    // Find resources by userId and title containing the provided string
    List<Resource> findByUserIdAndTitleContaining(UUID userId, String title);

    // Find resources that are public and have not been deleted
    List<Resource> findByIsPublicTrueAndDeletedAtIsNull();

    // Find public resources of the provided content type that are not deleted
    List<Resource> findByIsPublicTrueAndContentTypeAndDeletedAtIsNull(String contentType);

    // Find resources by userId and content type where deletedAt is null
    List<Resource> findByUserIdAndContentTypeAndDeletedAtIsNull(UUID userId, String contentType);

    // Find resource by id and content type where deletedAt is null
    Optional<Resource> findByIdAndContentTypeAndDeletedAtIsNull(UUID id, String contentType);

    // Find all resources by content type where deletedAt is null
    List<Resource> findAllByContentTypeAndDeletedAtIsNull(String contentType);

    // Find all resources that have not been deleted
    List<Resource> findByDeletedAtIsNull();

    // Find resources that are accessible by a student based on their ID and content type
    @Query("SELECT r FROM Resource r WHERE (r.isPublic = true OR r.id IN " +
            "(SELECT ra.resourceId FROM ResourceAccess ra WHERE ra.userId = :studentId AND ra.deletedAt IS NULL)) " +
            "AND r.contentType = :contentType AND r.deletedAt IS NULL")
    List<Resource> findAccessibleByStudent(@Param("studentId") UUID studentId, @Param("contentType") String contentType);

    // Find resources that are either public or belong to a mentor and have the specified content type
    @Query("SELECT r FROM Resource r WHERE (r.isPublic = true OR r.userId = :mentorId) " +
            "AND r.contentType = :contentType AND r.deletedAt IS NULL")
    List<Resource> findByMentor(@Param("mentorId") UUID mentorId, @Param("contentType") String contentType);

    // Find all resources by content type (without checking for deletedAt)
    List<Resource> findAllByContentType(String contentType);
}
