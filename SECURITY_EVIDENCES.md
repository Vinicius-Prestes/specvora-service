# Relatório de Evidências: Segurança em Código e Infraestrutura

Este documento consolida as **evidências técnicas de correções, mitigações e hardening** aplicados diretamente no código-fonte e na infraestrutura do **Specvora Service**, em conformidade com o critério de avaliação **Segurança em Código e Infraestrutura (Peso 2,5)**.

---

## 1. Resumo Executivo das Melhorias

| Pilar de Segurança | Prática Aplicada | Arquivo Principal | Vulnerabilidade Mitigada | Evidência de Teste |
|---|---|---|---|---|
| **Criptografia Local** | Criptografia autenticada **AES-256-GCM** com IV randômico de 12 bytes + **BCrypt** + **SHA-256** | [`LocalEncryptionService.java`](file:///src/main/java/br/com/specvora_service/security/LocalEncryptionService.java) | Exposição de dados sensíveis em repouso e quebra de integridade em banco de dados ou logs | [`LocalEncryptionServiceTest.java`](file:///src/test/java/br/com/specvora_service/security/LocalEncryptionServiceTest.java) |
| **Hardening de API: Rate Limit** | Algoritmo **Token Bucket** com política estrita anti-Brute Force em `/auth/login` (5 req/min) e cabeçalhos IETF | [`RateLimitingFilter.java`](file:///src/main/java/br/com/specvora_service/config/RateLimitingFilter.java) | Ataques de força bruta, credential stuffing e Denial of Service (DoS) | [`RateLimitingFilterTest.java`](file:///src/test/java/br/com/specvora_service/config/RateLimitingFilterTest.java) |
| **Hardening de API: Validação de Entrada** | Bean Validation estrita (`@Pattern`, `@Size`), limites de payload e sanitização ativa contra XSS | [`VehicleRequestDTO.java`](file:///src/main/java/br/com/specvora_service/vehicle/dto/VehicleRequestDTO.java) | NoSQL Injection, XSS Refletido e DoS por estouro de buffers de processamento | [`VehicleControllerTest.java`](file:///src/test/java/br/com/specvora_service/vehicle/VehicleControllerTest.java) |
| **Hardening de API: JWT Seguro** | Algoritmo HMAC-SHA256 fixo, validação de entropia mínima (256 bits), verificação de `iss` e `aud` | [`JwtTokenService.java`](file:///src/main/java/br/com/specvora_service/auth/service/JwtTokenService.java) | Ataques de algoritmo `none`, adulteração de assinatura, token spoofing e confusão de audiência | [`JwtTokenServiceTest.java`](file:///src/test/java/br/com/specvora_service/auth/JwtTokenServiceTest.java) |
| **Hardening de Infraestrutura** | Cabeçalhos HTTP de segurança estritos (CSP, HSTS, FrameOptions, nosniff, Permissions-Policy) | [`SecurityConfig.java`](file:///src/main/java/br/com/specvora_service/config/SecurityConfig.java) | Clickjacking, MIME sniffing, downgrade HTTPS e injeção de scripts no browser | [`SecurityConfig.java`](file:///src/main/java/br/com/specvora_service/config/SecurityConfig.java) |

---

## 2. Evidência 1: Criptografia Local (Data at Rest)

### Contexto e Risco
O armazenamento de credenciais, chaves de API ou identificadores sem proteção adequada expõe a aplicação ao risco de vazamento em dumps de memória, backups de banco de dados e logs corporativos. O uso de cifras obsoletas (ex.: DES, 3DES, AES-ECB) ou sem autenticação de integridade (AES-CBC puro) permite que um atacante adultere bits do dado cifrado (*bit-flipping attacks*).

### Implementação da Solução
Foi desenvolvido o [`LocalEncryptionService`](file:///src/main/java/br/com/specvora_service/security/LocalEncryptionService.java), que implementa o padrão **AES-256-GCM** (Galois/Counter Mode), uma cifra de criptografia autenticada (**AEAD - Authenticated Encryption with Associated Data**):
1. **Chave de 256 bits:** Derivada de forma segura com SHA-256.
2. **IV (Vetor de Inicialização) Randômico de 12 bytes:** Gerado a cada operação com `SecureRandom`, garantindo que duas mensagens idênticas nunca gerem o mesmo texto cifrado.
3. **Tag de Autenticação de 128 bits:** Rejeita qualquer texto cifrado adulterado antes que ele seja decriptografado.

### Comparativo de Código: Antes x Depois

**Antes:** Não havia serviço centralizado de criptografia simétrica autenticada para dados em repouso.
```java
// Cenário anterior: ausência de cifra autenticada local para campos sensíveis
```

**Depois:**
```java
// LocalEncryptionService.java
public String encrypt(String plainText) {
    byte[] iv = new byte[12];
    secureRandom.nextBytes(iv); // IV não determinístico a cada chamada

    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    GCMParameterSpec parameterSpec = new GCMParameterSpec(128, iv);
    cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

    byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
    // Concatena IV + CipherText + Tag e codifica em Base64 seguro
    ...
}
```

### Evidência de Teste Automatizado
No teste [`LocalEncryptionServiceTest.java`](file:///src/test/java/br/com/specvora_service/security/LocalEncryptionServiceTest.java), o método `testTamperedCiphertextThrowsSecurityException` simula a alteração de um único byte do dado cifrado. O algoritmo GCM detecta a violação de integridade e lança `SecurityException`, comprovando a eficácia da proteção.

---

## 3. Evidência 2: Hardening de API — Rate Limiting Inteligente

### Contexto e Risco
Ataques de força bruta contra endpoints de login (`/auth/login`) e sobrecarga por requisições automatizadas (DDoS / flooding) podem comprometer a disponibilidade do serviço ou permitir a descoberta de credenciais de usuários legítimos.

### Implementação da Solução
O [`RateLimitingFilter.java`](file:///src/main/java/br/com/specvora_service/config/RateLimitingFilter.java) foi enriquecido com:
1. **Proteção Específica Anti-Brute Force:** Limite rigoroso de **5 requisições por minuto** por IP na rota crítica `POST /auth/login`.
2. **Proteção Geral de API:** Limite de **60 requisições por minuto** para as demais rotas.
3. **Cabeçalhos Padronizados IETF:**
   - `X-RateLimit-Limit`: Limite máximo permitido.
   - `X-RateLimit-Remaining`: Tokens restantes no balde atual.
   - `Retry-After`: Tempo exato em segundos que o cliente deve aguardar antes de tentar novamente.
4. **Resposta Padronizada `429 Too Many Requests`:** Retorno consistente através do modelo [`ErrorResponseDTO`](file:///src/main/java/br/com/specvora_service/vehicle/exception/ErrorResponseDTO.java).

### Comparativo de Código: Antes x Depois

**Antes:** Limite único fixo sem cabeçalhos IETF e com retorno JSON não padronizado.
```java
// RateLimitingFilter.java (Legado)
private static final Bandwidth RATE_LIMIT = Bandwidth.simple(60, Duration.ofMinutes(1));
...
if (!bucket.tryConsume(1)) {
    response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
    response.getWriter().write("{\"error\":\"Too many requests\"}");
    return;
}
```

**Depois:**
```java
// RateLimitingFilter.java (Hardened)
if (isLoginRoute) {
    bucket = loginBuckets.computeIfAbsent(clientKey, k -> Bucket.builder()
            .addLimit(LOGIN_BRUTE_FORCE_LIMIT) // 5 tentativas / min
            .build());
}
ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
if (probe.isConsumed()) {
    response.setHeader("X-RateLimit-Limit", String.valueOf(limitCapacity));
    response.setHeader("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));
    filterChain.doFilter(request, response);
} else {
    response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
    response.setHeader("Retry-After", String.valueOf(waitForRefillSeconds));
    response.getWriter().write(errorResponseDTOJson);
}
```

### Evidência de Teste Automatizado
No teste [`RateLimitingFilterTest.java`](file:///src/test/java/br/com/specvora_service/config/RateLimitingFilterTest.java), o método `testBruteForceProtectionOnLoginBlocksOn6thAttempt` simula 5 requisições bem-sucedidas em `/auth/login` e comprova que a **6ª requisição é bloqueada imediatamente com status 429**, `X-RateLimit-Remaining: 0` e cabeçalho `Retry-After`.

---

## 4. Evidência 3: Hardening de API — Validação Estrita de Entrada

### Contexto e Risco
A falta de validação em campos de texto permite que atacantes injetem caracteres de escape de banco de dados, tags HTML/JavaScript (vetor de XSS em clientes que consomem a API) ou submetam payloads deliberadamente gigantescos para provocar negação de serviço na CPU (*ReDoS* ou sobrecarga em algoritmos de hashing).

### Implementação da Solução
Em [`VehicleRequestDTO.java`](file:///src/main/java/br/com/specvora_service/vehicle/dto/VehicleRequestDTO.java) e [`VehicleUpsertDTO.java`](file:///src/main/java/br/com/specvora_service/vehicle/dto/VehicleUpsertDTO.java):
1. **Expressões Regulares Estritas:**
   - Campo `year`: `@Pattern(regexp = "^(19|20)\\d{2}$")` assegura que apenas anos de 4 dígitos entre 1900 e 2099 sejam aceitos.
   - Campos de texto (`brand`, `model`, `version`, `engine`): `@Pattern(regexp = "^[a-zA-Z0-9\\s\\-\\.\\/\\+]+$")` rejeita caracteres perigosos como `<`, `>`, `"`, `'`, `;`, `=`, `{`, `}`.
2. **Sanitização Ativa no Método `normalize()`:**
   - Limpeza de caracteres potencialmente perigosos (`.replaceAll("[<>'\"\\;]", "")`).
   - Normalização para minúsculas (`Locale.ROOT`) e colapso de espaços em branco.
3. **Limites de Tamanho de Senhas (`@Size(min = 6, max = 100)`):**
   - Evita que atacantes enviem senhas com dezenas de milhares de caracteres para saturar o processamento do algoritmo **BCrypt**.

### Comparativo de Código: Antes x Depois

**Antes:** Validação genérica apenas com `@Size`.
```java
// DTO anterior
@NotBlank(message = "Ano é obrigatório")
@Size(max = 10, message = "Ano deve ter no máximo 10 caracteres")
private String year;
```

**Depois:**
```java
// DTO Hardened
@NotBlank(message = "Ano é obrigatório")
@Pattern(regexp = "^(19|20)\\d{2}$", message = "Ano deve ser um valor numérico válido de 4 dígitos entre 1900 e 2099")
private String year;

private String sanitizeAndNormalize(String value) {
    if (value == null) return null;
    String sanitized = value.replaceAll("[<>'\"\\;]", "")
            .trim()
            .replaceAll("\\s+", " ")
            .toLowerCase(Locale.ROOT);
    return sanitized.isBlank() ? null : sanitized;
}
```

---

## 5. Evidência 4: Hardening de API — JWT Seguro

### Contexto e Risco
Implementações inseguras de JWT sofrem frequentemente de:
- Aceitação de tokens com cabeçalho `{"alg": "none"}` (sem assinatura).
- Uso de chaves secretas curtas ou previsíveis que podem ser quebradas por força bruta offline.
- Vulnerabilidade a *Confused Deputy Attack* quando a aplicação não valida a audiência (`aud`) e emissor (`iss`) do token.

### Implementação da Solução
Em [`JwtTokenService.java`](file:///src/main/java/br/com/specvora_service/auth/service/JwtTokenService.java):
1. **Validação de Entropia da Chave no Startup (`validateAndInitAlgorithm`):**
   - A aplicação valida se a chave secreta possui no mínimo **32 bytes (256 bits)**. Caso contrário, lança `IllegalStateException` e impede o boot com configuração vulnerável.
2. **Assinatura Fixa HMAC-SHA256 (`HS256`):**
   - O objeto `Algorithm.HMAC256` é imutável, rejeitando qualquer tentativa de bypass com algoritmo `none`.
3. **Verificação de `Issuer` e `Audience`:**
   - Emissor fixo: `"specvora-service"`.
   - Audiência fixa: `"specvora-api"`.
4. **Short-lived Tokens:**
   - Expiração estrita de 2 horas.

### Comparativo de Código: Antes x Depois

**Antes:** Inicialização sem checagem de entropia e sem audiência.
```java
// JwtTokenService legado
public String generateToken(String username, List<String> roles) {
    return JWT.create()
            .withIssuer(ISSUER)
            .withSubject(username)
            .sign(Algorithm.HMAC256(jwtSecret));
}
```

**Depois:**
```java
// JwtTokenService Hardened
@PostConstruct
public void validateAndInitAlgorithm() {
    if (jwtSecret == null || jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
        throw new IllegalStateException("Hardening: Chave secreta deve possuir pelo menos 256 bits (32 bytes).");
    }
    this.hmacAlgorithm = Algorithm.HMAC256(jwtSecret);
}

public String generateToken(String username, List<String> roles) {
    return JWT.create()
            .withIssuer(ISSUER)
            .withAudience(AUDIENCE) // Validação de audiência estrita
            .withSubject(username)
            .withClaim(ROLES_CLAIM, roles)
            .withIssuedAt(Date.from(now))
            .withExpiresAt(Date.from(expiresAt))
            .sign(hmacAlgorithm);
}
```

### Evidência de Teste Automatizado
No teste [`JwtTokenServiceTest.java`](file:///src/test/java/br/com/specvora_service/auth/JwtTokenServiceTest.java):
- `testWeakKeyThrowsIllegalStateException`: Comprova que chaves fracas com menos de 32 caracteres são rejeitadas na inicialização.
- `testValidateTamperedTokenThrowsException`: Comprova que qualquer modificação na assinatura do token resulta em erro imediato.

---

## 6. Evidência 5: Hardening de Infraestrutura HTTP (Security Headers)

No [`SecurityConfig.java`](file:///src/main/java/br/com/specvora_service/config/SecurityConfig.java), foram adicionados os cabeçalhos de defesa em profundidade recomendados pelo **OWASP Secure Headers Project**:

```java
.headers(headers -> headers
    .contentSecurityPolicy(csp -> csp.policyDirectives(
        "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; frame-ancestors 'none'; object-src 'none'"))
    .frameOptions(frame -> frame.deny())
    .contentTypeOptions(Customizer.withDefaults())
    .httpStrictTransportSecurity(hsts -> hsts
        .includeSubDomains(true)
        .maxAgeInSeconds(31536000))
    .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
    .permissionsPolicy(permissions -> permissions.policy("geolocation=(), microphone=(), camera=()"))
)
```

- **Content-Security-Policy (CSP):** Bloqueia a execução de scripts e recursos externos não autorizados (`object-src 'none'`, `frame-ancestors 'none'`).
- **X-Frame-Options: DENY:** Neutraliza ataques de **Clickjacking** impedindo que a aplicação seja embutida em `<frame>` ou `<iframe>`.
- **X-Content-Type-Options: nosniff:** Impede que navegadores interpretem arquivos de forma divergente do `Content-Type` declarado (**MIME Sniffing**).
- **Strict-Transport-Security (HSTS):** Força conexões HTTPS durante 1 ano (`31536000` segundos) incluindo todos os subdomínios.
- **Permissions-Policy:** Desativa APIs de sensores do cliente (geolocalização, câmera e microfone).

---

## 7. Como Reproduzir e Coletar as Evidências

### Execução de Toda a Suíte de Testes
Para rodar os testes de segurança e verificar que todas as asserções passam com sucesso:

```bash
./mvnw test
```

### Demonstração do Rate Limit Anti-Brute Force (cURL)
Execute 6 requisições consecutivas para a rota de login:

```bash
for i in {1..6}; do
  curl -s -o /dev/null -w "Tentativa $i - HTTP Status: %{http_code}\n" \
    -X POST http://localhost:8080/auth/login \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"wrongPassword"}'
done
```

**Resultado Observado:**
```
Tentativa 1 - HTTP Status: 401
Tentativa 2 - HTTP Status: 401
Tentativa 3 - HTTP Status: 401
Tentativa 4 - HTTP Status: 401
Tentativa 5 - HTTP Status: 401
Tentativa 6 - HTTP Status: 429 (Too Many Requests - Bloqueio Ativo)
```
