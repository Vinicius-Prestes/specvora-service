package br.com.specvora_service.security;

import br.com.specvora_service.auth.controller.AuthController;
import br.com.specvora_service.auth.service.AuthService;
import br.com.specvora_service.auth.service.JwtTokenService;
import br.com.specvora_service.config.CrossOriginConfig;
import br.com.specvora_service.config.ErrorResponseWriter;
import br.com.specvora_service.config.IdempotencyFilter;
import br.com.specvora_service.config.JwtAuthFilter;
import br.com.specvora_service.config.RateLimitingFilter;
import br.com.specvora_service.config.SecurityConfig;
import br.com.specvora_service.vehicle.controller.VehicleController;
import br.com.specvora_service.vehicle.domain.VehicleModel;
import br.com.specvora_service.vehicle.exception.GlobalExceptionHandler;
import br.com.specvora_service.vehicle.service.VehicleService;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testes de integração passando pela SecurityFilterChain REAL (SecurityConfig + JwtAuthFilter +
 * IdempotencyFilter + RateLimitingFilter), cobrindo a matriz perfil × endpoint (401 / 403 / 2xx).
 */
@WebMvcTest(controllers = {VehicleController.class, AuthController.class})
@Import({SecurityConfig.class, JwtAuthFilter.class, IdempotencyFilter.class, RateLimitingFilter.class,
        ErrorResponseWriter.class, CrossOriginConfig.class, GlobalExceptionHandler.class,
        JwtTokenService.class, AuthService.class, LocalEncryptionService.class})
@TestPropertySource(properties = {
        "security.jwt.secret=" + SecurityFilterChainIntegrationTest.TEST_SECRET,
        "security.crypto.aes-secret=test-aes-secret-for-integration-tests"
})
@DisplayName("Integração - SecurityFilterChain real (matriz perfil × endpoint)")
class SecurityFilterChainIntegrationTest {

    static final String TEST_SECRET = "integration-test-secret-with-at-least-32-bytes!!";

    private static final List<String> USER_ROLES = List.of("ROLE_USER");
    private static final List<String> GESTOR_ROLES = List.of("ROLE_GESTOR", "ROLE_USER");
    private static final List<String> ADMIN_ROLES = List.of("ROLE_ADMINISTRADOR", "ROLE_ADMIN", "ROLE_GESTOR", "ROLE_USER");

