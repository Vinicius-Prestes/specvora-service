# Specvora Service — Pesquisa de Vulnerabilidades (OWASP Top 10 & OWASP API Security)

Este documento consolida a entrega da **Etapa 4 (Challenge Sprint 3 - Cybersecurity)** do projeto **Specvora Service**. Ele apresenta uma **pesquisa técnica detalhada sobre as principais vulnerabilidades em aplicações web e APIs**, acompanhada de uma **Matriz de Mapeamento de Riscos específica para o ecossistema Specvora / Ford** e do **Plano de Mitigação Arquitetural** implementado para neutralizar cada uma dessas ameaças.

---

## 1. Visão Geral e Fundamentação Teórica

A segurança no desenvolvimento moderno de software (*Application Security - AppSec*) exige o mapeamento proativo de ameaças com base em padrões consolidados pela indústria. A **OWASP (Open Web Application Security Project)** mantém duas referências essenciais:

1. **OWASP Top 10 (Web Applications):** Focado em riscos estruturais e vulnerabilidades clássicas de código e infraestrutura web.
2. **OWASP API Security Top 10:** Especializado nas ameaças únicas de arquiteturas orientadas a microsserviços, REST e GraphQL, onde a lógica de negócios e os endpoints são expostos diretamente a clientes automatizados, dispositivos móveis e sensores IoT.

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
   - Utilizada ativamente para cifrar registros de auditoria em repouso em [`AuthService.java`](src/main/java/br/com/specvora_service/auth/service/AuthService.java).
2. **Criptografia em Trânsito (TLS 1.2+ e mTLS):**
   - Comunicação com broker MQTT em porta 8883 segura com certificados mútuos (mTLS).
   - Suporte a HTTPS com TLS 1.3 / 1.2 e HTTP/2 na API REST.

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
1. **Pipeline DevSecOps com Quality Gates Bloqueantes:**
   - **Secret Scanning (TruffleHog & Gitleaks):** Bloqueio imediato de commits contendo chaves, tokens ou credenciais vazadas.
   - **SCA (Dependabot & Trivy FS):** Varredura automática do `pom.xml`, falhando o build em severidades críticas/altas (`exit-code: 1`).
   - **SAST (Semgrep):** Varredura de código Java contra padrões OWASP Top 10 com upload de relatório SARIF para o GitHub Security.
   - **IaC Scan (Trivy Config):** Análise do Dockerfile e docker-compose.
   - **Container Hardening:** Imagem multi-stage construída com usuário sem privilégios `appuser` (UID 10001).

---

### 4.8. Mitigação de Falhas de Logging e Monitoramento
1. **Logging Estruturado em JSON:**
   - Implementado no [`SecurityAuditLogger.java`](src/main/java/br/com/specvora_service/security/SecurityAuditLogger.java), emitindo logs estruturados contendo `timestamp`, `event_type`, `severity`, `user_id`, `client_ip`, `http_method` e `status_code`.
2. **Monitoramento e Alertas Proativos:**
   - Regras configuradas para alertar o SOC sobre picos de 401/403/429, anomalias mobile, desconexões em massa de sensores MQTT e desvio de modelos de ML, operando sob o framework de resposta **SANS PICERL** detalhado em [LOGS_ALERTAS_INCIDENTES.md](LOGS_ALERTAS_INCIDENTES.md).

---

## 5. Conclusão

A integração entre as práticas de desenvolvimento seguro, controles criptográficos ativos, validação rigorosa de entrada, separação estrita de privilégios via RBAC 3-Tier e automação de testes no pipeline DevSecOps consolida uma arquitetura resiliente e protegida contra as vulnerabilidades mais críticas dos catálogos **OWASP Top 10 Web** e **OWASP API Security Top 10**.
