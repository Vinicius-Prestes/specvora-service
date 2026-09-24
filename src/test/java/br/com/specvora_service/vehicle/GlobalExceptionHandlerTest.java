package br.com.specvora_service.vehicle;

import br.com.specvora_service.vehicle.exception.ErrorResponseDTO;
import br.com.specvora_service.vehicle.exception.GlobalExceptionHandler;
import br.com.specvora_service.vehicle.exception.VehicleNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("Testes do Tratamento Global de Erros - GlobalExceptionHandler")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("Deve converter VehicleNotFoundException para 404 NOT FOUND padronizado")
    void testHandleVehicleNotFound() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/vehicles/123");

        ResponseEntity<ErrorResponseDTO> response = handler.handleVehicleNotFound(
                new VehicleNotFoundException("Veículo 123 não encontrado"), request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(404, response.getBody().getStatus());
        assertEquals("Not Found", response.getBody().getError());
        assertEquals("Veículo 123 não encontrado", response.getBody().getMessage());
        assertEquals("/vehicles/123", response.getBody().getPath());
    }

    @Test
    @DisplayName("Deve converter AccessDeniedException para 403 FORBIDDEN")
    void testHandleAccessDenied() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/vehicles");

        ResponseEntity<ErrorResponseDTO> response = handler.handleAccessDenied(
                new AccessDeniedException("Acesso negado"), request);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(403, response.getBody().getStatus());
        assertEquals("Forbidden", response.getBody().getError());
    }

    @Test
    @DisplayName("Deve converter BadCredentialsException para 401 UNAUTHORIZED")
    void testHandleBadCredentials() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/auth/login");

        ResponseEntity<ErrorResponseDTO> response = handler.handleBadCredentials(
                new BadCredentialsException("Credenciais inválidas"), request);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(401, response.getBody().getStatus());
        assertEquals("Unauthorized", response.getBody().getError());
    }

    @Test
    @DisplayName("Deve converter exceção genérica para 500 INTERNAL SERVER ERROR sem vazar stacktrace")
    void testHandleGenericException() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/vehicles");

        ResponseEntity<ErrorResponseDTO> response = handler.handleGeneric(
                new RuntimeException("Falha crítica no sistema interno"), request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(500, response.getBody().getStatus());
        assertEquals("Internal Server Error", response.getBody().getError());
        assertEquals("Erro interno no servidor", response.getBody().getMessage());
    }
}
