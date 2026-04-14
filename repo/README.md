# Anju Medical Appointment System

Backend API for managing property resources, appointment scheduling, financial bookkeeping, and file storage. Built with Spring Boot 3, MySQL 8, and JWT authentication.

## Quick Start

```bash
docker-compose up --build
```

This starts:
- **App** on `http://localhost:8080`
- **MySQL 8** on `localhost:3306`

The app waits for MySQL to be healthy before starting. A default admin user is created on first run:

| Field | Value |
|-------|-------|
| Username | `admin` |
| Password | `Admin123` |
| Note | You'll be forced to change the password on first use |

## Running Tests

Tests run inside Docker — no local JDK or Maven required:

```bash
./run_tests.sh
```

This builds a test image using `Dockerfile.test` and runs `mvn clean verify` with an H2 in-memory database.

## Configuration

Environment variables in `docker-compose.yml`:

| Variable | Default | Description |
|----------|---------|-------------|
| `SPRING_DATASOURCE_URL` | `jdbc:mysql://mysql:3306/anju_db` | Database connection |
| `SPRING_DATASOURCE_USERNAME` | `anju_user` | DB username |
| `SPRING_DATASOURCE_PASSWORD` | `anju_pass` | DB password |
| `JWT_SECRET` | `change-this-...` | **Must change in production** |
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
