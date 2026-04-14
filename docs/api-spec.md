# Anju Accompanying Medical Appointment System - API Specification

## Authentication

All API endpoints (except login) require a valid JWT. Two delivery methods:
- **Header**: `Authorization: Bearer <accessToken>`
- **Cookie**: `accessToken` HttpOnly cookie (set by login/refresh)

### Error Response Format

```json
{
  "timestamp": "2026-04-10T22:00:00.000Z",
  "status": 409,
  "error": "Conflict",
  "message": "Business rule violation description"
}
```

Validation errors include an `errors` array:
```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "errors": ["username: must not be blank", "password: must not be blank"]
}
```

### Standard Error Codes

| Code | Description |
|------|-------------|
| 400 | Validation error |
| 401 | Unauthorized |
| 403 | Access denied |
| 404 | Resource not found |
| 409 | Business rule violation |
| 500 | Internal system error |

---

## 1. Authentication (`/api/auth`)

### POST `/api/auth/login`

Login and receive JWT tokens.

**Request**:
```json
{
  "username": "admin",
  "password": "Password1"
}
```

**Response** `200 OK`:
```json
{
  "accessToken": "eyJhbG...",
  "refreshToken": null,
  "forcePasswordReset": false,
  "role": "ADMIN"
}
```

Sets cookies:
- `accessToken` (HttpOnly, SameSite=Strict, path=/, max-age=1800)
- `refreshToken` (HttpOnly, SameSite=Strict, path=/api/auth, max-age=604800)

**Errors**:
- `409` - Invalid username or password
- `409` - Account is disabled
- `409` - Account is locked (unlocks after 15 minutes)

**Note**: Account locks after 5 consecutive failed login attempts.

---

### POST `/api/auth/refresh`

Refresh access token using the refresh cookie.

**Request**: No body. `refreshToken` cookie is read automatically.

**Response** `200 OK`:
```json
{
  "accessToken": "eyJhbG...",
  "refreshToken": null,
  "forcePasswordReset": false,
  "role": "DISPATCHER"
}
```

**Errors**:
- `400` - No refresh cookie
- `409` - Invalid/revoked/expired refresh token
- `409` - Account is disabled or locked

---

### POST `/api/auth/logout`

Revoke refresh token and clear cookies.

**Request**: No body.

**Response** `200 OK`: Empty body.

---

### POST `/api/auth/change-password`

Change the authenticated user's password.

**Request**:
```json
{
  "oldPassword": "currentPassword",
  "newPassword": "NewSecure1"
}
```

**Response** `200 OK`: Empty body.

**Password rules**: Minimum 8 characters, must contain both letters and numbers.

**Errors**:
- `409` - Current password is incorrect
- `409` - Password strength requirements not met
- `401` - Not authenticated

---

### POST `/api/auth/verify`

Secondary verification for sensitive operations (optional).

**Request**:
```json
{
  "password": "currentPassword",
  "operation": "FINANCIAL_SETTLEMENT"
}
```

**Response** `200 OK`:
```json
{
  "verificationToken": "abc123...",
  "expiresIn": 300
}
```

---

## 2. User Management (`/api/admin/users`)

All endpoints require **ADMIN** role.

### POST `/api/admin/users`

Create a new user.

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

**Response** `200 OK`:
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

**Note**: Sensitive data (phone, email) is masked in responses. Password is hashed with BCrypt.

**Errors**:
- `409` - Username already exists
- `400` - Password does not meet strength requirements

---

### GET `/api/admin/users`

List all users.

**Parameters** (all optional):
| Param | Type | Description |
|-------|------|-------------|
| `role` | string | Filter by role (ADMIN, REVIEWER, DISPATCHER, FINANCE) |
| `enabled` | boolean | Filter by enabled status |
| `page` | int | Page number (default: 0) |
| `size` | int | Page size (default: 20) |

