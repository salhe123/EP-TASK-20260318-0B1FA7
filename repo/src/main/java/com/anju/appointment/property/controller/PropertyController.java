package com.anju.appointment.property.controller;

import com.anju.appointment.auth.security.AuthenticatedUser;
import com.anju.appointment.property.dto.PropertyRequest;
import com.anju.appointment.property.dto.PropertyResponse;
import com.anju.appointment.property.service.PropertyService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/properties")
public class PropertyController {

    private final PropertyService propertyService;

    public PropertyController(PropertyService propertyService) {
        this.propertyService = propertyService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'DISPATCHER')")
    public ResponseEntity<PropertyResponse> createProperty(@Valid @RequestBody PropertyRequest request,
                                                            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(propertyService.createProperty(request, principal.getUserId()));
    }

    @GetMapping
    public ResponseEntity<Page<PropertyResponse>> listProperties(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(propertyService.listProperties(type, status, keyword, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PropertyResponse> getProperty(@PathVariable Long id) {
        return ResponseEntity.ok(propertyService.getProperty(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DISPATCHER')")
    public ResponseEntity<PropertyResponse> updateProperty(@PathVariable Long id,
                                                            @Valid @RequestBody PropertyRequest request,
                                                            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(propertyService.updateProperty(id, request, principal.getUserId()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteProperty(@PathVariable Long id,
                                                @AuthenticationPrincipal AuthenticatedUser principal) {
        propertyService.deleteProperty(id, principal.getUserId());
        return ResponseEntity.ok().build();
    }
}
