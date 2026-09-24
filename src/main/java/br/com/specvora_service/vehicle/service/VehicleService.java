package br.com.specvora_service.vehicle.service;

import br.com.specvora_service.vehicle.domain.VehicleModel;
import br.com.specvora_service.vehicle.dto.VehicleRequestDTO;
import br.com.specvora_service.vehicle.exception.VehicleNotFoundException;
import br.com.specvora_service.vehicle.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class VehicleService {

    private final VehicleRepository vehicleRepository;

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
}
