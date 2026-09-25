# Specvora Service — Documento de Segurança

## Resumo do Projeto

O **Specvora Service** é uma API REST que agrega e disponibiliza especificações técnicas de veículos. Clientes autenticados consultam dados por marca, modelo, versão, motor e ano. O serviço é construído sobre Spring Boot com MongoDB como banco de dados de documentos e autenticação JWT / Firebase Authentication. Por expor dados por meio de uma API com autenticação baseada em token, a superfície de ataque inclui autenticação indevida, abuso de volume, manipulação de queries, escalada de privilégios e vazamento de informações — todos os pontos cobertos pelas medidas descritas abaixo.

---

## Pontos de Segurança

---

### 1. Sanitização e Normalização de Input de Request

**Impacto da falha**
Sem normalização e sanitização, entradas inconsistentes (espaços extras, maiúsculas/minúsculas mistas, caracteres especiais e de controle) podem burlar comparações de string no banco, gerar resultados incorretos ou servir de vetor para payloads maliciosos.

**Como o projeto corrige**
O método `sanitizeAndNormalize()` em `VehicleRequestDTO` e `VehicleUpsertDTO` é executado na camada de serviço (`VehicleService`) antes de qualquer consulta ou persistência no MongoDB:
- Remove caracteres perigosos (`<`, `>`, `'`, `"`, `;`);
- `trim()` — remove espaços nas extremidades;
- `replaceAll("\\s+", " ")` — colapsa múltiplos espaços internos em um único;
- `toLowerCase(Locale.ROOT)` — força caixa-baixa independente do locale do servidor;
- Campos resultantes vazios são convertidos a `null`, impedindo que strings vazias entrem na query;
- As expressões regulares de validação nos DTOs suportam caracteres acentuados via Unicode (`\\p{L}`) e validam formatos estritos (como `year` no padrão `^(19|20)\\d{2}$`).

