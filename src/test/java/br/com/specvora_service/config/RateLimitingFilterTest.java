package br.com.specvora_service.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("Testes de Hardening de API - RateLimitingFilter")
class RateLimitingFilterTest {

    private RateLimitingFilter rateLimitingFilter;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        rateLimitingFilter = new RateLimitingFilter();
        filterChain = mock(FilterChain.class);
    }

    @Test
    @DisplayName("Requisições dentro do limite devem passar e conter cabeçalhos X-RateLimit-*")
    void testRequestWithinLimitSuccess() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/vehicles");
        request.setRemoteAddr("192.168.1.100");
        MockHttpServletResponse response = new MockHttpServletResponse();

        rateLimitingFilter.doFilterInternal(request, response, filterChain);

        assertEquals(200, response.getStatus());
        assertEquals("60", response.getHeader("X-RateLimit-Limit"));
        assertNotNull(response.getHeader("X-RateLimit-Remaining"));
        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    @DisplayName("Ataque de Força Bruta em /auth/login deve ser bloqueado na 6ª tentativa (limite de 5 req/min)")
    void testBruteForceProtectionOnLoginBlocksOn6thAttempt() throws ServletException, IOException {
        String clientIp = "203.0.113.42";

        // Realiza 5 tentativas permitidas
        for (int i = 1; i <= 5; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/auth/login");
            req.setRemoteAddr(clientIp);
            MockHttpServletResponse res = new MockHttpServletResponse();

            rateLimitingFilter.doFilterInternal(req, res, filterChain);
            assertEquals(200, res.getStatus(), "Tentativa " + i + " deveria ser permitida");
            assertEquals("5", res.getHeader("X-RateLimit-Limit"));
        }

        // 6ª tentativa excede o balde e deve retornar 429 Too Many Requests imediatamente
        MockHttpServletRequest blockedReq = new MockHttpServletRequest("POST", "/auth/login");
        blockedReq.setRemoteAddr(clientIp);
        MockHttpServletResponse blockedRes = new MockHttpServletResponse();

        rateLimitingFilter.doFilterInternal(blockedReq, blockedRes, filterChain);

        assertEquals(429, blockedRes.getStatus(), "6ª tentativa em /auth/login deve ser bloqueada com 429");
        assertEquals("0", blockedRes.getHeader("X-RateLimit-Remaining"));
        assertNotNull(blockedRes.getHeader("Retry-After"));
        assertTrue(blockedRes.getContentAsString().contains("Too Many Requests"));
        assertTrue(blockedRes.getContentAsString().contains("429"));
    }
}
