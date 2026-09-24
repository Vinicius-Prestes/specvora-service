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
@Schema(description = "Dados para cadastro de novo usuário")
public class RegisterRequestDTO {

    @NotBlank(message = "O username é obrigatório")
    @Size(min = 3, max = 50, message = "O username deve conter entre 3 e 50 caracteres")
    @Pattern(regexp = "^[a-zA-Z0-9_\\-\\.]+$", message = "Username deve conter apenas caracteres alfanuméricos e . - _")
    @Schema(description = "Nome de usuário desejado", example = "motorista")
    private String username;

    @NotBlank(message = "A senha é obrigatória")
    @Size(min = 6, max = 100, message = "A senha deve conter entre 6 e 100 caracteres")
    @Schema(description = "Senha de acesso", example = "senhaForte123")
    private String password;

    @Pattern(regexp = "^(?i)(USER|ADMIN)$", message = "O perfil deve ser USER ou ADMIN")
    @Schema(description = "Perfil de acesso (USER ou ADMIN). Padrão: USER", example = "USER")
    private String role;
}
