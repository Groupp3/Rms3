package com.example.rmss3.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.stereotype.Service;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import com.example.rmss3.entity.User;

@Service
public class JwtService {

    private final String SECRET_KEY = Base64.getEncoder().encodeToString("YourSuperSecretKey1234567890isSafeaNdStronG".getBytes());

    // Using ConcurrentHashMap for thread safety in a multi-user environment
    private final Set<String> invalidatedTokens = ConcurrentHashMap.newKeySet();

    public String generateToken(User user) {
        return Jwts.builder()
                .setSubject(user.getEmail())
                .claim("userId", user.getId().toString())
                .claim("role", user.getRole().getRole())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 1000 * 60 * 60 * 24)) // 24 hours
                .signWith(SignatureAlgorithm.HS256, SECRET_KEY.getBytes())
                .compact();
    }

    public UUID extractUserId(String token) {
        Claims claims = extractAllClaims(token);
        return UUID.fromString(claims.get("userId", String.class));
    }

    public String extractEmail(String token) {
        return extractAllClaims(token).getSubject();
    }

    public String extractRole(String token) {
        Claims claims = extractAllClaims(token);
        return claims.get("role", String.class);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(SECRET_KEY.getBytes())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public boolean isTokenValid(String token, String username) {
        if (invalidatedTokens.contains(token)) {
            return false;
        }
        final String extractedEmail = extractEmail(token);
        return (extractedEmail.equals(username) && !isTokenExpired(token));
    }

    private boolean isTokenExpired(String token) {
        return extractAllClaims(token).getExpiration().before(new Date());
    }

    /**
     * Invalidates a token by adding it to the blacklist
     * @param token The JWT token to invalidate
     */
    public void invalidateToken(String token) {
        // Add token to the invalidated set
        invalidatedTokens.add(token);

        // Optional: schedule cleanup of expired tokens
        cleanupExpiredTokens();
    }

    /**
     * Cleans up expired tokens from the invalidated tokens set
     * This method helps prevent memory leaks by removing tokens that have expired
     */
    private void cleanupExpiredTokens() {
        Date now = new Date();
        invalidatedTokens.removeIf(token -> {
            try {
                Date expiration = extractAllClaims(token).getExpiration();
                return expiration.before(now);
            } catch (Exception e) {
                // If token can't be parsed, it's either malformed or expired, remove it
                return true;
            }
        });
    }
}