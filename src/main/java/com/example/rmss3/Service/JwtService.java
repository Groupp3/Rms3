package com.example.rmss3.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import com.example.rmss3.entity.User;

@Service
public class JwtService {

    private static final Logger logger = Logger.getLogger(JwtService.class.getName());
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
        // First check if the token is in the blacklist
        if (invalidatedTokens.contains(token)) {
            return false;
        }

        try {
            final String extractedEmail = extractEmail(token);
            return (extractedEmail.equals(username) && !isTokenExpired(token));
        } catch (Exception e) {
            // If token parsing fails, it's invalid
            return false;
        }
    }

    private boolean isTokenExpired(String token) {
        try {
            return extractAllClaims(token).getExpiration().before(new Date());
        } catch (ExpiredJwtException e) {
            return true;
        }
    }

    /**
     * Invalidates a token by adding it to the blacklist
     * @param token The JWT token to invalidate
     */
    public void invalidateToken(String token) {
        // Simply add the token to the invalidated set - no validation needed
        invalidatedTokens.add(token);

        // Log the invalidation if needed
        logger.info("Token invalidated and added to blacklist");

        // Optional: schedule cleanup of expired tokens
        cleanupExpiredTokens();
    }

    /**
     * Cleans up expired tokens from the invalidated tokens set
     * This method helps prevent memory leaks by removing tokens that have expired
     */
    private void cleanupExpiredTokens() {
        // Store tokens to remove in a separate set to avoid concurrent modification
        Set<String> tokensToRemove = new HashSet<>();

        Date now = new Date();
        for (String token : invalidatedTokens) {
            try {
                Date expiration = extractAllClaims(token).getExpiration();
                if (expiration.before(now)) {
                    tokensToRemove.add(token);
                }
            } catch (Exception e) {
                // If token can't be parsed for any reason, mark it for removal
                tokensToRemove.add(token);
            }
        }

        // Remove the identified tokens
        invalidatedTokens.removeAll(tokensToRemove);

        // Log cleanup if needed
        if (!tokensToRemove.isEmpty()) {
            logger.info("Cleaned up " + tokensToRemove.size() + " expired tokens from blacklist");
        }
    }
}