package com.example.rmss3.Controller;

import com.example.rmss3.Service.JwtService;
import com.example.rmss3.Service.S3Service;
import com.example.rmss3.Service.UserService;
import com.example.rmss3.dto.ApiResponse;
import com.example.rmss3.entity.Resource;
import com.example.rmss3.security.JwtUtil;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/resources")
public class ResourceController {

    @Autowired
    private S3Service s3Service;

    @Autowired
    private JwtService jwtService;


    @Autowired
    private UserService userService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Resource>> uploadResource(

            @RequestParam("file") MultipartFile file,
            @RequestParam("visibility") String visibility,
            @RequestHeader("Authorization") String token) {

        try {

            UUID userId = extractUserIdFromToken(token);
            String userRole = extractRoleFromToken(token.substring(7));


            if (!userService.isUserApproved(userId)) {
                return ResponseEntity
                        .status(HttpStatus.FORBIDDEN)
                        .body(new ApiResponse<>(HttpStatus.FORBIDDEN.value(), "User not approved", null));
            }

            Resource resource = s3Service.uploadLearningMaterial(file, userId, userRole, visibility);
            return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Resource uploaded successfully", resource));
        } catch (IOException e) {
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
            String userRole = jwtService.extractRole(token.substring(7));


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

    @PostMapping("/{resourceId}/share")
    public ResponseEntity<ApiResponse<Void>> shareResource(
            @PathVariable UUID resourceId,
            @RequestParam("userId") UUID granteeId,
            @RequestHeader("Authorization") String token) {

        try {
            UUID grantorId = extractUserIdFromToken(token);
            String grantorRole = jwtService.extractRole(token.substring(7));


            if (!userService.isUserApproved(grantorId)) {
                return ResponseEntity
                        .status(HttpStatus.FORBIDDEN)
                        .body(new ApiResponse<>(HttpStatus.FORBIDDEN.value(), "User not approved", null));
            }

            s3Service.grantAccess(resourceId, granteeId, grantorId, grantorRole);
            return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Resource shared successfully", null));
        } catch (AccessDeniedException e) {
            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(new ApiResponse<>(HttpStatus.FORBIDDEN.value(), "You don't have permission to share this resource", null));
        } catch (Exception e) {
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
            String userRole = extractRoleFromToken(token.substring(7));


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
            String userRole = jwtService.extractRole(token.substring(7));
            UUID userId = extractUserIdFromToken(token);

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
        String userRole = jwtService.extractRole(token.substring(7));


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
            String revokerRole = jwtService.extractRole(token.substring(7));


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