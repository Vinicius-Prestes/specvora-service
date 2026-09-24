package br.com.specvora_service.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Informações do usuário autenticado")
public class UserResponseDTO {

    @Schema(description = "Identificador / username do usuário", example = "admin")
    private String username;

    @Schema(description = "Perfis/roles concedidos", example = "[\"ROLE_ADMIN\"]")
    private List<String> roles;

    @Schema(description = "Data/hora de expiração da sessão atual", example = "2026-09-24T12:00:00Z")
    private String expiresAt;
}