**Response** `200 OK`:
```json
{
  "content": [ ... ],
  "totalElements": 25,
  "totalPages": 2,
  "number": 0,
  "size": 20
}
```

---

### GET `/api/admin/users/{id}`

Get a single user.

**Response** `200 OK`: User object.

**Errors**: `404` - User not found.

---

### PUT `/api/admin/users/{id}`

Update a user.

**Request**:
```json
{
  "fullName": "John Smith",
  "role": "REVIEWER",
  "phone": "13900139000",
  "email": "john.smith@example.com"
}
```

**Response** `200 OK`: Updated User object.

---

### PUT `/api/admin/users/{id}/disable`

Disable a user account.

**Response** `200 OK`: Updated User object with `enabled: false`.

---

### PUT `/api/admin/users/{id}/enable`

Enable a user account.

**Response** `200 OK`: Updated User object with `enabled: true`.

---

### PUT `/api/admin/users/{id}/unlock`

Unlock a locked user account.

**Response** `200 OK`: Updated User object with `locked: false`.

---

### PUT `/api/admin/users/{id}/reset-password`

Reset a user's password. User will be forced to change on next login.

**Request**:
```json
{
  "newPassword": "TempPass1"
}
```

**Response** `200 OK`: Empty body.

---

## 3. Properties (`/api/properties`)

### POST `/api/properties`

Create a new property. **Requires**: ADMIN or DISPATCHER.

**Request**:
```json
{
  "name": "Meeting Room A",
  "type": "ROOM",
  "address": "Building 3, Floor 2",
  "description": "Standard consultation room with medical equipment",
  "capacity": 3,
  "rentalRules": {
    "minDuration": 30,
    "maxDuration": 120,
    "advanceBookingHours": 2,
    "maxAdvanceDays": 14
  }
}
```

**Response** `200 OK`:
```json
{
  "id": 1,
  "name": "Meeting Room A",
  "type": "ROOM",
  "address": "Building 3, Floor 2",
  "description": "Standard consultation room with medical equipment",
  "capacity": 3,
  "status": "ACTIVE",
  "complianceStatus": "PENDING",
  "rentalRules": {
    "minDuration": 30,
    "maxDuration": 120,
    "advanceBookingHours": 2,
    "maxAdvanceDays": 14
  },
  "createdAt": "2026-04-10T22:00:00",
  "updatedAt": "2026-04-10T22:00:00"
}
```

---

### GET `/api/properties`

List properties with filters.

**Parameters** (all optional):
| Param | Type | Description |
|-------|------|-------------|
| `type` | string | Filter by property type |
| `status` | string | Filter by status (ACTIVE, INACTIVE, MAINTENANCE) |
| `complianceStatus` | string | Filter (PENDING, COMPLIANT, NON_COMPLIANT) |
| `keyword` | string | Search name/address/description |
| `page` | int | Page number (default: 0) |
| `size` | int | Page size (default: 20) |

**Response** `200 OK`: Paginated list of Property objects.

---

### GET `/api/properties/{id}`

Get property detail.

**Response** `200 OK`: Property object with rental rules.

**Errors**: `404` - Property not found.

---

### PUT `/api/properties/{id}`

Update a property. **Requires**: ADMIN or DISPATCHER.

**Request**:
```json
{
  "name": "Meeting Room A - Updated",
  "description": "Renovated consultation room",
  "capacity": 5,
  "status": "ACTIVE",
  "rentalRules": {
    "minDuration": 15,
    "maxDuration": 90,
    "advanceBookingHours": 2,
    "maxAdvanceDays": 14
  }
}
```

**Response** `200 OK`: Updated Property object.

---

### DELETE `/api/properties/{id}`

Delete a property. **Requires**: ADMIN.

**Response** `200 OK`: Empty body.

**Errors**: `409` - Property has active appointments and cannot be deleted.

---

### PUT `/api/properties/{id}/validate`

Validate property compliance. **Requires**: REVIEWER or ADMIN.

