package br.com.specvora_service.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Componente de Logging Estruturado de Eventos Críticos de Segurança
 * Emite eventos no formato JSON para fácil ingestão e correlação em SIEM (Splunk, Elastic, Datadog)
 */
@Slf4j(topic = "SECURITY_AUDIT")
@Component
@RequiredArgsConstructor
public class SecurityAuditLogger {

    private final ObjectMapper objectMapper;

    public enum EventType {
        AUTH_LOGIN_SUCCESS,
        AUTH_LOGIN_FAILURE,
        AUTH_REGISTER_SUCCESS,
        AUTH_REGISTER_ELEVATED_DENIED,
        AUTH_TOKEN_EXPIRED,
        AUTH_TOKEN_INVALID,
        SECURITY_ACCESS_DENIED,
        RATE_LIMIT_EXCEEDED,
        IDEMPOTENCY_CONFLICT,
        VEHICLE_CREATED,
        VEHICLE_UPDATED,
        VEHICLE_DELETED
    }

    public enum Severity {
        INFO,
        WARN,
        ERROR,
        CRITICAL
    }

    public void logEvent(EventType eventType,
                         Severity severity,
                         String userId,
                         String clientIp,
                         String httpMethod,
                         String requestPath,
                         int statusCode,
                         String message,
                         Map<String, Object> details) {
        try {
            Map<String, Object> logPayload = new LinkedHashMap<>();
            logPayload.put("timestamp", Instant.now().toString());
            logPayload.put("log_type", "SECURITY_AUDIT_EVENT");
            logPayload.put("event_type", eventType.name());
            logPayload.put("severity", severity.name());
            logPayload.put("user_id", userId != null ? userId : "anonymous");
            logPayload.put("client_ip", clientIp != null ? clientIp : "unknown");
            logPayload.put("http_method", httpMethod != null ? httpMethod : "N/A");
            logPayload.put("request_path", requestPath != null ? requestPath : "N/A");
            logPayload.put("status_code", statusCode);
            logPayload.put("message", message);
            if (details != null && !details.isEmpty()) {
                logPayload.put("details", details);
            }

            String jsonLog = objectMapper.writeValueAsString(logPayload);

            switch (severity) {
                case CRITICAL, ERROR -> log.error(jsonLog);
                case WARN -> log.warn(jsonLog);
                default -> log.info(jsonLog);
            }
        } catch (Exception ex) {
            log.error("Falha ao serializar log estruturado de segurança para evento: {}", eventType, ex);
        }
    }

    public void logFromRequest(HttpServletRequest request,
                               EventType eventType,
                               Severity severity,
                               String userId,
                               int statusCode,
                               String message,
                               Map<String, Object> details) {
        String clientIp = request != null ? request.getRemoteAddr() : "unknown";
        String httpMethod = request != null ? request.getMethod() : "N/A";
        String requestPath = request != null ? request.getRequestURI() : "N/A";

        logEvent(eventType, severity, userId, clientIp, httpMethod, requestPath, statusCode, message, details);
    }
}
