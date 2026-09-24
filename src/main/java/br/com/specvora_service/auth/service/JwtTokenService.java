package br.com.specvora_service.auth.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class JwtTokenService {

    private static final String ISSUER = "specvora-service";
    private static final String ROLES_CLAIM = "roles";

    @Value("${security.jwt.secret:specvora-devsecops-super-secret-jwt-key-for-fiap-2026}")
    private String jwtSecret;

    @Value("${security.jwt.expiration-hours:2}")
    private long expirationHours;

    public String generateToken(String username, List<String> roles) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(Duration.ofHours(expirationHours));

        Algorithm algorithm = Algorithm.HMAC256(jwtSecret);

        return JWT.create()
                .withIssuer(ISSUER)
                .withSubject(username)
                .withClaim(ROLES_CLAIM, roles != null ? roles : Collections.emptyList())
                .withIssuedAt(Date.from(now))
                .withExpiresAt(Date.from(expiresAt))
                .sign(algorithm);
    }

    public DecodedJWT validateToken(String token) throws JWTVerificationException {
        Algorithm algorithm = Algorithm.HMAC256(jwtSecret);
        return JWT.require(algorithm)
                .withIssuer(ISSUER)
                .build()
                .verify(token);
    }

    public Authentication getAuthentication(String token) {
        DecodedJWT decodedJWT = validateToken(token);
        String username = decodedJWT.getSubject();
        List<String> roles = decodedJWT.getClaim(ROLES_CLAIM).asList(String.class);

        List<SimpleGrantedAuthority> authorities = roles == null
                ? Collections.emptyList()
                : roles.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());

        return new UsernamePasswordAuthenticationToken(username, null, authorities);
    }

    public long getExpiresInSeconds() {
        return Duration.ofHours(expirationHours).toSeconds();
    }

    public String getUsername(String token) {
        return validateToken(token).getSubject();
    }

    public List<String> getRoles(String token) {
        List<String> roles = validateToken(token).getClaim(ROLES_CLAIM).asList(String.class);
        return roles != null ? roles : Collections.emptyList();
    }

    public Instant getExpiration(String token) {
        return validateToken(token).getExpiresAt().toInstant();
    }
}
