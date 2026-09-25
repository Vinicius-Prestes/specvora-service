package br.com.specvora_service.auth.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Hardening de JWT (JSON Web Token Seguro)
 * - Assinatura forte com HMAC-SHA256 (HS256)
 * - Validação de entropia da chave secreta (mínimo 256 bits / 32 bytes)
 * - Proteção contra chave fraca ou segredo público fixo
 * - Verificação estrita de Issuer e Audience para impedir ataques de Confused Deputy
 * - Proteção contra bypass de algoritmo "none"
 * - Expiração controlada com short-lived tokens (2 horas)
 */
@Slf4j
@Service
public class JwtTokenService {

    public static final String ISSUER = "specvora-service";
    public static final String AUDIENCE = "specvora-api";
    public static final String ROLES_CLAIM = "roles";

    @Value("${security.jwt.secret:}")
    private String jwtSecret;

    @Value("${security.jwt.expiration-hours:2}")
    private long expirationHours;

    private Algorithm hmacAlgorithm;

    @PostConstruct
    public void validateAndInitAlgorithm() {
        if (jwtSecret == null || jwtSecret.trim().isEmpty()) {
            log.warn("AVISO DE SEGURANÇA: JWT_SECRET não configurado via variável de ambiente. Gerando chave randômica efêmera segura de 256 bits.");
            byte[] randomKey = new byte[32];
            new SecureRandom().nextBytes(randomKey);
            this.jwtSecret = Base64.getEncoder().encodeToString(randomKey);
        } else if (jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("Hardening de Segurança: A chave secreta JWT deve possuir pelo menos 256 bits (32 caracteres).");
        }
        this.hmacAlgorithm = Algorithm.HMAC256(jwtSecret);
    }

    public String generateToken(String username, List<String> roles) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(Duration.ofHours(expirationHours));

        return JWT.create()
                .withIssuer(ISSUER)
                .withAudience(AUDIENCE)
                .withSubject(username)
                .withClaim(ROLES_CLAIM, roles != null ? roles : Collections.emptyList())
                .withIssuedAt(Date.from(now))
                .withExpiresAt(Date.from(expiresAt))
                .sign(hmacAlgorithm);
    }

    public DecodedJWT validateToken(String token) throws JWTVerificationException {
        return JWT.require(hmacAlgorithm)
                .withIssuer(ISSUER)
                .withAudience(AUDIENCE)
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
