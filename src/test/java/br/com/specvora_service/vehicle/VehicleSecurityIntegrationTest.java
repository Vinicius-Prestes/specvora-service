package br.com.specvora_service.vehicle;

import br.com.specvora_service.auth.service.JwtTokenService;
import br.com.specvora_service.config.ErrorResponseWriter;
import br.com.specvora_service.config.JwtAuthFilter;
import br.com.specvora_service.config.SecurityConfig;
import br.com.specvora_service.vehicle.controller.VehicleController;
import br.com.specvora_service.vehicle.domain.VehicleModel;
import br.com.specvora_service.vehicle.dto.VehicleUpsertDTO;
import br.com.specvora_service.vehicle.exception.GlobalExceptionHandler;
import br.com.specvora_service.vehicle.service.VehicleService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Testes de Segurança, Hierarquia RBAC e Filtro JWT (SecurityFilterChain)")
class VehicleSecurityIntegrationTest {

    private static final String TEST_SECRET = "chave-de-teste-com-no-minimo-32-bytes-segura!!";

    private JwtTokenService jwtTokenService;
    private JwtAuthFilter jwtAuthFilter;
    private MockMvc mockMvc;

    @Mock
    private VehicleService vehicleService;

    @InjectMocks
    private VehicleController vehicleController;

    private static final String SAMPLE_BODY = """
            {
                "brand": "Ford",
                "model": "Nova Ranger 4x4",
                "version": "XLT",
                "engine": "3.0 V6 - 24V",
                "year": "2026",
                "vehicleCategory": "picape"
            }
            """;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();

        jwtTokenService = new JwtTokenService();
        ReflectionTestUtils.setField(jwtTokenService, "jwtSecret", TEST_SECRET);
        ReflectionTestUtils.setField(jwtTokenService, "expirationHours", 2L);
        jwtTokenService.validateAndInitAlgorithm();

        ErrorResponseWriter writer = new ErrorResponseWriter();
        jwtAuthFilter = new JwtAuthFilter(jwtTokenService, writer);

