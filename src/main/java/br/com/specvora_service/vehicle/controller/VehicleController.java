package br.com.specvora_service.vehicle.controller;

import br.com.specvora_service.vehicle.domain.VehicleModel;
import br.com.specvora_service.vehicle.dto.VehicleRequestDTO;
import br.com.specvora_service.vehicle.dto.VehicleUpsertDTO;
import br.com.specvora_service.vehicle.service.VehicleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class VehicleController implements VehicleApi {

    private final VehicleService vehicleService;

    @Override
    public ResponseEntity<List<VehicleModel>> findAll() {
        return ResponseEntity.ok(vehicleService.findAll());
    }

    @Override
    public ResponseEntity<VehicleModel> findById(String id) {
        return ResponseEntity.ok(vehicleService.findById(id));
    }

    @Override
    public ResponseEntity<VehicleModel> findVehicle(VehicleRequestDTO dto) {
        return ResponseEntity.ok(vehicleService.findVehicle(dto));
    }

    @Override
    public ResponseEntity<VehicleModel> createVehicle(VehicleUpsertDTO dto, UriComponentsBuilder uriBuilder) {
        VehicleModel created = vehicleService.createVehicle(dto);
        URI location = uriBuilder.path("/vehicles/{id}").buildAndExpand(created.getId()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @Override
    public ResponseEntity<VehicleModel> updateVehicle(String id, VehicleUpsertDTO dto) {
        return ResponseEntity.ok(vehicleService.updateVehicle(id, dto));
    }

    @Override
    public ResponseEntity<Void> deleteVehicle(String id) {
        vehicleService.deleteVehicle(id);
        return ResponseEntity.noContent().build();
    }
}
