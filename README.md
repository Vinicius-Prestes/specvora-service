# Specvora Service

Sistema agregador de dados que permite aos usuários consultar veículos e visualizar suas especificações técnicas.

---

## Arquitetura

O serviço segue o padrão **SOA (Service-Oriented Architecture)** com separação clara entre camadas:

```
┌─────────────────────────────────────────────────┐
│                Client / Browser                  │
└────────────────────────┬────────────────────────┘
                         │ HTTP/REST  Authorization: Bearer <token>
┌────────────────────────▼────────────────────────┐
│               Camada de Segurança                │
│   Spring Security · Firebase Auth · JwtAuthFilter│
└────────────────────────┬────────────────────────┘
                         │
┌────────────────────────▼────────────────────────┐
│              Camada de Apresentação              │
│         VehicleController  (REST API)            │
│         VehicleApi         (contrato)            │
└────────────────────────┬────────────────────────┘
                         │
┌────────────────────────▼────────────────────────┐
│                Camada de Serviço                 │
│              VehicleService                      │
└────────────────────────┬────────────────────────┘
                         │
┌────────────────────────▼────────────────────────┐
│                 Camada de Dados                  │
│    VehicleRepository / VehicleRepositoryImpl     │
└────────────────────────┬────────────────────────┘
                         │
┌────────────────────────▼────────────────────────┐
│                    MongoDB                       │
│         Collection: vehicles                     │
└─────────────────────────────────────────────────┘
```

### Estrutura de pacotes

```
src/main/java/br/com/specvora_service/
├── config/
│   ├── CrossOriginConfig.java    # Configuração de CORS e origins permitidas
│   ├── FirebaseConfig.java       # Inicialização do Firebase Admin SDK
│   ├── MongoConfig.java          # Configuração do pool de conexões MongoDB
│   ├── OpenApiConfig.java        # Configuração Swagger / OpenAPI
│   └── SecurityConfig.java       # Configuração do Spring Security (FilterChain)
├── security/
│   └── JwtAuthFilter.java        # Filtro de validação do Bearer token
├── vehicle/
│   ├── controller/
│   │   ├── VehicleApi.java       # Contrato da API (interface com anotações Swagger)
│   │   └── VehicleController.java
│   ├── domain/
│   │   └── VehicleModel.java     # Entidade MongoDB
│   ├── dto/
│   │   └── VehicleRequestDTO.java
│   ├── exception/
│   │   ├── VehicleNotFoundException.java
│   │   └── GlobalExceptionHandler.java
│   ├── migration/
│   │   └── InitialVehiclesChangeLog.java  # Migração Mongock (índices)
│   ├── repository/
│   │   ├── VehicleRepository.java
│   │   ├── VehicleRepositoryCustom.java
│   │   └── VehicleRepositoryImpl.java
│   └── service/
│       └── VehicleService.java
```

---

## Endpoints

A documentação interativa (Swagger UI) está disponível em `http://localhost:8080/swagger-ui.html` após iniciar a aplicação.

| Método   | Endpoint             | Descrição                              | Status de sucesso |
|----------|----------------------|----------------------------------------|-------------------|
| `GET`    | `/vehicles`          | Lista todos os veículos cadastrados    | `200 OK`          |
| `GET`    | `/vehicles/{id}`     | Busca veículo pelo ID                  | `200 OK`          |
| `POST`   | `/vehicles/search`   | Busca veículo por especificações       | `200 OK`          |

### Exemplo – busca por especificações (`POST /vehicles/search`)

**Request:**
```json
{
  "brand": "FORD",
  "model": "Nova Ranger 4x4",
  "version": "XLT",
  "engine": "3.0 V6 - 24V",
  "year": "2026"
}
```

**Response `200 OK`:**
```json
{
  "id": "...",
  "brand": "ford",
  "model": "nova ranger 4x4",
  "version": "xlt",
  "engine": "3.0 v6 - 24v",
  "year": "2026",
  "vehicleCategory": "picape",
  "categories": { ... }
}
```

### Respostas de erro

| Status | Situação                              |
|--------|---------------------------------------|
| `400`  | Dados de entrada inválidos            |
| `401`  | Token ausente, inválido ou expirado   |
| `404`  | Veículo não encontrado                |
| `409`  | Requisição duplicada (Idempotency-Key)|
| `429`  | Muitas requisições (rate limit)       |
| `500`  | Erro interno do servidor              |

---

## Segurança

### Autenticação

A API utiliza **Firebase Authentication** como provedor de identidade. O cliente autentica-se via Firebase SDK e inclui o ID Token obtido no header de cada request:

```
Authorization: Bearer <firebase-id-token>
```

O `JwtAuthFilter` intercepta todas as requests protegidas e valida o token via **Firebase Admin SDK**. Tokens inválidos ou expirados retornam `401 Unauthorized` sem expor detalhes do erro.

### CORS

Configurado via `CrossOriginConfig`, com origins permitidas definidas em `application.yml` (`http.cors.allowed-origins`). Apenas os métodos `GET` e `POST` são permitidos, condizente com a natureza de consumo da API.

### Endpoints públicos

Os seguintes endpoints não requerem autenticação:

| Endpoint            | Descrição               |
|---------------------|-------------------------|
| `/v3/api-docs/**`   | Especificação OpenAPI   |
| `/swagger-ui/**`    | Interface Swagger UI    |
| `/swagger-ui.html`  | Redirect Swagger UI     |

### Injeção (NoSQL Injection)

