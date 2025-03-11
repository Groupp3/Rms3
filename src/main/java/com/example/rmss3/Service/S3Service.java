package com.example.rmss3.Service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.amazonaws.util.IOUtils;
import com.example.rmss3.entity.Resource;
import com.example.rmss3.entity.ResourceAccess;
import com.example.rmss3.exception.ResourceNotFoundException;
import com.example.rmss3.repository.ResourceAccessRepository;
import com.example.rmss3.repository.ResourceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class S3Service {

    @Autowired
    private AmazonS3 s3Client;

    @Autowired
    private ResourceRepository resourceRepository;

    @Autowired
    private ResourceAccessRepository resourceAccessRepository;

    @Value("${aws.s3.bucket}")
    private String bucketName;

    public Resource uploadFile(MultipartFile file, UUID userId) throws IOException {
        return uploadFile(file, userId, null);
    }

    public Resource uploadFile(MultipartFile file, UUID userId, String title) throws IOException {
        return uploadFile(file, userId, title, "public");
    }



    public Resource uploadFile(MultipartFile file, UUID userId, String title, String visibility) throws IOException {
        String fileName = generateFileName(file);
        String objectKey = userId + "/" + fileName;

        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType(file.getContentType());
        metadata.setContentLength(file.getSize());

        // Upload file to S3
        s3Client.putObject(bucketName, objectKey, file.getInputStream(), metadata);

        // Create and save resource record
        Resource resource = new Resource();
        resource.setFileSize(file.getSize());
        resource.setVisibility(visibility != null ? visibility.toLowerCase() : "public");
        resource.setObjectKey(objectKey);
        resource.setFilename(file.getOriginalFilename());
        resource.setTitle(title != null ? title : file.getOriginalFilename());
        resource.setContentType(file.getContentType());
        resource.setUserId(userId);
        resource.setCreatedAt(LocalDateTime.now());
        resource.setModifiedAt(LocalDateTime.now());

        return resourceRepository.save(resource);
    }

    public Resource softDeleteResource(UUID resourceId, UUID userId, String userRole) throws AccessDeniedException {
        Resource resource = resourceRepository.findByIdAndDeletedAtIsNull(resourceId)
                .orElseThrow(() -> new ResourceNotFoundException("Resource not found"));

        // Check if user has permission to delete this resource
        if(!resource.getUserId().equals(userId) && !"ADMIN".equals(userRole)) {
            throw new AccessDeniedException("You don't have permission to delete this resource");
        }

        // Mark as deleted
        resource.setDeletedAt(LocalDateTime.now());
        resource.setModifiedAt(LocalDateTime.now());

        return resourceRepository.save(resource);
    }


    public Resource uploadProfilePicture(MultipartFile file, UUID userId) throws IOException {
        // Mark existing profile pictures as deleted
        List<Resource> existingProfilePics = resourceRepository.findByUserIdAndTitleContaining(userId, "Profile Picture");
        for (Resource oldPic : existingProfilePics) {
            oldPic.setDeletedAt(LocalDateTime.now());
            resourceRepository.save(oldPic);
        }


        return uploadFile(file, userId, "Profile Picture");
    }

    public ResponseEntity<byte[]> getFile(UUID resourceId, UUID userId, String userRole) throws AccessDeniedException {
        Resource resource = resourceRepository.findByIdAndDeletedAtIsNull(resourceId)
                .orElseThrow(() -> new ResourceNotFoundException("Resource not found"));

        if(!checkAccess(resource,userId,userRole)){
            throw new AccessDeniedException("You dont have permission to access this resource");
        }

        try {
            S3Object s3Object = s3Client.getObject(bucketName, resource.getObjectKey());
            S3ObjectInputStream inputStream = s3Object.getObjectContent();
            byte[] content = IOUtils.toByteArray(inputStream);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(resource.getContentType()));
            headers.setContentDispositionFormData("attachment", resource.getFilename());
            headers.setContentLength(content.length);

            return new ResponseEntity<>(content, headers, HttpStatus.OK);
        } catch (IOException e) {
            throw new RuntimeException("Error downloading file", e);
        }
    }

    public String getFileUrl(UUID resourceId) {
        Resource resource = resourceRepository.findById(resourceId)
                .orElseThrow(() -> new ResourceNotFoundException("Resource not found"));

        return s3Client.getUrl(bucketName, resource.getObjectKey()).toString();
    }

    private String generateFileName(MultipartFile file) {
        return System.currentTimeMillis() + "_" + file.getOriginalFilename().replaceAll("\\s+", "_");
    }

    public Resource updateResource(Resource resource) {
        return resourceRepository.save(resource);
    }

    private boolean checkAccess(Resource resource, UUID userId, String userRole) {
        if("ADMIN".equals(userRole)){
            return true;
        }

        if("public".equals(resource.getVisibility())){
            return true;
        }

        if(resource.getUserId().equals(userId)){
            return true;
        }

        return resourceAccessRepository.findByResourceIdAndUserIdAndDeletedAtIsNull(
                resource.getId(),userId).isPresent();


    }


    public void grantAccess(UUID resourceId, UUID granteeId, UUID grantorId,String grantorRole) throws AccessDeniedException {
        Resource resource= resourceRepository.findByIdAndDeletedAtIsNull(resourceId)
                .orElseThrow(() -> new ResourceNotFoundException("Resource not found"));

        if(!resource.getUserId().equals(grantorId) && !"ADMIN".equals(grantorRole)){
            throw new AccessDeniedException("You don't have permission to share this resource");
        }

        ResourceAccess access =new ResourceAccess();
        access.setResourceId(resourceId);
        access.setUserId(granteeId);
        access.setGrantedAt(LocalDateTime.now());

        resourceAccessRepository.save(access);
    }


    public void revokeAccess(UUID resourceId, UUID userId, UUID revokerUserId, String revokerRole) throws AccessDeniedException {
        Resource resource = resourceRepository.findByIdAndDeletedAtIsNull(resourceId)
                .orElseThrow(() -> new ResourceNotFoundException("Resource not found"));


        if (!resource.getUserId().equals(revokerUserId) && !"ADMIN".equals(revokerRole)) {
            throw new AccessDeniedException("You don't have permission to revoke access");
        }

        ResourceAccess access = resourceAccessRepository
                .findByResourceIdAndUserIdAndDeletedAtIsNull(resourceId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Access not found"));

        access.setDeletedAt(LocalDateTime.now());
        resourceAccessRepository.save(access);
    }


    public List<Resource> findAccessibleResources(UUID userId, String userRole) {
        List<Resource> resources = new ArrayList<>();


        resources.addAll(resourceRepository.findByVisibilityAndDeletedAtIsNull("public"));


        if ("ADMIN".equals(userRole)) {
            return resourceRepository.findByDeletedAtIsNull();
        }

        resources.addAll(resourceRepository.findByUserIdAndDeletedAtIsNull(userId));


        List<ResourceAccess> accessList = resourceAccessRepository
                .findByUserIdAndDeletedAtIsNull(userId);

        for (ResourceAccess access : accessList) {
            resourceRepository.findByIdAndDeletedAtIsNull(access.getResourceId())
                    .ifPresent(resources::add);
        }

        return resources;
    }


    public boolean isResourceTypeAllowed(String contentType, String userRole) {
        boolean isPDF = contentType.equals("application/pdf");
        boolean isVideo = contentType.startsWith("video/");
        boolean isCertificate = contentType.startsWith("image/");

        if ("STUDENT".equals(userRole)) {

            return isCertificate;
        }


        return true;
    }


    public Resource uploadLearningMaterial(MultipartFile file, UUID userId, String userRole,
                                           String visibility) throws IOException {
        if (!isResourceTypeAllowed(file.getContentType(), userRole)) {
            throw new AccessDeniedException("You don't have permission to upload this file type");
        }

        return uploadFile(file, userId, null, visibility); // Pass the visibility parameter
    }


}
