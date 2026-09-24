package br.com.specvora_service.vehicle.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Estrutura padronizada de resposta para erros da API (RFC 7807 inspired)")
public class ErrorResponseDTO {

    @Schema(description = "Momento exato da ocorrência do erro", example = "2026-09-24T12:00:00Z")
    @Builder.Default
    private Instant timestamp = Instant.now();

    @Schema(description = "Código de status HTTP", example = "400")
    private int status;

    @Schema(description = "Descrição resumida do erro HTTP", example = "Bad Request")
    private String error;

    @Schema(description = "Mensagem detalhada sobre o erro", example = "Dados de entrada inválidos")
    private String message;

    @Schema(description = "URI do recurso acessado", example = "/vehicles")
    private String path;

    @Schema(description = "Mapa de erros específicos por campo (quando aplicável)")
    private Map<String, String> fieldErrors;
}
