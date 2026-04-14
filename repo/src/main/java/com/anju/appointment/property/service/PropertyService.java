package com.anju.appointment.property.service;

import com.anju.appointment.appointment.entity.AppointmentStatus;
import com.anju.appointment.appointment.repository.AppointmentRepository;
import com.anju.appointment.audit.service.AuditService;
import com.anju.appointment.common.BusinessRuleException;
import com.anju.appointment.common.ResourceNotFoundException;
import com.anju.appointment.property.dto.PropertyRequest;
import com.anju.appointment.property.dto.PropertyResponse;
import com.anju.appointment.property.entity.ComplianceStatus;
import com.anju.appointment.property.entity.Property;
import com.anju.appointment.property.entity.PropertyStatus;
import com.anju.appointment.property.repository.PropertyRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PropertyService {

    private final PropertyRepository propertyRepository;
    private final AppointmentRepository appointmentRepository;
    private final AuditService auditService;

    public PropertyService(PropertyRepository propertyRepository,
                           AppointmentRepository appointmentRepository,
                           AuditService auditService) {
        this.propertyRepository = propertyRepository;
        this.appointmentRepository = appointmentRepository;
        this.auditService = auditService;
    }

    @Transactional
    public PropertyResponse createProperty(PropertyRequest request, Long userId) {
        validateForCreate(request);
        Property property = new Property();
        property.setName(request.getName());
        property.setType(request.getType());
        property.setAddress(request.getAddress());
        property.setDescription(request.getDescription());
        property.setCapacity(request.getCapacity());
        property.setStatus(PropertyStatus.ACTIVE);
        applyExtendedFields(property, request);

        property = propertyRepository.save(property);
        auditService.log(userId, null, "PROPERTY", "CREATE",
                "Property", property.getId(), "Created property: " + property.getName(), null);
        return PropertyResponse.fromEntity(property);
    }

    public Page<PropertyResponse> listProperties(String type, String statusFilter, String keyword, Pageable pageable) {
        PropertyStatus status = null;
        if (statusFilter != null && !statusFilter.isBlank()) {
            try {
                status = PropertyStatus.valueOf(statusFilter);
            } catch (IllegalArgumentException e) {
                throw new BusinessRuleException("Invalid status: " + statusFilter);
            }
        }

        return propertyRepository.findByFilters(type, status, keyword, pageable)
                .map(PropertyResponse::fromEntity);
    }

    public PropertyResponse getProperty(Long id) {
        Property property = findPropertyOrThrow(id);
        return PropertyResponse.fromEntity(property);
    }

    @Transactional
    public PropertyResponse updateProperty(Long id, PropertyRequest request, Long userId) {
        Property property = findPropertyOrThrow(id);

        if (request.getName() != null) {
            property.setName(request.getName());
        }
        if (request.getType() != null) {
            property.setType(request.getType());
        }
        if (request.getAddress() != null) {
            property.setAddress(request.getAddress());
        }
        if (request.getDescription() != null) {
            property.setDescription(request.getDescription());
        }
        if (request.getCapacity() != null) {
            property.setCapacity(request.getCapacity());
        }
        applyExtendedFields(property, request);
        validatePropertyRules(property);

        property = propertyRepository.save(property);
        auditService.log(userId, null, "PROPERTY", "UPDATE",
                "Property", property.getId(), "Updated property: " + property.getName(), null);
        return PropertyResponse.fromEntity(property);
    }

    @Transactional
    public void deleteProperty(Long id, Long userId) {
        Property property = findPropertyOrThrow(id);

        boolean hasActiveAppointments = appointmentRepository.existsByPropertyIdAndStatusIn(
                id, List.of(AppointmentStatus.CREATED, AppointmentStatus.CONFIRMED));
        if (hasActiveAppointments) {
            throw new BusinessRuleException("Property has active appointments and cannot be deleted");
        }

        property.setStatus(PropertyStatus.INACTIVE);
        propertyRepository.save(property);
        auditService.log(userId, null, "PROPERTY", "DELETE",
                "Property", property.getId(), "Soft-deleted property: " + property.getName(), null);
    }

    public void validateCompliance(Property property) {
        if (property.getStatus() != PropertyStatus.ACTIVE) {
            throw new BusinessRuleException("Property is inactive and cannot be used for bookings");
        }
        if (property.getComplianceStatus() == ComplianceStatus.NON_COMPLIANT) {
            throw new BusinessRuleException("Property is non-compliant and cannot be used for bookings");
        }
        if (property.getComplianceStatus() == ComplianceStatus.EXPIRED) {
            throw new BusinessRuleException("Property compliance has expired and must be renewed");
        }
        if (property.getComplianceExpiresAt() != null
                && property.getComplianceExpiresAt().isBefore(java.time.LocalDate.now())) {
            property.setComplianceStatus(ComplianceStatus.EXPIRED);
            propertyRepository.save(property);
            throw new BusinessRuleException("Property compliance has expired and must be renewed");
        }
    }

    private void applyExtendedFields(Property property, PropertyRequest request) {
        if (request.getComplianceStatus() != null) {
            try {
                property.setComplianceStatus(ComplianceStatus.valueOf(request.getComplianceStatus()));
            } catch (IllegalArgumentException e) {
                throw new BusinessRuleException("Invalid compliance status: " + request.getComplianceStatus());
            }
        }
        if (request.getComplianceNotes() != null) {
            property.setComplianceNotes(request.getComplianceNotes());
        }
        if (request.getComplianceExpiresAt() != null) {
            property.setComplianceExpiresAt(request.getComplianceExpiresAt());
        }
        if (request.getRentalPricePerSlot() != null) {
            property.setRentalPricePerSlot(request.getRentalPricePerSlot());
        }
        if (request.getDepositAmount() != null) {
            property.setDepositAmount(request.getDepositAmount());
        }
        if (request.getMinBookingLeadHours() != null) {
            property.setMinBookingLeadHours(request.getMinBookingLeadHours());
        }
        if (request.getMaxBookingLeadDays() != null) {
            property.setMaxBookingLeadDays(request.getMaxBookingLeadDays());
        }
        if (request.getRentalRules() != null) {
            property.setRentalRules(request.getRentalRules());
        }
        validatePropertyRules(property);
    }

    private void validateForCreate(PropertyRequest request) {
        if (request.getName() == null || request.getName().isBlank()) {
            throw new BusinessRuleException("Name must not be blank");
        }
        if (request.getType() == null || request.getType().isBlank()) {
            throw new BusinessRuleException("Type must not be blank");
        }
        if (request.getCapacity() == null) {
            throw new BusinessRuleException("Capacity must not be null");
        }
    }

    private void validatePropertyRules(Property property) {
        if (property.getDepositAmount() != null
                && property.getRentalPricePerSlot() != null
                && property.getDepositAmount().compareTo(property.getRentalPricePerSlot()) < 0) {
            throw new BusinessRuleException("Deposit amount must be greater than or equal to rental price per slot");
        }

        if (property.getMaxBookingLeadDays() != null
                && property.getMinBookingLeadHours() != null
                && property.getMaxBookingLeadDays() * 24 < property.getMinBookingLeadHours()) {
            throw new BusinessRuleException("Maximum booking lead days must allow the configured minimum booking lead hours");
        }

        if (property.getComplianceExpiresAt() != null
                && property.getComplianceStatus() == ComplianceStatus.COMPLIANT
                && property.getComplianceExpiresAt().isBefore(java.time.LocalDate.now())) {
            throw new BusinessRuleException("Compliant properties must have a non-expired compliance date");
        }
    }

    public Property findPropertyOrThrow(Long id) {
        return propertyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Property not found with id: " + id));
    }
}
