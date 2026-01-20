**Architectural Decision Record - Sistema de Pagamentos com Políticas de Limite**

---

## 1. Contexto de Negócio

**Domínio:** Sistema de pagamentos para carteiras virtuais

**Objetivo do Case:** Código suficiente para demonstrar capacidade técnica, **sem over-engineering**

---

## 2. Requisitos Funcionais

### Obrigatórios

1. **Criar Pagamento:** Debitar valor da carteira respeitando políticas ativas
2. **Consultar Pagamento:** Buscar por ID
3. **Listar Pagamentos:** Filtros (data, carteira) + paginação cursor-based
4. **Criar Carteira:** Saldo inicial
5. **Consultar Carteira:** Saldo atual
6. **Criar Política:** Associar limite a carteira (tipo + período + valor)
7. **Listar Políticas:** Por carteira

### Políticas Extensíveis

- **VALUE_LIMIT:** Limite de valor total por período
- **TX_COUNT_LIMIT:** Limite de quantidade de transações por período (Bônus)

### Períodos Dinâmicos

- **DAYTIME:** 06:00 - 17:59
- **NIGHTTIME:** 18:00 - 05:59
- **WEEKEND:** Sábado e Domingo (dia inteiro)

### Desejável

- **Auditoria:** Registro de eventos de pagamento (quem, quando, o quê)

---

## 3. Requisitos Não-Funcionais

### 3.1 Transacional

- **Atomicidade:** `@Transactional` para rollback automático em falhas
- **Exemplo de Cenário:** Crash durante débito → rollback automático, saldo preservado

### 3.2 Locking

- **Pessimistic Lock:** `@Lock(PESSIMISTIC_WRITE)` / `SELECT ... FOR UPDATE` para prevenir race conditions em operações de débito
- **Unique Constraint:** `UNIQUE (idempotency_key)` para prevenir duplicação de retries

**Por que Pessimistic Lock?**

- Alta probabilidade de conflito em operações de pagamento
- Custo de retry é alto (pode gerar inconsistência de saldo)
- Simplicidade operacional (sem lógica de retry complexa)

**Trade-off:**

- ✅ Consistência garantida, zero inconsistências
- ❌ Throughput menor que Optimistic Lock (aceitável para o case)

**Implementação da proteção contra Race Condition:**

- **Lock pessimista (FOR UPDATE):** Protege a **Regra de Negócio (Policy)** — garante que a validação e o débito ocorram com leituras consistentes dos saldos e totais já consumidos no período.
- **Índice único em `idempotency_key`:** Protege a **Integridade dos Dados** — evita duplicar o mesmo registro em retries.
- **Isolation level READ_COMMITTED:** Essencial para o cenário de "burlar limites das Políticas": combina o lock (`FOR UPDATE`) com leituras limpas, evitando leituras sujas e anomalias que permitiriam estourar limites em requisições concorrentes.

Ambos (lock + unique) são necessários; para o caso de violação de políticas por concorrência, o lock com `READ_COMMITTED` é o que garante o comportamento correto.

### 3.3 Durabilidade

- **PostgreSQL:** `synchronous_commit = on` (default) para prevenir perda de dados após COMMIT

### 3.4 Consistência

- **Constraint no Banco:** `CHECK (amount > 0)`, `CHECK (balance >= 0)`
- **Validação em Código:** Regras de negócio antes de persistir

### 3.5 Isolamento (Lock)

**Pessimistic Lock vs Optimistic Lock:**

| Critério | Pessimistic Lock | Optimistic Lock |
| --- | --- | --- |
| **Quando usar** | Alta probabilidade de conflito | Baixa probabilidade de conflito |
| **Throughput** | Menor (serializa acessos) | Maior (permite concorrência) |
| **Complexidade** | Simples (banco gerencia) | Complexa (retry em código) |
| **Caso de Uso** | Pagamentos, saldo de carteira | Edição de perfil, configurações |

