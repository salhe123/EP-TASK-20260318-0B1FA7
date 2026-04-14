## Business Logic Questions Log

### 1. How is "offline system" defined?
- **Problem:** Not clear if external services are allowed.
- **My Understanding:** System must run fully locally without internet.
- **Solution:** Use only local services (Spring Boot, MySQL, local storage), no external APIs.

---

### 2. How are appointment time slots managed?
- **Problem:** Slot generation and rules are not clearly defined.
- **My Understanding:** Slots follow fixed durations (15/30/60/90 min).
- **Solution:** Generate slots based on configurable working hours and durations.

---

### 3. How is double booking prevented?
- **Problem:** Conflict detection logic is not explicitly described.
- **My Understanding:** Same staff/resource cannot have overlapping appointments.
- **Solution:** Check time overlap in DB before creating appointment.

---

### 4. How does the appointment lifecycle work?
- **Problem:** State transitions are not fully defined.
- **My Understanding:** Appointments follow a controlled state flow.
- **Solution:** Enforce states (created → confirmed → completed/canceled) with validation rules.

---

### 5. What happens if an appointment is not confirmed?
- **Problem:** Auto-release behavior unclear.
- **My Understanding:** System should cancel it automatically.
- **Solution:** Use scheduled job to release unconfirmed appointments after 15 minutes.

---

### 6. How are reschedule and cancellation rules applied?
- **Problem:** Limits and penalties are partially defined.
- **My Understanding:** Max 2 reschedules, cancellation has penalty rules.
- **Solution:** Track reschedule count and apply penalty logic during cancellation.

---

### 7. What is the scope of the financial module?
- **Problem:** Not clear if real payment integration is required.
- **My Understanding:** Only internal bookkeeping is needed.
- **Solution:** Store transactions, refunds, and settlements locally.

---

### 8. How complex should file handling be?
- **Problem:** Chunk upload and deduplication increase complexity.
- **My Understanding:** Full implementation may not be necessary initially.
- **Solution:** Start with simple file upload and metadata storage.

---

### 9. How is security and access control handled?
- **Problem:** Role permissions and sensitive data handling not fully detailed.
- **My Understanding:** Requires RBAC and data protection.
- **Solution:** Implement role-based access and encrypt/mask sensitive data.