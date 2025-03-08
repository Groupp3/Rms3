package com.example.rmss3.dto;

import com.example.rmss3.entity.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {
    private UUID id;
    private String firstName;
    private String lastName;
    private String email;
    private String role;
    private UserStatus status;
    private String profileImageUrl;

    public UserDTO(UUID id, String firstName, String lastName, String email, String role, String profileImageUrl) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.role = role;
        this.profileImageUrl = profileImageUrl;
    }
}