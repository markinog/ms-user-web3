# Como rodar o **ms-user** (User Service)

Este repositório contém **apenas o User Service** (`ms-user`), feito em Spring Boot.
Ele é um dos três serviços do projeto:

| Serviço      | Repositório            | Porta padrão | Função                                            |
|--------------|------------------------|--------------|---------------------------------------------------|
| **ms-user**  | este repositório       | `8081`       | Cadastro/login, JWT, perfil, envia código por fila |
| ms-email     | repositório separado   | —            | Consome a fila e envia o e-mail com o código       |
| frontend     | repositório separado   | `3000`       | Telas de login, cadastro de nome/cargo e dashboard |

> **Não existe front-end dentro deste repositório.** O front (`register.html`,
> `dashboard.html`, rota `/api/protected`, etc.) é um projeto Node separado que
> apenas consome a API deste serviço. As instruções para o front estão no final,
> na seção [Front-end](#front-end-projeto-separado).

---

## 1. Pré-requisitos

- **JDK 21** (o projeto usa `--enable-preview` com Java 21).
- **Maven 3.9+** — ou abra o projeto numa IDE (IntelliJ/Eclipse/VS Code) que já traga o Maven.
  > Este repositório **não** possui o Maven Wrapper (`mvnw`). Se você não tem o `mvn`
  > instalado, o caminho mais simples é abrir na IntelliJ e rodar `SecrestApplication`.
- **MySQL 8** rodando localmente (ou acessível por rede).
- **Conta CloudAMQP** (RabbitMQ gerenciado) — usada para publicar a mensagem de e-mail.
- **Conta Gmail** com senha de app — usada **pelo ms-email** para enviar o e-mail.
  (O ms-user não envia e-mail diretamente; ele só publica na fila.)

---

## 2. Banco de dados

Crie o banco `ms_user` no MySQL:

```sql
CREATE DATABASE ms_user CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

As tabelas (`users`, `roles`, `users_roles`) são criadas/atualizadas automaticamente
pelo Hibernate, pois `spring.jpa.hibernate.ddl-auto=update`. O campo **`name`** já está
mapeado na entidade `User` e será adicionado à tabela na primeira execução.

---

## 3. Variáveis de ambiente

O `application.properties` lê tudo de variáveis de ambiente:

```properties
spring.datasource.url=${DB_URL}
spring.datasource.username=${DB_USERNAME}
spring.datasource.password=${DB_PASSWORD}
jwt.secret=${JWT_SECRET}
spring.rabbitmq.addresses=${RABBITMQ_ADDRESS}
```

Há um arquivo `.env.example` como referência. **Atenção:** o Spring Boot não carrega
`.env` automaticamente — você precisa exportar as variáveis no terminal (ou configurá-las
na IDE em *Run Configuration → Environment variables*).

Variáveis necessárias:

| Variável           | Exemplo                                                                                      |
|--------------------|----------------------------------------------------------------------------------------------|
| `DB_URL`           | `jdbc:mysql://localhost:3306/ms_user?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true` |
| `DB_USERNAME`      | `root`                                                                                        |
| `DB_PASSWORD`      | `suaSenha`                                                                                    |
| `JWT_SECRET`       | uma string secreta longa, ex.: `minha-chave-super-secreta-1234567890`                        |
| `RABBITMQ_ADDRESS` | a URL AMQP do CloudAMQP, ex.: `amqps://user:pass@host.rmq.cloudamqp.com/vhost`               |

### Definindo as variáveis no PowerShell (Windows)

```powershell
$env:DB_URL = "jdbc:mysql://localhost:3306/ms_user?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true"
$env:DB_USERNAME = "root"
$env:DB_PASSWORD = "suaSenha"
$env:JWT_SECRET = "minha-chave-super-secreta-1234567890"
$env:RABBITMQ_ADDRESS = "amqps://user:pass@host.rmq.cloudamqp.com/vhost"
```

(No Linux/macOS use `export DB_URL=...` etc.)

---

## 4. Executar o serviço

Com as variáveis definidas **no mesmo terminal**:

```powershell
# via Maven
mvn spring-boot:run
```

ou, gerando o JAR:

```powershell
mvn clean package
java --enable-preview -jar target/ms-user-0.0.1-SNAPSHOT.jar
```

ou, pela **IDE**: rode a classe `SecrestApplication` (lembre de configurar as variáveis
de ambiente na Run Configuration).

O serviço sobe em **http://localhost:8081**.

---

## 5. Endpoints principais

### Públicos
| Método | Rota                  | Descrição                               |
|--------|-----------------------|-----------------------------------------|
| POST   | `/auth/request-code`  | Recebe `{ "email": "..." }` e envia o código por e-mail (via fila). |
| POST   | `/auth/verify-code`   | Recebe `{ "email": "...", "code": "..." }` e devolve o **token JWT**. |
| POST   | `/users/login`        | Login por e-mail/senha (devolve JWT).   |
| POST   | `/users`              | Cria usuário (uso administrativo/teste).|

### Autenticados (header `Authorization: Bearer <token>`)
| Método | Rota                      | Descrição                                                        |
|--------|---------------------------|------------------------------------------------------------------|
| POST   | `/users/update-profile`   | **(Etapa 4)** Atualiza `name` e substitui a role do usuário.     |
| GET    | `/users/me`               | Retorna o perfil do usuário logado (`id`, `name`, `email`, `roles`). |
| GET    | `/users/test/customer`    | Teste de acesso para `ROLE_CUSTOMER`.                            |
| GET    | `/users/test/administrator` | Teste de acesso para `ROLE_ADMINISTRATOR`.                     |

### Corpo do `POST /users/update-profile`

```json
{
  "name": "Marcus Silva",
  "role": "ROLE_CUSTOMER"
}
```

`role` aceita `ROLE_CUSTOMER` ou `ROLE_ADMINISTRATOR`. O usuário fica com **apenas uma role**
(a lista é substituída). A resposta é o perfil atualizado:

```json
{
  "id": 1,
  "name": "Marcus Silva",
  "email": "marcus@exemplo.com",
  "roles": ["ROLE_CUSTOMER"]
}
```

### Teste rápido com cURL

```bash
# 1. Pedir o código
curl -X POST http://localhost:8081/auth/request-code \
  -H "Content-Type: application/json" \
  -d '{"email":"marcus@exemplo.com"}'

# 2. Verificar o código recebido por e-mail e pegar o token
curl -X POST http://localhost:8081/auth/verify-code \
  -H "Content-Type: application/json" \
  -d '{"email":"marcus@exemplo.com","code":"123456"}'

# 3. Atualizar o perfil (use o token do passo anterior)
curl -X POST http://localhost:8081/users/update-profile \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer SEU_TOKEN_JWT" \
  -d '{"name":"Marcus Silva","role":"ROLE_CUSTOMER"}'

# 4. Conferir o perfil
curl http://localhost:8081/users/me \
  -H "Authorization: Bearer SEU_TOKEN_JWT"
```

---

## Front-end (projeto separado)

O front-end **não está neste repositório**. Ele é um projeto Node (Express) que serve
as telas e faz proxy para o User Service. Para rodá-lo, no diretório do front:

```bash
npm install
npm start        # normalmente sobe em http://localhost:3000
```

O front consome este serviço (`http://localhost:8081`):
- `POST /register` (front) → `POST /users/update-profile` (este serviço), enviando o JWT no header `Authorization`;
- `GET /api/protected` (front) → `GET /users/test/customer` (este serviço);
- `GET /users/me` para a tela "Meu perfil".

### Fluxo completo de teste
1. Acesse `http://localhost:3000`.
2. Digite um e-mail → receba o código por e-mail.
3. Digite o código → vai para a página de cadastro de nome/cargo.
4. Preencha nome e escolha o cargo → é redirecionado para o dashboard.
5. No dashboard, use os botões "Testar endpoint protegido", "Meu perfil" e "Sair".

> Para o fluxo funcionar de ponta a ponta, os **três serviços** precisam estar no ar:
> `ms-user` (este, 8081), `ms-email` (consumidor da fila) e o `frontend` (3000).
