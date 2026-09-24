package br.com.specvora_service.auth;

import br.com.specvora_service.auth.dto.LoginRequestDTO;
import br.com.specvora_service.auth.dto.LoginResponseDTO;
import br.com.specvora_service.auth.dto.RegisterRequestDTO;
import br.com.specvora_service.auth.dto.UserResponseDTO;
import br.com.specvora_service.auth.service.AuthService;
import br.com.specvora_service.auth.service.JwtTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Testes de Unidade - AuthService")
class AuthServiceTest {

    @Mock
    private JwtTokenService jwtTokenService;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setUp() {
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
        assertTrue(response.getRoles().contains("ROLE_ADMIN"));
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
    @DisplayName("Deve cadastrar novo usuário comum com perfil ROLE_USER")
    void testRegisterUserSuccess() {
        RegisterRequestDTO request = RegisterRequestDTO.builder()
                .username("novo_usuario")
                .password("senhaSegura123")
                .role("USER")
                .build();

        UserResponseDTO response = authService.register(request);

        assertNotNull(response);
        assertEquals("novo_usuario", response.getUsername());
        assertEquals(List.of("ROLE_USER"), response.getRoles());
    }

    @Test
    @DisplayName("Deve cadastrar novo administrador com perfil ROLE_ADMIN")
    void testRegisterAdminSuccess() {
        RegisterRequestDTO request = RegisterRequestDTO.builder()
                .username("novo_admin")
                .password("senhaSegura123")
                .role("ADMIN")
                .build();

        UserResponseDTO response = authService.register(request);

        assertNotNull(response);
        assertEquals("novo_admin", response.getUsername());
        assertTrue(response.getRoles().contains("ROLE_ADMIN"));
    }

    @Test
    @DisplayName("Deve impedir cadastro com username duplicado")
    void testRegisterDuplicateUsernameThrowsException() {
        RegisterRequestDTO request = RegisterRequestDTO.builder()
                .username("admin")
                .password("outraSenha123")
                .build();

        assertThrows(IllegalArgumentException.class, () -> authService.register(request));
    }

    @Test
    @DisplayName("Deve retornar os dados do usuário autenticado no contexto")
    void testGetCurrentUserSuccess() {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "usuario_autenticado",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        UserResponseDTO response = authService.getCurrentUser(auth);

        assertNotNull(response);
        assertEquals("usuario_autenticado", response.getUsername());
        assertEquals(List.of("ROLE_USER"), response.getRoles());
    }
}
