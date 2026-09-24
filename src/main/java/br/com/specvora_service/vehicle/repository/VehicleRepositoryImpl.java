package br.com.specvora_service.vehicle.repository;

import br.com.specvora_service.vehicle.domain.VehicleModel;
import br.com.specvora_service.vehicle.dto.VehicleRequestDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class VehicleRepositoryImpl
        implements VehicleRepositoryCustom {

    private final MongoTemplate mongoTemplate;

    @Override
    public Optional<VehicleModel> findVehicle(VehicleRequestDTO dto) {

        Query query = new Query();

        query.addCriteria(
                Criteria.where("brand").is(dto.getBrand().toLowerCase())
                        .and("model").is(dto.getModel().toLowerCase())
                        .and("version").is(dto.getVersion().toLowerCase())
                        .and("engine").is(dto.getEngine().toLowerCase())
                        .and("year").is(dto.getYear().toLowerCase())
        );

        VehicleModel vehicle =
                mongoTemplate.findOne(query, VehicleModel.class);

        return Optional.ofNullable(vehicle);
    }
}