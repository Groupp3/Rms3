package com.example.rmss3.Service;

import com.example.rmss3.dto.*;
import com.example.rmss3.entity.UserStatus;

import java.util.List;
import java.util.UUID;

public interface UserService {
    boolean isAdmin(UUID userId);
    ApiResponse registerUser(RegisterDTO registerDTO);
    AuthResponseDTO loginUser(String email, String password);
    List<UserDTO> getAllUsers();
    boolean isUserApproved(UUID userId);
    UserDTO getUserById(UUID userId);
    UserDTO updateUser(UUID userId, UserUpdateDTO updateDTO);
    UserDTO updateUserStatus(UUID userId, UserStatus status);
    UserDTO updateUserRole(UUID userId, String role);
    UserDTO updateProfilePicture(UUID userId, Long resourceId);
}