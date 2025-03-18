package com.example.rmss3.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@Component
public class JwtUtil {

    private final String SECRET_KEY = Base64.getEncoder().encodeToString("YourSuperSecretKey1234567890isSafeaNdStronGd".getBytes());

    // Thread-safe map to store blacklisted JTIs with their expiration times
    private final Map<String, Long> blacklistedTokens = new ConcurrentHashMap<>();

    // Schedule cleanup to prevent memory leaks - run every 1 hour
    @Scheduled(fixedRate = 60 * 60 * 1000)
    public void cleanupBlacklistedTokens() {
        long now = System.currentTimeMillis();
        blacklistedTokens.entrySet().removeIf(entry -> entry.getValue() < now);
    }

    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public UUID extractUserId(String token) {
        Claims claims = extractAllClaims(token);
        return UUID.fromString(claims.get("userId", String.class));
    }

    public String extractRole(String token) {
        Claims claims = extractAllClaims(token);
        return claims.get("role", String.class);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(SECRET_KEY.getBytes())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public String generateToken(UserDetails userDetails, UUID userId, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId.toString());
        claims.put("role", role);
        // Add a unique jti (JWT ID) claim to ensure uniqueness
        claims.put("jti", UUID.randomUUID().toString());
        return createToken(claims, userDetails.getUsername());
    }

    private String createToken(Map<String, Object> claims, String subject) {
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + 1000 * 60 * 60 * 10)) // 10 hours validity
                .signWith(SignatureAlgorithm.HS256, SECRET_KEY.getBytes())
                .compact();
    }

    public boolean validateToken(String token, UserDetails userDetails) {
        if (isTokenBlacklisted(token)) {
            return false;
        }

        final String email = extractEmail(token);
        return (email.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }

    private boolean isTokenExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }

    /**
     * Checks if a token is blacklisted by its JTI
     * @param token The JWT token
     * @return true if blacklisted, false otherwise
     */
    public boolean isTokenBlacklisted(String token) {
        try {
            String jti = extractClaim(token, claims -> claims.get("jti", String.class));
            return blacklistedTokens.containsKey(jti);
        } catch (Exception e) {
            // If token can't be parsed, consider it invalid
            return true;
        }
    }

    /**
     * Blacklists a token by its JTI until its expiration
     * @param jti The JWT ID to blacklist
     * @param ttlMillis Time to live in milliseconds
     */
    public void blacklistToken(String jti, long ttlMillis) {
        blacklistedTokens.put(jti, System.currentTimeMillis() + ttlMillis);
    }

    /**
     * Invalidates a token (use blacklistToken instead for more efficient implementation)
     * @param token The JWT token to invalidate
     */
    @Deprecated
    public void invalidateToken(String token) {
        try {
            String jti = extractClaim(token, claims -> claims.get("jti", String.class));
            Date expiration = extractClaim(token, Claims::getExpiration);
            long ttl = expiration.getTime() - System.currentTimeMillis();
            if (ttl > 0) {
                blacklistToken(jti, ttl);
            }
        } catch (Exception e) {
            // Handle parse errors
        }
    }

    // Add this method to JwtUtil class
    /**
     * Safely extracts JTI from token without throwing exceptions
     * @param token JWT token
     * @return JTI string or null if token is invalid
     */
    public String extractJtiSafely(String token) {
        try {
            return extractClaim(token, claims -> claims.get("jti", String.class));
        } catch (Exception e) {
            // Log the error but return null
            System.err.println("Error extracting JTI: " + e.getMessage());
            return null;
        }
    }

    /**
     * Blacklists a token by its raw token string
     * Uses a simple hash of the token if JTI extraction fails
     * @param token The JWT token string
     */
    public void blacklistRawToken(String token) {
        try {
            // First try to extract JTI
            String jti = extractJtiSafely(token);

            if (jti != null) {
                // If we can extract JTI, use it
                Date expiration = extractClaim(token, Claims::getExpiration);
                long ttl = expiration.getTime() - System.currentTimeMillis();
                if (ttl > 0) {
                    blacklistToken(jti, ttl);
                }
            } else {
                // Fallback: Use a hash of the token as the key
                // This isn't ideal but will work for logout
                String tokenHash = Integer.toString(token.hashCode());
                // Use a reasonable expiry time (e.g., 24 hours)
                blacklistToken(tokenHash, 24 * 60 * 60 * 1000);
            }
        } catch (Exception e) {
            // Final fallback for any other errors
            String tokenHash = Integer.toString(token.hashCode());
            blacklistToken(tokenHash, 24 * 60 * 60 * 1000);
        }
    }
}