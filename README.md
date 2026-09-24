# Specvora Service

> **API RESTful Nível 2** para agregação, consulta e gerenciamento de especificações técnicas de veículos, desenvolvida com Spring Boot, MongoDB, Autenticação JWT com RBAC (Role-Based Access Control) e Pipeline DevSecOps Integrado.

---

## 1. Arquitetura da Solução

O serviço adota uma arquitetura em camadas bem delimitada, baseada nos princípios de **Clean Architecture** e **SOA (Service-Oriented Architecture)**, garantindo baixo acoplamento e alta coesão:

### 1.1. Diagrama de Componentes e Responsabilidades

```mermaid
flowchart TD
    classDef client fill:#e1f5fe,stroke:#0288d1,stroke-width:2px;
    classDef sec fill:#fff3e0,stroke:#f57c00,stroke-width:2px;
    classDef ctrl fill:#e8eaf6,stroke:#3949ab,stroke-width:2px;
    classDef srv fill:#f3e5f5,stroke:#7b1fa2,stroke-width:2px;
    classDef data fill:#e8f5e9,stroke:#388e3c,stroke-width:2px;
    classDef db fill:#efebe9,stroke:#5d4037,stroke-width:2px;

    Client["Cliente HTTP / Frontend / Swagger UI"]:::client

    subgraph SecurityLayer ["Camada de Segurança & Filtros HTTP"]
        CORS["CrossOriginConfig<br/>(CORS Restrito)"]:::sec
        JWTFilter["JwtAuthFilter<br/>(Extração & Validação de JWT)"]:::sec
        IdempFilter["IdempotencyFilter<br/>(Prevenção de Duplicidades)"]:::sec
        RateFilter["RateLimitingFilter<br/>(Bucket4j - 60 req/min)"]:::sec
        SecConfig["SecurityConfig<br/>(RBAC: ROLE_USER / ROLE_ADMIN)"]:::sec
    end

    subgraph PresentationLayer ["Camada de Apresentação (REST Controllers)"]
        AuthController["AuthController / AuthApi<br/>(/auth/login, /auth/register, /auth/me)"]:::ctrl
        VehicleController["VehicleController / VehicleApi<br/>(/vehicles - CRUD Nível 2)"]:::ctrl
        GlobalHandler["GlobalExceptionHandler<br/>(Respostas de Erro Padronizadas)"]:::ctrl
    end

    subgraph ServiceLayer ["Camada de Negócio & Segurança"]
        AuthService["AuthService<br/>(Gestão de Usuários & BCrypt)"]:::srv
        JwtService["JwtTokenService<br/>(Geração & Validação HMAC256)"]:::srv
        VehicleService["VehicleService<br/>(Regras de Negócio & Normalização)"]:::srv
    end

    subgraph PersistenceLayer ["Camada de Dados"]
        VehicleRepo["VehicleRepository<br/>(Spring Data MongoDB + Criteria Custom)"]:::data
        Mongock["MongockChangeLog<br/>(Migrações de Índices)"]:::data
    end

    Database[("MongoDB<br/>Collection: vehicles")]:::db

    Client --> CORS --> JWTFilter --> IdempFilter --> RateFilter --> SecConfig
    SecConfig --> AuthController & VehicleController
    AuthController --> AuthService --> JwtService
    VehicleController --> VehicleService --> VehicleRepo --> Database
    Mongock -.-> Database
    VehicleController -.-> GlobalHandler
```

### 1.2. Fluxo de Comunicação e Autenticação