**Request**:
```json
{
  "complianceStatus": "COMPLIANT",
  "notes": "All safety equipment verified"
}
```

**Response** `200 OK`: Updated Property object with new compliance status.

---

### POST `/api/properties/{id}/media`

Upload media (image/video) for a property. **Requires**: ADMIN or DISPATCHER.

**Request**: `multipart/form-data` with `file` part (JPG/PNG/MP4, max 10MB).

**Response** `200 OK`:
```json
{
  "id": 1,
  "propertyId": 1,
  "fileName": "room-a-photo.jpg",
  "filePath": "properties/1/room-a-photo.jpg",
  "contentType": "image/jpeg",
  "fileSize": 245760,
  "checksum": "e3b0c44298fc...",
  "displayOrder": 1,
  "uploadedAt": "2026-04-10T22:00:00"
}
```

---

### GET `/api/properties/{id}/media`

List media files for a property.

**Response** `200 OK`: `PropertyMedia[]`

---

### DELETE `/api/properties/{id}/media/{mediaId}`

Delete a media file. **Requires**: ADMIN or DISPATCHER.

**Response** `200 OK`: Empty body.

---

### PUT `/api/properties/{id}/availability`

Set availability schedule for a property. **Requires**: ADMIN or DISPATCHER.

**Request**:
```json
{
  "periods": [
    {
      "dayOfWeek": "MONDAY",
      "startTime": "08:00",
      "endTime": "17:00"
    },
    {
      "dayOfWeek": "TUESDAY",
      "startTime": "08:00",
      "endTime": "17:00"
    }
  ],
  "vacancyDates": ["2026-05-01", "2026-05-02"]
}
```

**Response** `200 OK`:
```json
{
  "propertyId": 1,
  "periods": [ ... ],
  "vacancyDates": ["2026-05-01", "2026-05-02"]
}
```

---

### GET `/api/properties/{id}/availability`

Get availability schedule for a property.

**Parameters**:
| Param | Type | Required | Description |
|-------|------|----------|-------------|
| `from` | date | no | Start date (default: today) |
| `to` | date | no | End date (default: 14 days from today) |

**Response** `200 OK`: Availability object with periods and vacancy dates.

---

## 4. Appointments (`/api/appointments`)

### POST `/api/appointments/slots/generate`

Generate appointment time slots for a property and date. **Requires**: ADMIN or DISPATCHER.

**Request**:
```json
{
  "propertyId": 1,
  "date": "2026-04-15",
  "slotDuration": 30,
  "startTime": "08:00",
  "endTime": "17:00"
}
```

**Response** `200 OK`:
```json
{
  "propertyId": 1,
  "date": "2026-04-15",
  "slotsGenerated": 18,
  "slotDuration": 30
}
```

**Slot durations**: 15, 30, 60, or 90 minutes.

**Errors**:
- `409` - Slots already exist for this property/date
- `400` - Invalid slot duration

---

### GET `/api/appointments/slots`

Get available appointment slots.

**Parameters**:
| Param | Type | Required | Description |
|-------|------|----------|-------------|
| `propertyId` | long | yes | Property ID |
| `date` | date (YYYY-MM-DD) | yes | Appointment date |

**Response** `200 OK`:
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

**Errors**:
- `409` - Date must be at least 2 hours in the future
- `409` - Date cannot be more than 14 days ahead

---

### POST `/api/appointments`

Create an appointment (book a slot).

**Request**:
```json
{
  "slotId": 1,
  "propertyId": 1,
  "patientName": "Li Wei",
  "patientPhone": "13800138000",
  "serviceType": "GENERAL_CONSULTATION",
  "notes": "First visit, needs wheelchair access",
  "idempotencyKey": "uuid-abc-123"
}
```

