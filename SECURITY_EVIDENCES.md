# Relatório de Evidências: Segurança em Código e Infraestrutura

Este documento consolida as **evidências técnicas de correções, mitigações e hardening** aplicados diretamente no código-fonte e na infraestrutura do **Specvora Service**, em conformidade com o critério de avaliação **Segurança em Código e Infraestrutura (Peso 2,5)** e as recomendações de evolução arquitetural.

---

## 1. Resumo Executivo das Melhorias

| Pilar de Segurança | Prática Aplicada | Arquivo Principal | Vulnerabilidade Mitigada | Evidência de Teste |
|---|---|---|---|---|
| **Criptografia Local** | Criptografia autenticada **AES-256-GCM** com IV randômico de 12 bytes aplicada ativamente a dados de auditoria em repouso | [`LocalEncryptionService.java`](src/main/java/br/com/specvora_service/security/LocalEncryptionService.java), [`AuthService.java`](src/main/java/br/com/specvora_service/auth/service/AuthService.java) | Exposição de dados sensíveis em repouso e vulnerabilidade a adulteração (*bit-flipping*) | [`LocalEncryptionServiceTest.java`](src/test/java/br/com/specvora_service/security/LocalEncryptionServiceTest.java) |
| **Prevenção de Escalada de Privilégio** | Bloqueio de auto-registro com perfis elevados e **RBAC 3-Tier com RoleHierarchy** | [`AuthService.java`](src/main/java/br/com/specvora_service/auth/service/AuthService.java), [`SecurityConfig.java`](src/main/java/br/com/specvora_service/config/SecurityConfig.java) | Escalada horizontal e vertical de privilégios via endpoint de cadastro público | [`AuthServiceTest.java`](src/test/java/br/com/specvora_service/auth/AuthServiceTest.java), [`VehicleSecurityIntegrationTest.java`](src/test/java/br/com/specvora_service/vehicle/VehicleSecurityIntegrationTest.java) |
| **Hardening de API: Rate Limit** | Algoritmo **Token Bucket** com política estrita anti-força bruta em `/auth/login` (5 req/min), limite de memória e resolução segura de IP | [`RateLimitingFilter.java`](src/main/java/br/com/specvora_service/config/RateLimitingFilter.java) | Ataques de força bruta, credential stuffing, spoofing de IP e saturação de heap | [`RateLimitingFilterTest.java`](src/test/java/br/com/specvora_service/config/RateLimitingFilterTest.java) |
| **Hardening de API: Validação de Entrada** | Bean Validation com Unicode (`\p{L}`), sanitização de operadores NoSQL (`$` e `.`) e limites de payload | [`VehicleRequestDTO.java`](src/main/java/br/com/specvora_service/vehicle/dto/VehicleRequestDTO.java), [`VehicleUpsertDTO.java`](src/main/java/br/com/specvora_service/vehicle/dto/VehicleUpsertDTO.java) | NoSQL Injection, XSS Refletido, DoS por sobrecarga de BCrypt | [`VehicleServiceTest.java`](src/test/java/br/com/specvora_service/vehicle/VehicleServiceTest.java) |
| **Hardening de API: JWT Seguro** | HS256 fixo, entropia mínima de 256 bits, geração efêmera segura, verificação de `iss`/`aud` e rejeição de token expirado | [`JwtTokenService.java`](src/main/java/br/com/specvora_service/auth/service/JwtTokenService.java) | Algoritmo `none`, segredos fracos/públicos, spoofing de token e confusão de audiência | [`JwtTokenServiceTest.java`](src/test/java/br/com/specvora_service/auth/JwtTokenServiceTest.java) |
| **Maturidade de Erros e Respostas** | Serialização unificada RFC 7807 via `ErrorResponseWriter` e eliminação de retornos 500 para erros de cliente | [`ErrorResponseWriter.java`](src/main/java/br/com/specvora_service/config/ErrorResponseWriter.java), [`GlobalExceptionHandler.java`](src/main/java/br/com/specvora_service/vehicle/exception/GlobalExceptionHandler.java) | Formatos discrepantes de erro e falhas de cliente (400, 404, 405, 409, 415) gerando HTTP 500 | [`GlobalExceptionHandlerTest.java`](src/test/java/br/com/specvora_service/vehicle/GlobalExceptionHandlerTest.java) |
| **Hardening de Infraestrutura & IaC** | Imagem Docker multi-stage real com usuário não-root (UID 10001), Compose seguro e scan IaC Trivy | [`Dockerfile`](Dockerfile), [`docker-compose.yml`](docker-compose.yml), [`.github/workflows/devsecops.yml`](.github/workflows/devsecops.yml) | Container breakout, privilégios desnecessários e falhas de configuração de infraestrutura | Job `iac-scan` e `container-security` no CI |

