# Specvora Service — Pesquisa de Vulnerabilidades (OWASP Top 10, API Top 10, Mobile Top 10 e ASVS)

Este documento consolida a entrega da **Etapa 4 (Challenge Sprint 3 - Cybersecurity)** do projeto **Specvora Service**. Ele apresenta uma **pesquisa técnica sobre as principais vulnerabilidades em aplicações web, APIs e no aplicativo mobile**, a definição do **nível de verificação OWASP ASVS** adotado, acompanhada de uma **Matriz de Mapeamento de Riscos específica para o ecossistema Specvora / Ford** e do **Plano de Mitigação Arquitetural** implementado para neutralizar cada uma dessas ameaças.

---

## 1. Visão Geral e Fundamentação Teórica

A segurança no desenvolvimento moderno de software (*Application Security - AppSec*) exige o mapeamento proativo de ameaças com base em padrões consolidados pela indústria. A **OWASP (Open Web Application Security Project)** mantém duas referências essenciais:

1. **OWASP Top 10 (Web Applications):** Focado em riscos estruturais e vulnerabilidades clássicas de código e infraestrutura web.
2. **OWASP API Security Top 10:** Especializado nas ameaças únicas de arquiteturas orientadas a microsserviços, REST e GraphQL, onde a lógica de negócios e os endpoints são expostos diretamente a clientes automatizados, dispositivos móveis e sensores IoT.
3. **OWASP Mobile Top 10 (2024):** Riscos do lado do aplicativo móvel — armazenamento no dispositivo, comunicação, credenciais embutidas no binário e engenharia reversa. No Specvora, aplica-se ao **aplicativo da brigada**, cliente da API que autentica via Firebase/JWT (o app não está neste repositório; os requisitos abaixo valem para ele e para o contrato com a API).
4. **OWASP ASVS (Application Security Verification Standard) 5.0:** Diferente das listas "Top 10" (conscientização sobre riscos), o ASVS é um **catálogo de requisitos verificáveis**, organizado em capítulos e em três níveis (L1, L2, L3). É usado aqui para definir o que precisa ser verificado na arquitetura e para medir a conformidade atual.

---

## 2. Parte I: Pesquisa Detalhada das Vulnerabilidades

### 2.1. OWASP Top 10 (Web Applications)

#### A01:2021 – Broken Access Control (Quebra de Controle de Acesso)
- **Definição:** Falhas que permitem a usuários não autorizados acessar recursos, modificar dados de terceiros ou executar funções além dos privilégios concedidos.
- **Vetor comum:** Acessar endpoints administrativos sem validação adequada de perfil ou alterar IDs de parâmetros da requisição.

#### A02:2021 – Cryptographic Failures (Falhas Criptográficas)
- **Definição:** Exposição de dados sensíveis devido à ausência de criptografia, uso de algoritmos obsoletos (MD5, SHA1, DES) ou chaves previsíveis/hardcoded.
- **Vetor comum:** Tráfego em texto plano (HTTP em vez de HTTPS) ou armazenamento de senhas e dados confidenciais sem cifra autenticada.

#### A03:2021 – Injection (Injeções: NoSQL, SQL, Command)
- **Definição:** Ocorre quando dados não confiáveis fornecidos pelo usuário são interpretados como comandos ou queries pelo interpretador do banco de dados ou sistema operacional.
- **Vetor comum:** Injeção de operadores MongoDB (`$where`, `$gt`, `$ne`, `$regex`) para contornar autenticações ou extrair coleções inteiras.

#### A04:2021 – Insecure Design (Design Inseguro)
- **Definição:** Falhas decorrentes da falta de modelagem de ameaças e de arquitetura de segurança na fase de concepção do software.
- **Vetor comum:** Ausência de limitação de requisições (rate limiting) em fluxos de login, permitindo ataques automatizados de força bruta.

#### A05:2021 – Security Misconfiguration (Configuração Incorreta de Segurança)
- **Definição:** Aplicação de configurações padrão inseguras, permissões excessivas, portas desnecessárias abertas ou exposição de stack traces detalhados ao cliente.
- **Vetor comum:** Mensagens de erro 500 expondo exceções e pacotes internos do framework, ou containers executando como `root`.

