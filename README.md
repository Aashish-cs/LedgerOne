# LedgerOne

Enterprise Investment Portfolio & Order Management Platform.

LedgerOne is a Spring Boot 3 and React application that simulates an internal investment management platform. It is intentionally focused on backend architecture, security, business rules, auditability, and a restrained enterprise UI rather than consumer trading visuals.

The API is self-hosted and free to run locally. It does not depend on paid brokerage, crypto, or market-data providers. The included market simulator creates realistic sample prices for interview and portfolio-demo workflows.

## Stack

- Java 21, Spring Boot 3.5, Spring Security, JWT, Spring Data JPA, Flyway, PostgreSQL
- React, Vite, TypeScript, Tailwind CSS, React Router, TanStack Query, Axios, React Hook Form, Recharts
- JUnit 5, Mockito-ready test setup, Testcontainers dependencies, Docker Compose, GitHub Actions

## Demo Accounts

| Role | Email | Password |
| --- | --- | --- |
| Admin | `admin@ledgerone.com` | `Admin123!` |
| User | `user@ledgerone.com` | `User123!` |

## Architecture

The backend uses package boundaries that mirror enterprise service ownership:

- `controller`: REST APIs with validation and response envelopes
- `service`: application use cases and transactional workflows
- `repository`: Spring Data persistence boundaries
- `entity`: JPA entities, never exposed directly
- `dto`: external contracts
- `mapper`: MapStruct DTO mapping
- `security`: JWT, refresh tokens, roles, current-user access
- `audit`, `risk`, `notification`, `scheduler`: cross-cutting domain capabilities
- `exception`, `validation`, `config`: platform concerns

Important rules implemented:

- BCrypt password hashing and role-based authorization
- JWT access tokens and opaque hashed refresh tokens
- Duplicate-safe order placement through `clientOrderId`
- Buying power and share availability validation
- Market and limit orders with pending, filled, cancelled, and rejected states
- Immutable transaction ledger records for completed orders
- Portfolio holdings, cost basis, realized P/L, unrealized P/L, allocation, and return calculations
- Market price simulation through Spring Scheduler and persisted price history
- Risk score, concentration risk, sector allocation, daily exposure, and risk alerts
- Admin portal APIs for users, orders, audit logs, and risk alerts

## Run With Docker

```bash
docker compose up --build
```

Then open:

- Frontend: http://localhost:5173
- Backend API: http://localhost:8080/api
- API status: http://localhost:8080/api/system/status
- Swagger UI: http://localhost:8080/swagger-ui.html

## Run Locally

Backend:

```bash
cd backend
./mvnw spring-boot:run
```

Frontend:

```bash
cd frontend
npm install
npm run dev
```

PostgreSQL is expected at `jdbc:postgresql://localhost:5432/ledgerone` unless overridden with environment variables. Flyway creates and seeds the database on startup.

## Verification

```bash
cd backend
./mvnw test

cd ../frontend
npm run lint
npm run build
```

The frontend includes demo fallback data so the dashboard remains reviewable while the backend is offline. When the backend is running, Axios and TanStack Query use the live REST APIs.

## Beyond The Prompt

Additional polish included for a stronger interview presentation:

- Self-hosted API status endpoint and live/demo mode indicator in the UI
- Focused trading service tests for duplicate requests, persisted rejections, cash updates, holdings, ledger writes, and risk hooks
- Operational health endpoint through Spring Actuator
- Dockerized frontend reverse proxy to the backend API
