# Anju Accompanying Medical Appointment System - Implementation Plan

> This file is the single source of truth. All code is implemented inside this `repo/` directory.

---

## How You Must Think

You are a **senior Java/Spring Boot developer with 10+ years of experience**. Every decision you make should reflect that. Here's how:

### Architecture
- Follow **layered architecture strictly**: Controller → Service → Repository. Controllers never touch repositories directly. Services contain all business logic.
- Use **DTOs** for request/response. Never expose JPA entities directly in API responses.
- Keep controllers thin — validate input, delegate to service, return response. Nothing else.
- One service class per domain module. Don't scatter business logic across helpers or utils.

### Code Quality
- Write **clean, readable code**. Meaningful variable and method names. No abbreviations like `apt` for `appointment` or `txn` for `transaction`.
- Follow Java conventions: camelCase for fields/methods, PascalCase for classes, UPPER_SNAKE for constants.
- Use `@Transactional` where data consistency matters (booking, cancellation, financial ops).
- Handle edge cases. A senior dev doesn't just code the happy path — they think about what can go wrong.
- No magic numbers. Use constants or config values.
- No dead code, no commented-out code, no TODOs left behind.

### Security Thinking
- Never trust input. Validate everything at the controller level with Bean Validation (`@Valid`, `@NotBlank`, `@Min`, etc.).
- Never return sensitive data (passwords, raw phone/email). Always mask in DTOs.
- Always check ownership — a user should not access another user's appointments unless they have the right role.
- Never expose stack traces in error responses. Log internally, return clean error messages.

### Database Thinking
- Add proper indexes: `idempotencyKey` (unique), `slotId + status`, `propertyId + date`, `userId`.
- Use database-level constraints (unique, not-null) as a safety net, not just application-level checks.
- Think about **concurrent access**: two users booking the last slot at the same time. Use optimistic locking (`@Version`) or pessimistic locking where needed.
- Use `BigDecimal` for money. Never `double` or `float`.

### Error Handling
- Throw specific exceptions (`BusinessRuleException`, `ResourceNotFoundException`), not generic ones.
- Every error response must follow the standard format defined in this plan.
- Log errors with context (what was the user trying to do, what ID, what state).

### Testing Mindset
- Write code that is testable. Inject dependencies, don't use static methods for business logic.
- Think about the test scenarios in Section 11 while writing the code. If your code can't be tested against those scenarios, refactor.

### What NOT To Do
- Don't over-engineer. No abstract factories, no event buses, no CQRS. This is a straightforward CRUD+workflow system.
- Don't add features not in this plan. If it's not described here, don't build it.
- Don't use Spring profiles for feature flags. Don't add caching layers. Don't add async processing. Keep it simple.
- Don't create separate modules/subprojects. This is a single Spring Boot monolith.

---

## Section 1: Project Overview & Tech Stack

### What Is This System
The Anju Accompanying Medical Appointment Operation Management System is an **offline** backend platform built with Spring Boot. It manages property resources, appointment scheduling, financial bookkeeping, and file storage. The system runs fully locally — no internet, no external APIs, no external payment gateways.

### Tech Stack
| Layer | Technology |
|-------|------------|
| Language | Java 17 |
| Framework | Spring Boot 3.x |
| Build | Maven |
| Database | MySQL 8 |
| Security | Spring Security + JWT + BCrypt |
| Deployment | Docker + docker-compose |
| Storage | Local filesystem + MySQL |

### Project Coordinates
- Group: `com.anju`
- Artifact: `appointment-system`
- Base package: `com.anju.appointment`

### Dependencies (pom.xml)
```xml
spring-boot-starter-web
spring-boot-starter-data-jpa
spring-boot-starter-security
spring-boot-starter-validation
mysql-connector-j
io.jsonwebtoken:jjwt-api / jjwt-impl / jjwt-jackson
lombok
spring-boot-starter-test
```

### Package Structure
```
com.anju.appointment
├── common/       — Exceptions, base entity, security config, response utils
├── auth/         — Login, JWT, user entity, password rules
├── property/     — Property CRUD
├── appointment/  — Slots, booking, conflict detection, lifecycle
├── financial/    — Transactions, refunds (internal bookkeeping only)
├── file/         — Simple file upload + metadata
└── audit/        — Operation logging
```

