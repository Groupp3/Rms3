package com.example.rmss3.Service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.amazonaws.util.IOUtils;
import com.example.rmss3.entity.Resource;
import com.example.rmss3.exception.ResourceNotFoundException;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class S3Service {

    @Autowired
    private AmazonS3 s3Client;

    @Autowired
    private ResourceRepository resourceRepository;

    @Value("${aws.s3.bucket}")
    private String bucketName;

    public Resource uploadFile(MultipartFile file, UUID userId) throws IOException {
        return uploadFile(file, userId, null);
    }

    public Resource uploadFile(MultipartFile file, UUID userId, String title) throws IOException {
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
        resource.setVisibility("public");
        resource.setObjectKey(objectKey);
        resource.setFilename(file.getOriginalFilename());
        resource.setTitle(title != null ? title : file.getOriginalFilename());
        resource.setContentType(file.getContentType());
        resource.setUserId(userId);
        resource.setCreatedAt(LocalDateTime.now());
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

        // Upload the new profile picture
        return uploadFile(file, userId, "Profile Picture");
    }

    public ResponseEntity<byte[]> getFile(Long resourceId, UUID userId) {
        Resource resource = resourceRepository.findByIdAndUserIdAndDeletedAtIsNull(resourceId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Resource not found"));

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

    public String getFileUrl(Long resourceId) {
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
}
