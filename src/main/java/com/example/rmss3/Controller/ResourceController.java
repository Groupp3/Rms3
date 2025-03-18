package com.example.rmss3.Controller;

import com.example.rmss3.Service.JwtService;
import com.example.rmss3.Service.S3Service;
import com.example.rmss3.Service.UserService;
import com.example.rmss3.dto.ApiResponse;
import com.example.rmss3.entity.Resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.AccessDeniedException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;


@RestController
@RequestMapping("/api/resources")
public class ResourceController {

    private static final Logger logger = LoggerFactory.getLogger(ResourceController.class);

    @Autowired
    private S3Service s3Service;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserService userService;

    @PostMapping(value = "/upload-multiple", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<?>> uploadMultipleResources(
            @RequestParam("files") MultipartFile[] files,
            @RequestParam("visibility") String visibility,
            @RequestHeader("Authorization") String token) {

        logger.info("Received request to upload {} files", files.length);

        try {
            UUID userId = extractUserIdFromToken(token);
            String userRole = extractRoleFromToken(token);

            logger.info("Request from user ID: {} with role: {}", userId, userRole);

            if (!userService.isUserApproved(userId)) {
                logger.warn("Upload attempt by unapproved user: {}", userId);
                return ResponseEntity
                        .status(HttpStatus.FORBIDDEN)
                        .body(new ApiResponse<>(HttpStatus.FORBIDDEN.value(), "User not approved", null));
            }

            // Convert visibility to Boolean safely
            Boolean isPublic;
            if ("true".equalsIgnoreCase(visibility)) {
                isPublic = true;
            } else if ("false".equalsIgnoreCase(visibility)) {
                isPublic = false;
            } else {
                logger.warn("Invalid visibility value received: {}", visibility);
                return ResponseEntity
                        .status(HttpStatus.BAD_REQUEST)
                        .body(new ApiResponse<>(HttpStatus.BAD_REQUEST.value(), "Invalid visibility value", null));
            }

            List<Resource> successfulUploads = new ArrayList<>();
            Map<String, String> failedUploads = new HashMap<>();

            // Process each file
            for (MultipartFile file : files) {
                String filename = file.getOriginalFilename();

                try {
                    logger.info("Processing file: {}, Size: {}, Content type: {}",
                            filename, file.getSize(), file.getContentType());

                    // Skip empty files
                    if (file.isEmpty()) {
                        logger.warn("Skipping empty file: {}", filename);
                        failedUploads.put(filename != null ? filename : "unknown", "Empty file");
                        continue;
                    }

                    // Check if user has permission to upload this type of file
                    if (!s3Service.isResourceTypeAllowed(file.getContentType(), userRole)) {
                        logger.warn("Permission denied for file type: {} by user role: {}",
                                file.getContentType(), userRole);
                        failedUploads.put(filename, "You don't have permission to upload this file type");
                        continue;
                    }

                    // Upload the file
                    Resource resource = s3Service.uploadLearningMaterial(file, userId, userRole, isPublic);

                    successfulUploads.add(resource);
                    logger.info("Successfully uploaded file: {} with resource ID: {}",
                            filename, resource.getId());
                } catch (Exception e) {
                    logger.error("Error uploading file {}: {}", filename, e.getMessage(), e);
                    failedUploads.put(filename != null ? filename : "unknown", e.getMessage());
                }
            }

            // Create response
            Map<String, Object> response = new HashMap<>();
            response.put("successfulUploads", successfulUploads);
            response.put("failedUploads", failedUploads);
            response.put("totalFiles", files.length);
            response.put("successCount", successfulUploads.size());
            response.put("failureCount", failedUploads.size());

            HttpStatus status = failedUploads.isEmpty() ? HttpStatus.CREATED : HttpStatus.PARTIAL_CONTENT;
            String message = failedUploads.isEmpty() ?
                    "All resources uploaded successfully" :
                    String.format("%d of %d resources uploaded successfully", successfulUploads.size(), files.length);

            logger.info("Upload multiple files result: {} of {} successful",
                    successfulUploads.size(), files.length);

            return ResponseEntity
                    .status(status)
                    .body(new ApiResponse<>(status.value(), message, response));

        } catch (Exception e) {
            logger.error("Global exception in uploadMultipleResources: {}", e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(HttpStatus.INTERNAL_SERVER_ERROR.value(), e.getMessage(), null));
        }
    }



    @GetMapping("/{resourceId}")
    public ResponseEntity<?> getResource(
            @PathVariable UUID resourceId,
            @RequestHeader("Authorization") String token) {

        try {
            UUID userId = extractUserIdFromToken(token);
            String userRole = extractRoleFromToken(token);

            if (!userService.isUserApproved(userId)) {
                return ResponseEntity
                        .status(HttpStatus.FORBIDDEN)
                        .body(new ApiResponse<>(HttpStatus.FORBIDDEN.value(), "User not approved", null));
            }

            return s3Service.getFile(resourceId, userId, userRole);
        } catch (AccessDeniedException e) {
            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(new ApiResponse<>(HttpStatus.FORBIDDEN.value(), "Access denied to this resource", null));
        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(HttpStatus.INTERNAL_SERVER_ERROR.value(), e.getMessage(), null));
        }
    }



