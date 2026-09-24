package br.com.specvora_service.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
    @Size(min = 3, max = 50, message = "O username deve ter entre 3 e 50 caracteres")
    @Pattern(regexp = "^[a-zA-Z0-9_\\-\\.]+$", message = "Username deve conter apenas caracteres alfanuméricos e . - _")
    @Schema(description = "Nome de usuário", example = "admin")
    private String username;

    @NotBlank(message = "A senha é obrigatória")
    @Size(min = 6, max = 100, message = "A senha deve ter entre 6 e 100 caracteres")
    @Schema(description = "Senha do usuário", example = "admin123")
    private String password;
}
