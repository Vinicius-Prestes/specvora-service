package br.com.specvora_service.vehicle.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "vehicles")
public class VehicleModel {

    @Id
    private String id;

    private String brand;
    private String model;
    private String version;
    private String engine;
    private String year;
    private String vehicleCategory;

    private Categories categories;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Categories {

        private Map<String, Object> engineAndTransmission;
        private Map<String, Object> wheels;
        private Map<String, Object> connectivity;
        private Map<String, Object> iceLineUp;
        private Map<String, Object> airConditioning;
        private Map<String, Object> safety;
        private Map<String, Object> highTech;
        private Map<String, Object> globalClosing;
        private Map<String, Object> trim;
        private Map<String, Object> sunroof;
        private Map<String, Object> seats;
        private Map<String, Object> lights;

        @JsonProperty("4x4")
        private Map<String, Object> fourByFour;

        private Map<String, Object> others;
        private Map<String, Object> inmetroPbev;
    }
}