**Decisão:** Pessimistic Lock para operações de pagamento.

### 3.6 Concorrência

**SERIALIZABLE vs READ_COMMITTED + Unique Index:**

| Critério | SERIALIZABLE | READ_COMMITTED + Unique Index |
| --- | --- | --- |
| **Consistência** | Máxima (zero anomalias) | Alta (previne duplicação) |
| **Throughput** | Baixo (serializa tudo) | Alto (permite concorrência) |
| **Escalabilidade** | Limitada | Excelente |
| **Caso de Uso** | Sistemas críticos (financeiro core) | APIs de alta frequência (100+ TPS) |

**Decisão:** `READ_COMMITTED` + `UNIQUE (idempotency_key)` para alto throughput.

### 3.7 Idempotência

**Obrigatório:** `Idempotency-Key` no **header** (padrão Stripe, PayPal)

**Por que no Header?**

- Infraestrutura resiliente (retry automático de proxies/gateways)
- Separação de responsabilidades (infra vs negócio)
- Padrão de mercado

**Implementação:**

1. Verificar se `idempotency_key` já existe
2. Se sim: retornar pagamento existente (HTTP 200)
3. Se não: processar pagamento normalmente

**Performance:**

- Índice em `idempotency_key` (B-Tree)
- Lookup O(log n) antes de processar

### 3.8 Extensibilidade

**Strategy Pattern para Políticas:**

- Adicionar nova política = criar nova classe `PolicyValidator`
- Sem modificar código existente (Open/Closed Principle)

**Resolução de múltiplas categorias ativas por carteira:**
- Uma carteira pode ter uma ou mais políticas ativas (`wallet.policyIds`). O fluxo de criação de pagamento resolve o **conjunto de categorias** presentes nessas políticas (sem duplicatas) via `resolveActivePolicyCategories(policyIds, getCategory)`.
- Regras: (1) Se `policyIds` estiver vazio ou nenhuma política for resolvida → default `VALUE_LIMIT`. (2) Caso contrário → `Set` das categorias das políticas encontradas. (3) Para cada validator na lista injetada, executa `validate` **no máximo uma vez**, somente se alguma das categorias ativas for suportada por esse validator.
- Comportamento preservado: 0 ou 1 política → 1 categoria, igual ao anterior. Quando a persistência suportar N políticas por carteira, todas as categorias ativas serão validadas sem alteração do `PaymentService` nem dos validators. A função `resolveActivePolicyCategories` é pura e testável em isolamento.

### 3.9 Auditoria (Opcional)

**Auditoria (Opcional):** Fora do escopo da implementação atual. Pode ser tratada em iteração futura (ex.: append-only em PostgreSQL ou evento em fila).

---

## 4. Decisões Arquiteturais

### 4.1 Banco de Dados

**PostgreSQL (não MongoDB) para Transações:**

**Justificativa:**

- ACID completo (Atomicity, Consistency, Isolation, Durability)
- Transações robustas com locks
- Maturidade em sistemas financeiros
- Constraints nativos (`CHECK`, `UNIQUE`, `FOREIGN KEY`)

**Trade-off:**

- ✅ Consistência garantida, zero perda de dados
- ❌ Menos flexível que MongoDB para schemas dinâmicos (não é problema aqui)

---

### 4.2 Paginação

**Cursor-based (não Offset):**

**Justificativa:**

- Performance constante (não degrada com páginas profundas)
- Evita duplicação/perda de registros em inserções concorrentes
- Padrão de APIs modernas (GraphQL, Stripe)

**Trade-off:**

- ✅ Escalável, performance O(1)
- ❌ Não permite pular páginas diretamente (aceitável)

**Implementação:**

- Cursor = `id` do último registro
- Query: `WHERE id > :cursor ORDER BY id LIMIT :limit`

---

### 4.3 Reset Diário de Limites

**Cálculo Dinâmico (não Job Scheduled):**

**Justificativa:**