### Roles (4 roles)
| Role | Access |
|------|--------|
| `ADMIN` | Full system control, config, audit, user management |
| `REVIEWER` | Approve/reject appointments, approve exception cancellations |
| `DISPATCHER` | Manage scheduling, slots, resource allocation |
| `FINANCE` | Record transactions, refunds, view reports |

### Configuration (application.yml)
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/anju_db
    username: root
    password: root
  jpa:
    hibernate:
      ddl-auto: update    # DEV ONLY — use 'validate' + Flyway migrations in production
    show-sql: false
  servlet:
    multipart:
      max-file-size: 10MB

app:
  jwt:
    secret: ${JWT_SECRET:default-dev-secret-change-in-production}
    access-token-expiry: 1800000    # 30 min
    refresh-token-expiry: 604800000 # 7 days
  storage:
    path: ./storage
```
**Important**: `app.jwt.secret` must be injected via environment variable `JWT_SECRET` in docker-compose. Never hardcode in source.

### Error Response Format (all endpoints)
Success responses return the object directly. Errors use this format:
```json
{
  "timestamp": "2026-04-10T22:00:00.000Z",
  "status": 409,
  "error": "Conflict",
  "message": "Business rule violation description"
}
```
Validation errors (400):
```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "errors": ["username: must not be blank", "password: must not be blank"]
}
```

| Code | Meaning |
|------|---------|
| 400 | Validation error |
| 401 | Unauthorized |
| 403 | Access denied (wrong role) |
| 404 | Resource not found |
| 409 | Business rule violation |
| 500 | Internal server error |

### Common Foundation
- `BaseEntity` — abstract class with `id` (Long, auto-generated), `createdAt`, `updatedAt` (auto-managed via `@PrePersist` / `@PreUpdate`)
- `GlobalExceptionHandler` — `@RestControllerAdvice` mapping exceptions to standard error format
- `BusinessRuleException` — maps to 409
- `ResourceNotFoundException` — maps to 404
- All list endpoints support pagination (default page=0, size=20) using Spring Data Page

---

## Section 2: Security & Authentication

### User Entity
```
User {
  id: Long
  username: String (unique)
  passwordHash: String (BCrypt)
  fullName: String
  role: Enum (ADMIN, REVIEWER, DISPATCHER, FINANCE)
  phone: String
  email: String
  enabled: boolean (default true)
  locked: boolean (default false)
  failedLoginAttempts: int (default 0)
  lockExpiresAt: LocalDateTime (nullable)
  forcePasswordReset: boolean (default false)
  createdAt: LocalDateTime
  updatedAt: LocalDateTime
}
```

### Password Rules
- Minimum 8 characters
- Must contain both letters and numbers
- Hashed with BCrypt

### Login Lockout
- Track `failedLoginAttempts` per user
- After 5 consecutive failures → set `locked=true`, `lockExpiresAt=now+15min`
- On login attempt: if locked and lockExpiresAt > now → reject with 409 "Account is locked"
- If locked and lockExpiresAt <= now → auto-unlock, reset counter
- On successful login → reset `failedLoginAttempts` to 0

### RefreshToken Entity
```
RefreshToken {
  id: Long
  token: String (unique)
  userId: Long
  expiresAt: LocalDateTime
  revoked: boolean (default false)
  createdAt: LocalDateTime
}
```

### JWT
- Access token: 30 min expiry, contains userId, username, role
- Refresh token: 7 days expiry, stored in `RefreshToken` table for revocation. On logout or refresh, revoke the old token.
- Delivered via HttpOnly cookies:
  - `accessToken` (SameSite=Strict, path=/, max-age=1800)
  - `refreshToken` (SameSite=Strict, path=/api/auth, max-age=604800)
- Also returned in response body for header-based auth

### Spring Security Config
- Stateless session (no server-side session)
- `JwtAuthenticationFilter` reads JWT from `Authorization: Bearer <token>` header OR `accessToken` cookie
- Permit without auth: `POST /api/auth/login`
- All other endpoints require authentication
- Role enforcement via `@PreAuthorize` on controllers
- **CORS**: Configure `CorsConfigurationSource` bean to allow `localhost` origins (for when a frontend runs on a different port). Allow all origins in dev, restrict in production.
- **Force password reset**: Add a filter/interceptor that checks `forcePasswordReset` on the authenticated user. If `true`, reject ALL requests except `POST /api/auth/change-password` and `POST /api/auth/logout` with `409` and message "Password change required". This ensures new users or reset users cannot access any functionality until they set a new password.
- **Login rate limiting**: The account lockout (5 attempts / 15 min) is the primary defense. No additional rate-limiting middleware needed for this offline system, but the lockout logic must be airtight — count failures even for non-existent usernames to prevent user enumeration.

### Data Masking (apply in all API responses)
- Phone: `13800138000` → `138****8000`
- Email: `john@example.com` → `joh****mple.com`
- Patient name: `Li Wei` → `Li ***`

**Implementation**: Create a `DataMaskingUtil` utility class in the `common/` package with static methods: `maskPhone()`, `maskEmail()`, `maskName()`. Call these in **DTO mapper/converter methods** when converting entities to response DTOs. Never mask at the entity level — masking is a presentation concern.

### Seed Data (on first startup)
- Create default ADMIN if no users exist
- Username: `admin`, Password: `Admin123`, forcePasswordReset: true

### Auth Endpoints

#### POST `/api/auth/login`
**Request**:
```json
{ "username": "admin", "password": "Password1" }
```
**Response** `200`:
```json
{
  "accessToken": "eyJhbG...",
  "refreshToken": null,
  "forcePasswordReset": false,
  "role": "ADMIN"
}
```
Also sets HttpOnly cookies.

**Errors**: `409` Invalid credentials | `409` Account disabled | `409` Account locked

#### POST `/api/auth/refresh`
No body. Reads `refreshToken` cookie.

**Response** `200`: Same shape as login response with new accessToken.

**Errors**: `400` No refresh cookie | `409` Invalid/expired token | `409` Account disabled/locked

#### POST `/api/auth/logout`
No body. Revokes refresh token in DB, clears both cookies.

**Response** `200`: Empty body.

#### POST `/api/auth/change-password`
Requires authentication.

**Request**:
```json
{ "oldPassword": "currentPassword", "newPassword": "NewSecure1" }
```
**Response** `200`: Empty body.

**Errors**: `409` Current password incorrect | `409` Password strength not met | `401` Not authenticated

---

## Section 3: User Management

All endpoints require **ADMIN** role.

#### POST `/api/admin/users`
**Request**:
```json
{
  "username": "john.doe",
  "password": "Initial1",
  "fullName": "John Doe",
  "role": "DISPATCHER",
  "phone": "13800138000",
  "email": "john@example.com"
}
```
**Response** `200`:
```json
{
  "id": 1,
  "username": "john.doe",
  "fullName": "John Doe",
  "role": "DISPATCHER",
  "phone": "138****8000",
  "email": "joh****mple.com",
  "enabled": true,
  "locked": false,
  "forcePasswordReset": true,
  "createdAt": "2026-04-10T22:00:00",
  "updatedAt": "2026-04-10T22:00:00"
}
```
**Errors**: `409` Username exists | `400` Password too weak

#### GET `/api/admin/users`
**Params** (optional): `role`, `enabled`, `page`, `size`

**Response** `200`: Paginated list of User objects (phone/email masked).

#### GET `/api/admin/users/{id}`
**Response** `200`: User object. **Error**: `404`

#### PUT `/api/admin/users/{id}`
**Request**:
```json
{ "fullName": "John Smith", "role": "REVIEWER", "phone": "13900139000", "email": "john.smith@example.com" }
```
**Response** `200`: Updated User.

#### PUT `/api/admin/users/{id}/disable`
**Response** `200`: User with `enabled: false`.

#### PUT `/api/admin/users/{id}/enable`
**Response** `200`: User with `enabled: true`.

#### PUT `/api/admin/users/{id}/unlock`
**Response** `200`: User with `locked: false`, `failedLoginAttempts: 0`.

#### PUT `/api/admin/users/{id}/reset-password`
**Request**:
```json
{ "newPassword": "TempPass1" }
```
**Response** `200`: Empty body. Sets `forcePasswordReset: true`.

---

## Section 4: Property Module

### Entity
```
Property {
  id: Long
  name: String
  type: String
  address: String
  description: String
  capacity: int
  status: Enum (ACTIVE, INACTIVE, MAINTENANCE)
  createdAt: LocalDateTime
  updatedAt: LocalDateTime
}
```

### Endpoints

#### POST `/api/properties`
**Requires**: ADMIN or DISPATCHER.

**Request**:
```json
{
  "name": "Meeting Room A",
  "type": "ROOM",
  "address": "Building 3, Floor 2",
  "description": "Standard consultation room",
  "capacity": 3
}
```
**Response** `200`:
```json
{
  "id": 1,
  "name": "Meeting Room A",
  "type": "ROOM",
  "address": "Building 3, Floor 2",
  "description": "Standard consultation room",
  "capacity": 3,
  "status": "ACTIVE",
  "createdAt": "2026-04-10T22:00:00",
  "updatedAt": "2026-04-10T22:00:00"
}
```

#### GET `/api/properties`
**Params** (optional): `type`, `status`, `keyword` (search name/address), `page`, `size`

**Response** `200`: Paginated list of Property objects.

#### GET `/api/properties/{id}`
**Response** `200`: Property object. **Error**: `404`

#### PUT `/api/properties/{id}`
**Requires**: ADMIN or DISPATCHER.

**Request**: Same fields as create (partial update).

**Response** `200`: Updated Property.

#### DELETE `/api/properties/{id}`
**Requires**: ADMIN.

**Response** `200`: Empty body.

**Behavior**: This is a **soft delete** — sets `status` to `INACTIVE`. The property record remains in the database for referential integrity (existing appointments, transactions, and audit logs reference it).

**Error**: `409` — Property has active appointments (status CREATED or CONFIRMED) and cannot be deleted.

---

## Section 5: Appointment Module

This is the **core module**. Most tests target this.

### Entities

```
AppointmentSlot {
  id: Long
  propertyId: Long
  date: LocalDate
  startTime: LocalTime
  endTime: LocalTime
  duration: int (minutes)
  capacity: int
  bookedCount: int (default 0)
  version: Long (@Version — for optimistic locking on concurrent bookings)
}
```

```
Appointment {
  id: Long
  slotId: Long
  propertyId: Long
  userId: Long (who created it)
  patientName: String
  patientPhone: String
  serviceType: String
  status: Enum (CREATED, CONFIRMED, COMPLETED, CANCELED, EXCEPTION)
  notes: String (nullable)
  rescheduleCount: int (default 0)
  cancelReason: String (nullable)
  completionNotes: String (nullable)
  expiresAt: LocalDateTime (createdAt + 15 min)
  idempotencyKey: String (unique)
  createdAt: LocalDateTime
  updatedAt: LocalDateTime
}
```

### Status Lifecycle
```
CREATED ──(confirm)──> CONFIRMED ──(complete)──> COMPLETED
   |                       |
   |──(cancel, >=1hr)──> CANCELED
   |──(cancel, <1hr)───> EXCEPTION ──(approve-cancel)──> CANCELED
   |
   |──(auto-release after 15 min)──> CANCELED

