package br.com.specvora_service.vehicle;

import br.com.specvora_service.vehicle.domain.VehicleModel;
import br.com.specvora_service.vehicle.dto.VehicleRequestDTO;
import br.com.specvora_service.vehicle.dto.VehicleUpsertDTO;
import br.com.specvora_service.vehicle.exception.VehicleNotFoundException;
import br.com.specvora_service.vehicle.repository.VehicleRepository;
import br.com.specvora_service.vehicle.service.VehicleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Testes de Unidade - VehicleService")
class VehicleServiceTest {

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private br.com.specvora_service.security.SecurityAuditLogger securityAuditLogger;

    @InjectMocks
    private VehicleService vehicleService;

    private VehicleModel sampleVehicle;

    @BeforeEach
    void setUp() {
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
    @DisplayName("Deve retornar todos os veículos com sucesso")
    void testFindAllSuccess() {
        when(vehicleRepository.findAll()).thenReturn(List.of(sampleVehicle));

        List<VehicleModel> result = vehicleService.findAll();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("ford", result.get(0).getBrand());
        verify(vehicleRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("Deve buscar veículo por ID com sucesso quando existente")
    void testFindByIdSuccess() {
        when(vehicleRepository.findById("65f1a2b3c4d5e6f7a8b9c0d1"))
                .thenReturn(Optional.of(sampleVehicle));

        VehicleModel result = vehicleService.findById("65f1a2b3c4d5e6f7a8b9c0d1");

        assertNotNull(result);
        assertEquals("65f1a2b3c4d5e6f7a8b9c0d1", result.getId());
        assertEquals("nova ranger 4x4", result.getModel());
    }

    @Test
    @DisplayName("Deve lançar VehicleNotFoundException quando ID não existir")
    void testFindByIdNotFoundThrowsException() {
        when(vehicleRepository.findById("inexistente")).thenReturn(Optional.empty());

        assertThrows(VehicleNotFoundException.class, () -> vehicleService.findById("inexistente"));
    }

    @Test
    @DisplayName("Deve buscar veículo por especificações com dados normalizados")
    void testFindVehicleSuccess() {
        VehicleRequestDTO dto = new VehicleRequestDTO();
        dto.setBrand("  FORD  ");
        dto.setModel("Nova Ranger 4x4");
        dto.setVersion("XLT");
        dto.setEngine("3.0 V6 - 24V");
        dto.setYear("2026");

        when(vehicleRepository.findVehicle(any(VehicleRequestDTO.class)))
                .thenReturn(Optional.of(sampleVehicle));

        VehicleModel result = vehicleService.findVehicle(dto);

        assertNotNull(result);
        assertEquals("ford", dto.getBrand()); // Verificação da normalização
        assertEquals("nova ranger 4x4", result.getModel());
    }

    @Test
    @DisplayName("Deve lançar VehicleNotFoundException quando busca por especificações não retornar resultado")
    void testFindVehicleNotFoundThrowsException() {
        VehicleRequestDTO dto = new VehicleRequestDTO();
        dto.setBrand("marca");
        dto.setModel("modelo");
        dto.setVersion("versao");
        dto.setEngine("motor");
        dto.setYear("2020");

        when(vehicleRepository.findVehicle(any(VehicleRequestDTO.class)))
                .thenReturn(Optional.empty());

        assertThrows(VehicleNotFoundException.class, () -> vehicleService.findVehicle(dto));
    }

    @Test
    @DisplayName("Deve criar um novo veículo com sucesso (persistência e normalização)")
    void testCreateVehicleSuccess() {
        VehicleUpsertDTO dto = VehicleUpsertDTO.builder()
                .brand("  Toyota ")
                .model("Corolla Cross ")
                .version("XRX ")
                .engine("2.0 Dynamic Force")
                .year("2025")
                .vehicleCategory("SUV")
                .build();

        when(vehicleRepository.save(any(VehicleModel.class)))
                .thenAnswer(invocation -> {
                    VehicleModel model = invocation.getArgument(0);
                    model.setId("generated-mongo-id-123");
                    return model;
                });

        VehicleModel created = vehicleService.createVehicle(dto);

        assertNotNull(created);
        assertEquals("generated-mongo-id-123", created.getId());
        assertEquals("toyota", created.getBrand());
        assertEquals("corolla cross", created.getModel());
        verify(vehicleRepository, times(1)).save(any(VehicleModel.class));
    }

    @Test
    @DisplayName("Deve atualizar veículo existente com sucesso")
    void testUpdateVehicleSuccess() {
        when(vehicleRepository.findById("65f1a2b3c4d5e6f7a8b9c0d1"))
                .thenReturn(Optional.of(sampleVehicle));
        when(vehicleRepository.save(any(VehicleModel.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        VehicleUpsertDTO updateDto = VehicleUpsertDTO.builder()
                .brand("Ford")
                .model("Nova Ranger 4x4")
                .version("Limited V6")
                .engine("3.0 V6 - 24V")
                .year("2026")
                .vehicleCategory("picape")
                .build();

        VehicleModel updated = vehicleService.updateVehicle("65f1a2b3c4d5e6f7a8b9c0d1", updateDto);

        assertNotNull(updated);
        assertEquals("limited v6", updated.getVersion());
        verify(vehicleRepository, times(1)).save(sampleVehicle);
    }

    @Test
    @DisplayName("Deve lançar VehicleNotFoundException ao tentar atualizar veículo inexistente")
    void testUpdateVehicleNotFoundThrowsException() {
        when(vehicleRepository.findById("inexistente")).thenReturn(Optional.empty());

        VehicleUpsertDTO dto = VehicleUpsertDTO.builder().brand("Ford").build();

        assertThrows(VehicleNotFoundException.class, () -> vehicleService.updateVehicle("inexistente", dto));
        verify(vehicleRepository, never()).save(any());
    }

    @Test
    @DisplayName("Deve deletar veículo existente com sucesso")
    void testDeleteVehicleSuccess() {
        when(vehicleRepository.existsById("65f1a2b3c4d5e6f7a8b9c0d1")).thenReturn(true);
        doNothing().when(vehicleRepository).deleteById("65f1a2b3c4d5e6f7a8b9c0d1");

        assertDoesNotThrow(() -> vehicleService.deleteVehicle("65f1a2b3c4d5e6f7a8b9c0d1"));
        verify(vehicleRepository, times(1)).deleteById("65f1a2b3c4d5e6f7a8b9c0d1");
    }

    @Test
    @DisplayName("Deve lançar VehicleNotFoundException ao tentar deletar veículo inexistente")
    void testDeleteVehicleNotFoundThrowsException() {
        when(vehicleRepository.existsById("inexistente")).thenReturn(false);

        assertThrows(VehicleNotFoundException.class, () -> vehicleService.deleteVehicle("inexistente"));
        verify(vehicleRepository, never()).deleteById(anyString());
    }
}
