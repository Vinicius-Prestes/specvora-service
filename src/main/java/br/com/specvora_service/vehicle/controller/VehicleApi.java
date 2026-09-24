package br.com.specvora_service.vehicle.controller;

import br.com.specvora_service.vehicle.domain.VehicleModel;
import br.com.specvora_service.vehicle.dto.VehicleRequestDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Vehicles", description = "Gerenciamento de especificações técnicas de veículos")
@RequestMapping("/vehicles")
public interface VehicleApi {

    @Operation(summary = "Busca veículos por especificações",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Veículo encontrado"),
                    @ApiResponse(responseCode = "400", description = "Dados inválidos"),
                    @ApiResponse(responseCode = "404", description = "Veículo não encontrado")
            })
    @PostMapping("/search")
    ResponseEntity<VehicleModel> findVehicle(@Valid @RequestBody VehicleRequestDTO dto);

    @Operation(summary = "Retorna todos os veículos cadastrados",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Lista de veículos")
            })
    @GetMapping
    ResponseEntity<List<VehicleModel>> findAll();

    @Operation(summary = "Busca veículo por ID",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Veículo encontrado"),
                    @ApiResponse(responseCode = "404", description = "Veículo não encontrado")
            })
    @GetMapping("/{id}")
    ResponseEntity<VehicleModel> findById(@PathVariable String id);
}