```mermaid
sequenceDiagram
    autonumber
    actor User as Cliente / Postman
    participant Sec as SecurityFilterChain / JwtAuthFilter
    participant Auth as AuthController / AuthService
    participant Token as JwtTokenService
    participant Ctrl as VehicleController
    participant Srv as VehicleService
    participant DB as MongoDB

    %% Fluxo de Login
    Note over User,Token: 1. Fluxo de Autenticação e Emissão de Token (Público)
    User->>Auth: POST /auth/login {username, password}
    Auth->>Auth: Valida senha via BCrypt
    Auth->>Token: generateToken(username, roles)
    Token-->>Auth: Retorna JWT assinado com claims e expiração (2h)
    Auth-->>User: 200 OK {token: "Bearer eyJhbG...", roles: ["ROLE_ADMIN"]}

    %% Fluxo de Consulta/Escrita Protegida
    Note over User,DB: 2. Fluxo de Acesso a Recurso Protegido com RBAC
    User->>Sec: POST /vehicles [Header: Authorization: Bearer eyJ...]
    Sec->>Token: validateToken(token) & getAuthentication(token)
    Token-->>Sec: Authentication {Principal: admin, Authorities: [ROLE_ADMIN]}
    Sec->>Sec: Verifica se usuário possui ROLE_ADMIN
    alt Perfil Válido (ROLE_ADMIN)
        Sec->>Ctrl: Encaminha para createVehicle(dto)
        Ctrl->>Srv: createVehicle(dto)
        Srv->>Srv: normalize() (trim, lowercase, saneamento)
        Srv->>DB: save(vehicleModel)
        DB-->>Srv: Entidade persistida com ID
        Srv-->>Ctrl: VehicleModel
        Ctrl-->>User: 201 Created [Header Location: /vehicles/{id}]
    else Perfil Insuficiente (ROLE_USER)
        Sec-->>User: 403 Forbidden {status: 403, error: "Forbidden", message: "Acesso proibido..."}
    else Token Ausente ou Inválido
        Sec-->>User: 401 Unauthorized {status: 401, error: "Unauthorized"}
    end
```

### 1.3. Estrutura de Pacotes

```
src/main/java/br/com/specvora_service/
├── auth/                                # Módulo de Autenticação e JWT
│   ├── controller/
│   │   ├── AuthApi.java                 # Contrato e anotações OpenAPI
│   │   └── AuthController.java          # REST Controller (/auth)
│   ├── dto/
│   │   ├── LoginRequestDTO.java         # Request de login
│   │   ├── LoginResponseDTO.java        # Resposta com JWT e roles
│   │   ├── RegisterRequestDTO.java      # Request de cadastro
│   │   └── UserResponseDTO.java          # Dados do usuário e perfil
│   └── service/
│       ├── AuthService.java             # Gestão de credenciais (BCrypt)
│       └── JwtTokenService.java         # Geração e validação de tokens JWT
├── config/                              # Configurações de Infraestrutura
│   ├── CrossOriginConfig.java           # Restrição de CORS
│   ├── FirebaseConfig.java              # Integração resiliente Firebase Admin
│   ├── IdempotencyFilter.java           # Prevenção de duplicações via header
│   ├── JwtAuthFilter.java               # Filtro de autenticação Bearer JWT
│   ├── MongoConfig.java                 # Conexão e pool MongoDB
│   ├── OpenApiConfig.java               # Configuração Swagger com BearerAuth
│   ├── RateLimitingFilter.java          # Bucket4j (Rate Limit)
│   └── SecurityConfig.java              # Spring Security Filter Chain e RBAC
└── vehicle/                             # Módulo de Domínio de Veículos
    ├── controller/
    │   ├── VehicleApi.java              # Contrato OpenAPI REST Nível 2
    │   └── VehicleController.java       # CRUD REST Controller
    ├── domain/
    │   └── VehicleModel.java            # Entidade mapeada no MongoDB
    ├── dto/
    │   ├── VehicleRequestDTO.java       # DTO para busca parametrizada
    │   └── VehicleUpsertDTO.java        # DTO para criação e atualização
    ├── exception/
    │   ├── ErrorResponseDTO.java        # Resposta padronizada de erro
    │   ├── GlobalExceptionHandler.java  # Tratamento centralizado de erros
    │   └── VehicleNotFoundException.java# Exceção de negócio para 404
    ├── migration/
    │   └── InitialVehiclesChangeLog.java# Migrações Mongock
    ├── repository/
    │   ├── VehicleRepository.java       # Spring Data MongoRepository
    │   ├── VehicleRepositoryCustom.java # Assinatura de busca avançada
    │   └── VehicleRepositoryImpl.java   # Implementação com Criteria
    └── service/
        └── VehicleService.java          # Regras de negócio e CRUD
```