#### A06:2021 – Vulnerable and Outdated Components (Componentes Desatualizados e Vulneráveis)
- **Definição:** Utilização de bibliotecas de terceiros, drivers ou frameworks com vulnerabilidades conhecidas (CVEs cadastradas no NVD).
- **Vetor comum:** Dependências antigas do Spring Boot ou drivers MongoDB com brechas de execução remota de código (RCE) ou negação de serviço (DoS).

#### A07:2021 – Identification and Authentication Failures (Falhas de Identificação e Autenticação)
- **Definição:** Falhas na confirmação da identidade do usuário, permitindo sequestro de sessão (*session hijacking*), credential stuffing ou uso de senhas fracas.
- **Vetor comum:** Permissão para ataques massivos de força bruta ou tokens sem prazo estrito de expiração.

#### A08:2021 – Software and Data Integrity Failures (Falhas de Integridade de Software e Dados)
- **Definição:** Uso de código, dependências ou plugins sem verificação de assinatura digital, ou desserialização insegura de objetos serializados.
- **Vetor comum:** Adulteração de dados cifrados em repouso por ausência de tag de autenticação (mitigado por cifras AEAD como AES-GCM).

#### A09:2021 – Security Logging and Monitoring Failures (Falhas de Registro e Monitoramento)
- **Definição:** Falha em registrar eventos de segurança relevantes ou ausência de monitoramento em tempo real, impedindo a detecção tempestiva de invasões.
- **Vetor comum:** Tentativas repetidas de invasão que não geram alertas no SOC nem deixam trilhas de auditoria.

#### A10:2021 – Server-Side Request Forgery (SSRF)
- **Definição:** Ocorre quando a aplicação web consome uma URL remota fornecida pelo usuário sem validar o destino, permitindo que o servidor acesse recursos da rede interna.
- **Vetor comum:** Enviar URLs apontando para `http://169.254.169.254` (metadados de nuvem) ou bancos de dados internos.

---

### 2.2. OWASP API Security Top 10 (2023)

#### API1:2023 – Broken Object Level Authorization (BOLA / IDOR)
- **Definição:** A API expõe endpoints que manipulam objetos com base no ID fornecido pelo cliente (`/vehicles/{id}`), sem verificar se o cliente tem permissão sobre aquele objeto específico.

#### API2:2023 – Broken Authentication
- **Definição:** Mecanismos de autenticação mal implementados que permitem a forja de tokens, ataque de repetição ou aceitação de tokens expirados/adulterados.

#### API3:2023 – Broken Object Property Level Authorization (BOPLA / Mass Assignment)
- **Definição:** O cliente envia propriedades adicionais no payload JSON (ex.: `"role": "ADMIN"`) e a API atualiza cegamente a entidade no banco sem restrição.

#### API4:2023 – Unrestricted Resource Consumption
- **Definição:** Ausência de limites de requisições, paginação ou tamanho máximo de payload, permitindo esgotamento de CPU, memória ou conexões de banco de dados.

#### API5:2023 – Broken Function Level Authorization (BFLA)
- **Definição:** Falha na restrição de métodos ou funções administrativas. Por exemplo, um usuário com perfil comum consegue executar um método `DELETE` ou `POST` administrativo.

#### API6:2023 – Unrestricted Access to Sensitive Business Flows
- **Definição:** Falha ao não proteger fluxos críticos de negócio contra automação excessiva (ex.: criação de milhares de registros falsos de telemetria veicular).

#### API7:2023 – Server Side Request Forgery (SSRF na API)
- **Definição:** Endpoints da API que buscam recursos externos com base em URIs enviadas pelo cliente sem validação estrita.

#### API8:2023 – Security Misconfiguration
- **Definição:** Configurações de CORS frouxas (`*`), headers de segurança HTTP ausentes ou exposição de métodos não suportados.

#### API9:2023 – Improper Inventory Management
- **Definição:** Falta de controle de versões de APIs (APIs zumbis ou endpoints antigos e sem correções mantidos ativos em produção).

#### API10:2023 – Unsafe Consumption of APIs
- **Definição:** Confiar cegamente em dados retornados por serviços e APIs terceiras sem validação prévia de esquema e sanitização.

---

### 2.3. OWASP Mobile Top 10 (2024)

#### M1:2024 – Improper Credential Usage (Uso Inadequado de Credenciais)
- **Definição:** Credenciais ou chaves embutidas no binário do app (chave de API, segredo JWT, conta de serviço) ou transmitidas/armazenadas de forma insegura.
- **No Specvora:** o app nunca deve conter o `JWT_SECRET`, a chave do Firebase Admin ou credenciais do MongoDB. Ele só recebe o token de curta duração (2 h) emitido por `/auth/login` ou o ID token do Firebase.

