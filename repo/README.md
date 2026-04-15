# Anju Medical Appointment System

Backend API for managing property resources, appointment scheduling, financial bookkeeping, and file storage. Built with Spring Boot 3, MySQL 8, and JWT authentication.

## Quick Start

```bash
docker-compose up --build
```

This starts:
- **App** on `http://localhost:8080`
- **MySQL 8** on `localhost:3306`

The app waits for MySQL to be healthy before starting. A default admin user is created on first run if `APP_SECURITY_BOOTSTRAP_ADMIN_PASSWORD` is set:

| Field | Value |
|-------|-------|
| Username | `admin` |
| Password | Set via `APP_SECURITY_BOOTSTRAP_ADMIN_PASSWORD` env variable |
| Note | You'll be forced to change the password on first login |

## Roles and User Setup

The system has 5 roles. Only the `admin` user is bootstrapped automatically. All other users must be created by an admin via `POST /api/admin/users`.

| Role | Purpose | Key Permissions |
|------|---------|----------------|
| `ADMIN` | System administration | Full access, user management, audit logs |
| `DISPATCHER` | Operations coordinator | Property/slot management, appointment lifecycle, file access |
| `REVIEWER` | Approval authority | View all appointments, approve cancellations |
| `FINANCE` | Financial operations | Transactions, refunds, settlements, reports |
| `SERVICE_STAFF` | Field staff | Complete assigned appointments only |

**Creating users (admin only):**

```bash
curl -X POST http://localhost:8080/api/admin/users \
  -H "Authorization: Bearer <admin-token>" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "dispatcher1",
    "password": "SecurePass1!xx",
    "fullName": "Dispatcher User",
    "role": "DISPATCHER",
    "phone": "13800138000",
    "email": "dispatcher@example.com"
  }'
```

Password policy: minimum 12 characters, must include uppercase, lowercase, digit, and special character.

**Test credentials** (used in automated tests only, H2 in-memory database):

| Username | Password | Role |
|----------|----------|------|
| `admin` | `Admin123` | ADMIN |
| `dispatcher` | `Dispatch1` | DISPATCHER |
| `reviewer` | `Reviewer1` | REVIEWER |
| `finance` | `Finance1` | FINANCE |

> These test users are created programmatically by the test harness and do not exist in production.

## Running Tests

### With Docker (no local JDK required)

```bash
./run_tests.sh
```

This builds a test image using `Dockerfile.test` and runs `mvn clean verify` with an H2 in-memory database.

### Without Docker (local JDK 17+ and Maven 3.9+ required)

```bash
mvn clean verify
```

Tests use the `application-test.yml` profile with an H2 in-memory database — no MySQL needed.

## Configuration

Environment variables in `docker-compose.yml`:

| Variable | Default | Description |
|----------|---------|-------------|
| `SPRING_DATASOURCE_URL` | `jdbc:mysql://mysql:3306/anju_db` | Database connection |
| `SPRING_DATASOURCE_USERNAME` | `anju_user` | DB username |
| `SPRING_DATASOURCE_PASSWORD` | `anju_pass` | DB password |
| `JWT_SECRET` | *(required)* | **Must set before startup** (min 32 chars) |
| `APP_SECURITY_BOOTSTRAP_ADMIN_PASSWORD` | *(required on first run)* | Initial admin password |
| `SPRING_JPA_HIBERNATE_DDL_AUTO` | `update` | Schema management |

## Storage

Uploaded files are stored in a Docker volume `app-storage`, mounted at `/app/storage` inside the container.

## Stopping

```bash
docker-compose down
```

To also remove the database volume:

```bash
docker-compose down -v
```
