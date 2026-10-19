# GoviLink

[![Backend CI](https://github.com/dayanganganath/govilink/actions/workflows/backend-ci.yml/badge.svg?branch=main)](https://github.com/dayanganganath/govilink/actions/workflows/backend-ci.yml)

A farmer collection and sales coordination application built with Java 17,
Spring Boot, and PostgreSQL.

GoviLink is an actively developed portfolio project. The current implementation
provides farmer management APIs, database migrations, automated service tests,
and a GitHub Actions build pipeline.

## Project Goal

Explore how software can support coordination between farmers, collection
coordinators, buyers, and transport providers.

The intended workflow covers harvest availability, buyer orders, produce
collection, acceptance, and settlement.

The product assumptions have not yet been validated through field research.
The application is not currently presented as a production service.

## Current Features

- Create a farmer with validated input.
- List the latest 50 farmers.
- Retrieve a farmer by UUID.
- Return HTTP 404 when a farmer does not exist.
- Reject invalid request bodies and malformed UUIDs.
- Normalize surrounding whitespace in farmer input.
- Store blank optional village values as null.
- Manage database schema changes with Flyway.
- Expose an application health endpoint.
- Run automated service tests.
- Build, test, and package the backend through GitHub Actions.

## Technology Stack

| Area | Technology |
|---|---|
| Language | Java 17 |
| Backend | Spring Boot 4.1.1 |
| Web APIs | Spring MVC |
| Persistence | Spring Data JPA / Hibernate |
| Database | PostgreSQL 18 |
| Migrations | Flyway |
| Validation | Jakarta Bean Validation |
| Health checks | Spring Boot Actuator |
| Unit tests | JUnit / Mockito |
| Build | Maven Wrapper |
| CI | GitHub Actions |

## Architecture

The backend uses a layered structure within the farmer feature package:

- Controller: HTTP requests and input validation.
- Service: application logic and transaction boundaries.
- Repository: database access.
- Entity: persistence mapping.
- DTOs: API request and response models.

Database changes are applied through versioned Flyway migration files.

## Repository Structure

```text
govilink/
├── .github/
│   └── workflows/
│       └── backend-ci.yml
├── backend/
│   ├── .mvn/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/govilink/
│   │   │   │   ├── BackendApplication.java
│   │   │   │   └── farmer/
│   │   │   └── resources/
│   │   │       ├── application.properties
│   │   │       └── db/migration/
│   │   └── test/java/com/govilink/
│   ├── mvnw
│   ├── mvnw.cmd
│   └── pom.xml
└── README.md
```

## Prerequisites

- JDK 17.
- PostgreSQL 18.
- Git.
- Internet access for the initial Maven dependency downloads.

A global Maven installation is not required. Use the included Maven Wrapper.

The setup examples below use Windows PowerShell.

## Local Setup

### 1. Clone the repository

```powershell
git clone https://github.com/dayanganganath/govilink.git
Set-Location .\govilink\backend
```

### 2. Create the database account

Connect to PostgreSQL using an administrator account.

For a default PostgreSQL 18 Windows installation:

```powershell
& "C:\Program Files\PostgreSQL\18\bin\psql.exe" -h localhost -p 5432 -U postgres -d postgres
```

Run the following inside the `psql` prompt:

```sql
CREATE ROLE govilink_app
    LOGIN
    NOSUPERUSER
    NOCREATEDB
    NOCREATEROLE
    NOREPLICATION;
```

Set a password interactively:

```text
\password govilink_app
```

Create the database:

```sql
CREATE DATABASE govilink_db OWNER govilink_app;
```

Exit `psql`:

```text
\q
```

These creation commands are intended for a fresh setup. If the account and
database already exist, skip this step.

### 3. Set the database password

In the PowerShell window that will run the backend:

```powershell
$govilinkSecurePassword = Read-Host "govilink_app database password" -AsSecureString

$govilinkCredential = [System.Management.Automation.PSCredential]::new(
    "govilink_app",
    $govilinkSecurePassword
)

$env:GOVILINK_DB_PASSWORD = $govilinkCredential.GetNetworkCredential().Password

Remove-Variable govilinkSecurePassword, govilinkCredential
```

This environment variable applies to the current PowerShell session and its
child processes. Set it again when opening a new backend terminal.

Do not commit real passwords or include them in screenshots.

### 4. Start the application

From the `backend` directory:

```powershell
.\mvnw.cmd spring-boot:run
```

Flyway applies pending migrations during startup. Hibernate validates the entity
mappings against the database schema.

Keep this terminal open while using the API. Pressing Ctrl+C stops the server.

### 5. Check application health

Open a separate terminal:

```powershell
curl.exe --noproxy "*" http://127.0.0.1:8085/actuator/health
```

A healthy application returns a JSON response containing:

```json
{
  "status": "UP"
}
```

Additional health-response fields may be present.

## Configuration

Default local configuration:

| Setting | Value |
|---|---|
| Server address | `127.0.0.1` |
| Server port | `8085` |
| Database host | `localhost` |
| Database port | `5432` |
| Database name | `govilink_db` |
| Database user | `govilink_app` |
| Password environment variable | `GOVILINK_DB_PASSWORD` |

The application currently binds to loopback for local development.

## API Endpoints

Base URL: `http://127.0.0.1:8085`

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/v1/farmers` | Create a farmer |
| GET | `/api/v1/farmers` | List the latest 50 farmers |
| GET | `/api/v1/farmers/{id}` | Retrieve a farmer by UUID |
| GET | `/actuator/health` | Check application health |

### Create a Farmer

Run this in a separate PowerShell terminal while the server is running:

```powershell
$farmerBody = @{
    fullName = "Demo Farmer"
    phone = "TEST-001"
    district = "Matale"
    village = "Demo Village"
} | ConvertTo-Json

$createdFarmer = Invoke-RestMethod `
    -Uri "http://127.0.0.1:8085/api/v1/farmers" `
    -Method Post `
    -ContentType "application/json" `
    -Body $farmerBody

$createdFarmer | ConvertTo-Json
```

Successful creation returns HTTP 201 and the created farmer, including its UUID.

`TEST-001` is fictional demo data. Current phone validation checks that the value
is not blank and does not exceed 20 characters; it does not validate telephone
number formats.

### Retrieve the Created Farmer

In the same test terminal:

```powershell
Invoke-RestMethod `
    -Uri "http://127.0.0.1:8085/api/v1/farmers/$($createdFarmer.id)" |
    ConvertTo-Json
```

### List Farmers

```powershell
Invoke-RestMethod `
    -Uri "http://127.0.0.1:8085/api/v1/farmers" |
    ConvertTo-Json -Depth 5
```

The list is ordered by creation timestamp descending, with UUID descending as
a tie-breaker. Client-controlled pagination is not yet implemented.

### Error Statuses

| Situation | HTTP Status |
|---|---|
| Invalid request body | 400 |
| Malformed farmer UUID | 400 |
| Farmer UUID not found | 404 |

A custom, consistent error-response format is planned.

## Tests

Run the farmer service unit tests without a database:

```powershell
.\mvnw.cmd "-Dtest=FarmerServiceTest" test
```

The five service tests cover:

1. Trimming farmer input.
2. Converting a blank village to null.
3. Requesting the latest 50 farmers in the expected order.
4. Retrieving an existing farmer.
5. Returning not-found status for a missing farmer.

The repository is mocked in these tests. They do not independently verify SQL
ordering, persistence, HTTP serialization, or controller validation.

Run the complete build:

```powershell
.\mvnw.cmd verify
```

The complete build includes a Spring application-context test. Locally, this
requires the configured PostgreSQL database and password environment variable.
Use a development database, not a production database.

## Continuous Integration

The Backend CI workflow:

1. Starts a temporary PostgreSQL 18 service.
2. Checks out the repository.
3. Configures Temurin JDK 17.
4. Runs Maven verification.
5. Packages the application as a JAR.

CI uses a disposable database and CI-only credentials. It does not connect to
the developer's local database.

The workflow runs for configured code changes on main, feature, test, and CI
branches, and for relevant pull requests targeting main.

## Roadmap

The following features are planned and are not currently implemented:

- Authentication and role-based access.
- Farmer updates and deactivation.
- Search and pagination.
- Consistent API error responses.
- HTTP and database integration tests.
- Buyer management.
- Harvest availability.
- Buyer orders.
- Collection planning and produce acceptance.
- Deductions and payment records.
- React and TypeScript frontend.
- Deployment documentation.

## Current Limitations

- No authentication or authorization yet.
- No production deployment or real-user adoption claims.
- No automated payments, SMS, or WhatsApp integration.
- No frontend yet.
- Local operation and development are the current focus.

Do not expose the current API publicly or use real personal data in public demos.

## Development Notes

- Keep real credentials out of Git.
- Add schema changes as new Flyway migrations.
- Do not edit a migration after it has been successfully applied.
- Keep test classes under `src/test/java`.
- Keep Java package declarations consistent with directory paths.
- Use fictional data for demonstrations.