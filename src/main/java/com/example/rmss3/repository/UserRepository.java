package com.example.rmss3.repository;

import com.example.rmss3.entity.User;
import com.example.rmss3.entity.UserRole;
import com.example.rmss3.entity.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    List<User> findByStatusAndRole_RoleAndDeletedAtIsNull(UserStatus status, String role);
    List<User> findByStatusAndDeletedAtIsNull(UserStatus status);

}