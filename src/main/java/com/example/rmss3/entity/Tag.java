package com.example.rmss3.entity;

import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.GenericGenerator;

//import jakarta.persistence.*;
//import java.time.LocalDateTime;
//import java.util.HashSet;
//import java.util.Set;
//import java.util.UUID;
//
//@Entity
//@Data
//@Table(name = "tags")
//public class Tag {
//
//    @Id
//    @GeneratedValue(generator = "UUID")
//    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
//    @Column(name = "tag_id", updatable = false, nullable = false)
//    private UUID id;
//
//    @Column(nullable = false, unique = true)
//    private String name;
//
//    @Column(name = "created_at")
//    private LocalDateTime createdAt;
//
//    @Column(name = "deleted_at")
//    private LocalDateTime deletedAt;
//
//    @ManyToMany(mappedBy = "tags")
//    private Set<Resource> resources = new HashSet<>();
//
//    @PrePersist
//    protected void onCreate() {
//        this.createdAt = LocalDateTime.now();
//    }
//}