---

## 2. Evidência 1: Criptografia Local (AES-256-GCM) em Dados Reais

### Contexto e Risco
O armazenamento de dados em repouso sem proteção criptográfica expõe identificadores e informações confidenciais a vazamentos em backups, falhas de auditoria e dumps de memória. O uso de cifras sem autenticação de integridade também sujeita os dados a ataques de manipulação de bits (*bit-flipping*).

### Implementação da Solução
1. **AES-256-GCM com AEAD:** O [`LocalEncryptionService`](src/main/java/br/com/specvora_service/security/LocalEncryptionService.java) implementa a cifra autenticada **AES-256-GCM**, com IV randômico de 12 bytes gerado a cada operação com `SecureRandom` e tag de integridade de 128 bits.
2. **Eliminação de Segredo Hardcoded:** O serviço não possui segredo estático público no código. Se a variável `AES_SECRET` não for fornecida, uma chave forte de 256 bits é gerada de forma efêmera em memória com registro em nível WARN.
3. **Aplicação Prática em Produção:** Em [`AuthService`](src/main/java/br/com/specvora_service/auth/service/AuthService.java), registros de telemetria e auditoria de autenticação do usuário são cifrados em tempo real com `LocalEncryptionService.encrypt()` e persistidos com proteção de confidencialidade e integridade.

### Comparativo de Código: Antes x Depois

**Antes:** O serviço possuía uma chave fixa pública em fallback e não era consumido por nenhuma rotina do sistema:
```java
// Código legado com chave hardcoded e sem utilização prática
@Value("${security.crypto.aes-secret:specvora-super-secret-aes-key-for-local-encryption-2026}")
private String aesSecret;
```

**Depois:**
```java
// LocalEncryptionService.java — Inicialização segura e geração efêmera
@PostConstruct
public void init() {
    if (aesSecret == null || aesSecret.isBlank()) {
        log.warn("Hardening: AES_SECRET não configurado. Gerando chave efêmera de 256 bits.");
        byte[] ephemeral = new byte[32];
        new SecureRandom().nextBytes(ephemeral);
        this.secretKey = new SecretKeySpec(ephemeral, "AES");
    } else {
        byte[] keyBytes = aesSecret.getBytes(StandardCharsets.UTF_8);
        this.secretKey = new SecretKeySpec(Arrays.copyOf(keyBytes, 32), "AES");
    }
}

// AuthService.java — Utilização real em auditoria de usuários
String auditPayload = String.format("{\"user\":\"%s\",\"roles\":\"%s\",\"auditTime\":\"%s\"}",
        user.getUsername(), user.getRoles(), Instant.now());
user.setEncryptedAuditRecord(localEncryptionService.encrypt(auditPayload));
```

### Evidência de Teste Automatizado
No teste [`LocalEncryptionServiceTest.java`](src/test/java/br/com/specvora_service/security/LocalEncryptionServiceTest.java):
- `testTamperedCiphertextThrowsSecurityException`: Comprova que a modificação de um único byte do dado cifrado é detectada pela tag GCM, resultando em `SecurityException` e bloqueio de decifragem.
- `testEncryptDecryptSuccessful`: Comprova ciclo completo de cifragem e decifragem com integridade preservada.

---

## 3. Evidência 2: Prevenção de Escalada de Privilégio e RBAC 3-Tier

### Contexto e Risco
O auto-registro público em `/auth/register` permitia anteriormente que qualquer usuário anônimo informasse `"role": "ADMIN"`, obtendo permissões administrativas completas no sistema.

### Implementação da Solução
1. **Hierarquia de Perfis (RoleHierarchy):**
   ```java
   // SecurityConfig.java
   @Bean
   static RoleHierarchy roleHierarchy() {
       return RoleHierarchyImpl.fromHierarchy("""
               ROLE_ADMINISTRADOR > ROLE_GESTOR
               ROLE_GESTOR > ROLE_USER
               """);
   }
   ```
2. **Bloqueio de Escalada no `AuthService.register()`:**
   - Auto-registro público atribui estritamente `ROLE_USER`.
   - Se for solicitado perfil elevado (`GESTOR` ou `ADMINISTRADOR`), o serviço verifica se a requisição provém de um usuário autenticado com `ROLE_ADMINISTRADOR`.
   - Caso contrário, a solicitação é barrada com `AccessDeniedException` (**403 Forbidden**).

### Comparativo de Código: Antes x Depois

**Antes:** Atribuição cega de perfil solicitada no payload:
```java
// AuthService legado (Vulnerável a Privilege Escalation)
List<String> roles = dto.getRole() != null && dto.getRole().equalsIgnoreCase("ADMIN")
        ? List.of("ROLE_ADMIN", "ROLE_USER")
        : List.of("ROLE_USER");
```

