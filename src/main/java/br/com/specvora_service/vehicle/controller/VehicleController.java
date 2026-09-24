package br.com.specvora_service.vehicle.controller;

import br.com.specvora_service.vehicle.domain.VehicleModel;
import br.com.specvora_service.vehicle.dto.VehicleRequestDTO;
import br.com.specvora_service.vehicle.service.VehicleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class VehicleController implements VehicleApi {

    private final VehicleService vehicleService;

    @Override
    public ResponseEntity<VehicleModel> findVehicle(VehicleRequestDTO dto) {
        return ResponseEntity.ok(vehicleService.findVehicle(dto));
    }

    @Override
    public ResponseEntity<List<VehicleModel>> findAll() {
        return ResponseEntity.ok(vehicleService.findAll());
    }

    @Override
    public ResponseEntity<VehicleModel> findById(String id) {
        return ResponseEntity.ok(vehicleService.findById(id));
    }
}
