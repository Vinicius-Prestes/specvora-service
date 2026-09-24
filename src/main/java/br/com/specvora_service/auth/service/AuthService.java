package br.com.specvora_service.auth.service;

import br.com.specvora_service.auth.dto.LoginRequestDTO;
import br.com.specvora_service.auth.dto.LoginResponseDTO;
import br.com.specvora_service.auth.dto.RegisterRequestDTO;
import br.com.specvora_service.auth.dto.UserResponseDTO;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final JwtTokenService jwtTokenService;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    // Armazenamento em memória para rápida demonstração e testes avaliativos
    private final Map<String, UserCredentials> users = new ConcurrentHashMap<>();

    private record UserCredentials(String username, String encodedPassword, List<String> roles) {}

    @PostConstruct
    public void initDefaultUsers() {
        // Usuário Administrador (Acesso total, CRUD completo de veículos)
        users.put("admin", new UserCredentials(
                "admin",
                passwordEncoder.encode("admin123"),
                List.of("ROLE_ADMIN", "ROLE_USER")
        ));

        // Usuário Comum (Apenas leitura e buscas de especificações)
        users.put("user", new UserCredentials(
                "user",
                passwordEncoder.encode("user123"),
                List.of("ROLE_USER")
        ));
    }

    public LoginResponseDTO login(LoginRequestDTO dto) {
        UserCredentials user = users.get(dto.getUsername());

        if (user == null || !passwordEncoder.matches(dto.getPassword(), user.encodedPassword())) {
            throw new BadCredentialsException("Credenciais inválidas: usuário ou senha incorretos");
        }

        String token = jwtTokenService.generateToken(user.username(), user.roles());

        return LoginResponseDTO.builder()
                .token(token)
                .tokenType("Bearer")
                .expiresIn(jwtTokenService.getExpiresInSeconds())
                .username(user.username())
                .roles(user.roles())
                .build();
    }

    public UserResponseDTO register(RegisterRequestDTO dto) {
        if (users.containsKey(dto.getUsername())) {
            throw new IllegalArgumentException("O nome de usuário já está em uso: " + dto.getUsername());
        }

        String assignedRole = (dto.getRole() != null && dto.getRole().trim().equalsIgnoreCase("ADMIN"))
                ? "ROLE_ADMIN"
                : "ROLE_USER";

        List<String> roles = assignedRole.equals("ROLE_ADMIN")
                ? List.of("ROLE_ADMIN", "ROLE_USER")
                : List.of("ROLE_USER");

        users.put(dto.getUsername(), new UserCredentials(
                dto.getUsername(),
                passwordEncoder.encode(dto.getPassword()),
                roles
        ));

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

        return UserResponseDTO.builder()
                .username(authentication.getName())
                .roles(roles)
                .expiresAt(null)
                .build();
    }
}
