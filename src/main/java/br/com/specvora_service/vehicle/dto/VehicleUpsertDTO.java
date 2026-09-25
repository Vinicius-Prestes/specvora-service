package br.com.specvora_service.vehicle.dto;

import br.com.specvora_service.vehicle.domain.VehicleModel;
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
@Schema(description = "Dados para criação ou atualização de veículo")
public class VehicleUpsertDTO {

    @NotBlank(message = "Marca é obrigatória")
    @Size(max = 100, message = "Marca deve ter no máximo 100 caracteres")
    @Pattern(regexp = "^[\\p{L}0-9\\s\\-\\.\\/\\+]+$", message = "Marca contém caracteres inválidos")
    @Schema(description = "Marca do veículo", example = "Citroën")
    private String brand;

    @NotBlank(message = "Modelo é obrigatório")
    @Size(max = 100, message = "Modelo deve ter no máximo 100 caracteres")
    @Pattern(regexp = "^[\\p{L}0-9\\s\\-\\.\\/\\+]+$", message = "Modelo contém caracteres inválidos")
    @Schema(description = "Modelo do veículo", example = "Nova Ranger 4x4")
    private String model;

    @NotBlank(message = "Versão é obrigatória")
    @Size(max = 100, message = "Versão deve ter no máximo 100 caracteres")
    @Pattern(regexp = "^[\\p{L}0-9\\s\\-\\.\\/\\+]+$", message = "Versão contém caracteres inválidos")
    @Schema(description = "Versão do veículo", example = "XLT")
    private String version;

    @NotBlank(message = "Motor é obrigatório")
    @Size(max = 100, message = "Motor deve ter no máximo 100 caracteres")
    @Pattern(regexp = "^[\\p{L}0-9\\s\\-\\.\\/\\+]+$", message = "Motorização contém caracteres inválidos")
    @Schema(description = "Motorização", example = "3.0 V6 - 24V")
    private String engine;

    @NotBlank(message = "Ano é obrigatório")
    @Pattern(regexp = "^(19|20)\\d{2}$", message = "Ano deve ser um valor numérico válido de 4 dígitos entre 1900 e 2099")
    @Schema(description = "Ano de fabricação (AAAA)", example = "2026")
    private String year;

    @Size(max = 50, message = "Categoria deve ter no máximo 50 caracteres")
    @Schema(description = "Categoria do veículo", example = "picape")
    private String vehicleCategory;

    @Schema(description = "Especificações técnicas categorizadas do veículo")
    private VehicleModel.Categories categories;

    public void normalize() {
        brand = sanitizeAndNormalize(brand);
        model = sanitizeAndNormalize(model);
        version = sanitizeAndNormalize(version);
        engine = sanitizeAndNormalize(engine);
        year = sanitizeAndNormalize(year);
        vehicleCategory = sanitizeAndNormalize(vehicleCategory);

        if (categories != null) {
            validateCategoryMap(categories.getEngineAndTransmission());
            validateCategoryMap(categories.getWheels());
            validateCategoryMap(categories.getConnectivity());
            validateCategoryMap(categories.getIceLineUp());
            validateCategoryMap(categories.getAirConditioning());
            validateCategoryMap(categories.getSafety());
            validateCategoryMap(categories.getHighTech());
            validateCategoryMap(categories.getGlobalClosing());
            validateCategoryMap(categories.getTrim());
            validateCategoryMap(categories.getSunroof());
            validateCategoryMap(categories.getSeats());
            validateCategoryMap(categories.getLights());
            validateCategoryMap(categories.getFourByFour());
            validateCategoryMap(categories.getOthers());
            validateCategoryMap(categories.getInmetroPbev());
        }
    }

    private void validateCategoryMap(Map<String, Object> map) {
        if (map == null) return;
        if (map.size() > 50) {
            throw new IllegalArgumentException("Número excessivo de atributos na categoria (máximo 50)");
        }
        for (String key : map.keySet()) {
            if (key == null || key.contains("$") || key.contains(".")) {
                throw new IllegalArgumentException("Chave inválida na especificação técnica: não pode conter '$' ou '.'");
            }
        }
    }

    private String sanitizeAndNormalize(String value) {
        if (value == null) {
            return null;
        }

        String sanitized = value.replaceAll("[<>'\"\\;]", "")
                .trim()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);

        return sanitized.isBlank() ? null : sanitized;
    }
}
