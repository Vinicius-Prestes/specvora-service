package br.com.specvora_service.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hardening de API — Rate Limiting Inteligente com Token Bucket
 * - Limite diferenciado contra Brute Force em /auth/login (5 req/min)
 * - Limite geral de API (60 req/min)
 * - Resolução segura de IP (prevenção contra spoofing de X-Forwarded-For)
 * - Inclusão de headers padronizados IETF (X-RateLimit-Limit, X-RateLimit-Remaining, Retry-After)
 * - Resposta em formato padronizado ErrorResponseDTO via ErrorResponseWriter
 */
@Component
@RequiredArgsConstructor
public class RateLimitingFilter extends OncePerRequestFilter {

    private final ErrorResponseWriter errorResponseWriter;

    private static final Bandwidth GENERAL_LIMIT = Bandwidth.simple(60, Duration.ofMinutes(1));
    private static final Bandwidth LOGIN_BRUTE_FORCE_LIMIT = Bandwidth.simple(5, Duration.ofMinutes(1));

    private final Map<String, Bucket> generalBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> loginBuckets = new ConcurrentHashMap<>();
    private static final int MAX_BUCKETS = 10_000;

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
            checkAndCleanupMap(loginBuckets);
            bucket = loginBuckets.computeIfAbsent(clientKey, k -> Bucket.builder()
                    .addLimit(LOGIN_BRUTE_FORCE_LIMIT)
                    .build());
            limitCapacity = 5;
        } else {
            checkAndCleanupMap(generalBuckets);
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

            response.setHeader("X-RateLimit-Limit", String.valueOf(limitCapacity));
            response.setHeader("X-RateLimit-Remaining", "0");
            response.setHeader("Retry-After", String.valueOf(waitForRefillSeconds));

            errorResponseWriter.write(response, request, HttpStatus.TOO_MANY_REQUESTS,
                    "Taxa limite de requisições excedida. Tente novamente em " + waitForRefillSeconds + " segundos.");
        }
    }

    private String resolveClientKey(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() != null && !"anonymousUser".equals(auth.getPrincipal())) {
            return "user:" + auth.getPrincipal();
        }

        // Prevenção contra spoofing de X-Forwarded-For: usa o IP direto de conexão do socket
        // Quando há proxy confiável reverso, a estratégia do servidor de aplicação deve ser configurada
        return "ip:" + request.getRemoteAddr();
    }

    private void checkAndCleanupMap(Map<String, Bucket> map) {
        if (map.size() > MAX_BUCKETS) {
            map.clear(); // Proteção simples contra esgotamento de memória por enxurrada de IPs falsos
        }
    }
}
