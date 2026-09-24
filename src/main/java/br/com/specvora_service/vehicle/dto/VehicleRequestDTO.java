package br.com.specvora_service.vehicle.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Locale;
import java.util.Map;

@Data
public class VehicleRequestDTO {

    @NotBlank(message = "Marca é obrigatória")
    @Size(max = 100, message = "Marca deve ter no máximo 100 caracteres")
    private String brand;

    @NotBlank(message = "Modelo é obrigatório")
    @Size(max = 100, message = "Modelo deve ter no máximo 100 caracteres")
    private String model;

    @NotBlank(message = "Versão é obrigatória")
    @Size(max = 100, message = "Versão deve ter no máximo 100 caracteres")
    private String version;

    @NotBlank(message = "Motor é obrigatório")
    @Size(max = 100, message = "Motor deve ter no máximo 100 caracteres")
    private String engine;

    @NotBlank(message = "Ano é obrigatório")
    @Size(max = 10, message = "Ano deve ter no máximo 10 caracteres")
    private String year;

    private String vehicleCategory;

    private Map<String, Object> categories;

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