    private static final String VEHICLE_BODY = """
            {
                "brand": "Ford",
                "model": "Nova Ranger 4x4",
                "version": "XLT",
                "engine": "3.0 V6 - 24V",
                "year": "2026",
                "vehicleCategory": "picape"
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService jwtTokenService;

    @MockitoBean
    private VehicleService vehicleService;

    @MockitoBean
    private SecurityAuditLogger securityAuditLogger;

    @BeforeEach
    void setUp() {
        VehicleModel vehicle = VehicleModel.builder().id("veh-1").brand("Ford").model("Nova Ranger 4x4").build();
        when(vehicleService.findAll()).thenReturn(List.of(vehicle));
        when(vehicleService.createVehicle(any())).thenReturn(vehicle);
        when(vehicleService.updateVehicle(any(), any())).thenReturn(vehicle);
    }

    private String bearer(String username, List<String> roles) {
        return "Bearer " + jwtTokenService.generateToken(username, roles);
    }

    // ---------------------------------------------------------------- 401

    @Test
    @DisplayName("Sem token → 401 no formato ErrorResponseDTO")
    void noTokenReturns401() throws Exception {
        mockMvc.perform(get("/vehicles"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value("/vehicles"));
    }

    @Test
    @DisplayName("Token com assinatura inválida → 401")
    void tamperedTokenReturns401() throws Exception {
        String forged = JWT.create()
                .withIssuer(JwtTokenService.ISSUER)
                .withAudience(JwtTokenService.AUDIENCE)
                .withSubject("atacante")
                .withClaim(JwtTokenService.ROLES_CLAIM, ADMIN_ROLES)
                .withExpiresAt(Date.from(Instant.now().plusSeconds(3600)))
                .sign(Algorithm.HMAC256("segredo-diferente-do-servidor-com-32-bytes"));

        mockMvc.perform(delete("/vehicles/veh-1").header("Authorization", "Bearer " + forged))
                .andExpect(status().isUnauthorized());

        verify(securityAuditLogger).logFromRequest(any(), eq(SecurityAuditLogger.EventType.AUTH_TOKEN_INVALID),
                eq(SecurityAuditLogger.Severity.WARN), isNull(), eq(401), anyString(), anyMap());
    }

    @Test
    @DisplayName("Token expirado → 401")
    void expiredTokenReturns401() throws Exception {
        String expired = JWT.create()
                .withIssuer(JwtTokenService.ISSUER)
                .withAudience(JwtTokenService.AUDIENCE)
                .withSubject("user")
                .withClaim(JwtTokenService.ROLES_CLAIM, USER_ROLES)
                .withIssuedAt(Date.from(Instant.now().minusSeconds(7200)))
                .withExpiresAt(Date.from(Instant.now().minusSeconds(60)))
                .sign(Algorithm.HMAC256(TEST_SECRET));

        mockMvc.perform(get("/vehicles").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized());

        verify(securityAuditLogger).logFromRequest(any(), eq(SecurityAuditLogger.EventType.AUTH_TOKEN_EXPIRED),
                eq(SecurityAuditLogger.Severity.WARN), eq("user"), eq(401), anyString(), anyMap());
    }

    // ---------------------------------------------------------------- USER

    @Test
    @DisplayName("USER: GET /vehicles → 200")
    void userCanRead() throws Exception {
        mockMvc.perform(get("/vehicles").header("Authorization", bearer("user", USER_ROLES)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("USER: POST /vehicles → 403")
    void userCannotCreate() throws Exception {
        mockMvc.perform(post("/vehicles").header("Authorization", bearer("user", USER_ROLES))
                        .contentType(MediaType.APPLICATION_JSON).content(VEHICLE_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("USER: PUT /vehicles/{id} → 403")
    void userCannotUpdate() throws Exception {
        mockMvc.perform(put("/vehicles/veh-1").header("Authorization", bearer("user", USER_ROLES))
                        .contentType(MediaType.APPLICATION_JSON).content(VEHICLE_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("USER: DELETE /vehicles/{id} → 403")
    void userCannotDelete() throws Exception {
        mockMvc.perform(delete("/vehicles/veh-1").header("Authorization", bearer("user", USER_ROLES)))
                .andExpect(status().isForbidden());

        verify(securityAuditLogger).logFromRequest(any(), eq(SecurityAuditLogger.EventType.SECURITY_ACCESS_DENIED),
                eq(SecurityAuditLogger.Severity.WARN), eq("user"), eq(403), anyString(), anyMap());
    }

    // ---------------------------------------------------------------- GESTOR

    @Test
    @DisplayName("GESTOR: POST /vehicles → 201 com Location")
    void gestorCanCreate() throws Exception {
        mockMvc.perform(post("/vehicles").header("Authorization", bearer("gestor", GESTOR_ROLES))
                        .contentType(MediaType.APPLICATION_JSON).content(VEHICLE_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/vehicles/veh-1")));
    }

    @Test
    @DisplayName("GESTOR: DELETE /vehicles/{id} → 403")
    void gestorCannotDelete() throws Exception {
        mockMvc.perform(delete("/vehicles/veh-1").header("Authorization", bearer("gestor", GESTOR_ROLES)))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- ADMINISTRADOR

    @Test
    @DisplayName("ADMINISTRADOR: DELETE /vehicles/{id} → 204")
    void adminCanDelete() throws Exception {
        mockMvc.perform(delete("/vehicles/veh-1").header("Authorization", bearer("admin", ADMIN_ROLES)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Hierarquia: token só com ROLE_ADMINISTRADOR herda permissões de GESTOR → 201")
    void roleHierarchyAppliesInFilterChain() throws Exception {
        mockMvc.perform(post("/vehicles").header("Authorization", bearer("root", List.of("ROLE_ADMINISTRADOR")))
                        .contentType(MediaType.APPLICATION_JSON).content(VEHICLE_BODY))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST repetido com a mesma Idempotency-Key → 409 e evento IDEMPOTENCY_CONFLICT")
    void duplicateIdempotencyKeyIsLogged() throws Exception {
        String token = bearer("gestor", GESTOR_ROLES);
        for (int expected : new int[]{201, 409}) {
            mockMvc.perform(post("/vehicles").header("Authorization", token)
                            .header("Idempotency-Key", "req-001")
                            .contentType(MediaType.APPLICATION_JSON).content(VEHICLE_BODY))
                    .andExpect(status().is(expected));
        }

        verify(securityAuditLogger).logFromRequest(any(), eq(SecurityAuditLogger.EventType.IDEMPOTENCY_CONFLICT),
                eq(SecurityAuditLogger.Severity.WARN), eq("gestor"), eq(409), anyString(), anyMap());
    }

    @Test
    @DisplayName("6ª tentativa de login no minuto → 429 e evento RATE_LIMIT_EXCEEDED")
    void loginBruteForceIsLogged() throws Exception {
        String body = "{\"username\":\"admin\",\"password\":\"senha-errada\"}";
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/auth/login").with(r -> { r.setRemoteAddr("203.0.113.7"); return r; })
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post("/auth/login").with(r -> { r.setRemoteAddr("203.0.113.7"); return r; })
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests());

        verify(securityAuditLogger).logFromRequest(any(), eq(SecurityAuditLogger.EventType.RATE_LIMIT_EXCEEDED),
                eq(SecurityAuditLogger.Severity.WARN), isNull(), eq(429), anyString(), anyMap());
    }

    // ---------------------------------------------------------------- /auth

    @Test
    @DisplayName("Registro anônimo com perfil USER → 201")
    void anonymousRegisterAsUser() throws Exception {
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"novo.user\",\"password\":\"senhaForte123\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roles").value(hasItem("ROLE_USER")));
    }

    @Test
    @DisplayName("Registro anônimo com perfil GESTOR → 403 (sem escalada de privilégio)")
    void anonymousRegisterAsGestorIsForbidden() throws Exception {
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"intruso\",\"password\":\"senhaForte123\",\"role\":\"GESTOR\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Registro por USER autenticado com perfil ADMINISTRADOR → 403")
    void userRegisterAsAdminIsForbidden() throws Exception {
        mockMvc.perform(post("/auth/register").header("Authorization", bearer("user", USER_ROLES))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"intruso2\",\"password\":\"senhaForte123\",\"role\":\"ADMINISTRADOR\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Registro por ADMINISTRADOR autenticado com perfil GESTOR → 201")
    void adminCanRegisterGestor() throws Exception {
        mockMvc.perform(post("/auth/register").header("Authorization", bearer("admin", ADMIN_ROLES))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"novo.gestor\",\"password\":\"senhaForte123\",\"role\":\"GESTOR\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roles").value(hasItem("ROLE_GESTOR")));
    }

    @Test
    @DisplayName("GET /auth/me com token válido → 200 com expiresAt")
    void meReturnsExpiresAt() throws Exception {
        mockMvc.perform(get("/auth/me").header("Authorization", bearer("user", USER_ROLES)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("user"))
                .andExpect(jsonPath("$.expiresAt").value(notNullValue()));
    }
}
