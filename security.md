# Specvora Service — Documento de Segurança

## Resumo do Projeto

O **Specvora Service** é uma API REST que agrega e disponibiliza dados técnicos de veículos. Clientes autenticados consultam especificações por marca, modelo, versão, motor e ano. O serviço é construído sobre Spring Boot com MongoDB como banco de dados de documentos e Firebase Authentication como provedor de identidade. Por expor dados por meio de uma API pública com autenticação baseada em token, a superfície de ataque inclui autenticação indevida, abuso de volume, manipulação de queries e vazamento de informações — todos os pontos cobertos pelas medidas descritas abaixo.

---

## Pontos de Segurança

---

### 1. Normalização de Input de Request

**Impacto da falha**
Sem normalização, entradas inconsistentes (espaços extras, maiúsculas/minúsculas mistas, caracteres de controle) podem burlar comparações de string no banco, gerar resultados incorretos ou servir de vetor para payloads maliciosos camuflados em whitespace.

**Como o projeto corrige**
O método `normalize()` em `VehicleRequestDTO` é chamado em `VehicleService.findVehicle()` antes de qualquer consulta ao banco. Cada campo de texto passa por:
- `trim()` — remove espaços nas extremidades;
- `replaceAll("\\s+", " ")` — colapsa múltiplos espaços internos em um único;
- `toLowerCase(Locale.ROOT)` — força caixa-baixa independente do locale do servidor.

Campos resultantes em branco após normalização são convertidos a `null`, impedindo que strings vazias entrem na query.

```java
// VehicleRequestDTO.java
private String normalizeField(String value) {
    if (value == null) return null;
    String normalized = value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    return normalized.isBlank() ? null : normalized;
}
```

---

### 2. Prevenção contra NoSQL Injection (SQLI)

**Impacto da falha**
Em bancos NoSQL, a injeção de operadores (`$where`, `$gt`, `$regex`, etc.) pode contornar filtros de autenticação, exfiltrar toda a coleção ou executar JavaScript arbitrário no servidor MongoDB.

**Como o projeto corrige**
Todo acesso ao MongoDB ocorre via `MongoTemplate` com a API `Criteria` do Spring Data. Os valores do usuário são passados como parâmetros tipados ao construtor da criteria — nunca interpolados em strings de query. Isso garante que um payload como `{ "$gt": "" }` seja tratado como um valor literal de string, não como um operador.

Complementarmente, a Bean Validation (`@NotBlank`, `@Size(max=100)`) em `VehicleRequestDTO` rejeita entradas inválidas antes mesmo de chegarem à camada de repositório, reduzindo ainda mais a superfície.

---

### 3. Prevenção contra XSS (Cross-Site Scripting)

**Impacto da falha**
Respostas que refletem input do usuário sem sanitização podem injetar scripts maliciosos no navegador de quem consome a API, levando a roubo de sessão, redirecionamento e execução de código no contexto do cliente.

**Como o projeto corrige**
A API retorna exclusivamente JSON serializado pelo Jackson, sem renderização de HTML. Por consequência, não há superfície de injeção de marcação. Além disso, o Spring Security adiciona automaticamente os headers de defesa em profundidade:

- `X-Content-Type-Options: nosniff` — impede que o browser interprete o content-type de forma diferente do declarado;
- `X-Frame-Options: DENY` — bloqueia clickjacking via `<iframe>`.

O Jackson realiza encoding de caracteres especiais (`<`, `>`, `&`) na serialização JSON, neutralizando qualquer tentativa de injetar tags HTML em campos de texto retornados.

---

### 4. Prevenção contra Buffer Overflow

**Impacto da falha**
Buffer overflows permitem que atacantes sobrescrevam memória adjacente, executem código arbitrário ou causem crash na aplicação.

**Como o projeto corrige**
A JVM gerencia toda a memória com bounds checking em tempo de execução. Não existe acesso direto a ponteiros ou buffers de memória no código da aplicação. Adicionalmente, a constraint `@Size(max=100)` em cada campo do `VehicleRequestDTO` rejeita payloads excessivamente grandes antes de qualquer processamento, evitando alocações desnecessárias e garantindo um teto previsível de memória por request.

---

### 5. Prevenção contra Flooding / DDoS — Rate Limiting

**Impacto da falha**
Sem limitação de taxa, um único cliente pode esgotar recursos de CPU, memória e conexões de banco, tornando o serviço indisponível para usuários legítimos (Denial of Service).

**Como o projeto corrige**
O `RateLimitingFilter` (executado via `OncePerRequestFilter`) aplica o algoritmo **Token Bucket** por meio da biblioteca `bucket4j-core`. O limite padrão é de **60 requisições por minuto** por identidade:

- **Usuário autenticado:** chave `user:<uid-anonimizado>` — o rate limit é aplicado por conta, impedindo que um token comprometido abuse o serviço em nome de outros usuários.
- **Requisição anônima:** chave `ip:<endereço>` — aplica o mesmo limite por IP de origem.

Quando o balde é esgotado, a resposta é `429 Too Many Requests` com body JSON, sem propagar informações internas.

```java
// RateLimitingFilter.java
private static final Bandwidth RATE_LIMIT = Bandwidth.simple(60, Duration.ofMinutes(1));
```

---

### 6. Ausência de Stacktrace Leak

**Impacto da falha**
Stack traces expostos em respostas de erro revelam nomes de classes internas, versões de bibliotecas, estrutura de pacotes e linhas de código — informações valiosas para um atacante mapear vulnerabilidades específicas.

**Como o projeto corrige**
O `GlobalExceptionHandler` centraliza o tratamento de todas as exceções com `@RestControllerAdvice`:

- `VehicleNotFoundException` → `404 Not Found` com mensagem controlada.
- `MethodArgumentNotValidException` → `400 Bad Request` apenas com os campos e mensagens de validação.
- Qualquer outra `Exception` → `500 Internal Server Error` com a mensagem genérica `"Erro interno no servidor"`, sem qualquer detalhe da causa original.

O `JwtAuthFilter` captura `FirebaseAuthException` com `catch (FirebaseAuthException ignored)` e retorna `401 Unauthorized` com body fixo `{"error":"Unauthorized"}`, sem propagar a exceção nem revelar o motivo da falha de autenticação.

---

### 7. Autenticação com Bearer Token (Firebase Authentication)

**Impacto da falha**
Sem autenticação forte, qualquer cliente pode consultar ou manipular dados da API. Tokens fracos ou sem validação de assinatura permitem falsificação de identidade.

**Como o projeto corrige**
A autenticação é delegada ao **Firebase Authentication**, provedor de identidade gerenciado pela Google com suporte a múltiplos fatores. O fluxo é:

1. O cliente autentica-se via Firebase SDK e obtém um **ID Token JWT** assinado.
2. Cada request inclui o token no header: `Authorization: Bearer <firebase-id-token>`.
3. O `JwtAuthFilter` intercepta a request, extrai o token e o valida via `FirebaseAuth.getInstance().verifyIdToken()` — que verifica assinatura criptográfica, emissor (`iss`), audience (`aud`) e expiração (`exp`) contra as chaves públicas do Firebase.
4. Tokens inválidos ou expirados resultam em `401 Unauthorized` imediato, sem avançar na chain de filtros.

A configuração do `SecurityConfig` garante que **toda rota** (exceto os endpoints públicos de documentação) exija autenticação com `.anyRequest().authenticated()`.

---

### 8. Ausência de Vazamento de Dados Sensíveis

**Impacto da falha**
Secrets, credenciais e dados pessoais hardcodados no código ou expostos em respostas podem ser extraídos de repositórios públicos, logs ou payloads de erro, comprometendo infraestrutura e privacidade.

**Como o projeto corrige**
- **Variáveis de ambiente:** as credenciais sensíveis (`MONGODB_URI`, `FIREBASE_CREDENTIALS_PATH`) são lidas exclusivamente via variáveis de ambiente, nunca escritas no código-fonte ou em arquivos versionados.
- **Respostas da API:** o `GlobalExceptionHandler` retorna apenas mensagens genéricas controladas; nenhum campo de senha, secret ou token aparece nas entidades expostas pelo `VehicleModel`.
- **Token do usuário:** o UID do Firebase nunca é retornado em respostas nem armazenado em logs (ver seção de Anonimização).

---

### 9. Idempotência — Prevenção de Requisições Duplicadas

**Impacto da falha**
Sem controle de idempotência, double-clicks, retries automáticos de rede ou scripts maliciosos podem submeter a mesma operação `POST` múltiplas vezes, gerando registros duplicados, cobranças duplas ou efeitos colaterais repetidos.

**Como o projeto corrige**
O `IdempotencyFilter` processa o header opcional `Idempotency-Key` em todas as requisições `POST`:

1. A chave é composta por `<uid-anonimizado>:<idempotency-key>`, vinculando a chave ao usuário e impedindo colisão entre clientes diferentes.
2. Na primeira recepção, a chave é registrada em um `ConcurrentHashMap` com o timestamp atual.
3. Se a mesma chave chegar novamente dentro de uma janela de **10 minutos**, a resposta é `409 Conflict` com body `{"error":"Duplicate request"}`.
4. Chaves de requisições que resultaram em erro (`status >= 400`) são removidas do mapa, permitindo reenvio legítimo.
5. Limpeza automática (TTL) remove entradas expiradas a cada requisição, evitando crescimento ilimitado de memória.

---

### 10. Assinatura de Requisição e Controle de Token Único