**Response** `200 OK`:
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
  "notes": "First visit, needs wheelchair access",
  "rescheduleCount": 0,
  "idempotencyKey": "uuid-abc-123",
  "createdAt": "2026-04-10T22:00:00",
  "updatedAt": "2026-04-10T22:00:00",
  "expiresAt": "2026-04-10T22:15:00"
}
```

**Note**: Unconfirmed appointments are automatically released after 15 minutes.

**Errors**:
- `409` - Slot is fully booked (no capacity)
- `409` - Time conflict with existing appointment for same staff/resource
- `409` - Booking must be at least 2 hours in advance
- `409` - Booking cannot be more than 14 days ahead
- `409` - Duplicate request (idempotency key already used)

---

### GET `/api/appointments`

List appointments with filters.

**Parameters** (all optional):
| Param | Type | Description |
|-------|------|-------------|
| `propertyId` | long | Filter by property |
| `status` | string | Filter by status |
| `dateFrom` | date | Start date range |
| `dateTo` | date | End date range |
| `patientName` | string | Search patient name |
| `page` | int | Page number (default: 0) |
| `size` | int | Page size (default: 20) |

**Response** `200 OK`: Paginated list of Appointment objects.

**Access**: ADMIN and DISPATCHER see all. Others see only their own.

---

### GET `/api/appointments/{id}`

Get appointment detail.

**Response** `200 OK`: Appointment object.

**Errors**: `404` - Appointment not found.

---

### PUT `/api/appointments/{id}/confirm`

Confirm a created appointment. **Requires**: DISPATCHER, REVIEWER, or ADMIN.

**Response** `200 OK`: Updated Appointment with `status: "CONFIRMED"`.

**Errors**:
- `409` - Appointment is not in CREATED status
- `409` - Appointment has expired (auto-released after 15 minutes)

---

### PUT `/api/appointments/{id}/complete`

Mark an appointment as completed. **Requires**: DISPATCHER, REVIEWER, or ADMIN.

**Request**:
```json
{
  "completionNotes": "Service completed successfully"
}
```

**Response** `200 OK`: Updated Appointment with `status: "COMPLETED"`.

**Errors**: `409` - Appointment is not in CONFIRMED status.

---

### PUT `/api/appointments/{id}/cancel`

Cancel an appointment.

**Request**:
```json
{
  "reason": "Patient requested cancellation"
}
```

**Response** `200 OK`: Updated Appointment with `status: "CANCELED"` or `"EXCEPTION"`.

**Rules**:
- If canceled less than 1 hour before appointment time, status becomes `EXCEPTION` (requires reviewer approval).
- Otherwise, status becomes `CANCELED` directly.

**Errors**:
- `409` - Appointment is already completed or canceled
- `409` - Only the creator or ADMIN/DISPATCHER can cancel

---

### PUT `/api/appointments/{id}/approve-cancel`

Approve an exception cancellation. **Requires**: REVIEWER or ADMIN.

**Request**:
```json
{
  "reason": "Approved: emergency situation"
}
```

**Response** `200 OK`: Updated Appointment with `status: "CANCELED"`.

**Errors**: `409` - Appointment is not in EXCEPTION status.

---

### PUT `/api/appointments/{id}/reschedule`

Reschedule an appointment to a new slot. Maximum 2 reschedules allowed.

**Request**:
```json
{
  "newSlotId": 5,
  "reason": "Patient schedule conflict"
}
```

**Response** `200 OK`: Updated Appointment with new slot and incremented `rescheduleCount`.

**Errors**:
- `409` - Maximum reschedules reached (2)
- `409` - New slot is fully booked
- `409` - Appointment is not in CREATED or CONFIRMED status
- `409` - New slot must be at least 2 hours in the future

---

### Appointment Lifecycle

```
CREATED ──(confirm)──> CONFIRMED ──(complete)──> COMPLETED
   |                      |
   |──(cancel)──> CANCELED / EXCEPTION
   |                      |
   |──(auto-release after 15 min)──> CANCELED
                          |
              EXCEPTION ──(approve-cancel)──> CANCELED