#### M2:2024 – Inadequate Supply Chain Security (Cadeia de Suprimentos)
- **Definição:** SDKs e bibliotecas de terceiros comprometidos ou desatualizados, ou pipeline de build do app sem integridade.
- **No Specvora:** SDK Firebase e bibliotecas HTTP do app precisam de SCA (Dependabot/Snyk) e assinatura do binário no pipeline mobile.

#### M3:2024 – Insecure Authentication/Authorization
- **Definição:** Decisões de autenticação/autorização tomadas no cliente (ex.: esconder o botão "Excluir" e confiar nisso), sessões sem expiração, bypass offline.
- **No Specvora:** toda decisão é do servidor — a `SecurityFilterChain` aplica RBAC por método/rota, independentemente do que o app exibe.

#### M4:2024 – Insufficient Input/Output Validation
- **Definição:** Dados vindos da API, de deep links ou de QR codes usados sem validação, levando a injeção em WebViews ou SQLite local.
- **No Specvora:** o app deve validar respostas da API contra o esquema e nunca renderizar campos como HTML; a API já sanitiza entradas (`VehicleUpsertDTO`).

#### M5:2024 – Insecure Communication
- **Definição:** Tráfego em HTTP, aceitação de certificados inválidos, ausência de *certificate pinning* em redes hostis.
- **No Specvora:** somente HTTPS/TLS 1.2+ até a API (terminação TLS no ingress; a API envia HSTS), com *pinning* da CA corporativa no app.

#### M6:2024 – Inadequate Privacy Controls
- **Definição:** Coleta ou exposição excessiva de dados pessoais (localização do brigadista, identificadores) em logs, analytics ou telas.
- **No Specvora:** o backend já anonimiza o UID do Firebase com SHA-256 no `JwtAuthFilter`; o app deve aplicar minimização de dados (LGPD).

#### M7:2024 – Insufficient Binary Protections
- **Definição:** App sem ofuscação ou verificação de integridade, permitindo engenharia reversa e redistribuição adulterada.
- **No Specvora:** ofuscação (R8/ProGuard) e atestação (Google Play Integrity / App Attest), com o resultado validado no servidor.

#### M8:2024 – Security Misconfiguration
- **Definição:** `android:debuggable`, backups habilitados, componentes exportados sem permissão, *cleartext traffic* permitido.
- **No Specvora:** `usesCleartextTraffic=false`, `allowBackup=false` e revisão de componentes exportados no manifesto do app.

#### M9:2024 – Insecure Data Storage
- **Definição:** Tokens e dados sensíveis gravados em `SharedPreferences`, arquivos ou logs do dispositivo em texto claro.
- **No Specvora:** o token JWT deve ficar só no Android Keystore / iOS Keychain (ou `EncryptedSharedPreferences`) e ser apagado no logout.

#### M10:2024 – Insufficient Cryptography
- **Definição:** Algoritmos fracos (MD5, SHA-1, DES, AES-ECB), chaves fixas no código ou geração de números aleatórios previsível.
- **No Specvora:** o app deve usar as APIs criptográficas da plataforma; no backend o Semgrep bloqueia esses padrões (regras `java.lang.security.audit.crypto.*`) e o `LocalEncryptionService` usa AES-256-GCM com IV aleatório.

---

### 2.4. OWASP ASVS 5.0 — Nível de Verificação Adotado

O ASVS define três níveis:

| Nível | Indicado para | Profundidade |
|---|---|---|
| **L1** | Toda aplicação (mínimo) | Requisitos testáveis de fora (caixa-preta) |
| **L2** | Aplicações com dados sensíveis ou funções críticas de negócio | Maioria das aplicações; exige revisão de código e arquitetura |
| **L3** | Aplicações críticas (saúde, finanças, infraestrutura crítica) | Verificação máxima, incluindo defesa contra atacantes avançados |

**Nível alvo do Specvora: L2.** A API controla o catálogo de viaturas usado pela brigada de emergência (disponibilidade e integridade críticas) e processa identidades e perfis de acesso. L1 seria insuficiente; L3 fica como meta para os componentes IoT de campo.

---

