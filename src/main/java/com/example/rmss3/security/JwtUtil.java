package com.example.rmss3.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
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

    // Thread-safe set to store invalidated tokens
    private final Set<String> invalidatedTokens = ConcurrentHashMap.newKeySet();

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
        // Check if token is invalidated
        if (invalidatedTokens.contains(token)) {
            return false;
        }

        final String email = extractEmail(token);
        return (email.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }

    private boolean isTokenExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }

    /**
     * Invalidates a token by adding it to the blacklist
     * @param token The JWT token to invalidate
     */
    public void invalidateToken(String token) {
        // Add token to the invalidated set
        invalidatedTokens.add(token);

        // Set the expiration date to a past date (effectively invalidating the token immediately)
        Claims claims = extractAllClaims(token);
        claims.setExpiration(new Date(System.currentTimeMillis() - 1000));  // Set to a time in the past

        // Recreate the token with the new expiration date
        String invalidatedToken = Jwts.builder()
                .setClaims(claims)
                .signWith(SignatureAlgorithm.HS256, SECRET_KEY.getBytes())
                .compact();

        // Add the invalidated token to the invalidated tokens set
        invalidatedTokens.add(invalidatedToken);

        // Schedule cleanup to prevent memory leaks
        cleanupExpiredTokens();
    }


    /**
     * Removes expired tokens from the invalidated tokens set
     */
    private void cleanupExpiredTokens() {
        Date now = new Date();
        invalidatedTokens.removeIf(token -> {
            try {
                Date expiration = extractClaim(token, Claims::getExpiration);
                return expiration.before(now);
            } catch (Exception e) {
                // If token can't be parsed, remove it
                return true;
            }
        });
    }
}