```

---

## 5. Financial (`/api/financial`)

All financial operations use idempotency keys. Audit logs are immutable.

### POST `/api/financial/transactions`

Record a transaction. **Requires**: FINANCE or ADMIN.

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

**Response** `200 OK`:
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

**Transaction types**: `SERVICE_FEE`, `DEPOSIT`, `PENALTY`, `ADJUSTMENT`

**Errors**:
- `409` - Duplicate idempotency key
- `404` - Appointment not found

---

### GET `/api/financial/transactions`

List transactions. **Requires**: FINANCE or ADMIN.

**Parameters** (all optional):
| Param | Type | Description |
|-------|------|-------------|
| `appointmentId` | long | Filter by appointment |
| `type` | string | Filter by transaction type |
| `status` | string | Filter by status |
| `dateFrom` | date | Start date |
| `dateTo` | date | End date |
| `page` | int | Page number (default: 0) |
| `size` | int | Page size (default: 20) |

**Response** `200 OK`: Paginated list of Transaction objects.

---

### GET `/api/financial/transactions/{id}`

Get transaction detail. **Requires**: FINANCE or ADMIN.

**Response** `200 OK`: Transaction object.

**Errors**: `404` - Transaction not found.

---

### POST `/api/financial/refunds`

Record a refund against an existing transaction. **Requires**: FINANCE or ADMIN.

**Request**:
```json
{
  "originalTransactionId": 1,
  "amount": 250.00,
  "reason": "Partial refund due to early cancellation",
  "idempotencyKey": "ref-uuid-789"
}
```

**Response** `200 OK`:
```json
{
  "id": 1,
  "originalTransactionId": 1,
  "amount": 250.00,
  "reason": "Partial refund due to early cancellation",
  "status": "RECORDED",
  "idempotencyKey": "ref-uuid-789",
  "createdBy": 2,
  "createdAt": "2026-04-10T22:00:00"
}
```

**Errors**:
- `409` - Refund amount exceeds original transaction amount
- `409` - Original transaction already fully refunded
- `404` - Original transaction not found

---

### GET `/api/financial/refunds`

List refunds. **Requires**: FINANCE or ADMIN.

**Parameters** (all optional):
| Param | Type | Description |
|-------|------|-------------|
| `originalTransactionId` | long | Filter by original transaction |
| `dateFrom` | date | Start date |
| `dateTo` | date | End date |
| `page` | int | Page number (default: 0) |
| `size` | int | Page size (default: 20) |

**Response** `200 OK`: Paginated list of Refund objects.

---

### POST `/api/financial/settlements`

Create a settlement record. **Requires**: FINANCE or ADMIN. May require secondary verification.

**Request**:
```json
{
  "dateFrom": "2026-04-01",
  "dateTo": "2026-04-10",
  "notes": "First 10 days of April settlement",
  "verificationToken": "abc123...",
  "idempotencyKey": "stl-uuid-012"
}
```

**Response** `200 OK`:
```json
{
  "id": 1,
  "dateFrom": "2026-04-01",
  "dateTo": "2026-04-10",
  "totalTransactions": 45,
  "totalAmount": 22500.00,
  "totalRefunds": 1500.00,
  "netAmount": 21000.00,
  "currency": "CNY",
  "status": "COMPLETED",
  "notes": "First 10 days of April settlement",
  "createdBy": 2,
  "createdAt": "2026-04-10T22:00:00"
}
```

---

### GET `/api/financial/settlements`

List settlements. **Requires**: FINANCE or ADMIN.

**Response** `200 OK`: Paginated list of Settlement objects.

---

### GET `/api/financial/reports/daily`

Get daily financial summary. **Requires**: FINANCE or ADMIN.

**Parameters**:
| Param | Type | Required | Description |
|-------|------|----------|-------------|
| `date` | date (YYYY-MM-DD) | yes | Report date |

**Response** `200 OK`:
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

### GET `/api/financial/reports/daily/export`

Export daily financial report. **Requires**: FINANCE or ADMIN.

**Parameters**:
| Param | Type | Required | Description |
|-------|------|----------|-------------|
| `date` | date (YYYY-MM-DD) | yes | Report date |
| `format` | string | no | Export format: `CSV` or `EXCEL` (default: CSV) |

**Response** `200 OK`: File download with appropriate Content-Type and Content-Disposition headers.

---

## 6. Files (`/api/files`)

### POST `/api/files/upload`

Upload a file with metadata tracking.

**Request**: `multipart/form-data`:
| Part | Type | Required | Description |
|------|------|----------|-------------|
| `file` | file | yes | The file to upload |
| `module` | string | yes | Module reference (PROPERTY, APPOINTMENT, FINANCIAL) |
| `referenceId` | long | no | Associated entity ID |
| `description` | string | no | File description |

**Response** `200 OK`:
```json
{
  "id": 1,
  "fileName": "patient-record.pdf",
  "filePath": "files/appointment/1/patient-record.pdf",
  "contentType": "application/pdf",
  "fileSize": 102400,
  "checksum": "e3b0c44298fc...",
  "module": "APPOINTMENT",
  "referenceId": 1,
  "description": "Patient medical history",
  "version": 1,
  "uploadedBy": 3,
  "uploadedAt": "2026-04-10T22:00:00"
}
```

**Note**: Files are deduplicated using SHA-256 hash. If an identical file already exists, the existing record is returned.

**Errors**:
- `400` - File is empty
- `409` - File size exceeds limit

---

### GET `/api/files`

List files with filters.

**Parameters** (all optional):
| Param | Type | Description |
|-------|------|-------------|
| `module` | string | Filter by module |
| `referenceId` | long | Filter by associated entity |
| `fileName` | string | Search file name |
| `page` | int | Page number (default: 0) |
| `size` | int | Page size (default: 20) |

**Response** `200 OK`: Paginated list of File metadata objects.

---

### GET `/api/files/{id}`

Get file metadata.

**Response** `200 OK`: File metadata object.

**Errors**: `404` - File not found.

---

### GET `/api/files/{id}/download`

Download a file.

**Response** `200 OK`: File bytes with appropriate `Content-Type` and `Content-Disposition` headers.

SHA-256 checksum is verified on retrieval.

**Errors**:
- `404` - File not found
- `403` - Access denied (role/ownership check)
- `409` - File integrity check failed

---

### GET `/api/files/{id}/preview`

Get a preview of the file (for supported types: images, PDFs).

**Response** `200 OK`: Preview content with appropriate `Content-Type`.

**Errors**: `409` - Preview not supported for this file type.

---

### GET `/api/files/{id}/versions`

List all versions of a file.

**Response** `200 OK`:
```json
[
  {
    "id": 3,
    "version": 2,
    "fileName": "patient-record-v2.pdf",
    "fileSize": 115200,
    "checksum": "a1b2c3d4...",
    "uploadedBy": 3,
    "uploadedAt": "2026-04-12T10:00:00"
  },
  {
    "id": 1,
    "version": 1,
    "fileName": "patient-record.pdf",
    "fileSize": 102400,
    "checksum": "e3b0c44298fc...",
    "uploadedBy": 3,
    "uploadedAt": "2026-04-10T22:00:00"
  }
]
```

---

### POST `/api/files/{id}/versions`

Upload a new version of an existing file.

**Request**: `multipart/form-data` with `file` part.

**Response** `200 OK`: New File metadata object with incremented `version`.

---

### DELETE `/api/files/{id}`

Delete a file (moves to recycle bin). **Requires**: File owner, ADMIN, or DISPATCHER.

**Response** `200 OK`: Empty body.

**Note**: File is retained in recycle bin for 30 days before permanent deletion.

---

### GET `/api/files/recycle-bin`

List files in recycle bin. **Requires**: ADMIN.

**Response** `200 OK`:
```json
[
  {
    "id": 1,
    "fileName": "old-document.pdf",
    "deletedBy": 3,
    "deletedAt": "2026-04-10T22:00:00",
    "expiresAt": "2026-05-10T22:00:00"
  }
]
```

---

### PUT `/api/files/recycle-bin/{id}/restore`

Restore a file from recycle bin. **Requires**: ADMIN.

**Response** `200 OK`: Restored File metadata object.

**Errors**: `409` - File has been permanently deleted (past 30-day retention).

---

## 7. Reviewer (`/api/reviewer`)

All endpoints require **REVIEWER** or **ADMIN** role.

### GET `/api/reviewer/queue`

Get appointments and operations pending review.

**Response** `200 OK`:
```json
{
  "pendingAppointments": [
    {
      "id": 1,
      "patientName": "Li ***",
      "serviceType": "GENERAL_CONSULTATION",
      "status": "CREATED",
      "createdAt": "2026-04-10T22:00:00"
    }
  ],
  "exceptionCancellations": [
    {
      "id": 5,
      "patientName": "Wang ***",
      "status": "EXCEPTION",
      "cancelReason": "Emergency",
      "createdAt": "2026-04-10T22:00:00"
    }
  ]
}
```

---

### GET `/api/reviewer/dashboard`

Get reviewer statistics.

**Response** `200 OK`:
```json
{
  "pendingCount": 5,
  "exceptionCount": 2,
  "confirmedToday": 12,
  "completedToday": 8,
  "canceledToday": 1
}
```

---

## 8. Audit (`/api/audit`)

### GET `/api/audit/logs`

List audit logs. **Requires**: ADMIN.

**Parameters** (all optional):
| Param | Type | Description |
|-------|------|-------------|
| `userId` | long | Filter by user |
| `module` | string | Filter by module (AUTH, PROPERTY, APPOINTMENT, FINANCIAL, FILE) |
| `operation` | string | Filter by operation type |
| `dateFrom` | datetime | Start datetime |
| `dateTo` | datetime | End datetime |
| `page` | int | Page number (default: 0) |
| `size` | int | Page size (default: 50) |

**Response** `200 OK`:
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

### GET `/api/audit/logs/{id}`

Get audit log detail. **Requires**: ADMIN.

**Response** `200 OK`: Audit log object with full detail including before/after state for data changes.

---

## 9. Admin Configuration (`/api/admin/config`)

All endpoints require **ADMIN** role.

### GET `/api/admin/config`

Get current system configuration.

**Response** `200 OK`:
```json
{
  "appointmentSlotDurations": [15, 30, 60, 90],
  "defaultSlotDuration": 30,
  "workingHoursStart": "08:00",
  "workingHoursEnd": "17:00",
  "maxAdvanceBookingDays": 14,
  "minAdvanceBookingHours": 2,
  "autoReleaseMinutes": 15,
  "maxReschedules": 2,
  "recycleBinRetentionDays": 30,
  "maxFileSize": 10485760,
  "passwordMinLength": 8,
  "maxLoginAttempts": 5,
  "lockoutDurationMinutes": 15
}
```

---

### PUT `/api/admin/config`

Update system configuration. **Requires**: ADMIN with secondary verification.

**Request**:
```json
{
  "defaultSlotDuration": 60,
  "workingHoursStart": "09:00",
  "workingHoursEnd": "18:00",
  "verificationToken": "abc123..."
}
```

**Response** `200 OK`: Updated configuration object.

---

### GET `/api/admin/dashboard`

Get system overview statistics.

**Response** `200 OK`:
```json
{
  "totalUsers": 25,
  "activeUsers": 22,
  "totalProperties": 15,
  "activeProperties": 12,
  "appointmentsToday": 18,
  "appointmentsThisWeek": 87,
  "completionRate": 0.92,
  "totalTransactionsThisMonth": 245,
  "revenueThisMonth": 122500.00,
  "currency": "CNY"
}
```
