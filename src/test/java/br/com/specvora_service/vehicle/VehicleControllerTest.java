package br.com.specvora_service.vehicle;

import br.com.specvora_service.vehicle.controller.VehicleController;
import br.com.specvora_service.vehicle.domain.VehicleModel;
import br.com.specvora_service.vehicle.dto.VehicleRequestDTO;
import br.com.specvora_service.vehicle.dto.VehicleUpsertDTO;
import br.com.specvora_service.vehicle.exception.GlobalExceptionHandler;
import br.com.specvora_service.vehicle.exception.VehicleNotFoundException;
import br.com.specvora_service.vehicle.service.VehicleService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Testes de Controller REST Nível 2 - VehicleController")
class VehicleControllerTest {

    private MockMvc mockMvc;

    @Mock
    private VehicleService vehicleService;

    @InjectMocks
    private VehicleController vehicleController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private VehicleModel sampleVehicle;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(vehicleController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        sampleVehicle = VehicleModel.builder()
                .id("65f1a2b3c4d5e6f7a8b9c0d1")
                .brand("ford")
                .model("nova ranger 4x4")
                .version("xlt")
                .engine("3.0 v6 - 24v")
                .year("2026")
                .vehicleCategory("picape")
                .build();
    }

    @Test
    @DisplayName("GET /vehicles - Deve retornar 200 OK com lista de veículos")
    void testFindAllReturns200() throws Exception {
        when(vehicleService.findAll()).thenReturn(List.of(sampleVehicle));

        mockMvc.perform(get("/vehicles"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].brand", is("ford")))
                .andExpect(jsonPath("$[0].model", is("nova ranger 4x4")));
    }

    @Test
    @DisplayName("GET /vehicles/{id} - Deve retornar 200 OK quando veículo for encontrado")
    void testFindByIdReturns200() throws Exception {
        when(vehicleService.findById("65f1a2b3c4d5e6f7a8b9c0d1")).thenReturn(sampleVehicle);

        mockMvc.perform(get("/vehicles/65f1a2b3c4d5e6f7a8b9c0d1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is("65f1a2b3c4d5e6f7a8b9c0d1")))
                .andExpect(jsonPath("$.brand", is("ford")));
    }

    @Test
    @DisplayName("GET /vehicles/{id} - Deve retornar 404 NOT FOUND com ErrorResponseDTO padronizado quando inexistente")
    void testFindByIdReturns404() throws Exception {
        when(vehicleService.findById("inexistente"))
                .thenThrow(new VehicleNotFoundException("Veículo não encontrado com id: inexistente"));

        mockMvc.perform(get("/vehicles/inexistente"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.error", is("Not Found")))
                .andExpect(jsonPath("$.message", containsString("inexistente")))
                .andExpect(jsonPath("$.timestamp", notNullValue()));
    }

    @Test
    @DisplayName("POST /vehicles/search - Deve retornar 200 OK com veículo correspondente")
    void testFindVehicleSearchReturns200() throws Exception {
        when(vehicleService.findVehicle(any(VehicleRequestDTO.class))).thenReturn(sampleVehicle);

        VehicleRequestDTO request = new VehicleRequestDTO();
        request.setBrand("ford");
        request.setModel("nova ranger 4x4");
        request.setVersion("xlt");
        request.setEngine("3.0 v6 - 24v");
        request.setYear("2026");

        mockMvc.perform(post("/vehicles/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.brand", is("ford")));
    }

    @Test
    @DisplayName("POST /vehicles/search - Deve retornar 400 BAD REQUEST quando validação de campos falhar")
    void testFindVehicleSearchValidationFailsReturns400() throws Exception {
        VehicleRequestDTO invalidRequest = new VehicleRequestDTO(); // Campos obrigatórios ausentes

        mockMvc.perform(post("/vehicles/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.fieldErrors", notNullValue()));
    }

    @Test
    @DisplayName("POST /vehicles - Deve retornar 201 CREATED com header Location e corpo do recurso criado")
    void testCreateVehicleReturns201() throws Exception {
        when(vehicleService.createVehicle(any(VehicleUpsertDTO.class))).thenReturn(sampleVehicle);

        VehicleUpsertDTO createDto = VehicleUpsertDTO.builder()
                .brand("Ford")
                .model("Nova Ranger 4x4")
                .version("XLT")
                .engine("3.0 V6 - 24V")
                .year("2026")
                .vehicleCategory("picape")
                .build();

        mockMvc.perform(post("/vehicles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDto)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/vehicles/65f1a2b3c4d5e6f7a8b9c0d1")))
                .andExpect(jsonPath("$.id", is("65f1a2b3c4d5e6f7a8b9c0d1")))
                .andExpect(jsonPath("$.brand", is("ford")));
    }

    @Test
    @DisplayName("PUT /vehicles/{id} - Deve retornar 200 OK com recurso atualizado")
    void testUpdateVehicleReturns200() throws Exception {
        when(vehicleService.updateVehicle(eq("65f1a2b3c4d5e6f7a8b9c0d1"), any(VehicleUpsertDTO.class)))
                .thenReturn(sampleVehicle);

        VehicleUpsertDTO updateDto = VehicleUpsertDTO.builder()
                .brand("Ford")
                .model("Nova Ranger 4x4")
                .version("XLT")
                .engine("3.0 V6 - 24V")
                .year("2026")
                .vehicleCategory("picape")
                .build();

        mockMvc.perform(put("/vehicles/65f1a2b3c4d5e6f7a8b9c0d1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is("65f1a2b3c4d5e6f7a8b9c0d1")));
    }

    @Test
    @DisplayName("DELETE /vehicles/{id} - Deve retornar 204 NO CONTENT após remoção com sucesso")
    void testDeleteVehicleReturns204() throws Exception {
        doNothing().when(vehicleService).deleteVehicle("65f1a2b3c4d5e6f7a8b9c0d1");

        mockMvc.perform(delete("/vehicles/65f1a2b3c4d5e6f7a8b9c0d1"))
                .andExpect(status().isNoContent());

        verify(vehicleService, times(1)).deleteVehicle("65f1a2b3c4d5e6f7a8b9c0d1");
    }
}
