package br.com.specvora_service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("Verificação da Classe Principal da Aplicação")
class SpecvoraServiceApplicationTests {

    @Test
    @DisplayName("Deve carregar a definição da classe principal da aplicação")
    void applicationClassPresent() {
        assertNotNull(SpecvoraServiceApplication.class);
    }
}