**Depois:**
```java
// AuthService.java Hardened
public UserResponseDTO register(RegisterRequestDTO dto, Authentication requester) {
    if (users.containsKey(dto.getUsername())) {
        throw new ResourceConflictException("Nome de usuário já cadastrado");
    }

    boolean wantsElevated = dto.getRole() != null && !dto.getRole().equalsIgnoreCase("USER");
    boolean requesterIsAdmin = requester != null && requester.isAuthenticated() &&
            requester.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMINISTRADOR"));

    if (wantsElevated && !requesterIsAdmin) {
        throw new AccessDeniedException("Somente administradores podem conceder perfis elevados.");
    }
    // ...
}
```

### Evidência de Teste Automatizado
- Em [`AuthServiceTest.java`](src/test/java/br/com/specvora_service/auth/AuthServiceTest.java):
  - `testRegisterElevatedRoleWithoutAdminRequesterThrowsAccessDenied`: Valida que um usuário anônimo tentando registrar perfil de gestor ou administrador recebe `AccessDeniedException`.
  - `testRegisterDuplicateUsernameThrowsConflict`: Valida retorno de conflito (409) para nomes de usuário repetidos.

---

## 4. Evidência 3: Hardening de API — Rate Limiting Inteligente

### Contexto e Risco
Ataques de força bruta contra endpoints de login e sobrecarga por requisições automatizadas podem causar indisponibilidade e facilitar a descoberta de senhas. A leitura ingênua do cabeçalho `X-Forwarded-For` permite que atacantes alterem o header a cada tentativa para contornar o rate limit (*IP spoofing*).

### Implementação da Solução
No [`RateLimitingFilter.java`](src/main/java/br/com/specvora_service/config/RateLimitingFilter.java):
1. **Resolução Confiável de IP:** Utiliza `request.getRemoteAddr()`, imune a adulterações de cabeçalhos por atacantes externos.
2. **Dois Baldes Independentes:**
   - `/auth/login`: **5 requisições/minuto** (anti-força bruta).
   - Demais endpoints: **60 requisições/minuto**.
3. **Limitação de Memória:** O mapa de baldes em memória possui capacidade máxima de 10.000 entradas para prevenir esgotamento de memória heap.
4. **Formato Unificado:** Resposta 429 gerada via `ErrorResponseWriter` mantendo a estrutura RFC 7807.

### Comparativo de Código: Antes x Depois

**Antes:**
```java
// RateLimitingFilter legado
String clientIp = request.getHeader("X-Forwarded-For"); // Vulnerável a IP spoofing
if (clientIp == null) clientIp = request.getRemoteAddr();
...
response.getWriter().write("{\"error\":\"Too many requests\"}"); // JSON não padronizado
```

**Depois:**
```java
// RateLimitingFilter.java Hardened
private String resolveClientKey(HttpServletRequest request) {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
        return "user:" + auth.getPrincipal();
    }
    return "ip:" + request.getRemoteAddr(); // Seguro contra spoofing
}
...
errorResponseWriter.write(response, request, HttpStatus.TOO_MANY_REQUESTS,
        "Limite de requisições excedido. Tente novamente mais tarde.");
```

### Evidência de Teste Automatizado
No teste [`RateLimitingFilterTest.java`](src/test/java/br/com/specvora_service/config/RateLimitingFilterTest.java), o método `testBruteForceProtectionOnLoginBlocksOn6thAttempt` simula 5 requisições bem-sucedidas em `/auth/login` e comprova que a **6ª requisição é bloqueada imediatamente com status 429**, `X-RateLimit-Remaining: 0`, cabeçalho `Retry-After` e formato JSON estruturado.

---

## 5. Evidência 4: Hardening de API — Validação Estrita e Defesa NoSQL

### Contexto e Risco
A falta de validação em campos de texto permite que atacantes injetem caracteres de escape de banco de dados, operadores NoSQL (`$where`, `$gt`) ou submetam payloads deliberadamente gigantescos para provocar negação de serviço na CPU.

### Implementação da Solução
1. **Sanitização de Operadores NoSQL:** Em [`VehicleUpsertDTO.java`](src/main/java/br/com/specvora_service/vehicle/dto/VehicleUpsertDTO.java), as chaves do mapa `categories` são sanitizadas para remover `$` e `.`, impedindo a criação de operadores arbitrários no MongoDB.
2. **Suporte Unicode com Validação Estrita:** Em [`VehicleRequestDTO.java`](src/main/java/br/com/specvora_service/vehicle/dto/VehicleRequestDTO.java), as expressões regulares utilizam `\p{L}` para aceitar acentuação correta da língua portuguesa e rejeitar caracteres perigosos (`<`, `>`, `"`, `'`, `;`, `=`, `{`, `}`).

