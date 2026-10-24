# Fraud Engine

## Persistent transaction evaluations

The service stores each evaluated transaction and its evaluation in PostgreSQL.
Flyway applies the schema migrations at application startup. Redis remains
responsible for real-time velocity enrichment and blacklist lookups.
Runtime PostgreSQL queries are stored under `src/main/resources/db/queries/`,
separately from Flyway schema migrations under `src/main/resources/db/migration/`.

The application imports `.env` from the project root when it starts. Set the
database and admin credentials there. The checked-in `.env.example` documents
the required settings. Verify or update the values in your local `.env` before
running the application. Environment variables supplied by the operating
system override values from the file.

Configure the PostgreSQL and Redis connections with:

| Environment variable | Default |
| --- | --- |
| `DB_HOST` | `postgres` |
| `DB_PORT` | `5432` |
| `POSTGRES_DB` | `fraudengine` |
| `REDIS_HOST` | `redis` |
| `REDIS_PORT` | `6379` |

Create the database and user before starting the service, or provide equivalent
credentials for an existing PostgreSQL instance. The database username and
password have no defaults. Also configure `FRAUD_ADMIN_USERNAME` and
`FRAUD_ADMIN_PASSWORD`; these admin credentials are required at startup.

Edit `.env` with your local credentials, then launch the application from the
project root:

```powershell
.\mvnw.cmd spring-boot:run
```

The default hosts `postgres` and `redis` are intended for Docker Compose
service discovery. When running the application directly on the host instead
of in Compose, set `DB_HOST=localhost` and `REDIS_HOST=localhost` in `.env`.

## Docker Compose

The Compose stack runs the application, PostgreSQL, Redis, and Grafana.
PostgreSQL data and Grafana state are retained in named volumes; database and
Redis ports are not published to the host by default. The app is available at
`http://localhost:8080` and Grafana at `http://localhost:3000`.

1. From the project root, build and start the stack:

   ```powershell
   docker compose --env-file .compose.env up --build -d
   ```

   The Dockerfile builds the application without running tests (`-DskipTests`).
   Run `.\mvnw.cmd clean verify` separately to execute the full test suite.

2. Check service status and application health:

   ```powershell
   docker compose --env-file .compose.env ps
   Invoke-RestMethod http://localhost:8080/api/v1/fraud/health
   ```

3. Stop the stack while retaining database data:

   ```powershell
   docker compose --env-file .compose.env down
   ```

To remove the PostgreSQL data and Grafana state as well, use
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

## Grafana transaction dashboard

Docker Compose provisions Grafana with a PostgreSQL data source and a
transaction dashboard backed by the stored evaluation history. The dashboard
includes transaction counts, action distribution, transaction amounts, risk
scores, and a recent transactions table.

Open `http://localhost:3000` and select the **Fraud Engine Transactions**
dashboard. Grafana is bound to localhost.

The Prometheus scrape endpoint is `GET /actuator/prometheus`. The provisioned
transaction dashboard reads PostgreSQL directly and does not require
Prometheus.

## Swagger UI

Open `http://localhost:8080/swagger-ui/index.html` to explore and try the REST
API. The OpenAPI document is available at `/v3/api-docs`; Actuator endpoints
are included in the actuator documentation group. Use **Authorize** to provide
the configured admin credentials for protected endpoints.