---

## 2. Autenticação, Autorização e JWT

O Specvora Service oferece um ecossistema seguro de autenticação e controle de acesso baseado em **JWT (JSON Web Token)** com suporte a **RBAC (Role-Based Access Control)**:

### 2.1. Perfis de Acesso (Roles)
- **`ROLE_ADMIN`**: Perfil com privilégio total. Permite cadastrar novos veículos (`POST /vehicles`), atualizar registros existentes (`PUT /vehicles/{id}`) e excluir veículos (`DELETE /vehicles/{id}`), além de realizar consultas.
- **`ROLE_USER`**: Perfil para clientes e consumidores da API. Permite listar veículos (`GET /vehicles`), buscar por ID (`GET /vehicles/{id}`), pesquisar por especificações técnicas (`POST /vehicles/search`) e consultar o próprio perfil (`GET /auth/me`). Não possui permissão para modificar ou apagar dados (retorna `403 Forbidden`).

### 2.2. Usuários Pré-configurados para Teste
A aplicação já inicia com duas credenciais prontas para validação:

| Usuário | Senha | Perfil / Permissão |
|---|---|---|
| **`admin`** | `admin123` | **`ROLE_ADMIN`**, `ROLE_USER` |
| **`user`** | `user123` | **`ROLE_USER`** |

Novos usuários também podem ser cadastrados a qualquer momento via `POST /auth/register`.

### 2.3. Especificação do JWT
- **Algoritmo:** HMAC256 (`HS256`).
- **Assinatura:** Chave criptográfica configurável (`security.jwt.secret`).
- **Validade:** 2 horas (`security.jwt.expiration-hours`).
- **Claims incorporadas:**
  - `sub`: Nome do usuário (username).
  - `roles`: Lista de perfis concedidos (ex.: `["ROLE_ADMIN", "ROLE_USER"]`).
  - `iss`: `"specvora-service"`.
  - `iat`: Timestamp de emissão.
  - `exp`: Timestamp de expiração.
- **Dual-mode de validação:** O filtro `JwtAuthFilter` valida o JWT emitido internamente e, de forma transparente, também suporta tokens do Firebase Authentication caso configurado.

---

## 3. Maturidade REST — Nível 2 (Richardson Maturity Model)

A API adota integralmente o **Nível 2** do modelo de maturidade REST, caracterizado por:
1. **Recursos bem definidos em URIs no plural** (`/vehicles`, `/auth`).
2. **Uso semântico estrito dos métodos HTTP** (`GET`, `POST`, `PUT`, `DELETE`).
3. **Status codes coerentes com a operação realizada**.

### 3.1. Matriz de Endpoints

| Método | Endpoint | Perfil Necessário | Status de Sucesso | Status de Erro Possíveis | Descrição |
|---|---|---|---|---|---|
| `POST` | `/auth/login` | Público | `200 OK` | `400`, `401` | Autentica e emite token JWT com roles |
| `POST` | `/auth/register` | Público | `201 Created` | `400` | Cadastra novo usuário (`USER` ou `ADMIN`) |
| `GET` | `/auth/me` | Autenticado | `200 OK` | `401` | Retorna username e roles do token atual |
| `GET` | `/vehicles` | `USER` / `ADMIN` | `200 OK` | `401` | Lista todos os veículos |
| `GET` | `/vehicles/{id}` | `USER` / `ADMIN` | `200 OK` | `401`, `404` | Busca detalhes de um veículo por ID |
| `POST` | `/vehicles/search` | `USER` / `ADMIN` | `200 OK` | `400`, `401`, `404` | Busca veículo por especificações técnicas |
| `POST` | `/vehicles` | **`ADMIN`** | **`201 Created`** | `400`, `401`, **`403`** | Cadastra veículo (inclui Header `Location`) |
| `PUT` | `/vehicles/{id}` | **`ADMIN`** | **`200 OK`** | `400`, `401`, **`403`**, `404` | Atualiza dados de um veículo |
| `DELETE` | `/vehicles/{id}` | **`ADMIN`** | **`204 No Content`** | `401`, **`403`**, `404` | Exclui um veículo do catálogo |