        mockMvc = MockMvcBuilders.standaloneSetup(vehicleController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Hierarquia RBAC: ADMINISTRADOR herda GESTOR e USER; GESTOR herda USER")
    void testRoleHierarchyReachableAuthorities() {
        RoleHierarchy hierarchy = SecurityConfig.roleHierarchy();

        // 1. Administrador deve alcançar ADMINISTRADOR, GESTOR e USER
        Collection<? extends GrantedAuthority> adminAuthorities = hierarchy.getReachableGrantedAuthorities(
                List.of(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR")));
        Set<String> adminRoles = adminAuthorities.stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
        assertTrue(adminRoles.contains("ROLE_ADMINISTRADOR"));
        assertTrue(adminRoles.contains("ROLE_GESTOR"));
        assertTrue(adminRoles.contains("ROLE_USER"));

        // 2. Gestor deve alcançar GESTOR e USER, mas NUNCA ADMINISTRADOR
        Collection<? extends GrantedAuthority> gestorAuthorities = hierarchy.getReachableGrantedAuthorities(
                List.of(new SimpleGrantedAuthority("ROLE_GESTOR")));
        Set<String> gestorRoles = gestorAuthorities.stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
        assertTrue(gestorRoles.contains("ROLE_GESTOR"));
        assertTrue(gestorRoles.contains("ROLE_USER"));
        assertFalse(gestorRoles.contains("ROLE_ADMINISTRADOR"));

        // 3. User só alcança USER
        Collection<? extends GrantedAuthority> userAuthorities = hierarchy.getReachableGrantedAuthorities(
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        Set<String> userRoles = userAuthorities.stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
        assertTrue(userRoles.contains("ROLE_USER"));
        assertFalse(userRoles.contains("ROLE_GESTOR"));
        assertFalse(userRoles.contains("ROLE_ADMINISTRADOR"));
    }

    @Test
    @DisplayName("JwtAuthFilter: Requisição sem token não autentica e passa para a cadeia")
    void testFilterWithoutTokenLeavesContextUnauthenticated() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/vehicles");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        jwtAuthFilter.doFilter(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    @DisplayName("JwtAuthFilter: Token JWT válido com ROLE_USER popula SecurityContext com GrantedAuthority correspondente")
    void testFilterWithValidUserTokenAuthenticatesSuccessfully() throws ServletException, IOException {
        String token = jwtTokenService.generateToken("cliente_ford", List.of("ROLE_USER"));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/vehicles");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        jwtAuthFilter.doFilter(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        assertEquals("cliente_ford", auth.getName());
        assertTrue(auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_USER")));
        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    @DisplayName("JwtAuthFilter: Token JWT com ROLE_GESTOR popula autoridades de Gestor")
    void testFilterWithValidGestorTokenAuthenticatesSuccessfully() throws ServletException, IOException {
        String token = jwtTokenService.generateToken("gestor_frota", List.of("ROLE_GESTOR"));

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/vehicles");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        jwtAuthFilter.doFilter(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        assertEquals("gestor_frota", auth.getName());
        assertTrue(auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_GESTOR")));
        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    @DisplayName("JwtAuthFilter: Token JWT com ROLE_ADMINISTRADOR popula autoridades administrativas")
    void testFilterWithValidAdminTokenAuthenticatesSuccessfully() throws ServletException, IOException {
        String token = jwtTokenService.generateToken("admin_ford", List.of("ROLE_ADMINISTRADOR"));

        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/vehicles/123");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        jwtAuthFilter.doFilter(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        assertEquals("admin_ford", auth.getName());
        assertTrue(auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMINISTRADOR")));
        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    @DisplayName("JwtAuthFilter: Token JWT adulterado ou malformado é rejeitado com 401 UNAUTHORIZED")
    void testFilterWithTamperedTokenDoesNotAuthenticate() throws ServletException, IOException {
        String validToken = jwtTokenService.generateToken("admin", List.of("ROLE_ADMINISTRADOR"));
        String tamperedToken = validToken.substring(0, validToken.length() - 6) + "xxxxxx";

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/vehicles");
        request.addHeader("Authorization", "Bearer " + tamperedToken);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        jwtAuthFilter.doFilter(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("Token JWT"));
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    @DisplayName("Controller: Consulta de catálogo por usuário autenticado retorna 200 OK")
    void testUserQueryingVehiclesReturns200() throws Exception {
        when(vehicleService.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/vehicles"))
                .andExpect(status().isOk());

        verify(vehicleService, times(1)).findAll();
    }

    @Test
    @DisplayName("Controller: Cadastro de veículo por gestor retorna 201 CREATED com header Location")
    void testGestorCreatingVehicleReturns201() throws Exception {
        VehicleModel created = VehicleModel.builder()
                .id("65f1a2b3c4d5e6f7a8b9c0d1")
                .brand("ford")
                .model("nova ranger 4x4")
                .version("xlt")
                .engine("3.0 v6 - 24v")
                .year("2026")
                .build();

        when(vehicleService.createVehicle(any(VehicleUpsertDTO.class))).thenReturn(created);

        mockMvc.perform(post("/vehicles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SAMPLE_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("/vehicles/65f1a2b3c4d5e6f7a8b9c0d1")));

        verify(vehicleService, times(1)).createVehicle(any(VehicleUpsertDTO.class));
    }

    @Test
    @DisplayName("Controller: Exclusão de veículo por administrador retorna 204 NO CONTENT")
    void testAdministradorDeletingVehicleReturns204() throws Exception {
        doNothing().when(vehicleService).deleteVehicle("65f1a2b3c4d5e6f7a8b9c0d1");

        mockMvc.perform(delete("/vehicles/65f1a2b3c4d5e6f7a8b9c0d1"))
                .andExpect(status().isNoContent());

        verify(vehicleService, times(1)).deleteVehicle("65f1a2b3c4d5e6f7a8b9c0d1");
    }
}
