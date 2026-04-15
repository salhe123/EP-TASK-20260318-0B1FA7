package com.anju.appointment.appointment.controller;

import com.anju.appointment.appointment.dto.AppointmentResponse;
import com.anju.appointment.appointment.dto.BookAppointmentRequest;
import com.anju.appointment.appointment.dto.CancelRequest;
import com.anju.appointment.appointment.dto.CompleteRequest;
import com.anju.appointment.appointment.dto.RescheduleRequest;
import com.anju.appointment.appointment.dto.SlotGenerateRequest;
import com.anju.appointment.appointment.dto.SlotGenerateResponse;
import com.anju.appointment.appointment.dto.SlotResponse;
import com.anju.appointment.appointment.service.AppointmentService;
import com.anju.appointment.auth.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {

    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @PostMapping("/slots/generate")
    @PreAuthorize("hasAnyRole('ADMIN', 'DISPATCHER')")
    public ResponseEntity<SlotGenerateResponse> generateSlots(@Valid @RequestBody SlotGenerateRequest request) {
        return ResponseEntity.ok(appointmentService.generateSlots(request));
    }

    @GetMapping("/slots")
    public ResponseEntity<List<SlotResponse>> listSlots(
            @RequestParam Long propertyId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(appointmentService.listSlots(propertyId, date));
    }

    @PostMapping
    public ResponseEntity<AppointmentResponse> bookAppointment(
            @Valid @RequestBody BookAppointmentRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(appointmentService.bookAppointment(request, principal.getUserId()));
    }

    @GetMapping
    public ResponseEntity<Page<AppointmentResponse>> listAppointments(
            @RequestParam(required = false) Long propertyId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(required = false) String patientName,
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(appointmentService.listAppointments(
                propertyId, status, dateFrom, dateTo, patientName,
                principal.getUserId(), principal.getRole(), pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AppointmentResponse> getAppointment(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(appointmentService.getAppointment(id, principal.getUserId(), principal.getRole()));
    }

    @PutMapping("/{id}/confirm")
    @PreAuthorize("hasAnyRole('DISPATCHER', 'REVIEWER', 'ADMIN')")
    public ResponseEntity<AppointmentResponse> confirmAppointment(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(appointmentService.confirmAppointment(id, principal.getUserId()));
    }

    @PutMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('DISPATCHER', 'REVIEWER', 'ADMIN', 'SERVICE_STAFF')")
    public ResponseEntity<AppointmentResponse> completeAppointment(
            @PathVariable Long id,
            @RequestBody(required = false) CompleteRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        String notes = request != null ? request.getCompletionNotes() : null;
        return ResponseEntity.ok(appointmentService.completeAppointment(
                id, notes, principal.getUserId(), principal.getRole()));
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<AppointmentResponse> cancelAppointment(
            @PathVariable Long id,
            @Valid @RequestBody CancelRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(appointmentService.cancelAppointment(
                id, request.getReason(), principal.getUserId(), principal.getRole()));
    }

    @PutMapping("/{id}/approve-cancel")
    @PreAuthorize("hasAnyRole('REVIEWER', 'ADMIN')")
    public ResponseEntity<AppointmentResponse> approveCancellation(
            @PathVariable Long id,
            @RequestBody(required = false) CancelRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        String reason = request != null ? request.getReason() : null;
        return ResponseEntity.ok(appointmentService.approveCancellation(id, reason, principal.getUserId()));
    }

    @PutMapping("/{id}/reschedule")
    public ResponseEntity<AppointmentResponse> rescheduleAppointment(
            @PathVariable Long id,
            @Valid @RequestBody RescheduleRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(appointmentService.rescheduleAppointment(
                id, request.getNewSlotId(), request.getReason(),
                principal.getUserId(), principal.getRole()));
    }

    @PutMapping("/{id}/assign")
    @PreAuthorize("hasAnyRole('DISPATCHER', 'ADMIN')")
    public ResponseEntity<AppointmentResponse> assignServiceStaff(
            @PathVariable Long id,
            @RequestParam Long serviceStaffId,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(appointmentService.assignServiceStaff(id, serviceStaffId, principal.getUserId()));
    }
}
