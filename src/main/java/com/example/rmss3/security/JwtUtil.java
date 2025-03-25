package com.example.rmss3.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import javax.crypto.SecretKey;
import java.util.*;
import java.util.function.Function;

@Component
public class JwtUtil {

    private SecretKey SECRET_KEY;

    private final Set<String> tokenBlacklist = new HashSet<>();


    @Value("${jwt.secret.key}")
    public void setSecretKey(String secret) {
        this.SECRET_KEY = Keys.hmacShaKeyFor(secret.getBytes());
    }

    public String generateToken(String userId) {
        return Jwts.builder()
                .setSubject(userId)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 1000 * 60 * 60 * 10))
                .signWith(SECRET_KEY)
                .compact();
    }

    public String extractUserId(String token) {
        return getClaims(token).getSubject();
    }

    public Boolean validateToken(String token, String userId) {
        final String extractedUserId = extractUserId(token);

        if (isTokenBlacklisted(token)) {
            return false;
        }
        return (extractedUserId.equals(userId) && !isTokenExpired(token));
    }


    public void blacklistToken(String token) {
        tokenBlacklist.add(token);
    }

    private boolean isTokenBlacklisted(String token) {
        return tokenBlacklist.contains(token);
    }

    private boolean isTokenExpired(String token) {
        return getClaims(token).getExpiration().before(new Date());
    }

    private Claims getClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(SECRET_KEY)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}