## 3. Parte II: Matriz de Mapeamento de Risco para o Projeto Specvora / Ford

A tabela abaixo cruza as vulnerabilidades mapeadas com os ativos, endpoints e fluxos do projeto **Specvora Service**:

| Código OWASP | Vulnerabilidade | Componente / Endpoint Afetado | Probabilidade | Impacto | Risco Residual | Cenário de Ameaça no Specvora |
|---|---|---|:---:|:---:|:---:|---|
| **API1 / A01** | BOLA / Quebra de Controle de Acesso | `DELETE /vehicles/{id}`, `PUT /vehicles/{id}` | Média | Alto | **Baixo** | Invasor tentar alterar dados de viaturas de resgate passando IDs arbitrários. |
| **API2 / A07** | Broken Authentication / Forja JWT | `POST /auth/login`, `POST /auth/register` | Média | Crítico | **Baixo** | Assinatura de tokens forjados com algoritmo `none` ou segredo padrão fraco. |
| **API3 / A01** | BOPLA / Escalada de Privilégios | `POST /auth/register` | Alta | Crítico | **Baixo** | Atacante enviar `{"role": "ADMIN"}` no auto-registro para obter controle total. |
| **API4 / A04** | Consumo Ilimitado / DoS de API | `/auth/login`, `POST /vehicles/search` | Alta | Alto | **Baixo** | Ataques de força bruta automatizada ou saturação de CPU com payloads gigantescos. |
| **API5 / A01** | BFLA / Funções Administrativas | `DELETE /vehicles/{id}` | Alta | Alto | **Baixo** | Usuário com perfil `USER` tentar excluir veículos essenciais do catálogo. |
| **A03** | NoSQL Injection | `POST /vehicles`, `VehicleUpsertDTO.categories` | Média | Crítico | **Baixo** | Envio de operadores `$where` ou `$gt` em chaves de especificações para vazar dados. |
| **A02** | Falhas Criptográficas | `LocalEncryptionService`, Banco de Dados | Média | Alto | **Baixo** | Vazamento de credenciais ou telemetria sensível armazenada em repouso. |
| **A05 / API8** | Security Misconfiguration | Headers HTTP, CORS, Container Docker | Média | Médio | **Baixo** | Erros 500 expondo stack traces, CORS permissivo ou container rodando como root. |
| **A06** | Componentes Desatualizados | Gerenciador `pom.xml`, bibliotecas Maven | Alta | Alto | **Baixo** | Vulnerabilidades em bibliotecas de terceiros (CVEs conhecidas no ecossistema Java). |
| **A09** | Falhas de Logging e Monitoramento | Todos os endpoints e filtros | Média | Médio | **Baixo** | Falha em registrar tentativas de intrusão, impossibilitando resposta do SOC. |
| **M1 / A07** | Credenciais embutidas no app | App da brigada ↔ `/auth/login` | Média | Crítico | **Médio** | Extração do segredo JWT ou da chave do Firebase por engenharia reversa do APK, permitindo forjar tokens de ADMINISTRADOR. |
| **M3 / API5** | Autorização decidida no cliente | App da brigada ↔ `DELETE`/`PUT /vehicles` | Alta | Alto | **Baixo** | App adulterado reexibindo funções ocultas; mitigado porque o RBAC é aplicado no servidor. |
| **M5** | Comunicação insegura | App ↔ API em redes móveis | Média | Alto | **Médio** | Interceptação (MitM) do token em Wi-Fi público de pátio; exige TLS + *pinning*. |
| **M9** | Armazenamento inseguro no dispositivo | Token JWT no aparelho | Média | Alto | **Médio** | Aparelho perdido ou com *root* expõe token válido por até 2 h. |
| **M7** | Proteção insuficiente do binário | APK/IPA distribuído | Média | Médio | **Médio** | Versão modificada do app contornando validações locais. |

---

## 4. Parte III: Plano de Mitigação Arquitetural

A arquitetura do **Specvora Service** implementa contramedidas em camadas (*Defense-in-Depth*) para mitigar integralmente cada risco mapeado:

### 4.1. Mitigação de BOLA, BFLA e BOPLA (Controle de Acesso e RBAC)
1. **Hierarquia Formal de Perfis (`RoleHierarchy`):**
   - Configurada em [`SecurityConfig.java`](src/main/java/br/com/specvora_service/config/SecurityConfig.java): `ROLE_ADMINISTRADOR > ROLE_GESTOR > ROLE_USER`.
   - Endpoints de escrita (`POST /vehicles`, `PUT /vehicles/{id}`) exigem autoridade mínima de `ROLE_GESTOR`.
   - Endpoint de destruição (`DELETE /vehicles/{id}`) restrito a `ROLE_ADMINISTRADOR`.
   - Consultas (`GET /vehicles/**`, `POST /vehicles/search`) permitidas a `ROLE_USER`.
2. **Bloqueio de Escalada de Privilégios (Anti-Mass Assignment):**
   - Em [`AuthService.java`](src/main/java/br/com/specvora_service/auth/service/AuthService.java), o auto-registro anônimo/público atribui obrigatoriamente `ROLE_USER`.
   - Se o payload contiver solicitação de perfil elevado (`GESTOR` ou `ADMINISTRADOR`), o serviço exige que a requisição venha de um usuário autenticado com perfil `ROLE_ADMINISTRADOR`, caso contrário lança `AccessDeniedException` (HTTP **403 Forbidden**).

---

### 4.2. Mitigação de Broken Authentication e Força Bruta
1. **Rate Limiting Inteligente (Anti-Brute Force):**
   - Em [`RateLimitingFilter.java`](src/main/java/br/com/specvora_service/config/RateLimitingFilter.java), o algoritmo Token Bucket limita a rota crítica `/auth/login` a **5 requisições por minuto** por endereço IP (resolvido com `request.getRemoteAddr()`, imune a spoofing).
   - Bloqueio com status **429 Too Many Requests**, cabeçalho `Retry-After` e formato JSON estruturado.
2. **JWT Criptograficamente Seguro:**
   - Em [`JwtTokenService.java`](src/main/java/br/com/specvora_service/auth/service/JwtTokenService.java), o algoritmo é fixado em `HS256` imutável (impossibilitando ataque do algoritmo `none`).
   - Validação de entropia de no mínimo 256 bits (32 bytes); geração efêmera com `SecureRandom` caso a variável não seja configurada em dev.
   - Verificação rigorosa de audiência (`aud: specvora-api`) e emissor (`iss: specvora-service`).
   - Expiração estrita de 2 horas e propagação de `expiresAt`.

---

### 4.3. Mitigação de Injeção NoSQL e XSS
1. **Queries Parametrizadas com Criteria do Spring Data:**
   - Em [`VehicleRepositoryImpl.java`](src/main/java/br/com/specvora_service/vehicle/repository/VehicleRepositoryImpl.java), todas as consultas utilizam `Criteria.where().is(value)`, passando valores como parâmetros tipados, impedindo interpretação de operadores JSON.
2. **Sanitização de Chaves em Mapas Dinâmicos:**
   - Em [`VehicleUpsertDTO.java`](src/main/java/br/com/specvora_service/vehicle/dto/VehicleUpsertDTO.java), as chaves do campo `categories` são limpas removendo caracteres reservados do MongoDB (`$` e `.`).
3. **Validação de Entrada Estrita e Unicode:**
   - `@Pattern` com suporte Unicode `\p{L}` aceitando caracteres legítimos e bloqueando caracteres de injeção (`<`, `>`, `"`, `'`, `;`, `=`, `{`, `}`).
   - Sanitização de espaços em branco e normalização para minúsculas (`Locale.ROOT`).

---

### 4.4. Mitigação de Falhas Criptográficas (Data-at-Rest e Trânsito)
1. **Criptografia Local Autenticada (AES-256-GCM):**
   - Implementada em [`LocalEncryptionService.java`](src/main/java/br/com/specvora_service/security/LocalEncryptionService.java), utilizando AEAD com IV randômico de 12 bytes gerado por `SecureRandom` a cada chamada e tag de integridade de 128 bits.
   - Aplicada ao registro de auditoria dos usuários em [`AuthService.java`](src/main/java/br/com/specvora_service/auth/service/AuthService.java).
2. **Criptografia em Trânsito:**
   - Cabeçalho HSTS emitido pela API; a terminação TLS 1.2+ fica no ingress/balanceador do ambiente (não configurada neste repositório — ver lacuna V12 do ASVS).
   - Para os sensores IoT (fora deste repositório), recomenda-se MQTT sobre TLS (porta 8883) com certificados por dispositivo.

---