CONFIRMED ──(cancel, >=1hr)──> CANCELED
CONFIRMED ──(cancel, <1hr)───> EXCEPTION ──(approve-cancel)──> CANCELED
```

### Business Rules

1. **Slot durations**: Only 15, 30, 60, or 90 minutes allowed
2. **Slot generation**: Generate from working hours (e.g. 08:00–17:00) for a given property + date + duration. Each slot's `capacity` is inherited from `Property.capacity` by default, but can be overridden in the generation request.
3. **Conflict detection**: Before booking, check DB — if slot's `bookedCount >= capacity` → reject 409
4. **Advance booking**: Must be at least 2 hours in the future
5. **Max booking window**: Cannot book more than 14 days ahead
6. **Auto-release**: `@Scheduled` job runs every minute. Finds appointments where `status = CREATED AND expiresAt < now` → sets to CANCELED, decrements `bookedCount`. The `expiresAt` field is only meaningful for CREATED status — once confirmed, it is ignored.
7. **Reschedule limit**: Max 2 reschedules per appointment. 3rd attempt → 409
8. **Late cancellation**: Compare `now()` against the appointment's `slot.date + slot.startTime`. If the difference is < 1 hour → status becomes EXCEPTION (needs REVIEWER/ADMIN approval). If >= 1 hour → CANCELED directly
9. **Idempotency**: `idempotencyKey` is unique. Duplicate key → return existing appointment instead of creating new

### Endpoints

#### POST `/api/appointments/slots/generate`
**Requires**: ADMIN or DISPATCHER.

**Request**:
```json
{
  "propertyId": 1,
  "date": "2026-04-15",
  "slotDuration": 30,
  "startTime": "08:00",
  "endTime": "17:00",
  "capacity": 3
}
```
`capacity` is optional — if omitted, inherits from `Property.capacity`.

**Response** `200`:
```json
{
  "propertyId": 1,
  "date": "2026-04-15",
  "slotsGenerated": 18,
  "slotDuration": 30
}
```
**Errors**: `409` Slots already exist for this property/date | `400` Invalid duration | `404` Property not found

#### GET `/api/appointments/slots`
**Params** (required): `propertyId`, `date` (YYYY-MM-DD)

**Response** `200`:
```json
[
  {
    "id": 1,
    "propertyId": 1,
    "date": "2026-04-15",
    "startTime": "08:00",
    "endTime": "08:30",
    "duration": 30,
    "capacity": 3,
    "booked": 1,
    "available": true
  }
]
```
**Note**: No booking-window restrictions on listing. The 2hr minimum and 14-day maximum rules apply only when **booking** (POST /api/appointments), not when viewing slots. Dispatchers and admins need to view any date's schedule freely. Since `propertyId` + `date` are required params, the result set is naturally bounded (max ~36 slots for a full day at 15-min intervals) — no pagination needed.

#### POST `/api/appointments`
**Requires**: Any authenticated user (all roles can book appointments).

**Request**:
```json
{
  "slotId": 1,
  "propertyId": 1,
  "patientName": "Li Wei",
  "patientPhone": "13800138000",
  "serviceType": "GENERAL_CONSULTATION",
  "notes": "First visit",
  "idempotencyKey": "uuid-abc-123"
}
```
**Response** `200`:
```json
{
  "id": 1,
  "slotId": 1,
  "propertyId": 1,
  "userId": 3,
  "patientName": "Li ***",
  "patientPhone": "138****8000",
  "serviceType": "GENERAL_CONSULTATION",
  "status": "CREATED",
  "notes": "First visit",
  "rescheduleCount": 0,
  "idempotencyKey": "uuid-abc-123",
  "createdAt": "2026-04-10T22:00:00",
  "updatedAt": "2026-04-10T22:00:00",
  "expiresAt": "2026-04-10T22:15:00"
}
```
**Errors**: `409` Slot full | `409` Conflict | `409` < 2hr advance | `409` > 14 days | `409` Duplicate idempotency key

#### GET `/api/appointments`
**Params** (optional): `propertyId`, `status`, `dateFrom`, `dateTo`, `patientName`, `page`, `size`

ADMIN/DISPATCHER see all. Others see own only.

**Response** `200`: Paginated Appointment list.

#### GET `/api/appointments/{id}`
**Response** `200`: Appointment. **Error**: `404`

#### PUT `/api/appointments/{id}/confirm`
**Requires**: DISPATCHER, REVIEWER, or ADMIN.

**Response** `200`: Appointment with `status: "CONFIRMED"`.

**Errors**: `409` Not in CREATED status | `409` Expired (auto-released)

#### PUT `/api/appointments/{id}/complete`
**Requires**: DISPATCHER, REVIEWER, or ADMIN.

**Request**:
```json
{ "completionNotes": "Service completed successfully" }
```
**Response** `200`: Appointment with `status: "COMPLETED"`.

**Error**: `409` Not in CONFIRMED status.

#### PUT `/api/appointments/{id}/cancel`
**Request**:
```json
{ "reason": "Patient requested cancellation" }
```
**Response** `200`: Appointment with `status: "CANCELED"` or `"EXCEPTION"`.

**Rules**:
- If appointment time is < 1 hour away → EXCEPTION (needs reviewer approval)
- Otherwise → CANCELED, decrement slot bookedCount

**Errors**: `409` Already completed/canceled | `409` Not creator/ADMIN/DISPATCHER

#### PUT `/api/appointments/{id}/approve-cancel`
**Requires**: REVIEWER or ADMIN.

**Request**:
```json
{ "reason": "Approved: emergency situation" }
```
**Response** `200`: Appointment with `status: "CANCELED"`.

**Error**: `409` Not in EXCEPTION status.

#### PUT `/api/appointments/{id}/reschedule`
**Request**:
```json
{ "newSlotId": 5, "reason": "Schedule conflict" }
```
**Response** `200`: Appointment with new slot, `rescheduleCount` incremented.

**Errors**: `409` Max reschedules (2) reached | `409` New slot full | `409` Not in CREATED/CONFIRMED status | `409` New slot < 2hr ahead

---

## Section 6: Financial Module

**Internal bookkeeping only. No external payment integration.**

### Entities

```
Transaction {
  id: Long
  appointmentId: Long
  type: Enum (SERVICE_FEE, DEPOSIT, PENALTY, ADJUSTMENT)
  amount: BigDecimal
  currency: String (default "CNY")
  status: String (default "RECORDED")
  description: String
  idempotencyKey: String (unique)
  createdBy: Long
  createdAt: LocalDateTime
  updatedAt: LocalDateTime
}
```

```
Refund {
  id: Long
  originalTransactionId: Long
  amount: BigDecimal
  reason: String
  status: String (default "RECORDED")
  idempotencyKey: String (unique)
  createdBy: Long
  createdAt: LocalDateTime
}
```

### Endpoints

#### POST `/api/financial/transactions`
**Requires**: FINANCE or ADMIN.

**Request**:
```json
{
  "appointmentId": 1,
  "type": "SERVICE_FEE",
  "amount": 500.00,
  "currency": "CNY",
  "description": "Consultation service fee",
  "idempotencyKey": "txn-uuid-456"
}
```
**Response** `200`:
```json
{
  "id": 1,
  "appointmentId": 1,
  "type": "SERVICE_FEE",
  "amount": 500.00,
  "currency": "CNY",
  "status": "RECORDED",
  "description": "Consultation service fee",
  "idempotencyKey": "txn-uuid-456",
  "createdBy": 2,
  "createdAt": "2026-04-10T22:00:00",
  "updatedAt": "2026-04-10T22:00:00"
}
```
**Errors**: `409` Duplicate idempotency key | `404` Appointment not found

#### GET `/api/financial/transactions`
**Requires**: FINANCE or ADMIN.

**Params** (optional): `appointmentId`, `type`, `status`, `dateFrom`, `dateTo`, `page`, `size`

**Response** `200`: Paginated Transaction list.

#### GET `/api/financial/transactions/{id}`
**Requires**: FINANCE or ADMIN. **Response** `200`: Transaction. **Error**: `404`

#### POST `/api/financial/refunds`
**Requires**: FINANCE or ADMIN.

**Request**:
```json
{
  "originalTransactionId": 1,
  "amount": 250.00,
  "reason": "Partial refund",
  "idempotencyKey": "ref-uuid-789"
}
```
**Response** `200`:
```json
{
  "id": 1,
  "originalTransactionId": 1,
  "amount": 250.00,
  "reason": "Partial refund",
  "status": "RECORDED",
  "idempotencyKey": "ref-uuid-789",
  "createdBy": 2,
  "createdAt": "2026-04-10T22:00:00"
}
```
**Errors**: `409` Exceeds original amount | `409` Already fully refunded | `404` Original not found

**Important**: When validating refund amount, check the **cumulative sum** of all existing refunds for that transaction, not just the individual refund amount. Example: original = 500, first refund = 300 (ok), second refund = 250 (reject — 300 + 250 = 550 > 500). Query: `SELECT COALESCE(SUM(amount), 0) FROM refund WHERE originalTransactionId = ?`

#### GET `/api/financial/refunds`
**Requires**: FINANCE or ADMIN.

**Params** (optional): `originalTransactionId`, `dateFrom`, `dateTo`, `page`, `size`

**Response** `200`: Paginated Refund list.

#### GET `/api/financial/reports/daily`
**Requires**: FINANCE or ADMIN.

**Params** (required): `date` (YYYY-MM-DD)

**Response** `200`:
```json
{
  "date": "2026-04-10",
  "totalTransactions": 12,
  "totalAmount": 6000.00,
  "totalRefunds": 500.00,
  "netAmount": 5500.00,
  "currency": "CNY",
  "byType": [
    { "type": "SERVICE_FEE", "count": 8, "amount": 4000.00 },
    { "type": "DEPOSIT", "count": 3, "amount": 1500.00 },
    { "type": "PENALTY", "count": 1, "amount": 500.00 }
  ],
  "generatedAt": "2026-04-10T23:59:59"
}
```

---

## Section 7: File Module

**Keep it simple. Basic file upload + metadata storage. No chunked upload, no deduplication, no versioning, no recycle bin.**

### Entity
```
FileRecord {
  id: Long
  fileName: String
  filePath: String
  contentType: String
  fileSize: Long
  module: String (PROPERTY, APPOINTMENT, FINANCIAL)
  referenceId: Long (nullable)
  description: String (nullable)
  uploadedBy: Long
  uploadedAt: LocalDateTime
}
```

### Endpoints

#### POST `/api/files/upload`
**Request**: `multipart/form-data`
| Part | Type | Required |
|------|------|----------|
| `file` | file | yes |
| `module` | string | yes |
| `referenceId` | long | no |
| `description` | string | no |

**Response** `200`:
```json
{
  "id": 1,
  "fileName": "patient-record.pdf",
  "filePath": "files/appointment/1/patient-record.pdf",
  "contentType": "application/pdf",
  "fileSize": 102400,
  "module": "APPOINTMENT",
  "referenceId": 1,
  "description": "Patient medical history",
  "uploadedBy": 3,
  "uploadedAt": "2026-04-10T22:00:00"
}
```
**Errors**: `400` File is empty

Store files under `storage/` directory on local filesystem.

#### GET `/api/files`
**Params** (optional): `module`, `referenceId`, `fileName`, `page`, `size`

**Response** `200`: Paginated FileRecord list.

#### GET `/api/files/{id}`
**Response** `200`: FileRecord metadata. **Error**: `404`

#### GET `/api/files/{id}/download`
**Response** `200`: File bytes with `Content-Type` and `Content-Disposition` headers.

**Errors**: `404` File not found | `403` Access denied

---

## Section 8: Audit Logging

### Entity
```
AuditLog {
  id: Long
  userId: Long
  username: String
  module: String (AUTH, PROPERTY, APPOINTMENT, FINANCIAL, FILE)
  operation: String (CREATE, UPDATE, DELETE, LOGIN, LOGIN_FAILED, STATE_CHANGE)
  entityType: String
  entityId: Long (nullable)
  details: String
  ipAddress: String
  timestamp: LocalDateTime
}
```

### Implementation Approach
Create an `AuditService` in the `audit/` package with a simple `log()` method:
```java
void log(Long userId, String username, String module, String operation,
         String entityType, Long entityId, String details, String ipAddress)
