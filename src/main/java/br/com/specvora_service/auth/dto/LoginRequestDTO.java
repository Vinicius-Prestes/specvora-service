package br.com.specvora_service.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Dados para autenticação e obtenção de token JWT")
public class LoginRequestDTO {

    @NotBlank(message = "O username é obrigatório")
    @Schema(description = "Nome de usuário", example = "admin")
    private String username;

    @NotBlank(message = "A senha é obrigatória")
    @Schema(description = "Senha do usuário", example = "admin123")
    private String password;
}