### 4.5. Mitigação de Consumo Excessivo de Recursos (DoS)
1. **Limites de Payload no Servidor Web:**
   - Em [`application.yml`](src/main/resources/application.yml), configurado `server.max-http-request-header-size: 8KB` e `server.tomcat.max-swallow-size: 256KB`.
2. **Limites de Tamanho de Senhas:**
   - Campo `password` restrito a `@Size(min = 6, max = 100)` para prevenir sobrecarga deliberada no algoritmo computacionalmente intensivo do **BCrypt**.
3. **Proteção de Memória nos Filtros:**
   - Capacidade máxima de 10.000 baldes no `RateLimitingFilter` com descarte de entradas antigas.

---

### 4.6. Mitigação de Configurações Incorretas (Security Misconfiguration)
1. **Tratamento Centralizado de Erros sem Stacktrace:**
   - Em [`GlobalExceptionHandler.java`](src/main/java/br/com/specvora_service/vehicle/exception/GlobalExceptionHandler.java), todos os erros de cliente (400, 404, 405, 409, 415) são tratados explicitamente com mensagens controladas, sem gerar erros 500 indesejados.
   - Respostas de erro formatadas de forma uniforme segundo a RFC 7807 através do [`ErrorResponseWriter.java`](src/main/java/br/com/specvora_service/config/ErrorResponseWriter.java).