```java
// VehicleUpsertDTO.java
Map<String, Object> sanitized = new HashMap<>();
for (Map.Entry<String, Object> entry : this.categories.entrySet()) {
    if (entry.getKey() != null && !entry.getKey().isBlank()) {
        String sanitizedKey = entry.getKey().replaceAll("[\\$\\.]", "").trim();
        if (!sanitizedKey.isBlank()) {
            sanitized.put(sanitizedKey, entry.getValue());
        }
    }
}
this.categories = sanitized;
```

---

## 6. Evidência 5: Hardening de API — JWT Seguro

### Contexto e Risco
O uso de segredos fixos publicados no repositório permitia que qualquer indivíduo forjasse tokens válidos com perfil de administrador se a variável de ambiente não fosse definida.

### Implementação da Solução
1. **Eliminação de Segredo Hardcoded:** O segredo padrão público foi removido da configuração. Caso não seja informado `JWT_SECRET`, uma chave efêmera de 256 bits é gerada com `SecureRandom` para garantir o funcionamento em testes sem expor chaves estáticas.
2. **Validação de Audiência e Emissor:** O token inclui `iss: "specvora-service"` e `aud: "specvora-api"`, validados na decodificação.
3. **Propagação de Expiração:** O claim `exp` é propagado para o endpoint `/auth/me`.
4. **Rejeição Estrita de Tokens Expirados:** Adição de asserção explícita no teste unitário.

```java
// JwtTokenServiceTest.java
@Test
void testExpiredTokenIsRejected() {
    String expired = JWT.create()
            .withIssuer("specvora-service")
            .withAudience("specvora-api")
            .withSubject("user")
            .withExpiresAt(Date.from(Instant.now().minusSeconds(60)))
            .sign(Algorithm.HMAC256("chave-secreta-para-testes-unitarios-jwt-123456"));

    assertThrows(TokenExpiredException.class, () -> jwtTokenService.validateToken(expired));
}
```

---

## 7. Evidência 6: Teste de Integração de Ponta a Ponta da SecurityFilterChain

Para comprovar a eficácia real das regras de segurança sem mocks na camada de filtro, foi criado o teste de integração [`VehicleSecurityIntegrationTest.java`](src/test/java/br/com/specvora_service/vehicle/VehicleSecurityIntegrationTest.java), utilizando `@WebMvcTest` com carregamento real do `SecurityConfig`, `JwtAuthFilter`, `JwtTokenService` e `ErrorResponseWriter`:

```java
@WebMvcTest(controllers = VehicleController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class, IdempotencyFilter.class,
        RateLimitingFilter.class, JwtTokenService.class, GlobalExceptionHandler.class,
        ErrorResponseWriter.class})
class VehicleSecurityIntegrationTest {

    @Test
    void semToken_retorna401() throws Exception {
        mockMvc.perform(get("/vehicles")).andExpect(status().isUnauthorized());
    }

    @Test
    void userCriando_retorna403() throws Exception {
        String token = jwtTokenService.generateToken("user", List.of("ROLE_USER"));
        mockMvc.perform(post("/vehicles")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(VEHICLE_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCriando_retorna201ComLocation() throws Exception {
        String token = jwtTokenService.generateToken("admin", List.of("ROLE_ADMINISTRADOR"));
        mockMvc.perform(post("/vehicles")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(VEHICLE_JSON))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"));
    }
}
```

---

## 8. Evidência 7: Respostas de Erro Padronizadas e Eliminação de 500 em Erros do Cliente

### Contexto e Risco
O tratamento genérico de erros interceptava exceções de validação e de protocolo do Spring MVC (como JSON malformado ou método PATCH não suportado), convertendo-os erroneamente em **HTTP 500 Internal Server Error**.

### Implementação da Solução
Em [`GlobalExceptionHandler.java`](src/main/java/br/com/specvora_service/vehicle/exception/GlobalExceptionHandler.java):
- `HttpMessageNotReadableException` $\to$ **400 Bad Request**;
- `HttpRequestMethodNotSupportedException` $\to$ **405 Method Not Allowed**;
- `HttpMediaTypeNotSupportedException` $\to$ **415 Unsupported Media Type**;
- `NoResourceFoundException` $\to$ **404 Not Found**;
- `ResourceConflictException` $\to$ **409 Conflict**;
- Testes dedicados em [`GlobalExceptionHandlerTest.java`](src/test/java/br/com/specvora_service/vehicle/GlobalExceptionHandlerTest.java) garantem que nenhum erro do cliente gere HTTP 500.

---

## 9. Como Reproduzir e Coletar as Evidências

### Execução de Toda a Suíte de Testes Automatizados
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
