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
@Schema(description = "Resposta contendo o token JWT gerado e dados do usuário")
public class LoginResponseDTO {

    @Schema(description = "Token JWT assinado", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    private String token;

    @Schema(description = "Tipo do token", example = "Bearer")
    @Builder.Default
    private String tokenType = "Bearer";

    @Schema(description = "Tempo de expiração em segundos", example = "7200")
    private Long expiresIn;

    @Schema(description = "Nome de usuário autenticado", example = "admin")
    private String username;

    @Schema(description = "Perfis/permissões atribuídos ao token", example = "[\"ROLE_ADMIN\"]")
    private List<String> roles;
}