```
Inject `AuditService` into each service class and call it directly at the end of each operation. No AOP, no Spring events — keep it explicit and simple. Extract the IP address from `HttpServletRequest` and pass it through the service layer.

### What to Log
- Login success and failure
- All create/update/delete on properties, appointments, transactions, refunds, files
- Appointment state transitions (confirm, cancel, complete, reschedule, auto-release)
- User management operations

### Endpoint

#### GET `/api/audit/logs`
**Requires**: ADMIN.

**Params** (optional): `userId`, `module`, `operation`, `dateFrom`, `dateTo`, `page`, `size` (default 50)

**Response** `200`:
```json
{
  "content": [
    {
      "id": 1,
      "userId": 3,
      "username": "john.doe",
      "module": "APPOINTMENT",
      "operation": "CREATE",
      "entityType": "Appointment",
      "entityId": 1,
      "details": "Created appointment for slot 2026-04-15 08:00-08:30",
      "ipAddress": "192.168.1.100",
      "timestamp": "2026-04-10T22:00:00"
    }
  ],
  "totalElements": 500,
  "totalPages": 10,
  "number": 0,
  "size": 50
}
```

---

## Section 9: Docker Deployment

### Dockerfile
```dockerfile
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### docker-compose.yml
```yaml
version: '3.8'
services:
  app:
    build: .
    ports:
      - "8080:8080"
    depends_on:
      mysql:
        condition: service_healthy
    volumes:
      - ./storage:/app/storage
    environment:
      SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/anju_db?useSSL=false&allowPublicKeyRetrieval=true
      SPRING_DATASOURCE_USERNAME: root
      SPRING_DATASOURCE_PASSWORD: root
      SPRING_JPA_HIBERNATE_DDL_AUTO: update
      JWT_SECRET: change-this-to-a-secure-random-string-in-production

  mysql:
    image: mysql:8
    ports:
      - "3306:3306"
    environment:
      MYSQL_DATABASE: anju_db
      MYSQL_ROOT_PASSWORD: root
    volumes:
      - mysql_data:/var/lib/mysql
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]
      interval: 10s
      timeout: 5s
      retries: 5

volumes:
  mysql_data:
```

