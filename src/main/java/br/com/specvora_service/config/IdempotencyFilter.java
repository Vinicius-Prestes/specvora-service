package br.com.specvora_service.config;

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
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class IdempotencyFilter extends OncePerRequestFilter {

    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
    private final ErrorResponseWriter errorResponseWriter;
    private final Map<String, Long> processedKeys = new ConcurrentHashMap<>();
    private final Duration ttl = Duration.ofMinutes(10);

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if ("POST".equalsIgnoreCase(request.getMethod())) {
            String idempotencyKey = request.getHeader(IDEMPOTENCY_HEADER);
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                String key = resolveKey(request, idempotencyKey);
                long now = System.currentTimeMillis();
                cleanup(now);

                if (processedKeys.putIfAbsent(key, now) != null) {
                    errorResponseWriter.write(response, request, HttpStatus.CONFLICT,
                            "Requisição duplicada: a chave Idempotency-Key já foi processada recentemente");
                    return;
                }

                try {
                    filterChain.doFilter(request, response);
                    if (response.getStatus() >= 400) {
                        processedKeys.remove(key);
                    }
                    return;
                } catch (Exception ex) {
                    processedKeys.remove(key);
                    throw ex;
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private String resolveKey(HttpServletRequest request, String idempotencyKey) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String userIdentifier = "anonymous";
        if (authentication != null && authentication.isAuthenticated() && authentication.getPrincipal() != null) {
            userIdentifier = String.valueOf(authentication.getPrincipal());
        }
        return userIdentifier + ":" + idempotencyKey.trim();
    }

    private void cleanup(long now) {
        long expirationThreshold = now - ttl.toMillis();
        Iterator<Map.Entry<String, Long>> iterator = processedKeys.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Long> entry = iterator.next();
            if (entry.getValue() < expirationThreshold) {
                iterator.remove();
            }
        }
    }
}
