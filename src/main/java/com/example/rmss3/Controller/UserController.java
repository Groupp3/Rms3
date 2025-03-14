package com.example.rmss3.Controller;

import com.example.rmss3.Service.JwtService;
import com.example.rmss3.Service.S3Service;
import com.example.rmss3.Service.UserService;
import com.example.rmss3.dto.*;
import com.example.rmss3.entity.Resource;
import com.example.rmss3.entity.UserRole;
import com.example.rmss3.entity.UserStatus;
import com.example.rmss3.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.MediaType;

import jakarta.validation.Valid;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private S3Service s3Service;

    @Autowired
    private JwtUtil jwtUtil;

    // Public endpoints
    @PostMapping("/auth/register")
    public ResponseEntity registerUser(@Valid @RequestBody RegisterDTO registerDTO) {
        ApiResponse response = userService.registerUser(registerDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/auth/login")
    public ResponseEntity<ApiResponse<AuthResponseDTO>> loginUser(@Valid @RequestBody LoginDTO loginDTO) {
        try {
            AuthResponseDTO authResponse = userService.loginUser(loginDTO.getEmail(), loginDTO.getPassword());
            return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Login successful", authResponse));
        } catch (RuntimeException e) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse<>(HttpStatus.UNAUTHORIZED.value(), e.getMessage(), null));
        }
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<ApiResponse<String>> logoutUser(@RequestHeader("Authorization") String token) {
        try {
            // Verify token exists and has correct format
            if (token != null && token.startsWith("Bearer ")) {
                String jwtToken = token.substring(7);

                // Invalidate the token
                jwtUtil.invalidateToken(jwtToken);

                return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Logout successful", null));
            } else {
                return ResponseEntity
                        .status(HttpStatus.BAD_REQUEST)
                        .body(new ApiResponse<>(HttpStatus.BAD_REQUEST.value(), "Invalid token format", null));
            }
        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Logout failed: " + e.getMessage(), null));
        }
    }



    // Admin endpoints
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @GetMapping("/admin/users")
    public ResponseEntity<ApiResponse<List<UserDTO>>> getAllUsers(@RequestParam String role) {
        List<UserDTO> users = userService.getAllUsers(UserStatus.APPROVED, role);
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Users retrieved successfully", users));
    }

    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @GetMapping("/admin/request")
    public ResponseEntity<ApiResponse<List<UserDTO>>> getPendingUsers() {
        List<UserDTO> users = userService.getPendingUsers(UserStatus.PENDING);
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Users retrieved successfully", users));
    }

    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @PutMapping("/admin/users/{userId}/status")
    public ResponseEntity<ApiResponse<UserDTO>> updateUserStatus(
            @PathVariable UUID userId,
            @RequestParam("status") UserStatus status) {

        UserDTO updatedUser = userService.updateUserStatus(userId, status);
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "User status updated successfully", updatedUser));
    }


    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @DeleteMapping("/admin/users/{userId}")
    public ResponseEntity<ApiResponse<UserDTO>> softDeleteUser(
            @PathVariable UUID userId) {

        UserDTO deletedUser = userService.softDeleteUser(userId);
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(),
                "User deleted successfully", deletedUser));
    }
    // User endpoints
    @GetMapping("/users/profile")
    public ResponseEntity<ApiResponse<UserDTO>> getUserProfile(@RequestHeader("Authorization") String token) {
        UUID userId = extractUserIdFromToken(token);
        UserDTO userDTO = userService.getUserById(userId);
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "User profile retrieved", userDTO));
    }

    @PutMapping("/users/profile")
    public ResponseEntity<ApiResponse<UserDTO>> updateUserProfile(
            @Valid @RequestBody UserUpdateDTO updateDTO,
            @RequestHeader("Authorization") String token) {

        UUID userId = extractUserIdFromToken(token);
        UserDTO updatedUser = userService.updateUser(userId, updateDTO);
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Profile updated successfully", updatedUser));
    }


    @PostMapping(value = "/users/profile-picture", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
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

            // Upload file as profile picture
            Resource resource = s3Service.uploadProfilePicture(file, userId);

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

    // Helper method to safely extract user ID from token
    private UUID extractUserIdFromToken(String token) {
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        return jwtService.extractUserId(token);
    }
}