---

## 4. Padronização de Erros e Documentação OpenAPI

### 4.1. Resposta de Erro Padronizada (`ErrorResponseDTO`)

Todas as falhas da API (de validação, regras de negócio ou segurança) retornam uma resposta uniforme inspirada no padrão **RFC 7807 (Problem Details)**:

```json
{
  "timestamp": "2026-09-24T03:45:00.123Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Falha na validação dos campos da requisição",
  "path": "/vehicles",
  "fieldErrors": {
    "brand": "Marca é obrigatória",
    "year": "Ano é obrigatório"
  }
}
```

### 4.2. Documentação Interativa com Swagger UI

A documentação interativa da API está disponível em:
👉 **`http://localhost:8080/swagger-ui.html`**

**Como testar pelo Swagger UI:**
1. Abra o endpoint `POST /auth/login` e execute com o usuário `admin` e senha `admin123`.
2. Copie o valor do campo `"token"` retornado.
3. Clique no botão **Authorize** (com ícone de cadeado no topo da página).
4. Cole o token no campo de texto e clique em **Authorize**.
5. Todos os endpoints protegidos agora podem ser testados diretamente pela interface.

---

## 5. Testes Automatizados

A suíte de testes cobre integralmente os comportamentos críticos da API, incluindo cenários de sucesso, validação de dados, regras de negócio, autenticação e restrições de acesso não autorizado:

