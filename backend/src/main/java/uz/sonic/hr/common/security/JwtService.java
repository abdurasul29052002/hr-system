package uz.sonic.hr.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {

    /** Identity carried by a token: the account id and whether it is the project admin. */
    public record AuthToken(Long id, boolean admin) {
    }

    private final SecretKey key;
    private final Duration expiration;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expiration-hours}") long expirationHours) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = Duration.ofHours(expirationHours);
    }

    public String generateToken(Long accountId, boolean admin, String username) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(accountId))
                .claim("admin", admin)
                .claim("username", username)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)))
                .signWith(key)
                .compact();
    }

    /** @return the token identity, or null if the token is invalid/expired */
    public AuthToken validate(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            boolean admin = Boolean.TRUE.equals(claims.get("admin", Boolean.class));
            return new AuthToken(Long.parseLong(claims.getSubject()), admin);
        } catch (Exception e) {
            return null;
        }
    }
}
