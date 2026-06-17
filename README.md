# ms-user-web3 — User Service

Microsserviço de autenticação com **Spring Boot 4**, **Spring Security**, **JWT** e **RabbitMQ (CloudAMQP)**. Faz parte de um sistema distribuído composto por três peças:

| Serviço         | Repositório          | Porta |
| --------------- | -------------------- | ----- |
| **User Service** | `ms-user-web3` ← você está aqui | `8081` |
| Email Service   | `ms-email-web3`      | `8082` |
| Frontend        | dentro do `ms-email-web3` | `3000` |

---

## Pré-requisitos

- **Java 21+** (o `pom.xml` usa Java 21 com `--enable-preview`)
- **Maven** (ou use o Wrapper `mvnw` incluído no projeto)
- **MySQL 8** rodando localmente na porta `3306`
- Conta no **CloudAMQP** (plano gratuito *Little Lemur*) com a URI AMQP em mãos

---

## 1. Banco de dados

Crie o schema no MySQL antes de subir a aplicação:

```sql
CREATE DATABASE ms_user;
```

> As tabelas (`users`, `roles`) são criadas automaticamente pelo Hibernate (`ddl-auto=update`) na primeira execução.

---

## 2. Variáveis de ambiente

O projeto usa um arquivo `.env` (veja o modelo `.env.example` na raiz):

```
DB_URL=jdbc:mysql://localhost:3306/ms_user?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
DB_USERNAME=root
DB_PASSWORD=sua_senha_mysql
JWT_SECRET=uma_string_secreta_longa_e_aleatoria
```

> **Não commite o `.env`** — ele já está no `.gitignore`.

Você também precisa da URI do CloudAMQP. Adicione ao `.env` (ou diretamente no `application.properties`):

```
RABBITMQ_ADDRESS=amqps://usuario:senha@beaver.rmq.cloudamqp.com/vhost
```

E certifique-se de que o `application.properties` referencia a variável:

```properties
spring.rabbitmq.addresses=${RABBITMQ_ADDRESS}
broker.queue.email.name=default.email
```

---

## 3. Executando

### Opção A — Maven Wrapper (recomendado)

**Linux / macOS:**

```bash
# 1. Carregue as variáveis de ambiente
set -a; source .env; set +a

# 2. Suba o serviço
./mvnw spring-boot:run
```

**Windows (PowerShell):**

```powershell
# 1. Carregue as variáveis de ambiente
Get-Content .env | Where-Object { $_ -match '=' } | ForEach-Object {
    $p = $_ -split '=', 2
    [Environment]::SetEnvironmentVariable($p[0].Trim(), $p[1].Trim())
}

# 2. Suba o serviço
.\mvnw.cmd spring-boot:run
```

### Opção B — Build + JAR

```bash
./mvnw clean package -DskipTests
java -jar target/ms-user-0.0.1-SNAPSHOT.jar
```

O serviço sobe em **http://localhost:8081**.

---

## 4. Endpoints

### Autenticação e usuários

| Método | Endpoint | Auth | Descrição |
| ------ | -------- | ---- | --------- |
| `POST` | `/users` | ❌ Pública | Cria um novo usuário com e-mail, senha e role |
| `POST` | `/users/login` | ❌ Pública | Autentica e retorna um token JWT |
| `GET`  | `/users/test/customer` | ✅ JWT + `ROLE_CUSTOMER` | Endpoint protegido de teste |
| `GET`  | `/users/me` | ✅ JWT | Retorna dados do usuário autenticado |
| `POST` | `/users/update-profile` | ✅ JWT | Atualiza nome e role do usuário |

### Fluxo OTP (código de acesso)

| Método | Endpoint | Auth | Descrição |
| ------ | -------- | ---- | --------- |
| `POST` | `/auth/request-code` | ❌ Pública | Gera código de 6 dígitos, armazena em cache e publica na fila RabbitMQ |
| `POST` | `/auth/verify-code` | ❌ Pública | Valida o código e retorna JWT se correto |

