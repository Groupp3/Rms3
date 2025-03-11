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
    List<Resource> findByUserIdAndDeletedAtIsNull(UUID userId);
    Optional<Resource> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);
    Optional<Resource> findByIdAndDeletedAtIsNull(UUID id);
    List<Resource> findByUserIdAndTitleContaining(UUID userId, String profilePicture);
    List<Resource> findByVisibilityAndDeletedAtIsNull(String visibility);
    List<Resource> findByVisibilityAndContentTypeAndDeletedAtIsNull(String visibility, String contentType);
    List<Resource> findByUserIdAndContentTypeAndDeletedAtIsNull(UUID userId, String contentType);
    Optional<Resource> findByIdAndContentTypeAndDeletedAtIsNull(UUID id, String contentType);
    List<Resource> findAllByContentTypeAndDeletedAtIsNull(String contentType);
    List<Resource> findByDeletedAtIsNull();


    @Query("SELECT r FROM Resource r WHERE r.visibility = 'PUBLIC' OR r.id IN " +
            "(SELECT ra.resourceId FROM ResourceAccess ra WHERE ra.userId = :studentId AND ra.deletedAt IS NULL) " +
            "AND r.contentType = :contentType")
    List<Resource> findAccessibleByStudent(@Param("studentId") UUID studentId, @Param("contentType") String contentType);

    @Query("SELECT r FROM Resource r WHERE r.visibility = 'PUBLIC' OR r.userId = :mentorId " +
            "AND r.contentType = :contentType")
    List<Resource> findByMentor(@Param("mentorId") UUID mentorId, @Param("contentType") String contentType);



    List<Resource> findAllByContentType(String contentType);


}