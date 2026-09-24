package br.com.specvora_service.vehicle.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Locale;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Critérios de busca parametrizada de veículos")
public class VehicleRequestDTO {

    @NotBlank(message = "Marca é obrigatória")
    @Size(max = 100, message = "Marca deve ter no máximo 100 caracteres")
    @Pattern(regexp = "^[a-zA-Z0-9\\s\\-\\.\\/\\+]+$", message = "Marca contém caracteres inválidos")
    @Schema(description = "Marca do veículo", example = "Ford")
    private String brand;

    @NotBlank(message = "Modelo é obrigatório")
    @Size(max = 100, message = "Modelo deve ter no máximo 100 caracteres")
    @Pattern(regexp = "^[a-zA-Z0-9\\s\\-\\.\\/\\+]+$", message = "Modelo contém caracteres inválidos")
    @Schema(description = "Modelo do veículo", example = "Nova Ranger 4x4")
    private String model;

    @NotBlank(message = "Versão é obrigatória")
    @Size(max = 100, message = "Versão deve ter no máximo 100 caracteres")
    @Pattern(regexp = "^[a-zA-Z0-9\\s\\-\\.\\/\\+]+$", message = "Versão contém caracteres inválidos")
    @Schema(description = "Versão do veículo", example = "XLT")
    private String version;

    @NotBlank(message = "Motor é obrigatório")
    @Size(max = 100, message = "Motor deve ter no máximo 100 caracteres")
    @Pattern(regexp = "^[a-zA-Z0-9\\s\\-\\.\\/\\+]+$", message = "Motorização contém caracteres inválidos")
    @Schema(description = "Motorização", example = "3.0 V6 - 24V")
    private String engine;

    @NotBlank(message = "Ano é obrigatório")
    @Pattern(regexp = "^(19|20)\\d{2}$", message = "Ano deve ser um valor numérico válido de 4 dígitos entre 1900 e 2099")
    @Schema(description = "Ano de fabricação (AAAA)", example = "2026")
    private String year;

    @Size(max = 50, message = "Categoria deve ter no máximo 50 caracteres")
    @Schema(description = "Categoria do veículo", example = "picape")
    private String vehicleCategory;

    @Schema(description = "Especificações adicionais")
    private Map<String, Object> categories;

    public void normalize() {
        brand = sanitizeAndNormalize(brand);
        model = sanitizeAndNormalize(model);
        version = sanitizeAndNormalize(version);
        engine = sanitizeAndNormalize(engine);
        year = sanitizeAndNormalize(year);
        vehicleCategory = sanitizeAndNormalize(vehicleCategory);
    }

    private String sanitizeAndNormalize(String value) {
        if (value == null) {
            return null;
        }

        // Sanitização contra caracteres de controle, injeção de tags HTML/scripts e whitespace excessivo
        String sanitized = value.replaceAll("[<>'\"\\;]", "")
                .trim()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);

        return sanitized.isBlank() ? null : sanitized;
    }
}