2. **Cabeçalhos HTTP de Segurança Estritos:**
   - Injeção obrigatória de Content-Security-Policy (CSP), `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `HSTS` (1 ano) e `Permissions-Policy`.
3. **CORS Restrito:**
   - Origens parametrizadas sem wildcard (`*`), métodos explícitos (`GET`, `POST`, `PUT`, `DELETE`, `OPTIONS`) e cache de preflight de 1 hora.

---

### 4.7. Mitigação de Componentes Vulneráveis e Supply Chain
1. **Pipeline DevSecOps:**
   - **Secret Scanning (TruffleHog & Gitleaks) — bloqueante:** impede commits contendo chaves, tokens ou credenciais vazadas.
   - **SAST (Semgrep) — bloqueante:** varredura de código Java contra padrões OWASP Top 10 com upload de relatório SARIF.
   - **SCA (Dependabot com `cooldown` + Trivy FS) — informativo:** PRs semanais de atualização e relatório SARIF de CVEs do `pom.xml`.
   - **Container (Trivy Image) — informativo:** imagem multi-stage com usuário sem privilégios `appuser` (UID 10001) e relatório SARIF.

---

### 4.8. Mitigação de Falhas de Logging e Monitoramento
1. **Logging Estruturado em JSON:**
   - Implementado no [`SecurityAuditLogger.java`](src/main/java/br/com/specvora_service/security/SecurityAuditLogger.java), emitindo logs estruturados contendo `timestamp`, `event_type`, `severity`, `user_id`, `client_ip`, `http_method` e `status_code`.
2. **Monitoramento e Alertas Proativos:**
   - Regras configuradas para alertar o SOC sobre picos de 401/403/429, anomalias mobile, desconexões em massa de sensores IoT e desvio de modelos de ML, operando sob o framework de resposta **SANS PICERL** detalhado em [LOGS_ALERTAS_INCIDENTES.md](LOGS_ALERTAS_INCIDENTES.md).

---

### 4.9. Mitigação dos Riscos Mobile (OWASP Mobile Top 10)

| Risco | Controle no app | Controle no backend (já existente ou previsto) |
|---|---|---|
| M1 | Nenhum segredo no binário; apenas tokens de curta duração | `JWT_SECRET` só em variável de ambiente; Gitleaks/TruffleHog impedem commit de segredos |
| M3 | UI por perfil é só conveniência | RBAC na `SecurityFilterChain` + testes 401/403 (`SecurityFilterChainIntegrationTest`) |
| M5 | HTTPS obrigatório, *certificate pinning*, `usesCleartextTraffic=false` | HSTS nos cabeçalhos; TLS terminado no ingress |
| M6 | Minimização de dados e consentimento (LGPD) | UID do Firebase anonimizado (SHA-256) antes de ir para o contexto e logs |
| M7 | R8/ProGuard + Play Integrity / App Attest | (Previsto) validar o veredito de atestação no servidor antes de aceitar o token |
| M9 | Token no Keystore/Keychain; limpeza no logout | Expiração de 2 h; evento `AUTH_TOKEN_EXPIRED` registrado |
| M2, M8, M10 | SCA no pipeline mobile; manifesto endurecido; APIs criptográficas da plataforma | Dependabot com `cooldown`; Semgrep bloqueia criptografia fraca |

---

### 4.10. Checklist ASVS 5.0 (Nível 2) — Situação Atual do Specvora Service

Requisitos-chave de cada capítulo aplicável, verificados contra o código deste repositório:

| Capítulo ASVS | Requisito verificado (resumo) | Evidência no projeto | Status |
|---|---|---|:---:|
| **V1 Encoding and Sanitization** | Consultas ao banco parametrizadas; saída codificada conforme o contexto | `VehicleRepositoryImpl` usa `Criteria`; respostas somente JSON | ✅ |
| **V2 Validation and Business Logic** | Validação positiva de entrada no servidor; limites contra automação de fluxos | Bean Validation nos DTOs; bloqueio de chaves `$`/`.`; rate limit | ✅ |
| **V4 API and Web Service** | `Content-Type` validado; métodos HTTP restritos; CORS com lista de origens | 415/405 tratados; `CrossOriginConfig` com origens explícitas | ✅ |
| **V6 Authentication** | Senhas com hash adaptativo; proteção contra força bruta | BCrypt; 5 tentativas/min em `/auth/login` | ✅ |
| **V6 Authentication** | Credenciais padrão inexistentes em produção | Usuários `admin/gestor/user` de demonstração criados no `AuthService` | ❌ |
| **V7 Session Management** | Sessão *stateless* com expiração definida | `SessionCreationPolicy.STATELESS`; JWT de 2 h | ✅ |
| **V7 Session Management** | Revogação de sessão (logout / conta comprometida) | Não há lista de revogação de JWT | ⚠️ |
| **V8 Authorization** | Autorização no servidor, negar por padrão, por função | `anyRequest().authenticated()`; RBAC + `RoleHierarchy` | ✅ |
| **V9 Self-contained Tokens** | Assinatura verificada, algoritmo fixo, `iss`/`aud`/`exp` validados | `JwtTokenService` (HS256, issuer, audience, expiração) | ✅ |
| **V11 Cryptography** | Algoritmos aprovados; IV único; chaves fora do código | AES-256-GCM com IV aleatório; `AES_SECRET` do ambiente | ✅ |
| **V11 Cryptography** | Chaves geridas por cofre/KMS e rotacionáveis | Chave vinda de variável de ambiente, sem KMS | ⚠️ |
| **V12 Secure Communication** | TLS em todas as conexões | HSTS habilitado; TLS depende do ingress (não configurado neste repositório) | ⚠️ |
| **V13 Configuration** | Segredos fora do código; dependências monitoradas; cabeçalhos de segurança | `.env`/ambiente; Dependabot; CSP, HSTS, X-Frame-Options | ✅ |
| **V14 Data Protection** | Dados sensíveis protegidos em repouso; minimização | UID anonimizado; registro de auditoria cifrado | ⚠️ |
| **V16 Security Logging and Error Handling** | Eventos de autenticação, autorização e alterações críticas registrados; erros sem detalhes internos | `SecurityAuditLogger` (login, falhas, 401/403/409/429, CRUD); `GlobalExceptionHandler` sem stack trace | ✅ |

**Resumo:** 11 de 15 requisitos verificados atendidos. Lacunas para atingir L2: remover os usuários de demonstração fora do perfil `dev`, implementar revogação de token, usar cofre de segredos (Vault/KMS) e documentar a terminação TLS do ambiente.

---

## 5. Conclusão

A integração entre as práticas de desenvolvimento seguro, controles criptográficos ativos, validação rigorosa de entrada, separação estrita de privilégios via RBAC 3-Tier e automação de testes no pipeline DevSecOps consolida uma arquitetura resiliente e protegida contra as vulnerabilidades mais críticas dos catálogos **OWASP Top 10 Web** e **OWASP API Security Top 10**. O **OWASP Mobile Top 10** define os requisitos do aplicativo da brigada e do seu contrato com a API, e o **ASVS 5.0 Nível 2** serve de régua verificável: a verificação atual aponta quatro lacunas (credenciais de demonstração, revogação de token, gestão de chaves em cofre e TLS de borda), que formam o backlog de segurança da próxima Sprint.
