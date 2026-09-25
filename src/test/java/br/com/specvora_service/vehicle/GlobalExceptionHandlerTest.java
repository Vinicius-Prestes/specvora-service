package br.com.specvora_service.vehicle;

import br.com.specvora_service.vehicle.exception.ErrorResponseDTO;
import br.com.specvora_service.vehicle.exception.GlobalExceptionHandler;
import br.com.specvora_service.vehicle.exception.ResourceConflictException;
import br.com.specvora_service.vehicle.exception.VehicleNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.http.HttpMethod;

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
    @DisplayName("Deve converter HttpMessageNotReadableException (JSON malformado) para 400 BAD REQUEST e não 500")
    void testHandleUnreadableJsonReturns400() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/vehicles");

        HttpMessageNotReadableException ex = new HttpMessageNotReadableException(
                "JSON parse error", new MockHttpInputMessage("invalid json".getBytes()));

        ResponseEntity<ErrorResponseDTO> response = handler.handleUnreadable(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().getStatus());
        assertEquals("Bad Request", response.getBody().getError());
        assertEquals("Corpo da requisição ausente ou JSON malformado", response.getBody().getMessage());
    }

    @Test
    @DisplayName("Deve converter HttpRequestMethodNotSupportedException (ex: PATCH) para 405 METHOD NOT ALLOWED e não 500")
    void testHandleMethodNotSupportedReturns405() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/vehicles/123");

        HttpRequestMethodNotSupportedException ex = new HttpRequestMethodNotSupportedException("PATCH");

        ResponseEntity<ErrorResponseDTO> response = handler.handleMethodNotSupported(ex, request);

        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(405, response.getBody().getStatus());
        assertEquals("Method Not Allowed", response.getBody().getError());
    }

    @Test
    @DisplayName("Deve converter ResourceConflictException para 409 CONFLICT")
    void testHandleConflictReturns409() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/auth/register");

        ResourceConflictException ex = new ResourceConflictException("Username já em uso");

        ResponseEntity<ErrorResponseDTO> response = handler.handleConflict(ex, request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(409, response.getBody().getStatus());
        assertEquals("Conflict", response.getBody().getError());
        assertEquals("Username já em uso", response.getBody().getMessage());
    }

    @Test
    @DisplayName("Deve converter NoResourceFoundException para 404 NOT FOUND")
    void testHandleNoResourceFoundReturns404() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/naoexiste");

        NoResourceFoundException ex = new NoResourceFoundException(HttpMethod.GET, "/naoexiste", "Recurso não encontrado");

        ResponseEntity<ErrorResponseDTO> response = handler.handleNoResourceFound(ex, request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(404, response.getBody().getStatus());
        assertEquals("Not Found", response.getBody().getError());
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
