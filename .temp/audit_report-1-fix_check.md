# Follow-Up Review of Previously Reported Issues
Date: 2026-04-15

## Scope and Boundary
- Reviewed the previously reported issues from `.tmp/delivery-architecture-audit-2026-04-15.md` (Section 5).
- Static analysis only.
- Did not start the project, run tests, run Docker, or perform manual/browser checks.
- Conclusions are limited to what is provable from current repository contents.

## Summary
- Fixed: 7
- Partially Fixed: 1
- Not Fixed: 0

## Issue-by-Issue Verification

### 1) Cross-user idempotency key reuse could return another user’s existing record
- Status: **Fixed**
- Rationale: Appointment/transaction/refund idempotency now rejects keys reused by a different actor rather than returning another user’s resource.
- Evidence:
  - `src/main/java/com/anju/appointment/appointment/service/AppointmentService.java:120-126`
  - `src/main/java/com/anju/appointment/financial/service/FinancialService.java:59-65`
  - `src/main/java/com/anju/appointment/financial/service/FinancialService.java:122-128`

### 2) Hardcoded default bootstrap admin credentials
- Status: **Fixed**
- Rationale: Admin bootstrap now depends on `APP_SECURITY_BOOTSTRAP_ADMIN_PASSWORD`; if absent, bootstrap creation is skipped.
- Evidence:
  - `src/main/java/com/anju/appointment/auth/service/AuthService.java:57-66`
  - `src/main/java/com/anju/appointment/auth/service/AuthService.java:58-61`
  - `README.md:15-21`
  - `src/test/java/com/anju/appointment/auth/AuthServiceTest.java:607-615`
  - `src/test/java/com/anju/appointment/auth/AuthServiceTest.java:640-649`

### 3) Staff assignment accepted arbitrary user ID without role/existence validation
- Status: **Fixed**
- Rationale: Assignment now verifies target user exists, is enabled, and has `SERVICE_STAFF` role.
- Evidence:
  - `src/main/java/com/anju/appointment/appointment/service/AppointmentService.java:275-282`

### 4) Daily financial reporting mixed currencies but output fixed `CNY`
- Status: **Fixed**
- Rationale: Report generation now detects mixed currencies and rejects the report; output currency is derived from data (or default when empty).
- Evidence:
  - `src/main/java/com/anju/appointment/financial/service/FinancialService.java:175-185`
  - `src/main/java/com/anju/appointment/financial/service/FinancialService.java:214`

### 5) “Full audit logging” only partially implemented
- Status: **Partially Fixed**
- Rationale: Additional audit events were added (e.g., password change, report/export, file download), but many read/list flows are still not audited, so “full audit logging of operations” remains unproven.
- Evidence (added):
  - `src/main/java/com/anju/appointment/auth/service/AuthService.java:207-208`
  - `src/main/java/com/anju/appointment/financial/service/FinancialService.java:204-206`
  - `src/main/java/com/anju/appointment/financial/service/FinancialService.java:239-241`
  - `src/main/java/com/anju/appointment/file/service/FileService.java:142-145`
- Evidence (still missing on key read/list flows):
  - `src/main/java/com/anju/appointment/financial/service/FinancialService.java:95-112`
  - `src/main/java/com/anju/appointment/financial/service/FinancialService.java:114-117`
  - `src/main/java/com/anju/appointment/financial/service/FinancialService.java:159-165`
  - `src/main/java/com/anju/appointment/financial/service/FinancialService.java:300-304`
  - `src/main/java/com/anju/appointment/file/service/FileService.java:109-119`
  - `src/main/java/com/anju/appointment/file/service/FileService.java:121-125`
  - `src/main/java/com/anju/appointment/file/service/FileService.java:151-159`

### 6) Appointment date filtering used record creation time, not scheduled date
- Status: **Fixed**
- Rationale: Queries now join `AppointmentSlot` and filter by slot date instead of appointment `createdAt`.
- Evidence:
  - `src/main/java/com/anju/appointment/appointment/repository/AppointmentRepository.java:41-47`
  - `src/main/java/com/anju/appointment/appointment/repository/AppointmentRepository.java:54-60`
  - `src/main/java/com/anju/appointment/appointment/repository/AppointmentRepository.java:68-74`

### 7) Password strength policy too weak
- Status: **Fixed**
- Rationale: Policy now requires minimum length 12 and uppercase/lowercase/digit/special character.
- Evidence:
  - `src/main/java/com/anju/appointment/auth/service/AuthService.java:30-32`
  - `src/main/java/com/anju/appointment/auth/service/AuthService.java:211-217`

### 8) Slot generation allowed invalid time windows (silent zero-slot behavior)
- Status: **Fixed**
- Rationale: Explicit validation now rejects `startTime >= endTime`.
- Evidence:
  - `src/main/java/com/anju/appointment/appointment/service/AppointmentService.java:76-78`

## Final Assessment
Most previously reported defects are now addressed by static code evidence.

Outstanding item:
- The prior “full audit logging” finding is only **partially** resolved; key read/list operations still show no audit instrumentation.

Static boundary still applies:
- This recheck did not execute the project or tests.
- Runtime/DB compatibility of the updated date-cast query logic remains **Manual Verification Required**.
