package br.com.specvora_service.vehicle.controller;

import br.com.specvora_service.vehicle.domain.VehicleModel;
import br.com.specvora_service.vehicle.dto.VehicleRequestDTO;
import br.com.specvora_service.vehicle.dto.VehicleUpsertDTO;
import br.com.specvora_service.vehicle.exception.ErrorResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

@Tag(name = "Veículos", description = "Gerenciamento completo (CRUD) e busca de especificações técnicas de veículos")
@RequestMapping("/vehicles")
@SecurityRequirement(name = "bearerAuth")
public interface VehicleApi {

    @Operation(summary = "Retorna todos os veículos cadastrados (Requer ROLE_USER ou ROLE_ADMIN)",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Lista de veículos recuperada com sucesso"),
                    @ApiResponse(responseCode = "401", description = "Não autenticado / Token ausente ou inválido",
                            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
            })
    @GetMapping
    ResponseEntity<List<VehicleModel>> findAll();

    @Operation(summary = "Busca veículo pelo identificador único (Requer ROLE_USER ou ROLE_ADMIN)",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Veículo encontrado"),
                    @ApiResponse(responseCode = "401", description = "Não autenticado",
                            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
                    @ApiResponse(responseCode = "404", description = "Veículo não encontrado",
                            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
            })
    @GetMapping("/{id}")
    ResponseEntity<VehicleModel> findById(@PathVariable String id);

    @Operation(summary = "Busca veículo por especificações técnicas (Requer ROLE_USER ou ROLE_ADMIN)",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Veículo encontrado"),
                    @ApiResponse(responseCode = "400", description = "Dados de entrada inválidos",
                            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
                    @ApiResponse(responseCode = "401", description = "Não autenticado",
                            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
                    @ApiResponse(responseCode = "404", description = "Veículo não encontrado",
                            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
            })
    @PostMapping("/search")
    ResponseEntity<VehicleModel> findVehicle(@Valid @RequestBody VehicleRequestDTO dto);

    @Operation(summary = "Cadastra um novo veículo (Exclusivo ROLE_ADMIN)",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Veículo criado com sucesso (Header Location presente)"),
                    @ApiResponse(responseCode = "400", description = "Campos obrigatórios ausentes ou inválidos",
                            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
                    @ApiResponse(responseCode = "401", description = "Não autenticado",
                            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
                    @ApiResponse(responseCode = "403", description = "Acesso proibido: apenas administradores podem cadastrar veículos",
                            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
            })
    @PostMapping
    ResponseEntity<VehicleModel> createVehicle(@Valid @RequestBody VehicleUpsertDTO dto, UriComponentsBuilder uriBuilder);

    @Operation(summary = "Atualiza os dados de um veículo existente (Exclusivo ROLE_ADMIN)",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Veículo atualizado com sucesso"),
                    @ApiResponse(responseCode = "400", description = "Dados inválidos",
                            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
                    @ApiResponse(responseCode = "401", description = "Não autenticado",
                            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
                    @ApiResponse(responseCode = "403", description = "Acesso proibido: apenas administradores",
                            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
                    @ApiResponse(responseCode = "404", description = "Veículo não encontrado",
                            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
            })
    @PutMapping("/{id}")
    ResponseEntity<VehicleModel> updateVehicle(@PathVariable String id, @Valid @RequestBody VehicleUpsertDTO dto);

    @Operation(summary = "Remove um veículo pelo identificador (Exclusivo ROLE_ADMIN)",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Veículo excluído com sucesso (Sem conteúdo)"),
                    @ApiResponse(responseCode = "401", description = "Não autenticado",
                            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
                    @ApiResponse(responseCode = "403", description = "Acesso proibido: apenas administradores",
                            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
                    @ApiResponse(responseCode = "404", description = "Veículo não encontrado",
                            content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
            })
    @DeleteMapping("/{id}")
    ResponseEntity<Void> deleteVehicle(@PathVariable String id);
}
