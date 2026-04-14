# Anju Accompanying Medical Appointment System - System Design Document

## 1. Overview

The Anju Accompanying Medical Appointment Operation Management System is an offline backend platform built with Spring Boot to manage property resources, appointment scheduling, financial bookkeeping, and file management in a controlled operational environment.

The system supports four roles: administrators, reviewers, dispatchers, and finance staff. It is designed for fully offline deployment with strict business rules, auditability, and workflow-driven operations.

Core functionality includes property lifecycle management, appointment scheduling with conflict prevention, financial transaction tracking (internal only), and file handling with deduplication and version control.

## 2. Architecture

### 2.1 Technology Stack

| Layer | Technology |
|-------|------------|
| Backend | Spring Boot (Java 17) |
| Database | MySQL |
| Security | Spring Security + BCrypt |
| Deployment | Docker (offline environment) |
| Configuration | Local Nacos |
| Storage | Local filesystem + database |

### 2.2 High-Level Architecture

```
Clients (Admin / Reviewer / Dispatcher / Finance)
        |
        | REST API Calls
        v
Spring Security Filter Chain (Authentication + RBAC)
        |
        v
Controller Layer (REST APIs)
        |
        v
Service Layer (Business Logic)
        |
        v
Data Access Layer (Repositories)
        |
        v
MySQL Database + Local File Storage
```

### 2.3 Package Structure

- `auth` - Authentication, login, JWT handling, user management
- `property` - Property creation, update, listing, compliance rules
- `appointment` - Scheduling engine, slot management, conflict detection
- `financial` - Transactions, refunds, settlement processing
- `file` - File upload, deduplication, version control, storage
- `audit` - System logs, operation tracking, audit history
- `admin` - Administrative configuration and management tools
- `reviewer` - Review workflows and approval processes
- `common` - Shared utilities, exceptions, and security configuration

## 3. Security Model

### 3.1 Authentication

- Local username/password authentication
- BCrypt password hashing (minimum 8 characters, letters + numbers required)
- Account lock after 5 failed login attempts (15 minutes)
- Stateless security model using Spring Security filters
- Role-based access control enforced at API level

### 3.2 Roles and Permissions

| Role | Description |
|------|-------------|
| `ADMIN` | Full system control, configuration, audit access |
| `REVIEWER` | Approve/reject appointments and operations |
| `DISPATCHER` | Manage scheduling and resource allocation |
| `FINANCE` | Handle transactions, refunds, and reports |

### 3.3 Security Rules

- Sensitive data (ID, contact info) is masked or encrypted
- All critical operations logged in audit tables
- Idempotency keys used for appointment and financial operations
- Strict role validation for financial and scheduling actions

## 4. Core Modules

### 4.1 Property Module

- Create, update, delete properties
- Manage availability periods
- Attach media files (images/videos)
- Validate compliance rules
- Maintain rental and vacancy schedules

### 4.2 Appointment Module

- Fixed slot durations: 15/30/60/90 minutes
- Prevent overlapping bookings for same staff/resource
- Auto-release unconfirmed appointments after 15 minutes
- Booking rules:
  - Minimum 2 hours advance booking
  - Maximum 14 days ahead
- Lifecycle:
  - `CREATED` → `CONFIRMED` → `COMPLETED` / `CANCELED` / `EXCEPTION`

### 4.3 Financial Module

- Internal bookkeeping system (no external payment integration)
- Track transactions, refunds, and settlements
- Generate daily financial summaries
- Maintain immutable audit logs for all financial operations

### 4.4 File Module

- Chunk-based file upload
- Resumable upload support
- SHA-256 hash deduplication
- File versioning support
- Preview support for common file types
- Recycle bin retention (30 days)

## 5. Data Model

- `Property` → `Appointment` → `Financial Record` chain
- Files linked across all modules
- Audit logs track all system operations
- Users mapped to roles and actions

## 6. Appointment Rules Engine

- No overlapping appointments allowed
- Slot capacity enforced per resource
- Automatic release after timeout (15 min)
- Strict validation for rescheduling and cancellation
- Maximum 2 reschedules per appointment

## 7. Financial Rules

- No external payment gateway integration
- All transactions stored internally
- Refunds tracked with original reference
- Daily reconciliation reports generated automatically

## 8. File Storage Rules

- Stored on local disk
- SHA-256 checksum validation
- Duplicate prevention via hashing
- Access controlled by role and ownership
- Automatic cleanup via retention policy

## 9. Error Handling

| Code | Description |
|------|-------------|
| 400 | Validation error |
| 401 | Unauthorized |
| 403 | Access denied |
| 404 | Resource not found |
| 409 | Business rule violation |
| 500 | Internal system error |

## 10. Deployment

- Fully offline Docker-based deployment
- MySQL as primary database
- Local Nacos configuration management
- No external API dependencies

## 11. Testing Strategy

- Unit tests for business services
- Integration tests for REST APIs
- Appointment conflict test cases
- Financial consistency validation tests
- File integrity and deduplication tests
- Security and RBAC validation tests
