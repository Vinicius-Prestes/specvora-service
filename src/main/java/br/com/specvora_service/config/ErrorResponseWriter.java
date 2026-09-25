package br.com.specvora_service.config;

import br.com.specvora_service.vehicle.exception.ErrorResponseDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;

@Component
public class ErrorResponseWriter {

    private final ObjectMapper objectMapper;

    public ErrorResponseWriter() {
        this(new ObjectMapper());
    }

    public ErrorResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy().findAndRegisterModules();
    }

    public void write(HttpServletResponse response, HttpServletRequest request, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ErrorResponseDTO error = ErrorResponseDTO.builder()
                .timestamp(Instant.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(request.getRequestURI())
                .build();

        objectMapper.writeValue(response.getWriter(), error);
    }
}
