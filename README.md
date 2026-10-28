# Fraud Engine

See [ARCHITECTURE.md](./ARCHITECTURE.md) for the system context, evaluation
flow, storage responsibilities, security model, and extension points.

## Persistent transaction evaluations

The service stores each evaluated transaction and its evaluation in PostgreSQL.
Flyway applies the schema migrations at application startup. Redis remains
responsible for real-time velocity enrichment and blacklist lookups.
Runtime PostgreSQL queries are stored under `src/main/resources/db/queries/`,
separately from Flyway schema migrations under `src/main/resources/db/migration/`.

The checked-in `.env.example` documents the required settings. Verify or update the values in your local `.env` before
running the application. 

Configure PostgreSQL, Redis, and Kafka connections, along with the admin
credentials. Compose uses the service hosts `postgres`, `redis`, and `kafka`.
When running the application directly on the host, point PostgreSQL and Redis
to their host addresses and set `KAFKA_BOOTSTRAP_SERVERS=localhost:9092`.

Also configure `FRAUD_ADMIN_USERNAME` and `FRAUD_ADMIN_PASSWORD`, these admin credentials are required at startup.

```terminal/powershell
.\mvnw.cmd spring-boot:run
```

## Docker Compose

The Compose stack runs the application, PostgreSQL, Redis, Kafka, Prometheus,
and Grafana. A one-shot k6 runner is available with the `loadtest` profile.
PostgreSQL, Kafka, Prometheus, and Grafana state are retained in named volumes;
PostgreSQL and Redis ports are not published to the host by default. Kafka,
Prometheus, and Grafana are bound to host loopback at `localhost:9092`,
`localhost:9090`, and `localhost:3000`, respectively. The app is available at
`http://localhost:8080`.

Transaction evaluation remains synchronous. After the decision and history row
are committed, a PostgreSQL transactional outbox publishes a
`FraudEvaluationCompletedEvent` to the configured Kafka topic. Kafka delivery
retries in the background and does not delay the decision response. The
publisher claims outbox rows using short-lived database leases and sends each
batch concurrently. Delivery is at least once, so downstream consumers should
deduplicate using `eventId`.
The application publishes events but does not include a Kafka consumer.
The event payload includes `schemaVersion`, `eventId`, `transactionId`,
`accountId`, `evaluatedAt`, `action`, `riskLevel`, and `riskScore`.

The default topic is `fraud.evaluation.completed.v1`; configure it with
`FRAUD_KAFKA_TOPIC`. `KAFKA_BOOTSTRAP_SERVERS` configures the broker address.
`FRAUD_KAFKA_PRODUCER_MAX_BLOCK_MS` sets the maximum time (in milliseconds)
Kafka producer sends may block while acquiring metadata or buffer space; the
same value is used when calculating the outbox claim lease.
Compose configures the application to use the internal `kafka:29092` listener;
host-side consumers can use `localhost:9092`.

1. From the project root, build and start the stack:

   ```terminal/powershell
   docker compose --env-file .compose.env up --build -d
   ```

   The Dockerfile builds the application without running tests (`-DskipTests`).
   Run `.\mvnw.cmd clean verify` separately to execute the full test suite.

2. Check service status and application health:

   ```terminal/powershell
   docker compose --env-file .compose.env ps
   Invoke-RestMethod http://localhost:8080/api/v1/fraud/health
   ```

3. Stop the stack while retaining database data:

   ```terminal/powershell
   docker compose --env-file .compose.env down
   ```

To remove the PostgreSQL, Kafka, Prometheus, and Grafana state as well, use
`docker compose --env-file .compose.env down --volumes`.

## API endpoints

The application uses HTTP Basic authentication with the configured admin
credentials for blacklist updates, transaction-history reads, and the
Prometheus scrape endpoint. History results are ordered newest first and
paginated. `page` is zero-based; `size` defaults to 20 and is limited to 100.

- `POST /api/v1/fraud/evaluate` evaluates and stores a transaction.
- `GET /api/v1/fraud/evaluations/{evaluationId}` retrieves one evaluation.
- `GET /api/v1/fraud/transactions/{transactionId}/evaluations?page=0&size=20`
  retrieves evaluations for a transaction.
- `GET /api/v1/fraud/accounts/{accountId}/evaluations?page=0&size=20`
  retrieves an account's evaluations.
- `GET /api/v1/fraud/transactions/{transactionId}` retrieves a stored
  transaction. If it has been evaluated more than once, the most recent
  transaction payload is returned.
- `GET /api/v1/fraud/accounts/{accountId}/transactions?page=0&size=20`
  retrieves the account's transactions, newest first. Repeated evaluations of
  a transaction are represented once in this list.

Each result contains the stored transaction and its evaluation response,
including evaluated rule summaries.

Request validation rejects blank required identifiers and non-positive amounts.
Transaction/account IDs, merchant/device fields, currency, category code, and IP
address have field-appropriate maximum lengths. Metadata is limited to 20
entries, with keys up to 64 characters and values up to 256; blacklist values
and reasons are limited to 255 and 512 characters respectively.

## Grafana transaction dashboard

Docker Compose provisions Grafana with a PostgreSQL data source and a
transaction dashboard backed by the stored evaluation history. The dashboard
includes transaction counts, action distribution, transaction amounts, risk
scores, and a recent transactions table.

Open `http://localhost:3000` and select the **Fraud Engine Transactions**
dashboard. Grafana is bound to localhost.

The Prometheus scrape endpoint is `GET /actuator/prometheus`. The provisioned
transaction dashboard reads PostgreSQL directly. Prometheus scrapes application
HTTP metrics, and the load-test dashboard displays k6 results sent to Prometheus.

## Load test and Grafana results

Start the Compose stack, then run the bounded k6 scenario from the project root.
It ramps to five virtual users over 60 seconds and records synthetic evaluation
transactions with `LOAD-` identifiers in PostgreSQL and Redis. Use a unique run
ID to separate each run's metrics:

```powershell
$env:TEST_RUN_ID = "local-$(Get-Date -Format yyyyMMdd-HHmmss)"
docker compose --env-file .compose.env up --build -d
docker compose --env-file .compose.env ps
docker compose --env-file .compose.env --profile loadtest run --rm -e "TEST_RUN_ID=$env:TEST_RUN_ID" k6
```

Wait for PostgreSQL, Redis, Kafka, the application, and Prometheus to be ready
before running k6. The test checks HTTP success, decision response shape, a
failure rate below 1%, and p95 request latency below two seconds. Review the
k6 summary in the terminal; the same run's request rate, p95 latency, failed
request rate, virtual users, total request count, and application-side
evaluation throughput/latency are sent to Prometheus.

Open Grafana at `http://localhost:3000`, then select the **Fraud Engine Load
Test** dashboard in the **Fraud Engine** folder. Choose the run ID in the
dashboard's **Test run** selector. Prometheus and Grafana are local-development
services; Prometheus's remote-write receiver is not intended to be exposed to
untrusted networks. The load test writes records into the configured database
and Redis, so use a development/test environment rather than production.

The scenario is defined in `load-test/fraud-engine-load.js`; adjust its stages
for a deliberately higher test only after confirming the target environment's
capacity and permitted test load.

## Swagger UI

Open `http://localhost:8080/swagger-ui/index.html` to explore and try the REST
API. The OpenAPI document is available at `/v3/api-docs`; Actuator endpoints
are included in the actuator documentation group. Use **Authorize** to provide
the configured admin credentials for protected endpoints.
