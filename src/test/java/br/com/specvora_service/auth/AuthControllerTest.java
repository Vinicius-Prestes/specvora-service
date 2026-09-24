package br.com.specvora_service.auth;

import br.com.specvora_service.auth.controller.AuthController;
import br.com.specvora_service.auth.dto.LoginRequestDTO;
import br.com.specvora_service.auth.dto.LoginResponseDTO;
import br.com.specvora_service.auth.dto.RegisterRequestDTO;
import br.com.specvora_service.auth.dto.UserResponseDTO;
import br.com.specvora_service.auth.service.AuthService;
import br.com.specvora_service.vehicle.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("Testes de Controller de Autenticação - AuthController")
class AuthControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController authController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /auth/login - Deve retornar 200 OK com token JWT quando credenciais forem válidas")
    void testLoginSuccessReturns200() throws Exception {
        LoginResponseDTO responseDTO = LoginResponseDTO.builder()
                .token("valid-jwt-token-example")
                .tokenType("Bearer")
                .expiresIn(7200L)
                .username("admin")
                .roles(List.of("ROLE_ADMIN", "ROLE_USER"))
                .build();

        when(authService.login(any(LoginRequestDTO.class))).thenReturn(responseDTO);

        LoginRequestDTO request = LoginRequestDTO.builder()
                .username("admin")
                .password("admin123")
                .build();

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", is("valid-jwt-token-example")))
                .andExpect(jsonPath("$.username", is("admin")))
                .andExpect(jsonPath("$.tokenType", is("Bearer")));
    }

    @Test
    @DisplayName("POST /auth/login - Deve retornar 401 UNAUTHORIZED quando credenciais forem inválidas")
    void testLoginInvalidCredentialsReturns401() throws Exception {
        when(authService.login(any(LoginRequestDTO.class)))
                .thenThrow(new BadCredentialsException("Credenciais inválidas: usuário ou senha incorretos"));

        LoginRequestDTO request = LoginRequestDTO.builder()
                .username("admin")
                .password("senhaIncorreta")
                .build();

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.error", is("Unauthorized")))
                .andExpect(jsonPath("$.message", is("Credenciais inválidas: usuário ou senha incorretos")));
    }

    @Test
    @DisplayName("POST /auth/register - Deve retornar 201 CREATED ao registrar novo usuário")
    void testRegisterSuccessReturns201() throws Exception {
        UserResponseDTO responseDTO = UserResponseDTO.builder()
                .username("novo_user")
                .roles(List.of("ROLE_USER"))
                .build();

        when(authService.register(any(RegisterRequestDTO.class))).thenReturn(responseDTO);

        RegisterRequestDTO request = RegisterRequestDTO.builder()
                .username("novo_user")
                .password("senhaForte123")
                .role("USER")
                .build();

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username", is("novo_user")))
                .andExpect(jsonPath("$.roles[0]", is("ROLE_USER")));
    }

    @Test
    @DisplayName("GET /auth/me - Deve retornar 200 OK com dados do usuário autenticado")
    void testMeReturns200() throws Exception {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "admin", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        UserResponseDTO userDTO = UserResponseDTO.builder()
                .username("admin")
                .roles(List.of("ROLE_ADMIN"))
                .build();

        when(authService.getCurrentUser(any())).thenReturn(userDTO);

        mockMvc.perform(get("/auth/me").principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username", is("admin")))
                .andExpect(jsonPath("$.roles[0]", is("ROLE_ADMIN")));
    }
}
