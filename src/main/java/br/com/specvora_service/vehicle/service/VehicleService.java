package br.com.specvora_service.vehicle.service;

import br.com.specvora_service.vehicle.domain.VehicleModel;
import br.com.specvora_service.vehicle.dto.VehicleRequestDTO;
import br.com.specvora_service.vehicle.dto.VehicleUpsertDTO;
import br.com.specvora_service.vehicle.exception.VehicleNotFoundException;
import br.com.specvora_service.vehicle.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class VehicleService {

    private final VehicleRepository vehicleRepository;
    private final br.com.specvora_service.security.SecurityAuditLogger securityAuditLogger;

    public VehicleModel findVehicle(VehicleRequestDTO dto) {
        dto.normalize();
        return vehicleRepository.findVehicle(dto)
                .orElseThrow(() -> new VehicleNotFoundException("Veículo não encontrado"));
    }

    public List<VehicleModel> findAll() {
        return vehicleRepository.findAll();
    }

    public VehicleModel findById(String id) {
        return vehicleRepository.findById(id)
                .orElseThrow(() -> new VehicleNotFoundException("Veículo não encontrado com id: " + id));
    }

    public VehicleModel createVehicle(VehicleUpsertDTO dto) {
        dto.normalize();
        VehicleModel vehicle = VehicleModel.builder()
                .brand(dto.getBrand())
                .model(dto.getModel())
                .version(dto.getVersion())
                .engine(dto.getEngine())
                .year(dto.getYear())
                .vehicleCategory(dto.getVehicleCategory())
                .categories(dto.getCategories())
                .build();
        VehicleModel saved = vehicleRepository.save(vehicle);

        securityAuditLogger.logEvent(
                br.com.specvora_service.security.SecurityAuditLogger.EventType.VEHICLE_CREATED,
                br.com.specvora_service.security.SecurityAuditLogger.Severity.INFO,
                null,
                null,
                "POST",
                "/vehicles",
                201,
                "Veículo cadastrado no catálogo",
                java.util.Map.of("vehicleId", saved.getId() != null ? saved.getId() : "", "brand", saved.getBrand(), "model", saved.getModel())
        );

        return saved;
    }

    public VehicleModel updateVehicle(String id, VehicleUpsertDTO dto) {
        VehicleModel existing = findById(id);
        dto.normalize();

        existing.setBrand(dto.getBrand());
        existing.setModel(dto.getModel());
        existing.setVersion(dto.getVersion());
        existing.setEngine(dto.getEngine());
        existing.setYear(dto.getYear());
        existing.setVehicleCategory(dto.getVehicleCategory());
        existing.setCategories(dto.getCategories());

        VehicleModel updated = vehicleRepository.save(existing);

        securityAuditLogger.logEvent(
                br.com.specvora_service.security.SecurityAuditLogger.EventType.VEHICLE_UPDATED,
                br.com.specvora_service.security.SecurityAuditLogger.Severity.INFO,
                null,
                null,
                "PUT",
                "/vehicles/" + id,
                200,
                "Veículo atualizado no catálogo",
                java.util.Map.of("vehicleId", id, "brand", updated.getBrand(), "model", updated.getModel())
        );

        return updated;
    }

    public void deleteVehicle(String id) {
        if (!vehicleRepository.existsById(id)) {
            throw new VehicleNotFoundException("Veículo não encontrado com id: " + id);
        }
        vehicleRepository.deleteById(id);

        securityAuditLogger.logEvent(
                br.com.specvora_service.security.SecurityAuditLogger.EventType.VEHICLE_DELETED,
                br.com.specvora_service.security.SecurityAuditLogger.Severity.WARN,
                null,
                null,
                "DELETE",
                "/vehicles/" + id,
                204,
                "Veículo excluído do catálogo",
                java.util.Map.of("vehicleId", id)
        );
    }
}