```java
// VehicleRequestDTO.java / VehicleUpsertDTO.java
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

### 2. Prevenção contra NoSQL Injection

**Impacto da falha**
Em bancos NoSQL, a injeção de operadores (`$where`, `$gt`, `$regex`, `$ne`, etc.) ou a manipulação arbitrária de chaves de documentos pode contornar filtros de consulta, exfiltrar coleções inteiras ou corromper índices estruturais no MongoDB.

**Como o projeto corrige**
1. **Criteria Parametrizada:** Todo acesso ao MongoDB via `VehicleRepositoryImpl` utiliza `MongoTemplate` com a API `Criteria` do Spring Data. Os valores fornecidos pelo usuário são passados como parâmetros tipados aos métodos `Criteria.is()` — nunca concatenados ou interpolados em strings JSON de query.
2. **Sanitização de Chaves em Mapas Dinâmicos:** No campo `categories` (`VehicleUpsertDTO`), a sanitização iterativa remove ou rejeita qualquer chave que contenha operadores de injeção NoSQL (`$` e `.`):
   ```java
   String sanitizedKey = entry.getKey().replaceAll("[\\$\\.]", "").trim();
   ```
3. **Bean Validation Estrita:** `@Pattern`, `@NotBlank` e `@Size(max=100)` validam os campos antes que cheguem à camada de repositório.

---

### 3. Prevenção contra XSS (Cross-Site Scripting)

**Impacto da falha**
Respostas que refletem input do usuário sem encoding apropriado podem injetar scripts maliciosos no navegador de quem consome a API ou no painel de administração, levando a roubo de sessão e exfiltração de dados.

**Como o projeto corrige**
A API retorna exclusivamente JSON serializado pelo Jackson, sem renderização de HTML. O Jackson realiza o escape de caracteres de controle e a sanitização preliminar (`replaceAll("[<>'\"\\;]", "")`) remove tags antes do armazenamento. Além disso, o Spring Security injeta headers de proteção:
- `Content-Security-Policy: default-src 'self'; script-src 'self' 'unsafe-inline'; frame-ancestors 'none'; object-src 'none'`;
- `X-Content-Type-Options: nosniff`;
- `X-Frame-Options: DENY`.

---

### 4. Prevenção contra Buffer Overflow e DoS de Payload

**Impacto da falha**
Payloads gigantescos submetidos em requisições HTTP podem saturar a memória heap da JVM, degradar a CPU ou causar crash na aplicação (Denial of Service).

**Como o projeto corrige**
- Limite de tamanho de requisição e swallow configurado em `application.yml`:
  ```yaml
  server:
    max-http-request-header-size: 8KB
    tomcat:
      max-swallow-size: 256KB
  ```
- Constraints `@Size(max = 100)` em campos textuais e `@Size(min = 6, max = 100)` no campo `password`, impedindo o envio de senhas com dezenas de milhares de caracteres que causariam sobrecarga de CPU no algoritmo **BCrypt**.

---

### 5. Prevenção contra Flooding / Força Bruta — Rate Limiting Inteligente

**Impacto da falha**
Sem limitação de taxa adaptativa, ataques automatizados de força bruta contra endpoints de autenticação (*credential stuffing*) podem comprometer contas com senhas fracas, além de exaurir recursos de conexões e CPU.

**Como o projeto corrige**
O `RateLimitingFilter` implementa o algoritmo **Token Bucket** (via `bucket4j-core`) com diferenciação de rotas:
1. **Rota Crítica (`POST /auth/login`):** Limite estrito anti-força bruta de **5 requisições por minuto** por cliente.
2. **Rotas Gerais de API:** Limite de **60 requisições por minuto** por cliente.
3. **Resolução Segura de IP:** Utiliza `request.getRemoteAddr()` (confiável sob proxy reverso) em vez de confiar cegamente em `X-Forwarded-For` arbitrário do cliente.
4. **Proteção de Memória:** O número de baldes em memória é limitado com descarte preventivo (máximo de 10.000 entradas ativas).
5. **Cabeçalhos Padronizados IETF:** Retorna `X-RateLimit-Limit`, `X-RateLimit-Remaining` e `Retry-After`. Em caso de estouro, retorna `429 Too Many Requests` formatado via `ErrorResponseWriter`.

---

### 6. Ausência de Stacktrace Leak e Tratamento Unificado de Erros

**Impacto da falha**
Stack traces expostos em respostas de erro revelam nomes de classes internas, versões de frameworks e detalhes do banco de dados, facilitando a elaboração de ataques direcionados.

**Como o projeto corrige**
1. **Formato Unificado (`ErrorResponseDTO`):** Todas as respostas de erro da aplicação seguem a mesma estrutura conforme RFC 7807:
   ```json
   {
     "timestamp": "2026-09-25T03:00:00Z",
     "status": 404,
     "error": "Not Found",
     "message": "Veículo não encontrado com o ID especificado",
     "path": "/vehicles/123"
   }
   ```
2. **Serialização Centralizada (`ErrorResponseWriter`):** Os filtros de segurança (`SecurityConfig`, `JwtAuthFilter`, `RateLimitingFilter`, `IdempotencyFilter`) utilizam o componente injetado `ErrorResponseWriter` para garantir que erros 401, 403, 409 e 429 sigam o mesmo padrão de serialização do controller advice.
3. **Mapeamento Explicito de Exceções do Spring MVC:** O `GlobalExceptionHandler` possui handlers dedicados para:
   - `HttpMessageNotReadableException` $\to$ **400 Bad Request** (JSON malformado ou body ausente);
   - `HttpRequestMethodNotSupportedException` $\to$ **405 Method Not Allowed** (ex.: PATCH não suportado);
   - `HttpMediaTypeNotSupportedException` $\to$ **415 Unsupported Media Type**;
   - `NoResourceFoundException` $\to$ **404 Not Found** (rotas inexistentes);
   - `ResourceConflictException` $\to$ **409 Conflict** (username já cadastrado);
   - Falhas inesperadas $\to$ **500 Internal Server Error** com mensagem genérica `"Erro interno no servidor"`, sem expor stack traces.

---

### 7. Autenticação JWT, Prevenção de Escalada de Privilégio e RBAC 3-Tier

**Impacto da falha**
Endpoints públicos de registro que aceitam perfis arbitrários permitem que atacantes anônimos se auto-atribuam privilégios de administrador (Privilege Escalation).

**Como o projeto corrige**
1. **Controle Estrito no Cadastro (`POST /auth/register`):**
   - O auto-registro anônimo/público atribui obrigatoriamente o perfil de menor privilégio: `ROLE_USER`.
   - A concessão de perfis elevados (`GESTOR` ou `ADMINISTRADOR`) exige que o usuário chamador esteja autenticado no `SecurityContext` com `ROLE_ADMINISTRADOR`. Caso contrário, uma `AccessDeniedException` é lançada, resultando em **403 Forbidden**.
2. **RBAC em 3 Níveis com `RoleHierarchy`:**
   - **`ROLE_ADMINISTRADOR > ROLE_GESTOR > ROLE_USER`**
   - `ROLE_USER`: Consulta de catálogo (`GET /vehicles/**`), busca técnica (`POST /vehicles/search`) e perfil (`GET /auth/me`).
   - `ROLE_GESTOR`: Herda permissões de User + criação (`POST /vehicles`) e atualização (`PUT /vehicles/{id}`).
   - `ROLE_ADMINISTRADOR`: Herda todas as permissões + exclusão de registros (`DELETE /vehicles/{id}`) e concessão de perfis elevados.
3. **JWT Seguro e Validação Estrita:**
   - Algoritmo fixo HMAC-SHA256 (`HS256`).
   - Validação de emissor fixo (`iss: "specvora-service"`) e audiência (`aud: "specvora-api"`).
   - Validação de expiração de 2 horas e propagação de `expiresAt` para `/auth/me`.

---

### 8. Gestão Segura de Segredos e Chaves Criptográficas

**Impacto da falha**
Hardcoding de chaves de assinatura e segredos em arquivos de configuração públicos expõe toda a segurança criptográfica a comprometimento por inspeção de repositório.

**Como o projeto corrige**
- **Sem Fallbacks Hardcoded:** Os valores padrão fixos de segredos foram removidos de `application.yml` (`${JWT_SECRET:}`).
- **Geração Efêmera Criptográfica em Desenvolvimento/Testes:** Caso as variáveis de ambiente `JWT_SECRET` e `AES_SECRET` não sejam fornecidas em ambiente local, os serviços `JwtTokenService` e `LocalEncryptionService` geram dinamicamente uma chave de 256 bits via `SecureRandom` em memória, com log de advertência em nível WARN.
- **Validação de Entropia:** Se uma chave for configurada explicitamente, ela deve possuir no mínimo 32 bytes (256 bits), rejeitando inicializações com segredos fracos.
- **Auditoria Limpa no Gitleaks:** A remoção dos segredos permite que o `.gitleaks.toml` audite `application.yml` sem falsas exceções na allowlist.

---

### 9. Idempotência com Tratamento Padronizado (HTTP 409)

**Impacto da falha**
Retries acidentais de rede ou cliques duplos podem duplicar a inserção de veículos ou transações no backend.

**Como o projeto corrige**
O `IdempotencyFilter` intercepta requisições `POST` contendo o cabeçalho `Idempotency-Key`:
- Armazena a chave vinculada ao usuário em janela deslizante de 10 minutos;
- Requisições duplicadas dentro da janela recebem resposta imediata **409 Conflict** padronizada via `ErrorResponseWriter`;
- Requisições que falharam com erro do cliente (status $\ge 400$) têm sua chave liberada para reenvio.

---

### 10. CORS Restrito e Seguro

**Impacto da falha**
Políticas de CORS excessivamente permissivas (`allowedOrigins("*")` com métodos arbitrários) habilitam explorações de Cross-Origin Request Forgery a partir de sites maliciosos no browser.

**Como o projeto corrige**
O `CrossOriginConfig` define uma política explícita:
- Origens permitidas parametrizadas (`http.cors.allowed-origins`, padrão `http://localhost:3000`);
- Métodos explicitamente habilitados condizentes com a maturidade REST: `GET`, `POST`, `PUT`, `DELETE`, `OPTIONS`;
- Cabeçalhos de requisição permitidos: `Content-Type`, `Accept`, `Authorization`, `Idempotency-Key`;
- Cabeçalhos expostos ao cliente: `Location`, `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `Retry-After`, `Idempotency-Key`;
- `maxAge(3600)` para otimização de requisições preflight.

---

### 11. Anonimização de Identificadores (Privacidade e LGPD)

**Impacto da falha**
Armazenamento e log de identificadores pessoais persistentes expõem dados sensíveis em caso de vazamento de logs.

**Como o projeto corrige**
No processamento de autenticação via Firebase, o UID original é substituído pelo seu hash SHA-256 determinístico antes de ser persistido no `SecurityContextHolder`, impedindo a exposição do identificador primário do usuário em logs ou traces.

---

### 12. Criptografia Local Ativa (AES-256-GCM)

**Impacto da falha**
Dados sensíveis armazenados em repouso sem criptografia autenticada estão sujeitos a vazamentos e ataques de adulteração de bits (*bit-flipping*).

**Como o projeto corrige**
O `LocalEncryptionService` implementa criptografia autenticada **AES-256-GCM (AEAD)**:
- Utiliza vetor de inicialização (IV) randômico de 12 bytes gerado por `SecureRandom` a cada cifragem;
- Tag de autenticação de 128 bits;
- **Aplicação Real em Produção:** Em `AuthService`, o serviço é utilizado para criptografar em tempo real registros de auditoria sensíveis de telemetria dos usuários (`encryptedAuditRecord`), garantindo proteção efetiva de dados em repouso.

---

### 13. Pipeline DevSecOps Integrado (Shift-Left)

**Como o projeto corrige**
O pipeline automatizado no GitHub Actions ([`.github/workflows/devsecops.yml`](.github/workflows/devsecops.yml)) assegura que nenhum artefato vulnerável atinja os ambientes da Ford:
- **Secret Scanning (Gitleaks):** Bloqueio imediato (`exit-code: 1`) contra commits contendo chaves ou URIs expostas.
- **SCA (Dependabot & Trivy FS):** Monitoramento contínuo de CVEs em dependências do Maven, bloqueio de severidades críticas/altas (`exit-code: 1`) e exportação SARIF para a aba de segurança do GitHub.
- **SAST (Semgrep & SonarCloud):** Análise estática contra OWASP Top 10 e padrões Java com `--error`.
- **IaC & Container Security (Trivy Config & Trivy Image):** Verificação de configurações de containers e escaneamento da imagem Docker multi-stage sem root (`appuser` 10001).
- **Evidências de Teste:** Upload automático dos relatórios de teste do Maven Surefire como artefato do build.

Para o detalhamento do fluxo corporativo no ecossistema Ford e arquitetura MQTT/TLS, consulte [DEVSECOPS.md](DEVSECOPS.md). Para os testes e comparativos de código "Antes x Depois", consulte [SECURITY_EVIDENCES.md](SECURITY_EVIDENCES.md).