---

## Section 10: Implementation Order

Build in this order. Each phase must compile before moving on.

| Step | What | Key Files |
|------|------|-----------|
| 1 | Maven project + pom.xml + application.yml | pom.xml, application.yml |
| 2 | BaseEntity, GlobalExceptionHandler, custom exceptions | common/ |
| 3 | User entity, JWT provider, Security config, Auth controller | auth/ |
| 4 | User management endpoints | auth/ (or admin/) |
| 5 | Property entity + CRUD endpoints | property/ |
| 6 | AppointmentSlot + Appointment entities | appointment/ |
| 7 | Slot generation + slot listing endpoints | appointment/ |
| 8 | Appointment booking with conflict detection + idempotency | appointment/ |
| 9 | Appointment lifecycle (confirm/complete/cancel/reschedule) | appointment/ |
| 10 | Auto-release scheduled job | appointment/ |
| 11 | Transaction + Refund entities + endpoints | financial/ |
| 12 | Daily report endpoint | financial/ |
| 13 | FileRecord entity + upload/download endpoints | file/ |
| 14 | AuditLog entity + logging across all modules | audit/ |
| 15 | Dockerfile + docker-compose.yml | root |

---

## Section 11: Test Scenarios (What Will Be Tested)

### Appointment Tests (Highest Priority)
- [ ] Slot generation creates correct number of slots for given hours + duration
- [ ] Booking succeeds for available slot
- [ ] Booking rejected when slot at capacity (409)
- [ ] Booking rejected when < 2 hours advance (409)
- [ ] Booking rejected when > 14 days ahead (409)
- [ ] Duplicate idempotency key returns existing appointment
- [ ] Lifecycle: CREATED → CONFIRMED → COMPLETED
- [ ] Auto-release: unconfirmed CREATED appointment canceled after 15 min
- [ ] Cancel >= 1hr before → CANCELED
- [ ] Cancel < 1hr before → EXCEPTION
- [ ] EXCEPTION → approve-cancel → CANCELED
- [ ] Reschedule works (1st and 2nd time)
- [ ] 3rd reschedule rejected (409)
- [ ] Canceled appointment frees slot capacity

### Auth Tests
- [ ] Login returns JWT + cookies
- [ ] Wrong password returns 409
- [ ] 5 wrong attempts → account locked
- [ ] Locked account returns 409 for 15 min
- [ ] Correct password after unlock works
- [ ] Invalid/expired JWT returns 401
- [ ] Wrong role returns 403
- [ ] Change password validates old password
- [ ] New password must be 8+ chars with letters + numbers

### Financial Tests
- [ ] Transaction created with idempotency key
- [ ] Duplicate key returns existing transaction
- [ ] Refund recorded against transaction
- [ ] Refund > original amount rejected (409)
- [ ] Daily report aggregates correctly

### Property Tests
- [ ] CRUD operations work
- [ ] Delete rejected if active appointments exist (409)
- [ ] Only ADMIN/DISPATCHER can create/update

### File Tests
- [ ] Upload stores file and returns metadata
- [ ] Download returns correct file bytes
- [ ] List files with filters works

### RBAC Tests
- [ ] ADMIN can access everything
- [ ] FINANCE cannot create appointments
- [ ] DISPATCHER cannot record transactions
- [ ] REVIEWER can approve cancellations
