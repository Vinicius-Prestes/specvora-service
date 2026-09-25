package br.com.specvora_service.auth.service;

import br.com.specvora_service.auth.dto.LoginRequestDTO;
import br.com.specvora_service.auth.dto.LoginResponseDTO;
import br.com.specvora_service.auth.dto.RegisterRequestDTO;
import br.com.specvora_service.auth.dto.UserResponseDTO;
import br.com.specvora_service.security.LocalEncryptionService;
import br.com.specvora_service.vehicle.exception.ResourceConflictException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final JwtTokenService jwtTokenService;
    private final LocalEncryptionService localEncryptionService;
    private final br.com.specvora_service.security.SecurityAuditLogger securityAuditLogger;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    // Armazenamento em memória para rápida demonstração e testes avaliativos
    private final Map<String, UserCredentials> users = new ConcurrentHashMap<>();

    private record UserCredentials(String username, String encodedPassword, List<String> roles, String encryptedAuditData) {}

    @PostConstruct
    public void initDefaultUsers() {
        // Usuário Administrador (Acesso total, gestão e exclusão)
        users.put("admin", new UserCredentials(
                "admin",
                passwordEncoder.encode("admin123"),
                List.of("ROLE_ADMINISTRADOR", "ROLE_ADMIN", "ROLE_GESTOR", "ROLE_USER"),
                localEncryptionService.encrypt("audit:created_at=" + Instant.now() + ";user=admin;role=ADMINISTRADOR")
        ));

        // Usuário Gestor (Leitura, criação e edição de veículos)
        users.put("gestor", new UserCredentials(
                "gestor",
                passwordEncoder.encode("gestor123"),
                List.of("ROLE_GESTOR", "ROLE_USER"),
                localEncryptionService.encrypt("audit:created_at=" + Instant.now() + ";user=gestor;role=GESTOR")
        ));

        // Usuário Padrão / Consumidor (Apenas leitura e buscas)
        users.put("user", new UserCredentials(
                "user",
                passwordEncoder.encode("user123"),
                List.of("ROLE_USER"),
                localEncryptionService.encrypt("audit:created_at=" + Instant.now() + ";user=user;role=USER")
        ));
    }

    public LoginResponseDTO login(LoginRequestDTO dto) {
        UserCredentials user = users.get(dto.getUsername());

        if (user == null || !passwordEncoder.matches(dto.getPassword(), user.encodedPassword())) {
            securityAuditLogger.logEvent(
                    br.com.specvora_service.security.SecurityAuditLogger.EventType.AUTH_LOGIN_FAILURE,
                    br.com.specvora_service.security.SecurityAuditLogger.Severity.WARN,
                    dto.getUsername(),
                    null,
                    "POST",
                    "/auth/login",
                    401,
                    "Tentativa de login falhou: credenciais inválidas",
                    Map.of("username", dto.getUsername() != null ? dto.getUsername() : "")
            );
            throw new BadCredentialsException("Credenciais inválidas: usuário ou senha incorretos");
        }

        String token = jwtTokenService.generateToken(user.username(), user.roles());

        securityAuditLogger.logEvent(
                br.com.specvora_service.security.SecurityAuditLogger.EventType.AUTH_LOGIN_SUCCESS,
                br.com.specvora_service.security.SecurityAuditLogger.Severity.INFO,
                user.username(),
                null,
                "POST",
                "/auth/login",
                200,
                "Login realizado com sucesso",
                Map.of("roles", user.roles())
        );

        return LoginResponseDTO.builder()
                .token(token)
                .tokenType("Bearer")
                .expiresIn(jwtTokenService.getExpiresInSeconds())
                .username(user.username())
                .roles(user.roles())
                .build();
    }

    public UserResponseDTO register(RegisterRequestDTO dto, Authentication requester) {
        if (users.containsKey(dto.getUsername())) {
            throw new ResourceConflictException("O nome de usuário já está em uso: " + dto.getUsername());
        }

        String requestedRole = (dto.getRole() != null && !dto.getRole().isBlank())
                ? dto.getRole().trim().toUpperCase()
                : "USER";

        boolean wantsElevated = !requestedRole.equals("USER");
        boolean requesterIsAdmin = requester != null && requester.isAuthenticated() && requester.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMINISTRADOR") || a.getAuthority().equals("ROLE_ADMIN"));

        // Bloqueio de escalada de privilégios: auto-registro só permite perfil básico
        if (wantsElevated && !requesterIsAdmin) {
            securityAuditLogger.logEvent(
                    br.com.specvora_service.security.SecurityAuditLogger.EventType.AUTH_REGISTER_ELEVATED_DENIED,
                    br.com.specvora_service.security.SecurityAuditLogger.Severity.WARN,
                    dto.getUsername(),
                    null,
                    "POST",
                    "/auth/register",
                    403,
                    "Tentativa de auto-registro com perfil elevado bloqueada",
                    Map.of("requestedRole", requestedRole, "requester", requester != null ? requester.getName() : "anonymous")
            );
            throw new AccessDeniedException("Somente administradores autenticados podem conceder perfis elevados (GESTOR ou ADMINISTRADOR)");
        }

        List<String> roles;
        if ("ADMINISTRADOR".equals(requestedRole) || "ADMIN".equals(requestedRole)) {
            roles = List.of("ROLE_ADMINISTRADOR", "ROLE_ADMIN", "ROLE_GESTOR", "ROLE_USER");
        } else if ("GESTOR".equals(requestedRole)) {
            roles = List.of("ROLE_GESTOR", "ROLE_USER");
        } else {
            roles = List.of("ROLE_USER");
        }

        // Criptografia local do registro de auditoria do usuário via AES-256-GCM
        String auditPayload = "audit:registered_at=" + Instant.now() + ";user=" + dto.getUsername() + ";roles=" + roles;
        String encryptedAudit = localEncryptionService.encrypt(auditPayload);

        users.put(dto.getUsername(), new UserCredentials(
                dto.getUsername(),
                passwordEncoder.encode(dto.getPassword()),
                roles,
                encryptedAudit
        ));

        securityAuditLogger.logEvent(
                br.com.specvora_service.security.SecurityAuditLogger.EventType.AUTH_REGISTER_SUCCESS,
                br.com.specvora_service.security.SecurityAuditLogger.Severity.INFO,
                dto.getUsername(),
                null,
                "POST",
                "/auth/register",
                201,
                "Novo usuário cadastrado no sistema",
                Map.of("roles", roles)
        );

        return UserResponseDTO.builder()
                .username(dto.getUsername())
                .roles(roles)
                .expiresAt(null)
                .build();
    }

    public UserResponseDTO getCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BadCredentialsException("Nenhum usuário autenticado encontrado no contexto");
        }

        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        String expiresAt = null;
        if (authentication.getDetails() instanceof Map<?, ?> details) {
            Object exp = details.get("expiresAt");
            if (exp != null) {
                expiresAt = exp.toString();
            }
        }

        return UserResponseDTO.builder()
                .username(authentication.getName())
                .roles(roles)
                .expiresAt(expiresAt)
                .build();
    }
}
