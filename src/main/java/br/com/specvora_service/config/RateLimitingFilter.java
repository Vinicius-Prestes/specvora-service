package br.com.specvora_service.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hardening de API — Rate Limiting Inteligente com Token Bucket
 * - Limite diferenciado contra Brute Force em /auth/login (5 req/min)
 * - Limite geral de API (60 req/min)
 * - Inclusão de headers padronizados IETF (X-RateLimit-Limit, X-RateLimit-Remaining, Retry-After)
 * - Resposta em formato padronizado ErrorResponseDTO em caso de 429 Too Many Requests
 */
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    // Limite padrão para consumo geral da API (60 requisições por minuto)
    private static final Bandwidth GENERAL_LIMIT = Bandwidth.simple(60, Duration.ofMinutes(1));

    // Limite estrito contra ataques de Força Bruta e Credential Stuffing em /auth/login (5 tentativas por minuto)
    private static final Bandwidth LOGIN_BRUTE_FORCE_LIMIT = Bandwidth.simple(5, Duration.ofMinutes(1));

    private final Map<String, Bucket> generalBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> loginBuckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String uri = request.getRequestURI();
        boolean isLoginRoute = uri != null && uri.endsWith("/auth/login");

        String clientKey = resolveClientKey(request);
        Bucket bucket;
        long limitCapacity;

        if (isLoginRoute) {
            bucket = loginBuckets.computeIfAbsent(clientKey, k -> Bucket.builder()
                    .addLimit(LOGIN_BRUTE_FORCE_LIMIT)
                    .build());
            limitCapacity = 5;
        } else {
            bucket = generalBuckets.computeIfAbsent(clientKey, k -> Bucket.builder()
                    .addLimit(GENERAL_LIMIT)
                    .build());
            limitCapacity = 60;
        }

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            response.setHeader("X-RateLimit-Limit", String.valueOf(limitCapacity));
            response.setHeader("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));
            filterChain.doFilter(request, response);
        } else {
            long waitForRefillSeconds = Math.max(1, probe.getNanosToWaitForRefill() / 1_000_000_000);

            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.setHeader("X-RateLimit-Limit", String.valueOf(limitCapacity));
            response.setHeader("X-RateLimit-Remaining", "0");
            response.setHeader("Retry-After", String.valueOf(waitForRefillSeconds));

            String errorJson = String.format("""
                    {
                        "timestamp": "%s",
                        "status": 429,
                        "error": "Too Many Requests",
                        "message": "Taxa limite de requisições excedida. Tente novamente em %d segundos.",
                        "path": "%s"
                    }
                    """, Instant.now(), waitForRefillSeconds, uri);

            response.getWriter().write(errorJson);
        }
    }

    private String resolveClientKey(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() != null && !"anonymousUser".equals(auth.getPrincipal())) {
            return "user:" + auth.getPrincipal();
        }

        // Suporte a proxy reverso / load balancer para extração segura do IP
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return "ip:" + xForwardedFor.split(",")[0].trim();
        }

        return "ip:" + request.getRemoteAddr();
    }
}
