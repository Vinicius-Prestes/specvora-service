package br.com.specvora_service.vehicle.dto;

import br.com.specvora_service.vehicle.domain.VehicleModel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Locale;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Dados para criação ou atualização de veículo")
public class VehicleUpsertDTO {

    @NotBlank(message = "Marca é obrigatória")
    @Size(max = 100, message = "Marca deve ter no máximo 100 caracteres")
    @Schema(description = "Marca do veículo", example = "Ford")
    private String brand;

    @NotBlank(message = "Modelo é obrigatório")
    @Size(max = 100, message = "Modelo deve ter no máximo 100 caracteres")
    @Schema(description = "Modelo do veículo", example = "Nova Ranger 4x4")
    private String model;

    @NotBlank(message = "Versão é obrigatória")
    @Size(max = 100, message = "Versão deve ter no máximo 100 caracteres")
    @Schema(description = "Versão do veículo", example = "XLT")
    private String version;

    @NotBlank(message = "Motor é obrigatório")
    @Size(max = 100, message = "Motor deve ter no máximo 100 caracteres")
    @Schema(description = "Motorização", example = "3.0 V6 - 24V")
    private String engine;

    @NotBlank(message = "Ano é obrigatório")
    @Size(max = 10, message = "Ano deve ter no máximo 10 caracteres")
    @Schema(description = "Ano de fabricação", example = "2026")
    private String year;

    @Schema(description = "Categoria do veículo", example = "picape")
    private String vehicleCategory;

    @Schema(description = "Especificações técnicas categorizadas do veículo")
    private VehicleModel.Categories categories;

    public void normalize() {
        brand = normalizeField(brand);
        model = normalizeField(model);
        version = normalizeField(version);
        engine = normalizeField(engine);
        year = normalizeField(year);
        vehicleCategory = normalizeField(vehicleCategory);
    }

    private String normalizeField(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }
}
