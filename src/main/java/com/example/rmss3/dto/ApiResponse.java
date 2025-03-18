package com.example.rmss3.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)  // Excludes null fields in JSON response
public class ApiResponse<T> {
    private int statusCode;  // HTTP status code (e.g., 200, 404)
    private String message;  // Response message (e.g., "Success", "Error")
    private T data;          // Actual response payload


}
