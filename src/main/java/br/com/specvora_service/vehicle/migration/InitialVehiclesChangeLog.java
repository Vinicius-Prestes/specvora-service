package br.com.specvora_service.vehicle.migration;

import br.com.specvora_service.vehicle.domain.VehicleModel;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.CompoundIndexDefinition;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;

@ChangeUnit(id = "vehicles-indexes-v1", order = "1", author = "specvora")
public class InitialVehiclesChangeLog {

    @Execution
    public void createIndexes(MongoTemplate mongoTemplate) {
        IndexOperations indexOps = mongoTemplate.indexOps(VehicleModel.class);

        indexOps.createIndex(new Index().on("brand", Sort.Direction.ASC));
        indexOps.createIndex(new Index().on("model", Sort.Direction.ASC));
        indexOps.createIndex(new Index().on("year", Sort.Direction.ASC));

        indexOps.createIndex(
                new CompoundIndexDefinition(
                        new Document("brand", 1)
                                .append("model", 1)
                                .append("year", 1)
                )
        );
    }

    @RollbackExecution
    public void rollback(MongoTemplate mongoTemplate) {
        IndexOperations indexOps = mongoTemplate.indexOps(VehicleModel.class);
        indexOps.dropIndex("brand_1");
        indexOps.dropIndex("model_1");
        indexOps.dropIndex("year_1");
        indexOps.dropIndex("brand_1_model_1_year_1");
    }
}
