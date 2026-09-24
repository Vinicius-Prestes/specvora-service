package br.com.specvora_service.vehicle.repository;

import br.com.specvora_service.vehicle.domain.VehicleModel;
import br.com.specvora_service.vehicle.dto.VehicleRequestDTO;

import java.util.Optional;

public interface VehicleRepositoryCustom {

    Optional<VehicleModel> findVehicle(VehicleRequestDTO dto);
}