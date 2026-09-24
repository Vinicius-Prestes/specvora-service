package br.com.specvora_service.security;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Serviço de Criptografia Local (Data-at-Rest e Dados Sensíveis)
 * Padrão: AES-256-GCM (Authenticated Encryption with Associated Data - AEAD)
 * Garante confidencialidade e autenticidade/integridade contra adulteração de dados.
 */
@Service
public class LocalEncryptionService {

    private static final String ENCRYPTION_ALGORITHM = "AES";
    private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH_BYTES = 12; // 96 bits recomendado pelo NIST
    private static final int GCM_TAG_LENGTH_BITS = 128; // Tag de integridade de 128 bits

    @Value("${security.crypto.aes-secret:specvora-aes-256-local-encryption-key-32b!}")
    private String rawSecret;

    private SecretKey secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    @PostConstruct
    public void init() {
        // Deriva uma chave de exatamente 256 bits (32 bytes) a partir da chave configurada
        byte[] keyBytes = deriveKey256(rawSecret);
        this.secretKey = new SecretKeySpec(keyBytes, ENCRYPTION_ALGORITHM);
    }

    /**
     * Criptografa texto plano utilizando AES-256-GCM com IV randômico seguro.
     * Retorna o IV concatenado com o texto cifrado codificado em Base64.
     */
    public String encrypt(String plainText) {
        if (plainText == null) {
            return null;
        }

        try {
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            // Concatena IV (12 bytes) + CipherText + Tag
            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + cipherText.length);
            byteBuffer.put(iv);
            byteBuffer.put(cipherText);

            return Base64.getEncoder().encodeToString(byteBuffer.array());
        } catch (Exception ex) {
            throw new SecurityException("Falha ao criptografar dados localmente", ex);
        }
    }

    /**
     * Decriptografa dados em Base64, extrai o IV e valida a autenticidade da tag GCM.
     * Caso o texto cifrado tenha sido adulterado, lança exceção de segurança.
     */
    public String decrypt(String encryptedBase64) {
        if (encryptedBase64 == null) {
            return null;
        }

        try {
            byte[] decoded = Base64.getDecoder().decode(encryptedBase64);

            if (decoded.length < GCM_IV_LENGTH_BYTES) {
                throw new IllegalArgumentException("Payload cifrado inválido: tamanho menor que o IV necessário");
            }

            ByteBuffer byteBuffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            byteBuffer.get(iv);

            byte[] cipherText = new byte[byteBuffer.remaining()];
            byteBuffer.get(cipherText);

            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            byte[] plainTextBytes = cipher.doFinal(cipherText);
            return new String(plainTextBytes, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new SecurityException("Falha ao decriptografar dados: integridade violada ou chave incorreta", ex);
        }
    }

    /**
     * Gera Hash criptográfico unidirecional SHA-256 (para anonimização e integridade)
     */
    public String hashSha256(String input) {
        if (input == null) return null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Algoritmo SHA-256 não disponível", ex);
        }
    }

    private byte[] deriveKey256(String input) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            return sha256.digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algoritmo SHA-256 indisponível para derivação de chave", e);
        }
    }
}
