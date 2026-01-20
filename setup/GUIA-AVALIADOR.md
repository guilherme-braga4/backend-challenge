# Guia do Avaliador — Payment API

**Objetivo:** Comandos e passos para subir a aplicação 100% com Docker, importar a collection para testes manuais e executar a suíte de testes.

---

## Conteúdo

1. [Pré-requisitos](#1-pré-requisitos)  
2. [Subir a aplicação 100% com Docker](#2-subir-a-aplicação-100-com-docker)  
3. [Variáveis de ambiente (Docker)](#3-variáveis-de-ambiente-docker)  
4. [Postman (ou Insomnia) — Collection em JSON](#4-postman-ou-insomnia--collection-em-json)  
5. [Comandos para rodar os testes](#5-comandos-para-rodar-os-testes)  
6. [Resumo de comandos](#6-resumo-de-comandos)  
7. [Base URL e portas](#7-base-url-e-portas)  
8. [Possíveis erros e checagens](#8-possíveis-erros-e-checagens)  
9. [Arquivos de referência](#9-arquivos-de-referência)

---

## 1. Pré-requisitos

- **Docker** e **Docker Compose** (v2+): para rodar a aplicação e os testes de integração/E2E.
- **JDK 17** e **Gradle** (ou uso do wrapper `./gradlew`): só é obrigatório se for rodar a aplicação ou os testes **fora** do Docker (ex.: `./gradlew run`, `./gradlew test`).

---

## 2. Subir a aplicação 100% com Docker


### 2.1. Build e execução
Na primeira execução é necessário fazer o **build** da imagem da aplicação.

```bash
docker compose build
docker compose up
```

- `docker compose build` — Constrói a imagem da aplicação a partir do `Dockerfile` (Gradle `shadowJar`, JAR em `build/libs/payment-api-all.jar`).
- `docker compose up` — Sobe `postgres` e `app`; a app só inicia após o Postgres estar healthy. O Flyway executa as migrações no startup. A aplicação expõe a porta **8080** no host.

### 2.2. Verificar se a aplicação está de pé

- **Health:**  
  `GET http://localhost:8080/health`  
  Resposta esperada: `200 OK`, corpo `OK`.

**cURL:**

```bash
curl -s http://localhost:8080/health
```


### 2.3. Parar os containers

```bash
docker compose down
```

Com remoção de volumes (banco limpo na próxima subida):

```bash
docker compose down -v
```
---

## 3. Variáveis de ambiente (Docker)

O `docker-compose.yml` define para o serviço `app`:

- `DB_HOST=postgres`
- `DB_PORT=5432`
- `DB_NAME=payment`
- `DB_USER=payment`
- `DB_PASSWORD=payment`

A aplicação usa `FrameworkConfig` e, na ausência de `DATABASE_JDBC_URL`, monta a URL a partir de `DB_HOST`, `DB_PORT`, `DB_NAME`. A porta da aplicação é **8080** (ou `PORT` se definida; no compose não é definida).

Não é necessário configurar variáveis adicionais para a avaliação com Docker.

---

## 4. Postman (ou Insomnia) — Collection em JSON

### 4.1. Onde está a collection

A collection Postman (formato v2.1) está na raiz do projeto em:

```
postman/Payment-API.postman_collection.json
```

(Caminho relativo à raiz do repositório clonado.)

### 4.2. Importar no Postman ou Insomnia

**Postman:**  
1. Abra o Postman.  
2. **Import** → **File** (ou **Upload Files**).  
3. Selecione `postman/Payment-API.postman_collection.json`.  
4. A collection **Payment API** será adicionada.

**Insomnia:**  
1. **Application** → **Import/Export** → **Import Data** → **From File**.  
2. Selecione `postman/Payment-API.postman_collection.json`.  
3. O Insomnia aceita o formato Postman Collection v2.1.

### 4.3. Variáveis da collection

A collection define as variáveis:

- `base_url` = `http://localhost:8080`
- `walletId` = (preenchido automaticamente após **Criar carteira**)
- `policyId` = (preenchido após **Criar política VALUE_LIMIT**)
- `policyIdTx` = (preenchido após **Criar política TX_COUNT_LIMIT**)

Os scripts de **Tests** em "Criar carteira" e "Criar política VALUE_LIMIT" gravam `id` em `walletId` e `policyId`. "Criar política TX_COUNT_LIMIT" grava em `policyIdTx`. As requisições que usam `{{walletId}}`, `{{policyId}}` ou `{{policyIdTx}}` passam a funcionar após rodar as que salvam esses IDs.

### 4.4. Ordem sugerida para testar

1. **Health** — `GET /health` (não depende de IDs).
2. **Criar carteira** — `POST /wallets`; grava `walletId`.
3. **Criar política VALUE_LIMIT** — `POST /policies`; grava `policyId`.
4. **Associar política à carteira** — `PUT /wallets/{{walletId}}/policy` com `{"policyId":"{{policyId}}"}`.
5. **Realizar pagamento** — `POST /wallets/{{walletId}}/payments` com header `Idempotency-Key` e body `amount`, `occurredAt`.
6. **Listar pagamentos** — `GET /wallets/{{walletId}}/payments` (opcionais: `limit`, `cursor`, `startDate`, `endDate`).
7. **Políticas da carteira** — `GET /wallets/{{walletId}}/policies`.
8. **Listar políticas** — `GET /policies` (opcionais: `limit`, `cursor`).

Para o **bônus TX_COUNT_LIMIT**:

- **Criar política TX_COUNT_LIMIT** — `POST /policies` com `name`, `category: "TX_COUNT_LIMIT"`, `maxTxPerDay`; grava `policyIdTx`.
- **Associar política TX_COUNT à carteira** — `PUT /wallets/{{walletId}}/policy` com `{"policyId":"{{policyIdTx}}"}`.

Depois, **Realizar pagamento** várias vezes no mesmo dia; a 6.ª (com política de 5 tx/dia) deve retornar **422**.

### 4.5. Requisitos importantes da API

- **POST /wallets/{walletId}/payments:** o header **`Idempotency-Key`** é **obrigatório**. Valores únicos para cada novo pagamento (ex.: `eval-pay-1`, `eval-pay-2`). Mesma chave com mesmo body → 200 (replay); mesma chave com body diferente → 409.
- **Body de pagamento:** `amount` (0 < x ≤ 1000), `occurredAt` (ISO-8601 em UTC, ex.: `2024-08-26T10:00:00Z`).
- **Política VALUE_LIMIT:** `name`, `category: "VALUE_LIMIT"`, `maxPerPayment`, `daytimeDailyLimit`, `nighttimeDailyLimit`, `weekendDailyLimit`.
- **Política TX_COUNT_LIMIT:** `name`, `category: "TX_COUNT_LIMIT"`, `maxTxPerDay`.

---

## 5. Comandos para rodar os testes

### 5.1. Testes unitários (sem Docker)

**Linux / macOS:**

```bash
./gradlew test
```

**Windows (PowerShell):**

```powershell
.\gradlew.bat test
```

Isso executa, entre outros:

- `ValueLimitValidatorTest`
- `TxCountLimitValidatorTest`
- `PeriodTest`

Os testes de integração e E2E estão anotados com `@Ignore` e **não** rodam nesse comando.

### 5.2. Testes de integração e E2E (com Docker)

Esses testes usam **Testcontainers** (PostgreSQL). É **obrigatório** que o Docker esteja em execução (Docker daemon acessível). Eles estão em `com.trace.payments.infrastructure`:

- `IntegrationTest` (base abstrata)
- `WalletAndPolicyIntegrationTest`
- `PaymentIntegrationTest`
- `ApiE2ETest`

Por padrão estão com `@Ignore`. Para incluí-los na execução:

1. **Remover a anotação `@Ignore`** (e a mensagem associada) das classes:
   - `IntegrationTest`
   - `WalletAndPolicyIntegrationTest`
   - `PaymentIntegrationTest`
   - `ApiE2ETest`

   Caminho: `src/test/kotlin/com/trace/payments/infrastructure/`.

2. Rodar apenas a pasta de infraestrutura:

**Linux / macOS:**

```bash
./gradlew test --tests 'com.trace.payments.infrastructure.*'
```

**Windows (PowerShell):**

```powershell
.\gradlew.bat test --tests "com.trace.payments.infrastructure.*"
```

Após os testes, pode-se **recolocar o `@Ignore`** se quiser que o `./gradlew test` padrão continue sem exigir Docker.

### 5.3. Rodar só os E2E de API

Se `@Ignore` for removido **apenas** de `ApiE2ETest`:

```bash
./gradlew test --tests 'com.trace.payments.infrastructure.ApiE2ETest'
```

(Windows: `.\gradlew.bat test --tests "com.trace.payments.infrastructure.ApiE2ETest"`.)

### 5.4. Rodar toda a suíte de testes (unitários + integração + E2E)

Com `@Ignore` removido das classes de `com.trace.payments.infrastructure` e **Docker em execução**:

```bash
./gradlew test
```

### 5.5. Rebuild do projeto antes dos testes (recomendado)

Se houve alteração no código:

```bash
./gradlew clean test
```

Para apenas os testes de infraestrutura:

```bash
./gradlew clean test --tests 'com.trace.payments.infrastructure.*'
```

(Exige que o `@Ignore` dessas classes tenha sido removido.)

---

## 6. Resumo de comandos

| Objetivo | Comando |
|----------|---------|
| Subir app + Postgres (Docker) | `docker compose build && docker compose up` |
| Subir em segundo plano | `docker compose up -d` |
| Rebuild e subir | `docker compose up --build` |
| Parar | `docker compose down` |
| Health | `curl -s http://localhost:8080/health` |
| Testes unitários (sem Docker) | `./gradlew test` |
| Testes de integração + E2E (com Docker, sem @Ignore) | `./gradlew test --tests 'com.trace.payments.infrastructure.*'` |
| Só E2E (com Docker, sem @Ignore em ApiE2ETest) | `./gradlew test --tests 'com.trace.payments.infrastructure.ApiE2ETest'` |

---

## 7. Base URL e portas

- **Aplicação:** `http://localhost:8080`
- **Health:** `http://localhost:8080/health`
- **PostgreSQL (Docker):** apenas entre containers; não está mapeado na porta do host. O banco é `payment`, usuário `payment`, em `postgres:5432` na rede interna do compose.

---