### Exemplos de corpo das requisições

**POST `/users`**
```json
{
  "email": "joao@example.com",
  "password": "senha123",
  "role": "ROLE_CUSTOMER"
}
```

**POST `/users/login`**
```json
{
  "email": "joao@example.com",
  "password": "senha123"
}
```

**POST `/auth/request-code`**
```json
{
  "email": "joao@example.com"
}
```
> Se o e-mail não existir no banco, um usuário temporário é criado automaticamente com `ROLE_CUSTOMER` e senha aleatória.

**POST `/auth/verify-code`**
```json
{
  "email": "joao@example.com",
  "code": "123456"
}
```

**POST `/users/update-profile`** *(requer `Authorization: Bearer <token>`)*
```json
{
  "name": "João Silva",
  "role": "ROLE_CUSTOMER"
}
```

---

## 5. Arquitetura interna

```
src/main/java/com/exemplo/msuser/
├── config/
│   ├── SecurityConfiguration.java   # Filtros, permissões de rota
│   └── RabbitMQConfig.java          # Declaração da fila + conversor JSON
├── controller/
│   └── UserController.java          # Endpoints REST
├── dto/
│   ├── LoginDto.java
│   ├── CreateUserDto.java
│   ├── EmailDto.java                # Payload enviado à fila
│   └── UpdateProfileDto.java
├── model/
│   ├── User.java
│   ├── Role.java
│   └── RoleName.java (enum)
├── repository/
│   └── UserRepository.java
├── security/
│   ├── JwtTokenService.java         # Geração e validação de JWT
│   ├── UserAuthenticationFilter.java
│   ├── UserDetailsImpl.java
│   └── UserDetailsServiceImpl.java
├── service/
│   ├── UserService.java             # Lógica de negócio
│   ├── UserProducer.java            # Publica mensagens no RabbitMQ
│   └── CodigoCacheService.java      # Cache em memória com expiração (5 min)
└── MsUserApplication.java
```

---

## 6. Como o cache de código funciona

O `CodigoCacheService` usa um `ConcurrentHashMap` para armazenar pares `email → código`. Um agendamento via `@Scheduled` varre o mapa e remove entradas com mais de 5 minutos — o código **não é retornado pela API** por segurança.

---

## 7. Como o JWT funciona

O token é gerado pelo `JwtTokenService` (lib `com.auth0:java-jwt 4.4.0`) com o segredo definido em `JWT_SECRET`. Ele é enviado no header:

```
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

O `UserAuthenticationFilter` intercepta todas as requisições protegidas, valida o token e popula o `SecurityContext`.

---

## 8. Integração com o sistema completo

Para o fluxo funcionar de ponta a ponta você precisará também do **Email Service** e do **Frontend** (ambos no repositório `ms-email-web3`):

1. Este serviço publica uma mensagem JSON na fila `default.email` do CloudAMQP.
2. O Email Service consome a fila e envia o e-mail com o código via Gmail SMTP.
3. O Frontend (Node.js/Express) serve as telas e faz proxy das chamadas para este serviço.

---

## 9. Solução de problemas

| Sintoma | Causa provável | Solução |
| ------- | -------------- | ------- |
| Serviço não sobe | MySQL indisponível ou credenciais erradas | Verifique se o MySQL está rodando e se `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` estão corretos |
| `401 Unauthorized` em endpoints protegidos | Token ausente ou expirado | Faça login novamente via `/users/login` ou `/auth/verify-code` |
| Código não chega por e-mail | RabbitMQ mal configurado | Confirme a `RABBITMQ_ADDRESS` e que o Email Service está rodando |
| `Connection refused` no RabbitMQ | URI do CloudAMQP incorreta | Copie a URI exata do painel CloudAMQP (começa com `amqps://`) |
| Erro `enable-preview` na compilação | Versão do Java < 21 | Use Java 21+ |
