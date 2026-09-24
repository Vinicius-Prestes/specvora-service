package br.com.specvora_service.auth.controller;

import br.com.specvora_service.auth.dto.LoginRequestDTO;
import br.com.specvora_service.auth.dto.LoginResponseDTO;
import br.com.specvora_service.auth.dto.RegisterRequestDTO;
import br.com.specvora_service.auth.dto.UserResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Autenticação", description = "Endpoints para emissão de JWT, cadastro de usuários e consulta de perfil")
@RequestMapping("/auth")
public interface AuthApi {

    @Operation(summary = "Realiza login e emite token JWT com roles e expiração",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Autenticação bem-sucedida"),
                    @ApiResponse(responseCode = "400", description = "Parâmetros inválidos"),
                    @ApiResponse(responseCode = "401", description = "Credenciais inválidas")
            })
    @PostMapping("/login")
    ResponseEntity<LoginResponseDTO> login(@Valid @RequestBody LoginRequestDTO dto);

    @Operation(summary = "Cadastra novo usuário (USER ou ADMIN)",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Usuário cadastrado com sucesso"),
                    @ApiResponse(responseCode = "400", description = "Dados inválidos ou usuário já existente")
            })
    @PostMapping("/register")
    ResponseEntity<UserResponseDTO> register(@Valid @RequestBody RegisterRequestDTO dto);

    @Operation(summary = "Retorna os dados e perfis do usuário autenticado no token atual",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Perfil recuperado com sucesso"),
                    @ApiResponse(responseCode = "401", description = "Não autenticado ou token inválido")
            })
    @GetMapping("/me")
    ResponseEntity<UserResponseDTO> me(Authentication authentication);
}