O projeto utiliza MongoDB via `MongoTemplate` com a API `Criteria` do Spring Data. Entradas do usuário são passadas como parâmetros tipados — nunca concatenadas em strings de query — eliminando a superfície de ataque de injeção NoSQL.

### XSS (Cross-Site Scripting)

A API retorna exclusivamente JSON, sem renderização de HTML. O Spring Security adiciona automaticamente os headers `X-Content-Type-Options: nosniff` e `X-Frame-Options: DENY`. A serialização via Jackson realiza encoding de caracteres especiais na saída.

### Normalização de entrada

Os dados de requisição são normalizados antes da consulta: campos são `trim`ados, espaços consecutivos são reduzidos e valores são convertidos para `lowercase`.

### Buffer Overflow

A JVM gerencia memória automaticamente com bounds checking em tempo de execução. Não há acesso direto a ponteiros ou buffers de memória no código da aplicação.

### Idempotência

Endpoints `POST` aceitam o cabeçalho `Idempotency-Key`, evitando que requisições duplicadas comecem a mesma operação duas vezes.

### Flooding

A aplicação utiliza `bucket4j-core` para rate limiting ativo. O limite padrão é de 60 requisições por minuto por usuário autenticado; requisições anônimas são rateadas por IP.

### Anonimização de usuário

O token Firebase é validado e o identificador do usuário é armazenado de forma anônima/hashing no contexto de segurança, reduzindo exposição de UID original.

### Configuração sensível

Os dados sensíveis de conexão são lidos via variáveis de ambiente e não devem ficar hardcoded no código:
- `MONGODB_URI`
- `FIREBASE_CREDENTIALS_PATH`

### Stacktrace Leak

O `GlobalExceptionHandler` captura todas as exceções e retorna mensagens genéricas e controladas, sem expor stack traces ou detalhes internos. O `JwtAuthFilter` trata `FirebaseAuthException` retornando `401 Unauthorized` com mensagem fixa, sem propagar a causa original do erro.

---

## Tecnologias

| Tecnologia        | Versão   | Uso                                        |
|-------------------|----------|--------------------------------------------|
| Java              | 21       | Linguagem principal                        |
| Spring Boot       | 4.0.5    | Framework web                              |
| Spring Data MongoDB | —      | Acesso ao banco de dados                   |
| MongoDB           | 7+       | Banco de dados de documentos               |
| Mongock           | 5.4.4    | Controle de migrações MongoDB              |
| SpringDoc OpenAPI | 2.8.8    | Documentação automática da API (Swagger)   |
| Lombok            | —        | Redução de boilerplate                     |
| Maven             | 3.9+     | Gerenciador de dependências e build        |

---

## Pré-requisitos

- JDK 21
- Maven 3.9+
- MongoDB 7+ rodando em `localhost:27017`

---

## Como simular ambiente

```bash
# Clonar o repositório
git clone <url-do-repositorio>
cd specvora-service

# Subir MongoDB (via Docker, opcional)
docker pull mongo
docker run -d --name mongodb -p 27017:27017 -e MONGO_INITDB_ROOT_USERNAME=specvora -e MONGO_INITDB_ROOT_PASSWORD=fiap mongo
```

Antes de iniciar, defina as variáveis de ambiente sensíveis:

```bash
export MONGODB_URI="mongodb://specvora:fiap@localhost:27017/?authSource=admin"
export FIREBASE_CREDENTIALS_PATH="/path/to/firebase-key.json"
```

Em seguida:

```bash
./mvnw spring-boot:run
```

Caso utilize o docker local fornecido, string de conexão fica como segue:

```
mongodb://specvora:fiap@localhost:27017/?authSource=admin
```

---

## Migrações de banco de dados

O projeto utiliza **Mongock** para controle de migrações do MongoDB. As migrações ficam em `vehicle/migration/` e são executadas automaticamente ao iniciar a aplicação.

| Migração                   | Descrição                                      |
|----------------------------|------------------------------------------------|
| `vehicles-indexes-v1`      | Cria índices em `brand`, `model` e `year`      |

O histórico das migrações aplicadas é armazenado na coleção `mongockChangeLog` dentro do banco `specvora`.

---

## Pipeline DevSecOps Integrado (CI/CD)

O Specvora Service adota o conceito de **Shift-Left Security**, incorporando verificações rigorosas de segurança em todas as fases do pipeline de integração e entrega contínua (**GitHub Actions**):

```
[ Git Commit / PR ]
         │
         ├──► 1. Secret Scanning (Gitleaks / GitGuardian)
         ├──► 2. SCA - Análise de Dependências (Dependabot / Snyk / Trivy)
         ├──► 3. SAST - Análise Estática de Código (Semgrep / OWASP Rules)
         ▼
[ 4. Build & Test (Maven + JUnit) ]
         ▼
[ 5. Container Security (Docker + Trivy) ]
         ▼
[ 6. Deploy com Quality Gate (Staging / Produção) ]
```

- **Secret Scanning**: Gitleaks configurado via `.gitleaks.toml` para barrar credenciais e chaves privadas do Firebase.
- **SCA**: Dependabot (`.github/dependabot.yml`) e Snyk / Trivy para escanear e atualizar dependências vulneráveis no `pom.xml`.
- **SAST**: Semgrep inspecionando o código Java contra as diretrizes do OWASP Top 10.
- **Container Hardening**: Dockerfile multi-stage com usuário não-root (`appuser`).
- **Quality Gates**: Bloqueio automático de release se houver vulnerabilidades críticas ou segredos expostos.

Consulte a documentação técnica e arquitetural detalhada em **[DEVSECOPS.md](DEVSECOPS.md)** e **[security.md](security.md)**.