- Sem estado persistido (zero complexidade)
- Agregação SQL em tempo real (rápida com índices)
- Sem jobs agendados (menos infraestrutura)

**Trade-off:**

- ✅ Simplicidade, zero bugs de sincronização
- ❌ Query adicional por validação (mitigado por índices)

**Implementação:** Agregação `SUM(amount) WHERE wallet_id = ? AND created_at BETWEEN :periodStart AND :periodEnd`. Índice `(wallet_id, created_at DESC)` → O(log n).

---

### 4.4 Políticas Extensíveis

**Strategy Pattern:**

**Justificativa:**

- Adicionar nova política = nova classe (Open/Closed)
- Testabilidade isolada
- Sem `if/else` gigante

**Trade-off:**

- ✅ Extensível, testável
- ❌ Mais classes (aceitável)

**Estrutura:** `PolicyValidator` (interface) → `ValueLimitValidator`, `TxCountLimitValidator`, `[FutureValidator]`.

---

### 4.5 Identificadores: UUID em vez de ID Incremental

**Decisão:** UUID para `id` de entidades (Wallet, Payment, Policy) em vez de ID auto-incremental.

**Justificativas:**

| Critério | UUID | ID Incremental (BIGSERIAL) |
| --- | --- | --- |
| **Geração descentralizada** | Pode ser gerado no cliente, em múltiplos nós ou offline, sem coordenar com o banco | Exige o banco (ou um gerador central) para obter o próximo valor |
| **Exposição de informação** | Não revela volume, sequência nem ritmo de criação | `id = 1_000_000` permite estimar volume e padrões de uso |
| **Enumeração / segurança** | Dificulta tentativa de adivinhar outros recursos (`/payments/{uuid}`) | `/payments/1`, `/payments/2` facilitam varredura e enumeração |

**Trade-offs:**

- ✅ IDs invisíveis na API; alinhado a práticas de mercado em pagamentos.
- ❌ 16 bytes do UUID vs 8 (BIGINT): índice e chaves estrangeiras maiores; ordenação por UUID tende a ser menos performático para Cache do que BIGINT.

---

### 4.6 Exposed em vez de JPA/Hibernate

**Decisão:** Uso de **Exposed** (Kotlin SQL DSL) em vez de JPA/Hibernate para persistência.

**Justificativas:**

1. **Type-safety em queries críticas:** Constraints de idempotência e regras de limite validadas em compile-time.
2. **Controle fino de transações:** Isolation level `SERIALIZABLE` (ou `READ_COMMITTED` + lock) explícito para cenários de race condition.
3. **Transparência em queries de limite:** SQL visível facilita revisão e otimização de performance.
4. **Simplicidade em políticas dinâmicas:** Mapeamento manual evita a complexidade de `@Inheritance` e estratégias de JPA para modelos de política variados.
5. **Kotlin idiomático:** DSL alinhada com Ktor e ecossistema Kotlin.

**Trade-off consciente:** Exposed oferece menos “mágica” de ORM que Hibernate; para um sistema financeiro crítico, preferimos **controle explícito** em vez de **conveniência implícita**.

---

### 4.7 policyIds na Wallet como Lista (extensibilidade N:N)

**Decisão:** `policyIds` na Wallet modelada como **lista** (e não como string ou valor único) no domínio e na aplicação.

**Justificativa:** Extensibilidade futura. O `PolicyValidator` (Strategy) e a busca de Policies no `PaymentService` estão preparados para uma evolução no banco de dados de **1:N para N:N** (Wallets ↔ Policies). Em uma migração futura, alteram-se apenas **persistência e queries**; **domínio e aplicação permanecem estáveis**. Objetivo: suportar **N políticas ativas por Wallet** sem mudar `PolicyValidator` nem `PaymentService`.

---

## 5. Arquitetura Hexagonal

### 5.1 Estrutura de Diretórios

`domain/` (model, port/output, validator, exception) · `application/` (service, dto, mapper) · `infrastructure/` (adapter: web, persistence; config).

