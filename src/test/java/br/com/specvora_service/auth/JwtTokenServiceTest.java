package br.com.specvora_service.auth;

import br.com.specvora_service.auth.service.JwtTokenService;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Testes de Unidade - JwtTokenService")
class JwtTokenServiceTest {

    private JwtTokenService jwtTokenService;

    @BeforeEach
    void setUp() {
        jwtTokenService = new JwtTokenService();
        ReflectionTestUtils.setField(jwtTokenService, "jwtSecret", "chave-secreta-para-testes-unitarios-jwt-123456");
        ReflectionTestUtils.setField(jwtTokenService, "expirationHours", 2L);
        jwtTokenService.validateAndInitAlgorithm();
    }

    @Test
    @DisplayName("Deve gerar um token JWT válido com claims de usuário, audience e roles")
    void testGenerateTokenSuccess() {
        String username = "admin";
        List<String> roles = List.of("ROLE_ADMIN", "ROLE_USER");

        String token = jwtTokenService.generateToken(username, roles);

        assertNotNull(token);
        assertFalse(token.isBlank());

        DecodedJWT decoded = jwtTokenService.validateToken(token);
        assertEquals("admin", decoded.getSubject());
        assertEquals("specvora-service", decoded.getIssuer());
        assertTrue(decoded.getAudience().contains("specvora-api"));
        assertEquals(roles, decoded.getClaim("roles").asList(String.class));
        assertTrue(decoded.getExpiresAt().toInstant().isAfter(Instant.now()));
    }

    @Test
    @DisplayName("Deve extrair a Authentication do Spring Security com as devidas GrantedAuthorities")
    void testGetAuthenticationSuccess() {
        String token = jwtTokenService.generateToken("motorista", List.of("ROLE_USER"));

        Authentication authentication = jwtTokenService.getAuthentication(token);

        assertNotNull(authentication);
        assertEquals("motorista", authentication.getName());
        assertEquals(1, authentication.getAuthorities().size());
        assertTrue(authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_USER")));
    }

    @Test
    @DisplayName("Deve rejeitar token adulterado ou com assinatura inválida")
    void testValidateTamperedTokenThrowsException() {
        String token = jwtTokenService.generateToken("user", List.of("ROLE_USER"));
        String tamperedToken = token.substring(0, token.length() - 5) + "abcde";

        assertThrows(JWTVerificationException.class, () -> jwtTokenService.validateToken(tamperedToken));
    }

    @Test
    @DisplayName("Deve rejeitar inicialização com chave secreta fraca com menos de 256 bits (32 bytes)")
    void testWeakKeyThrowsIllegalStateException() {
        JwtTokenService weakService = new JwtTokenService();
        ReflectionTestUtils.setField(weakService, "jwtSecret", "chave-curta");
        assertThrows(IllegalStateException.class, weakService::validateAndInitAlgorithm);
    }

    @Test
    @DisplayName("Deve retornar tempo de expiração em segundos coerente")
    void testGetExpiresInSeconds() {
        assertEquals(7200L, jwtTokenService.getExpiresInSeconds());
    }
}