| Classe de Teste | Camada Testada | Cenários Validados |
|---|---|---|
| [`JwtTokenServiceTest`](file:///src/test/java/br/com/specvora_service/auth/JwtTokenServiceTest.java) | Token / Criptografia | Geração de token, assinatura HMAC256, extração de roles, rejeição de tokens adulterados e expiração. |
| [`AuthServiceTest`](file:///src/test/java/br/com/specvora_service/auth/AuthServiceTest.java) | Segurança / Negócio | Login com sucesso (`admin` e `user`), validação de senhas com BCrypt, rejeição de credenciais inválidas (`401`), cadastro e rejeição de duplicidade. |
| [`VehicleServiceTest`](file:///src/test/java/br/com/specvora_service/vehicle/VehicleServiceTest.java) | Regras de Negócio | CRUD completo: `findAll`, `findById`, busca por especificações com normalização de dados, criação, atualização, exclusão e lançamento de `VehicleNotFoundException` (`404`). |
| [`VehicleControllerTest`](file:///src/test/java/br/com/specvora_service/vehicle/VehicleControllerTest.java) | Controller REST Nível 2 | `GET /vehicles` (200), `GET /vehicles/{id}` (200 e 404), `POST /vehicles/search` (200 e 400), `POST /vehicles` (**201 Created** com header **Location**), `PUT /vehicles/{id}` (200), `DELETE /vehicles/{id}` (**204 No Content**). |
| [`AuthControllerTest`](file:///src/test/java/br/com/specvora_service/auth/AuthControllerTest.java) | Controller / Auth | Login com emissão de token (200), rejeição de credenciais (401), cadastro de usuário (201) e recuperação de perfil (`/auth/me`). |
| [`GlobalExceptionHandlerTest`](file:///src/test/java/br/com/specvora_service/vehicle/GlobalExceptionHandlerTest.java) | Tratamento de Erros | Validação de conversão para status codes padronizados: 400, 401, 403, 404 e 500 sem vazamento de stacktrace. |

### Como Executar os Testes
Para rodar a suíte completa de testes automatizados e gerar o relatório:

```bash
./mvnw test
```

---

## 6. Pipeline DevSecOps Integrado

O projeto conta com uma pipeline CI/CD automatizada no **GitHub Actions** ([`.github/workflows/devsecops.yml`](file:///.github/workflows/devsecops.yml)), integrando:
- **Secret Scanning:** Gitleaks com regras customizadas em [`.gitleaks.toml`](file:///.gitleaks.toml).
- **SCA (Software Composition Analysis):** Dependabot ([`.github/dependabot.yml`](file:///.github/dependabot.yml)) + Snyk & Trivy.
- **SAST (Static Application Security Testing):** Semgrep com regras OWASP Top 10 e boas práticas Java.
- **Container Hardening:** Imagem Docker segura multi-stage com usuário não-root ([`Dockerfile`](file:///Dockerfile)).
- **Quality Gates:** Bloqueio automático de deploy caso sejam detectadas falhas de segurança.

Documentação completa e diagrama em: **[DEVSECOPS.md](DEVSECOPS.md)**.

### Relatório de Evidências de Segurança em Código e Infraestrutura
Para a demonstração detalhada com comparativos "Antes x Depois", testes de criptografia local (**AES-256-GCM**), proteção contra ataques de força bruta no Rate Limit e validação estrita de entrada, consulte o relatório: **[SECURITY_EVIDENCES.md](SECURITY_EVIDENCES.md)**.

---

## 7. Como Executar a Aplicação Localmente

### Pré-requisitos
- **JDK 21**
- **Maven 3.9+** (ou utilizar o `./mvnw` incluso)
- **MongoDB 7+** rodando localmente na porta `27017`

### Passo 1: Subir o Banco de Dados (Docker)
```bash
docker pull mongo
docker run -d --name mongodb -p 27017:27017 \
  -e MONGO_INITDB_ROOT_USERNAME=specvora \
  -e MONGO_INITDB_ROOT_PASSWORD=fiap \
  mongo
```

### Passo 2: Executar a Aplicação
```bash
# Definir a URI do MongoDB (caso utilize usuário e senha)
export MONGODB_URI="mongodb://specvora:fiap@localhost:27017/?authSource=admin"

# Iniciar o servidor Spring Boot
./mvnw spring-boot:run
```

A aplicação estará acessível em `http://localhost:8080`.

---

## 8. Exemplos Práticos de Uso com cURL

### 1. Obter Token JWT como Administrador
```bash
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
```

### 2. Cadastrar um Novo Veículo (Requer ROLE_ADMIN)
```bash
curl -X POST http://localhost:8080/vehicles \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <SEU_TOKEN_ADMIN>" \
  -d '{
    "brand": "Toyota",
    "model": "Corolla Cross",
    "version": "XRX Hybrid",
    "engine": "1.8 Hybrid",
    "year": "2026",
    "vehicleCategory": "SUV"
  }'
```
*Resposta esperada: `201 Created` com o cabeçalho `Location: /vehicles/<id>`.*

### 3. Consultar Veículos Cadastrados
```bash
curl -X GET http://localhost:8080/vehicles \
  -H "Authorization: Bearer <SEU_TOKEN_AQUI>"
```

### 4. Atualizar um Veículo
```bash
curl -X PUT http://localhost:8080/vehicles/<ID_DO_VEICULO> \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <SEU_TOKEN_ADMIN>" \
  -d '{
    "brand": "Toyota",
    "model": "Corolla Cross",
    "version": "GR-Sport",
    "engine": "2.0 Dynamic Force",
    "year": "2026",
    "vehicleCategory": "SUV"
  }'
```

### 5. Excluir um Veículo
```bash
curl -X DELETE http://localhost:8080/vehicles/<ID_DO_VEICULO> \
  -H "Authorization: Bearer <SEU_TOKEN_ADMIN>"
```
*Resposta esperada: `204 No Content`.*