---

### 5.2 Camadas e Responsabilidades

**Domain (Regras de Negócio):**

- Entidades puras (sem anotações JPA)
- Interfaces de repositórios (ports)
- Validadores de políticas
- Exceções de domínio

**Application (Casos de Uso):**

- Orquestração de regras de negócio
- DTOs de entrada/saída
- Mappers (Domain ↔ DTO)

**Infrastructure (Adaptadores):**

- Controllers (REST)
- Implementação de repositórios (Exposed)
- Configurações de frameworks (Ktor, OpenAPI, etc.)

---

## 6. Schema Design

### 6.1 Flyway Migration

**Tabelas:** `wallets` (id, balance, created_at) · `payments` (id, wallet_id, amount, idempotency_key, status, created_at) · `policies` (id, wallet_id, type, period, limit_value, created_at). **Constraints:** `CHECK (balance >= 0)`, `CHECK (amount > 0)`, `UNIQUE (idempotency_key)`, `FK wallet_id`. **Índices:** `(wallet_id, created_at DESC)`, `(idempotency_key)`, `(id DESC)`, `(wallet_id, type)`.

**Justificativas:**

- **IDs:** UUID (ver decisão **4.5 Identificadores: UUID em vez de ID Incremental**). A implementação usa `uuid_generate_v4()` e `UUID`. Alternativa descartada: BIGSERIAL/auto-incremento.
- `NUMERIC(19, 2)`: Precisão decimal para valores monetários (evita erros de arredondamento)
- `CHECK (balance >= 0)`: Previne saldo negativo no banco
- `UNIQUE (idempotency_key)`: Previne duplicação de pagamentos
- **Índices:**
    - `(wallet_id, created_at DESC)`: Otimiza listagem de pagamentos por carteira
    - `(idempotency_key)`: Otimiza lookup de idempotência (O(log n))
    - `(id DESC)`: Otimiza cursor pagination

---

### 6.2 Constraints no Flyway, colunas no Exposed (sem `.references()`)

**Decisão:** Todo o relacionamento entre tabelas (FKs, CHECK, UNIQUE, índices) é definido via **constraints SQL no Flyway**; nas tabelas do **Exposed** ficam apenas as **colunas**, **sem** uso de `.references()` ou definição de relacionamento no ORM.

**Justificativas:**

- **Integridade referencial no banco:** A garantia de consistência fica no SGBD, não na aplicação.
- **Mudanças de schema sem mudar código:** Alterações de estrutura (novas FKs, índices, constraints) são feitas em migrações; o Exposed continua focado em DML.
- **Separação de responsabilidades:**
  - **Flyway:** Estrutura e integridade (DDL, constraints, índices).
  - **Exposed:** Queries e mapeamento (DML, SELECT, INSERT, UPDATE).

**Trade-off aceito:**

- ❌ Perda da conveniência de `.references()` do Exposed (joins e condições devem ser escritos manualmente).
- ✅ Ganho de controle, performance e segurança — em sistema financeiro crítico, **integridade garantida pelo banco** é preferível à conveniência de joins automáticos.

---

## 7. Configurações de Infraestrutura

### 7.1 application.conf

Datasource JDBC (PostgreSQL), Flyway em `classpath:db/migration`, Hikari, health/metrics/prometheus.

---

### 7.2 Docker Compose

Postgres:15 + app (build ., porta 8080, healthcheck em postgres).

---

### 7.3 Dockerfile

Multistage: Gradle build + JRE 17, `java -jar app.jar`.

## 8. Conclusão

As decisões desta ADR visam um sistema funcional e manutenível. 

A extensibilidade foi tratada na camada de aplicação e domínio (Strategy, policyIds como lista, resolução de categorias), permitindo evoluir regras de negócio e o modelo 1:N→N:N sem alterar schema. 

O código fica preparado para crescimento; o banco permanece estável até a migração ser necessária.

