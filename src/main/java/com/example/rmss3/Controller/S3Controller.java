package com.example.rmss3.Controller;

import com.example.rmss3.Service.JwtService;
import com.example.rmss3.dto.ApiResponse;
import com.example.rmss3.dto.UserDTO;
import com.example.rmss3.entity.Resource;
import com.example.rmss3.Service.S3Service;
import com.example.rmss3.Service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/files")
public class S3Controller {

    @Autowired
    private S3Service s3Service;

    @Autowired
    private UserService userService;

    @Autowired
    private JwtService jwtService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Resource>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestHeader("Authorization") String token) {

        UUID userId = extractUserIdFromToken(token);

        // Verify user is approved
        if (!userService.isUserApproved(userId)) {
            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(new ApiResponse<>(HttpStatus.FORBIDDEN.value(), "User not approved", null));
        }

        try {
            Resource resource = s3Service.uploadFile(file, userId);
            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(new ApiResponse<>(HttpStatus.CREATED.value(), "File uploaded successfully", resource));
        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(HttpStatus.INTERNAL_SERVER_ERROR.value(), e.getMessage(), null));
        }
    }

    @GetMapping("/{resourceId}")
    public ResponseEntity<byte[]> getFile(
            @PathVariable Long resourceId,
            @RequestHeader("Authorization") String token) {

        UUID userId = extractUserIdFromToken(token);

        // Verify user is approved
        if (!userService.isUserApproved(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return s3Service.getFile(resourceId, userId);
    }

    // Helper method to safely extract user ID from token
    private UUID extractUserIdFromToken(String token) {
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        return jwtService.extractUserId(token);
    }


    @PostMapping(value = "/profile-picture", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<UserDTO>> uploadProfilePicture(
            @RequestParam("file") MultipartFile file,
            @RequestHeader("Authorization") String token) {

        UUID userId = extractUserIdFromToken(token);

        // Verify user is approved
        if (!userService.isUserApproved(userId)) {
            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(new ApiResponse<>(HttpStatus.FORBIDDEN.value(), "User not approved", null));
        }

        try {
            // Validate file is an image
            if (!file.getContentType().startsWith("image/")) {
                return ResponseEntity
                        .status(HttpStatus.BAD_REQUEST)
                        .body(new ApiResponse<>(HttpStatus.BAD_REQUEST.value(),
                                "Only image files are allowed for profile pictures", null));
            }

            // Upload file and get resource
            Resource resource = s3Service.uploadFile(file, userId);

            // Update user profile with the resource ID
            UserDTO updatedUser = userService.updateProfilePicture(userId, resource.getId());

            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(new ApiResponse<>(HttpStatus.OK.value(), "Profile picture updated successfully", updatedUser));
        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(HttpStatus.INTERNAL_SERVER_ERROR.value(), e.getMessage(), null));
        }
    }
}