    @PutMapping("/{resourceId}")
    public ResponseEntity<ApiResponse<Resource>> updateResource(
            @PathVariable UUID resourceId,
            @RequestBody Map<String, Object> updates,
            @RequestHeader("Authorization") String token) {

        try {
            UUID userId = extractUserIdFromToken(token);
            String userRole = extractRoleFromToken(token);

            if (!userService.isUserApproved(userId)) {
                return ResponseEntity
                        .status(HttpStatus.FORBIDDEN)
                        .body(new ApiResponse<>(HttpStatus.FORBIDDEN.value(), "User not approved", null));
            }

            String title = updates.containsKey("title") ? (String) updates.get("title") : null;
            Boolean isPublic = updates.containsKey("isPublic") ? (Boolean) updates.get("isPublic") : null;

            Resource updatedResource = s3Service.updateResourceDetails(resourceId, userId, userRole, title, isPublic);
            return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Resource updated successfully", updatedResource));
        } catch (AccessDeniedException e) {
            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(new ApiResponse<>(HttpStatus.FORBIDDEN.value(), e.getMessage(), null));
        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(HttpStatus.INTERNAL_SERVER_ERROR.value(), e.getMessage(), null));
        }
    }



    @PostMapping("/{resourceId}/share")
    public ResponseEntity<ApiResponse<?>> shareResource(
            @PathVariable UUID resourceId,
            @RequestParam("userIds") List<UUID> granteeIds,
            @RequestHeader("Authorization") String token) {

        logger.info("Received request to share resource: {} with {} users", resourceId, granteeIds.size());

        try {
            UUID grantorId = extractUserIdFromToken(token);
            String grantorRole = extractRoleFromToken(token);

            logger.info("Share request from user ID: {} with role: {}", grantorId, grantorRole);

            if (!userService.isUserApproved(grantorId)) {
                logger.warn("Share attempt by unapproved user: {}", grantorId);
                return ResponseEntity
                        .status(HttpStatus.FORBIDDEN)
                        .body(new ApiResponse<>(HttpStatus.FORBIDDEN.value(), "User not approved", null));
            }

            // Results to track successful and failed shares
            List<UUID> successfulShares = new ArrayList<>();
            Map<UUID, String> failedShares = new HashMap<>();

            // Share with each user
            for (UUID granteeId : granteeIds) {
                try {
                    s3Service.grantAccess(resourceId, granteeId, grantorId, grantorRole);
                    successfulShares.add(granteeId);
                    logger.info("Successfully shared resource: {} with user: {}", resourceId, granteeId);
                } catch (AccessDeniedException e) {
                    failedShares.put(granteeId, "Access denied");
                    logger.warn("Failed to share resource: {} with user: {} - Access denied", resourceId, granteeId);
                } catch (Exception e) {
                    failedShares.put(granteeId, e.getMessage());
                    logger.error("Error sharing resource: {} with user: {}: {}", resourceId, granteeId, e.getMessage());
                }
            }

            // Create response based on results
            Map<String, Object> result = new HashMap<>();
            result.put("successfulShares", successfulShares);
            result.put("failedShares", failedShares);
            result.put("totalUsers", granteeIds.size());
            result.put("successCount", successfulShares.size());
            result.put("failureCount", failedShares.size());

            HttpStatus status = failedShares.isEmpty() ? HttpStatus.OK : HttpStatus.PARTIAL_CONTENT;
            String message = failedShares.isEmpty() ?
                    "Resource shared successfully with all users" :
                    String.format("Resource shared with %d of %d users", successfulShares.size(), granteeIds.size());

            logger.info("Share resource result: {} of {} successful", successfulShares.size(), granteeIds.size());

            return ResponseEntity
                    .status(status)
                    .body(new ApiResponse<>(status.value(), message, result));

        } catch (Exception e) {
            logger.error("Global exception in shareResource: {}", e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(HttpStatus.INTERNAL_SERVER_ERROR.value(), e.getMessage(), null));
        }
    }

    @GetMapping("/list")
    public ResponseEntity<ApiResponse<List<Resource>>> listAccessibleResources(
            @RequestParam(required = false) String contentType,
            @RequestHeader("Authorization") String token)
    {
        try {
            UUID userId = extractUserIdFromToken(token);
            String userRole = extractRoleFromToken(token);

            if (!userService.isUserApproved(userId)) {
                return ResponseEntity
                        .status(HttpStatus.FORBIDDEN)
                        .body(new ApiResponse<>(HttpStatus.FORBIDDEN.value(), "User not approved", null));
            }

            List<Resource> resources = s3Service.findResourcesByRole(userId, userRole, contentType);
            return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Resources retrieved successfully", resources));
        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(HttpStatus.INTERNAL_SERVER_ERROR.value(), e.getMessage(), null));
        }
    }

    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @GetMapping("/admin/all")
    public ResponseEntity<ApiResponse<List<Resource>>> getAllResources(
            @RequestHeader("Authorization") String token) {
        try {
            UUID userId = extractUserIdFromToken(token);
            String userRole = extractRoleFromToken(token);

            List<Resource> resources = s3Service.findAccessibleResources(userId, userRole);
            return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "All resources retrieved successfully", resources));
        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(HttpStatus.INTERNAL_SERVER_ERROR.value(), e.getMessage(), null));
        }
    }

    @DeleteMapping("/{resourceId}")
    public ResponseEntity<ApiResponse<Resource>> softDeleteResource(
            @PathVariable UUID resourceId,
            @RequestHeader("Authorization") String token) {

        UUID userId = extractUserIdFromToken(token);
        String userRole = extractRoleFromToken(token);

        if (!userService.isUserApproved(userId)) {
            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(new ApiResponse<>(HttpStatus.FORBIDDEN.value(), "User not approved", null));
        }

        try {
            Resource deletedResource = s3Service.softDeleteResource(resourceId, userId, userRole);
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(new ApiResponse<>(HttpStatus.OK.value(), "Resource deleted successfully", deletedResource));
        } catch (AccessDeniedException e) {
            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(new ApiResponse<>(HttpStatus.FORBIDDEN.value(), e.getMessage(), null));
        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(HttpStatus.INTERNAL_SERVER_ERROR.value(), e.getMessage(), null));
        }
    }

    @PostMapping("/{resourceId}/revoke")
    public ResponseEntity<ApiResponse<Void>> revokeAccess(
            @PathVariable UUID resourceId,
            @RequestParam("userId") UUID targetUserId,
            @RequestHeader("Authorization") String token) {

        try {
            UUID revokerUserId = extractUserIdFromToken(token);
            String revokerRole = extractRoleFromToken(token);

            if (!userService.isUserApproved(revokerUserId)) {
                return ResponseEntity
                        .status(HttpStatus.FORBIDDEN)
                        .body(new ApiResponse<>(HttpStatus.FORBIDDEN.value(), "User not approved", null));
            }

            s3Service.revokeAccess(resourceId, targetUserId, revokerUserId, revokerRole);
            return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Access revoked successfully", null));
        } catch (AccessDeniedException e) {
            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(new ApiResponse<>(HttpStatus.FORBIDDEN.value(), "You don't have permission to revoke access", null));
        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(HttpStatus.INTERNAL_SERVER_ERROR.value(), e.getMessage(), null));
        }
    }

    private UUID extractUserIdFromToken(String token) {
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        return jwtService.extractUserId(token);
    }

    private String extractRoleFromToken(String token) {
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        return jwtService.extractRole(token);
    }
}