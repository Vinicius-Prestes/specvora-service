package br.com.specvora_service.vehicle.repository;

import br.com.specvora_service.vehicle.domain.VehicleModel;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface VehicleRepository extends
        MongoRepository<VehicleModel, String>,
        VehicleRepositoryCustom {
}