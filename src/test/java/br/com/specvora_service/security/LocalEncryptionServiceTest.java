package br.com.specvora_service.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Testes de Criptografia Local - AES-256-GCM")
class LocalEncryptionServiceTest {

    private LocalEncryptionService encryptionService;

    @BeforeEach
    void setUp() {
        encryptionService = new LocalEncryptionService();
        ReflectionTestUtils.setField(encryptionService, "rawSecret", "chave-secreta-de-teste-local-aes-256");
        encryptionService.init();
    }

    @Test
    @DisplayName("Deve criptografar e decriptografar com sucesso preservando integridade (AES-256-GCM)")
    void testEncryptAndDecryptSuccess() {
        String sensitiveData = "API_KEY_OR_FIREBASE_SECRET_CREDENTIAL_12345";

        String encrypted = encryptionService.encrypt(sensitiveData);
        assertNotNull(encrypted);
        assertNotEquals(sensitiveData, encrypted);

        String decrypted = encryptionService.decrypt(encrypted);
        assertEquals(sensitiveData, decrypted);
    }

    @Test
    @DisplayName("Criptografar o mesmo texto duas vezes deve gerar ciphertexts distintos devido ao IV randômico")
    void testRandomIvProducesDifferentCiphertexts() {
        String data = "dado_confidencial";

        String encrypted1 = encryptionService.encrypt(data);
        String encrypted2 = encryptionService.encrypt(data);

        assertNotEquals(encrypted1, encrypted2, "Ciphertexts devem ser diferentes para impedir ataques de dicionário");
        assertEquals(data, encryptionService.decrypt(encrypted1));
        assertEquals(data, encryptionService.decrypt(encrypted2));
    }

    @Test
    @DisplayName("Deve rejeitar texto cifrado adulterado (GCM Authentication Tag rejeita violação de integridade)")
    void testTamperedCiphertextThrowsSecurityException() {
        String sensitiveData = "dados-imutaveis-confidenciais";
        String encrypted = encryptionService.encrypt(sensitiveData);

        // Adulterar um byte do payload Base64 cifrado
        byte[] decoded = Base64.getDecoder().decode(encrypted);
        decoded[decoded.length - 1] ^= 0xFF; // Inverte bits da tag de autenticação
        String tampered = Base64.getEncoder().encodeToString(decoded);

        assertThrows(SecurityException.class, () -> encryptionService.decrypt(tampered));
    }

    @Test
    @DisplayName("Deve gerar hash determinístico SHA-256 correto")
    void testHashSha256() {
        String input = "user_uid_123456";
        String hash1 = encryptionService.hashSha256(input);
        String hash2 = encryptionService.hashSha256(input);

        assertNotNull(hash1);
        assertEquals(64, hash1.length()); // SHA-256 tem 64 caracteres hexadecimais
        assertEquals(hash1, hash2, "Mesmo input deve gerar o mesmo hash determinístico");
    }

    @Test
    @DisplayName("Deve tratar valores nulos com segurança")
    void testNullHandling() {
        assertNull(encryptionService.encrypt(null));
        assertNull(encryptionService.decrypt(null));
        assertNull(encryptionService.hashSha256(null));
    }
}
