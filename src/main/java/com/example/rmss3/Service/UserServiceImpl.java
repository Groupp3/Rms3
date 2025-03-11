package com.example.rmss3.Service;

import com.example.rmss3.dto.*;
import com.example.rmss3.entity.Resource;
import com.example.rmss3.entity.User;
import com.example.rmss3.entity.UserRole;
import com.example.rmss3.entity.UserStatus;
import com.example.rmss3.exception.ResourceNotFoundException;
import com.example.rmss3.repository.ResourceRepository;
import com.example.rmss3.repository.UserRepository;
import com.example.rmss3.repository.UserRoleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private ResourceRepository resourceRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private S3Service s3Service;

    @Autowired
    private JwtService jwtService;

    @Override
    public boolean isAdmin(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return user.getRole() != null && user.getRole().getRole().equals("ADMIN");
    }

    @Override
    public ApiResponse registerUser(RegisterDTO registerDTO) {
        if (userRepository.findByEmail(registerDTO.getEmail()).isPresent()) {
            throw new RuntimeException("Email already registered");
        }

        UserRole studentRole = userRoleRepository.findByRole("STUDENT")
                .orElseThrow(() -> new ResourceNotFoundException("Role STUDENT not found"));

        User user = new User();
        user.setFirstName(registerDTO.getFirstName());
        user.setLastName(registerDTO.getLastName());
        user.setEmail(registerDTO.getEmail());
        user.setPassword(passwordEncoder.encode(registerDTO.getPassword()));
        user.setStatus(UserStatus.PENDING);
        user.setRole(studentRole);

        user = userRepository.save(user);

        return new ApiResponse<>(201, "User registered successfully", "Success");
    }


    @Override
    public AuthResponseDTO loginUser(String email, String password) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Invalid email or password"));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new RuntimeException("Invalid email or password");
        }

        if (user.getStatus() != UserStatus.APPROVED) {
            throw new RuntimeException("Your account has not been approved yet");
        }

        if (user.getDeletedAt() != null) {
            throw new RuntimeException("Your account has been disabled");
        }

        Optional<Resource> profilePic = resourceRepository.findByUserIdAndDeletedAtIsNull(user.getId())
                .stream()
                .findFirst();

        String profileImageUrl = profilePic.map(resource -> s3Service.getFileUrl(resource.getId())).orElse(null);
        String token = jwtService.generateToken(user);

        return new AuthResponseDTO(token, convertToDTO(user, profileImageUrl));
    }

    @Override
    public List<UserDTO> getAllUsers(UserStatus status, String role) {
        List<User> users = userRepository.findByStatusAndRole_RoleAndDeletedAtIsNull(UserStatus.APPROVED,role);
        return users.stream()
                .map(user -> {
                    Optional<Resource> profilePic = resourceRepository.findByUserIdAndDeletedAtIsNull(user.getId())
                            .stream()
                            .findFirst();
                    String profileImageUrl = profilePic.map(resource -> s3Service.getFileUrl(resource.getId())).orElse(null);
                    return convertToDTO(user, profileImageUrl);
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<UserDTO> getPendingUsers(UserStatus status){

        if (status != UserStatus.PENDING){
            return Collections.emptyList();
        }
        List<User> pendingUsers = userRepository.findByStatusAndDeletedAtIsNull(UserStatus.PENDING);
        return pendingUsers.stream()
                .map(user -> convertToDTO(user,null))
                .collect(Collectors.toList());

    }

    @Override
    public boolean isUserApproved(UUID userId) {
        return userRepository.findById(userId)
                .map(user -> user.getStatus() == UserStatus.APPROVED)
                .orElse(false);
    }

    @Override
    public UserDTO getUserById(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        Optional<Resource> profilePic = resourceRepository.findByUserIdAndDeletedAtIsNull(userId)
                .stream()
                .findFirst();
        String profileImageUrl = profilePic.map(resource -> s3Service.getFileUrl(resource.getId())).orElse(null);

        return convertToDTO(user, profileImageUrl);
    }

    @Override
    public UserDTO updateUser(UUID userId, UserUpdateDTO updateDTO) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        if (updateDTO.getFirstName() != null) {
            user.setFirstName(updateDTO.getFirstName());
        }

        if (updateDTO.getLastName() != null) {
            user.setLastName(updateDTO.getLastName());
        }

        if (updateDTO.getEmail() != null) {
            user.setLastName(updateDTO.getEmail());
        }

        // Only update password if provided


        user.setModifiedAt(LocalDateTime.now());
        User updatedUser = userRepository.save(user);

        Optional<Resource> profilePic = resourceRepository.findByUserIdAndDeletedAtIsNull(userId)
                .stream()
                .findFirst();
        String profileImageUrl = profilePic.map(resource -> s3Service.getFileUrl(resource.getId())).orElse(null);

        return convertToDTO(updatedUser, profileImageUrl);
    }

    @Override
    public UserDTO updateUserStatus(UUID userId, UserStatus status) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        user.setStatus(status);
        user.setModifiedAt(LocalDateTime.now());
        User updatedUser = userRepository.save(user);

        Optional<Resource> profilePic = resourceRepository.findByUserIdAndDeletedAtIsNull(userId)
                .stream()
                .findFirst();
        String profileImageUrl = profilePic.map(resource -> s3Service.getFileUrl(resource.getId())).orElse(null);

        return convertToDTO(updatedUser, profileImageUrl);
    }

    @Override
    public UserDTO updateUserRole(UUID userId, String roleName) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        UserRole role = userRoleRepository.findByRole(roleName)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + roleName));

        user.setRole(role);
        user.setModifiedAt(LocalDateTime.now());
        User updatedUser = userRepository.save(user);

        Optional<Resource> profilePic = resourceRepository.findByUserIdAndDeletedAtIsNull(userId)
                .stream()
                .findFirst();
        String profileImageUrl = profilePic.map(resource -> s3Service.getFileUrl(resource.getId())).orElse(null);

        return convertToDTO(updatedUser, profileImageUrl);
    }

    @Override
    public UserDTO updateProfilePicture(UUID userId, UUID resourceId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));


        Resource profilePicResource = resourceRepository.findById(resourceId)
                .orElseThrow(() -> new ResourceNotFoundException("Resource not found"));


        String imageUrl = s3Service.getFileUrl(resourceId);


        return convertToDTO(user, imageUrl);
    }

    private UserDTO convertToDTO(User user, String profileImageUrl) {
        return new UserDTO(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getRole().getRole(),
                user.getStatus(),
                profileImageUrl
        );
    }
}