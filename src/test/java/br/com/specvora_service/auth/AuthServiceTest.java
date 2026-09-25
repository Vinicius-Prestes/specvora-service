package br.com.specvora_service.auth;

import br.com.specvora_service.auth.dto.LoginRequestDTO;
import br.com.specvora_service.auth.dto.LoginResponseDTO;
import br.com.specvora_service.auth.dto.RegisterRequestDTO;
import br.com.specvora_service.auth.dto.UserResponseDTO;
import br.com.specvora_service.auth.service.AuthService;
import br.com.specvora_service.auth.service.JwtTokenService;
import br.com.specvora_service.security.LocalEncryptionService;
import br.com.specvora_service.vehicle.exception.ResourceConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Testes de Unidade - AuthService")
class AuthServiceTest {

    @Mock
    private JwtTokenService jwtTokenService;

    @Mock
    private LocalEncryptionService localEncryptionService;

    @Mock
    private br.com.specvora_service.security.SecurityAuditLogger securityAuditLogger;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setUp() {
        when(localEncryptionService.encrypt(any())).thenReturn("mocked-encrypted-audit-data");
        authService.initDefaultUsers();
    }

    @Test
    @DisplayName("Deve autenticar com sucesso o usuário admin e emitir token JWT")
    void testLoginAdminSuccess() {
        when(jwtTokenService.generateToken(eq("admin"), any()))
                .thenReturn("mocked-jwt-token-admin");
        when(jwtTokenService.getExpiresInSeconds()).thenReturn(7200L);

        LoginRequestDTO request = LoginRequestDTO.builder()
                .username("admin")
                .password("admin123")
                .build();

        LoginResponseDTO response = authService.login(request);

        assertNotNull(response);
        assertEquals("mocked-jwt-token-admin", response.getToken());
        assertEquals("admin", response.getUsername());
        assertTrue(response.getRoles().contains("ROLE_ADMINISTRADOR"));
    }

    @Test
    @DisplayName("Deve rejeitar login com senha incorreta e disparar BadCredentialsException")
    void testLoginWrongPasswordThrowsBadCredentialsException() {
        LoginRequestDTO request = LoginRequestDTO.builder()
                .username("admin")
                .password("senhaErrada")
                .build();

        assertThrows(BadCredentialsException.class, () -> authService.login(request));
    }

    @Test
    @DisplayName("Deve rejeitar login de usuário inexistente")
    void testLoginUnknownUserThrowsBadCredentialsException() {
        LoginRequestDTO request = LoginRequestDTO.builder()
                .username("inexistente")
                .password("123456")
                .build();

        assertThrows(BadCredentialsException.class, () -> authService.login(request));
    }

    @Test
    @DisplayName("Auto-registro sem autenticação prévia deve conceder apenas perfil USER")
    void testAutoRegisterWithoutElevatedRoleSuccess() {
        RegisterRequestDTO request = RegisterRequestDTO.builder()
                .username("novo_usuario")
                .password("senhaSegura123")
                .build();

        UserResponseDTO response = authService.register(request, null);

        assertNotNull(response);
        assertEquals("novo_usuario", response.getUsername());
        assertTrue(response.getRoles().contains("ROLE_USER"));
        assertFalse(response.getRoles().contains("ROLE_ADMINISTRADOR"));
    }

    @Test
    @DisplayName("Tentativa anônima de auto-registro com perfil ADMIN deve ser rejeitada com AccessDeniedException")
    void testRegisterAdminWithoutAdminRequesterThrowsAccessDeniedException() {
        RegisterRequestDTO request = RegisterRequestDTO.builder()
                .username("hacker")
                .password("hacker123")
                .role("ADMINISTRADOR")
                .build();

        assertThrows(AccessDeniedException.class, () -> authService.register(request, null));
    }

    @Test
    @DisplayName("Administrador autenticado pode cadastrar novos usuários com perfil elevado")
    void testRegisterAdminWithAdminRequesterSuccess() {
        Authentication adminAuth = new UsernamePasswordAuthenticationToken(
                "admin", null, List.of(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR")));

        RegisterRequestDTO request = RegisterRequestDTO.builder()
                .username("novo_admin")
                .password("senhaSegura123")
                .role("ADMINISTRADOR")
                .build();

        UserResponseDTO response = authService.register(request, adminAuth);

        assertNotNull(response);
        assertEquals("novo_admin", response.getUsername());
        assertTrue(response.getRoles().contains("ROLE_ADMINISTRADOR"));
    }

    @Test
    @DisplayName("Deve impedir cadastro com username duplicado disparando ResourceConflictException (409)")
    void testRegisterDuplicateUsernameThrowsResourceConflictException() {
        RegisterRequestDTO request = RegisterRequestDTO.builder()
                .username("admin")
                .password("outraSenha123")
                .build();

        assertThrows(ResourceConflictException.class, () -> authService.register(request, null));
    }

    @Test
    @DisplayName("Deve retornar os dados do usuário autenticado no contexto incluindo expiresAt")
    void testGetCurrentUserSuccess() {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "usuario_autenticado",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        auth.setDetails(Map.of("expiresAt", "2026-09-25T12:00:00Z"));

        UserResponseDTO response = authService.getCurrentUser(auth);

        assertNotNull(response);
        assertEquals("usuario_autenticado", response.getUsername());
        assertTrue(response.getRoles().contains("ROLE_USER"));
        assertEquals("2026-09-25T12:00:00Z", response.getExpiresAt());
    }
}