**Impacto da falha**
Tokens sem rastreamento de singularidade permitem que um mesmo token seja reutilizado concorrentemente por múltiplos clientes, potencialmente por um token roubado ou vazado. Volume anormal de tokens de uma mesma conta também é sinal de comprometimento.

**Como o projeto corrige**
O modelo de autenticação Firebase garante que cada ID Token seja **assinado criptograficamente** e tenha **expiração curta** (1 hora por padrão). A combinação dos filtros garante o comportamento desejado:

- **Token único por operação:** o `IdempotencyFilter` usa a identidade do token autenticado (`authentication.getPrincipal()`) como parte da chave, portanto dois tokens diferentes de usuários diferentes não colidem; dois envios do mesmo token pelo mesmo usuário resultam em `409 Conflict`.
- **Muitos tokens de uma pessoa = block:** o `RateLimitingFilter` rastreia requisições por `user:<uid-anonimizado>`. Independentemente de quantos tokens ativos o usuário possua, o balde de rate limit é compartilhado por UID, portanto volume excessivo — mesmo com tokens distintos válidos — resulta em `429 Too Many Requests`.

---

### 11. CORS Apropriado

**Impacto da falha**
CORS mal configurado (ex.: `allowedOrigins("*")`) permite que qualquer site malicioso faça requisições autenticadas à API em nome de um usuário logado, explorando cookies ou tokens armazenados no browser (CSRF via CORS).

**Como o projeto corrige**
O `CrossOriginConfig` define uma lista explícita de origens permitidas lida de `application.yml` (`http.cors.allowed-origins`). Nenhuma wildcard é usada. As demais restrições aplicadas são:

- `allowedHeaders`: apenas `Content-Type`, `Accept` e `Authorization` são aceitos.
- `allowedMethods`: apenas `GET` e `POST`, condizente com os endpoints existentes.
- `maxAge(3600)`: o resultado do preflight é cacheado por 1 hora, reduzindo requests OPTIONS desnecessários.

Qualquer origem não listada terá a request bloqueada pelo browser antes mesmo de atingir o filtro de autenticação.

---

### 12. Anonimização do Usuário

**Impacto da falha**
Armazenar ou logar o UID original do Firebase expõe um identificador único e persistente do usuário. Em caso de vazamento de logs, contexto de segurança ou dumps de memória, esse dado pode ser cruzado com outras fontes para re-identificar o indivíduo, violando princípios de privacidade (LGPD/GDPR).

**Como o projeto corrige**
No `JwtAuthFilter`, após validação do token, o UID original é **substituído pelo seu hash SHA-256** antes de ser armazenado no `SecurityContext`:

```java
// JwtAuthFilter.java
private String anonymizeUid(String uid) {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    byte[] hash = digest.digest(uid.getBytes(StandardCharsets.UTF_8));
    return HexFormat.of().formatHex(hash);
}
```

O hash é determinístico — o mesmo UID sempre produz o mesmo hash — o que permite rastreamento de requisições do mesmo usuário sem expor o identificador original. O UID nunca é propagado além do filtro de autenticação.

---

### 13. Pipeline DevSecOps Integrado (Shift-Left Security)

**Impacto da falha**
Sem um pipeline automatizado de segurança, vulnerabilidades em bibliotecas terceiras, erros de programação (OWASP Top 10) e credenciais acidentalmente commitadas só seriam detectadas após incidentes em produção ou auditorias manuais tardias, elevando drasticamente o risco e o custo de correção.

**Como o projeto corrige**
O projeto implementa uma pipeline CI/CD DevSecOps completa via **GitHub Actions** (`.github/workflows/devsecops.yml`), garantindo que nenhum artefato seja publicado sem validação prévia de segurança (*Quality Gates*):
- **Secret Scanning (Gitleaks & GitGuardian):** Analisa cada commit em busca de chaves Firebase e strings de conexão expostas, utilizando regras em `.gitleaks.toml`.
- **SCA - Software Composition Analysis (Dependabot & Snyk / Trivy):** Escaneia o `pom.xml` contra CVEs conhecidas e automatiza a abertura de PRs para atualização de dependências vulneráveis via `.github/dependabot.yml`.
- **SAST - Static Application Security Testing (Semgrep & SonarQube):** Analisa o código-fonte Java contra os padrões do OWASP Top 10 e vulnerabilidades de injeção/tratamento.
- **Container Hardening (Docker & Trivy):** Constrói a imagem Docker baseada em JRE 21 com usuário não-root (`appuser`) e valida ausência de CVEs no sistema operacional.
- **Quality Gate no Deploy:** O deploy para ambientes de staging/produção só é liberado se todos os testes e scans forem aprovados com sucesso.

Para a documentação completa, diagrama do pipeline e guia de execução local, consulte o arquivo [DEVSECOPS.md](DEVSECOPS.md).
