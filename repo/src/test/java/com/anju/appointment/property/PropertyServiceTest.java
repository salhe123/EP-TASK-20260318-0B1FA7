package com.anju.appointment.property;

import com.anju.appointment.appointment.entity.AppointmentStatus;
import com.anju.appointment.appointment.repository.AppointmentRepository;
import com.anju.appointment.audit.service.AuditService;
import com.anju.appointment.common.BusinessRuleException;
import com.anju.appointment.common.ResourceNotFoundException;
import com.anju.appointment.property.dto.PropertyRequest;
import com.anju.appointment.property.dto.PropertyResponse;
import com.anju.appointment.property.dto.PropertyUpdateRequest;
import com.anju.appointment.property.entity.ComplianceStatus;
import com.anju.appointment.property.entity.Property;
import com.anju.appointment.property.entity.PropertyStatus;
import com.anju.appointment.property.repository.PropertyRepository;
import com.anju.appointment.property.service.PropertyService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PropertyServiceTest {

    @Mock
    private PropertyRepository propertyRepository;

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private PropertyService propertyService;

    @Captor
    private ArgumentCaptor<Property> propertyCaptor;

    private static final Long USER_ID = 42L;
    private static final Long PROPERTY_ID = 1L;

    private Property buildProperty() {
        Property property = new Property();
        property.setId(PROPERTY_ID);
        property.setName("Test Property");
        property.setType("OFFICE");
        property.setAddress("123 Main St");
        property.setDescription("A test property");
        property.setCapacity(10);
        property.setStatus(PropertyStatus.ACTIVE);
        return property;
    }

    private PropertyRequest buildFullRequest() {
        PropertyRequest request = new PropertyRequest();
        request.setName("New Property");
        request.setType("OFFICE");
        request.setAddress("456 Oak Ave");
        request.setDescription("A new property");
        request.setCapacity(20);
        request.setComplianceStatus("COMPLIANT");
        request.setComplianceNotes("All inspections passed");
        request.setComplianceExpiresAt(LocalDate.of(2027, 12, 31));
        request.setRentalPricePerSlot(new BigDecimal("150.00"));
        request.setDepositAmount(new BigDecimal("500.00"));
        request.setMinBookingLeadHours(24);
        request.setMaxBookingLeadDays(90);
        request.setRentalRules("No smoking; no pets");
        return request;
    }

    // -----------------------------------------------------------------------
    // createProperty
    // -----------------------------------------------------------------------

    @Nested
    class CreateProperty {

        @Test
        void createProperty_successWithAllFields_returnsResponseAndLogsAudit() {
            PropertyRequest request = buildFullRequest();

            when(propertyRepository.save(any(Property.class))).thenAnswer(invocation -> {
                Property saved = invocation.getArgument(0);
                saved.setId(PROPERTY_ID);
                return saved;
            });

            PropertyResponse response = propertyService.createProperty(request, USER_ID);

            assertNotNull(response);
            assertEquals(PROPERTY_ID, response.getId());
            assertEquals("New Property", response.getName());
            assertEquals("OFFICE", response.getType());
            assertEquals("456 Oak Ave", response.getAddress());
            assertEquals("A new property", response.getDescription());
            assertEquals(20, response.getCapacity());
            assertEquals("ACTIVE", response.getStatus());
            assertEquals("COMPLIANT", response.getComplianceStatus());
            assertEquals("All inspections passed", response.getComplianceNotes());
            assertEquals(LocalDate.of(2027, 12, 31), response.getComplianceExpiresAt());
            assertEquals(new BigDecimal("150.00"), response.getRentalPricePerSlot());
            assertEquals(new BigDecimal("500.00"), response.getDepositAmount());
            assertEquals(24, response.getMinBookingLeadHours());
            assertEquals(90, response.getMaxBookingLeadDays());
            assertEquals("No smoking; no pets", response.getRentalRules());

            verify(propertyRepository).save(propertyCaptor.capture());
            Property captured = propertyCaptor.getValue();
            assertEquals(PropertyStatus.ACTIVE, captured.getStatus());
            assertEquals(ComplianceStatus.COMPLIANT, captured.getComplianceStatus());

            verify(auditService).log(eq(USER_ID), isNull(), eq("PROPERTY"), eq("CREATE"),
                    eq("Property"), eq(PROPERTY_ID), anyString(), isNull());
        }
    }

    // -----------------------------------------------------------------------
    // listProperties
    // -----------------------------------------------------------------------

    @Nested
    class ListProperties {

        @Test
        void listProperties_withFilters_returnsMappedPage() {
            Pageable pageable = PageRequest.of(0, 10);
            Property property = buildProperty();
            Page<Property> page = new PageImpl<>(List.of(property), pageable, 1);

            when(propertyRepository.findByFilters(eq("OFFICE"), eq(PropertyStatus.ACTIVE),
                    eq("Main"), eq(pageable))).thenReturn(page);

            Page<PropertyResponse> result = propertyService.listProperties("OFFICE", "ACTIVE", "Main", pageable);

            assertEquals(1, result.getTotalElements());
            assertEquals("Test Property", result.getContent().get(0).getName());
            verify(propertyRepository).findByFilters("OFFICE", PropertyStatus.ACTIVE, "Main", pageable);
        }

        @Test
        void listProperties_nullStatus_passesNullToRepository() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<Property> page = new PageImpl<>(List.of(), pageable, 0);

            when(propertyRepository.findByFilters(isNull(), isNull(), isNull(), eq(pageable)))
                    .thenReturn(page);

            Page<PropertyResponse> result = propertyService.listProperties(null, null, null, pageable);

            assertEquals(0, result.getTotalElements());
            verify(propertyRepository).findByFilters(null, null, null, pageable);
        }

        @Test
        void listProperties_blankStatus_passesNullToRepository() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<Property> page = new PageImpl<>(List.of(), pageable, 0);

            when(propertyRepository.findByFilters(isNull(), isNull(), isNull(), eq(pageable)))
                    .thenReturn(page);

            Page<PropertyResponse> result = propertyService.listProperties(null, "  ", null, pageable);

            assertEquals(0, result.getTotalElements());
            verify(propertyRepository).findByFilters(null, null, null, pageable);
        }

        @Test
        void listProperties_invalidStatus_throwsBusinessRuleException() {
            Pageable pageable = PageRequest.of(0, 10);

            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> propertyService.listProperties(null, "BOGUS", null, pageable));

            assertEquals("Invalid status: BOGUS", ex.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // getProperty
    // -----------------------------------------------------------------------

    @Nested
    class GetProperty {

        @Test
        void getProperty_found_returnsResponse() {
            Property property = buildProperty();
            when(propertyRepository.findById(PROPERTY_ID)).thenReturn(Optional.of(property));

            PropertyResponse response = propertyService.getProperty(PROPERTY_ID);

            assertNotNull(response);
            assertEquals(PROPERTY_ID, response.getId());
            assertEquals("Test Property", response.getName());
        }

        @Test
        void getProperty_notFound_throwsResourceNotFoundException() {
            when(propertyRepository.findById(PROPERTY_ID)).thenReturn(Optional.empty());

            ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                    () -> propertyService.getProperty(PROPERTY_ID));

            assertEquals("Property not found with id: " + PROPERTY_ID, ex.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // updateProperty
    // -----------------------------------------------------------------------

    @Nested
    class UpdateProperty {

        @Test
        void updateProperty_partialUpdate_onlyUpdatesProvidedFields() {
            Property existing = buildProperty();
            existing.setComplianceStatus(ComplianceStatus.PENDING_REVIEW);
            when(propertyRepository.findById(PROPERTY_ID)).thenReturn(Optional.of(existing));
            when(propertyRepository.save(any(Property.class))).thenAnswer(inv -> inv.getArgument(0));

            PropertyUpdateRequest request = new PropertyUpdateRequest();
            request.setName("Updated Name");
            // type, address, description, capacity left null => not updated

            PropertyResponse response = propertyService.updateProperty(PROPERTY_ID, request, USER_ID);

            assertEquals("Updated Name", response.getName());
            // Original values remain unchanged
            assertEquals("OFFICE", response.getType());
            assertEquals("123 Main St", response.getAddress());
            assertEquals("A test property", response.getDescription());
            assertEquals(10, response.getCapacity());

            verify(propertyRepository).save(propertyCaptor.capture());
            Property saved = propertyCaptor.getValue();
            assertEquals("Updated Name", saved.getName());
            assertEquals("OFFICE", saved.getType());

            verify(auditService).log(eq(USER_ID), isNull(), eq("PROPERTY"), eq("UPDATE"),
                    eq("Property"), eq(PROPERTY_ID), anyString(), isNull());
        }

        @Test
        void updateProperty_allFields_updatesEverything() {
            Property existing = buildProperty();
            when(propertyRepository.findById(PROPERTY_ID)).thenReturn(Optional.of(existing));
            when(propertyRepository.save(any(Property.class))).thenAnswer(inv -> inv.getArgument(0));

            PropertyUpdateRequest request = new PropertyUpdateRequest();
            request.setName("Fully Updated");
            request.setType("OFFICE");
            request.setAddress("456 Oak Ave");
            request.setDescription("A new property");
            request.setCapacity(20);
            request.setComplianceStatus("COMPLIANT");
            request.setComplianceNotes("Passed audit");
            request.setRentalPricePerSlot(new BigDecimal("150.00"));
            request.setDepositAmount(new BigDecimal("50.00"));
            request.setMinBookingLeadHours(2);
            request.setMaxBookingLeadDays(14);
            request.setRentalRules("No smoking; no pets");

            PropertyResponse response = propertyService.updateProperty(PROPERTY_ID, request, USER_ID);

            assertEquals("Fully Updated", response.getName());
            assertEquals("OFFICE", response.getType());
            assertEquals("456 Oak Ave", response.getAddress());
            assertEquals("A new property", response.getDescription());
            assertEquals(20, response.getCapacity());
            assertEquals("COMPLIANT", response.getComplianceStatus());
            assertEquals(new BigDecimal("150.00"), response.getRentalPricePerSlot());

            verify(auditService).log(eq(USER_ID), isNull(), eq("PROPERTY"), eq("UPDATE"),
                    eq("Property"), eq(PROPERTY_ID), anyString(), isNull());
        }

        @Test
        void updateProperty_notFound_throwsResourceNotFoundException() {
            when(propertyRepository.findById(PROPERTY_ID)).thenReturn(Optional.empty());

            PropertyUpdateRequest request = new PropertyUpdateRequest();
            request.setName("Irrelevant");

            assertThrows(ResourceNotFoundException.class,
                    () -> propertyService.updateProperty(PROPERTY_ID, request, USER_ID));
        }
    }

    // -----------------------------------------------------------------------
    // deleteProperty
    // -----------------------------------------------------------------------

    @Nested
    class DeleteProperty {

        @Test
        void deleteProperty_success_softDeletesToInactiveAndLogsAudit() {
            Property property = buildProperty();
            when(propertyRepository.findById(PROPERTY_ID)).thenReturn(Optional.of(property));
            when(appointmentRepository.existsByPropertyIdAndStatusIn(eq(PROPERTY_ID),
                    eq(List.of(AppointmentStatus.CREATED, AppointmentStatus.CONFIRMED))))
                    .thenReturn(false);
            when(propertyRepository.save(any(Property.class))).thenAnswer(inv -> inv.getArgument(0));

            propertyService.deleteProperty(PROPERTY_ID, USER_ID);

            verify(propertyRepository).save(propertyCaptor.capture());
            assertEquals(PropertyStatus.INACTIVE, propertyCaptor.getValue().getStatus());

            verify(auditService).log(eq(USER_ID), isNull(), eq("PROPERTY"), eq("DELETE"),
                    eq("Property"), eq(PROPERTY_ID), anyString(), isNull());
        }

        @Test
        void deleteProperty_hasActiveAppointments_throwsBusinessRuleException() {
            Property property = buildProperty();
            when(propertyRepository.findById(PROPERTY_ID)).thenReturn(Optional.of(property));
            when(appointmentRepository.existsByPropertyIdAndStatusIn(eq(PROPERTY_ID),
                    eq(List.of(AppointmentStatus.CREATED, AppointmentStatus.CONFIRMED))))
                    .thenReturn(true);

            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> propertyService.deleteProperty(PROPERTY_ID, USER_ID));

            assertEquals("Property has active appointments and cannot be deleted", ex.getMessage());
            verify(propertyRepository, never()).save(any());
            verify(auditService, never()).log(anyLong(), any(), anyString(), anyString(),
                    anyString(), anyLong(), anyString(), any());
        }

        @Test
        void deleteProperty_notFound_throwsResourceNotFoundException() {
            when(propertyRepository.findById(PROPERTY_ID)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class,
                    () -> propertyService.deleteProperty(PROPERTY_ID, USER_ID));
        }
    }

    // -----------------------------------------------------------------------
    // validateBookingEligibility
    // -----------------------------------------------------------------------

    @Nested
    class ValidateBookingEligibility {

        @Test
        void compliantActiveProperty_passes() {
            Property property = buildProperty();
            property.setComplianceStatus(ComplianceStatus.COMPLIANT);
            property.setComplianceExpiresAt(LocalDate.now().plusYears(1));

            propertyService.validateBookingEligibility(property);

            verify(propertyRepository, never()).save(any());
        }

        @Test
        void inactiveProperty_throwsBusinessRuleException() {
            Property property = buildProperty();
            property.setStatus(PropertyStatus.INACTIVE);
            property.setComplianceStatus(ComplianceStatus.COMPLIANT);

            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> propertyService.validateBookingEligibility(property));
            assertTrue(ex.getMessage().contains("not active"));
        }

        @Test
        void maintenanceProperty_throwsBusinessRuleException() {
            Property property = buildProperty();
            property.setStatus(PropertyStatus.MAINTENANCE);
            property.setComplianceStatus(ComplianceStatus.COMPLIANT);

            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> propertyService.validateBookingEligibility(property));
            assertTrue(ex.getMessage().contains("not active"));
        }

        @Test
        void pendingReview_throwsBusinessRuleException() {
            Property property = buildProperty();
            property.setComplianceStatus(ComplianceStatus.PENDING_REVIEW);

            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> propertyService.validateBookingEligibility(property));
            assertTrue(ex.getMessage().contains("PENDING_REVIEW"));
        }

        @Test
        void nullComplianceStatus_throwsBusinessRuleException() {
            Property property = buildProperty();
            property.setComplianceStatus(null);

            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> propertyService.validateBookingEligibility(property));
            assertTrue(ex.getMessage().contains("unset"));
        }

        @Test
        void nonCompliant_throwsBusinessRuleException() {
            Property property = buildProperty();
            property.setComplianceStatus(ComplianceStatus.NON_COMPLIANT);

            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> propertyService.validateBookingEligibility(property));
            assertTrue(ex.getMessage().contains("NON_COMPLIANT"));
        }

        @Test
        void expired_throwsBusinessRuleException() {
            Property property = buildProperty();
            property.setComplianceStatus(ComplianceStatus.EXPIRED);

            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> propertyService.validateBookingEligibility(property));
            assertTrue(ex.getMessage().contains("EXPIRED"));
        }

        @Test
        void expiredDate_autoSetsExpiredStatusAndThrows() {
            Property property = buildProperty();
            property.setComplianceStatus(ComplianceStatus.COMPLIANT);
            property.setComplianceExpiresAt(LocalDate.now().minusDays(1));

            when(propertyRepository.save(any(Property.class))).thenAnswer(inv -> inv.getArgument(0));

            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> propertyService.validateBookingEligibility(property));

            assertTrue(ex.getMessage().contains("EXPIRED"));
            assertEquals(ComplianceStatus.EXPIRED, property.getComplianceStatus());
            verify(propertyRepository).save(property);
        }

        @Test
        void expiresAtNull_compliant_passes() {
            Property property = buildProperty();
            property.setComplianceStatus(ComplianceStatus.COMPLIANT);
            property.setComplianceExpiresAt(null);

            propertyService.validateBookingEligibility(property);

            assertEquals(ComplianceStatus.COMPLIANT, property.getComplianceStatus());
            verify(propertyRepository, never()).save(any());
        }

        @Test
        void expiresAtToday_compliant_passes() {
            Property property = buildProperty();
            property.setComplianceStatus(ComplianceStatus.COMPLIANT);
            property.setComplianceExpiresAt(LocalDate.now());

            propertyService.validateBookingEligibility(property);

            assertEquals(ComplianceStatus.COMPLIANT, property.getComplianceStatus());
            verify(propertyRepository, never()).save(any());
        }
    }

    // -----------------------------------------------------------------------
    // applyExtendedFields (tested indirectly via createProperty/updateProperty)
    // -----------------------------------------------------------------------

    @Nested
    class ApplyExtendedFields {

        @Test
        void applyExtendedFields_setsComplianceRentalAndRulesFields() {
            PropertyRequest request = buildFullRequest();

            when(propertyRepository.save(any(Property.class))).thenAnswer(invocation -> {
                Property saved = invocation.getArgument(0);
                saved.setId(PROPERTY_ID);
                return saved;
            });

            PropertyResponse response = propertyService.createProperty(request, USER_ID);

            verify(propertyRepository).save(propertyCaptor.capture());
            Property captured = propertyCaptor.getValue();

            assertEquals(ComplianceStatus.COMPLIANT, captured.getComplianceStatus());
            assertEquals("All inspections passed", captured.getComplianceNotes());
            assertEquals(LocalDate.of(2027, 12, 31), captured.getComplianceExpiresAt());
            assertEquals(new BigDecimal("150.00"), captured.getRentalPricePerSlot());
            assertEquals(new BigDecimal("500.00"), captured.getDepositAmount());
            assertEquals(24, captured.getMinBookingLeadHours());
            assertEquals(90, captured.getMaxBookingLeadDays());
            assertEquals("No smoking; no pets", captured.getRentalRules());
        }

        @Test
        void applyExtendedFields_invalidComplianceStatus_throwsBusinessRuleException() {
            PropertyRequest request = new PropertyRequest();
            request.setName("Prop");
            request.setType("OFFICE");
            request.setCapacity(5);
            request.setComplianceStatus("INVALID_STATUS");

            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> propertyService.createProperty(request, USER_ID));

            assertEquals("Invalid compliance status: INVALID_STATUS", ex.getMessage());
            verify(propertyRepository, never()).save(any());
        }

        @Test
        void applyExtendedFields_nullExtendedFields_doesNotOverwrite() {
            Property existing = buildProperty();
            existing.setComplianceStatus(ComplianceStatus.COMPLIANT);
            existing.setComplianceNotes("Existing notes");
            existing.setRentalPricePerSlot(new BigDecimal("100.00"));

            when(propertyRepository.findById(PROPERTY_ID)).thenReturn(Optional.of(existing));
            when(propertyRepository.save(any(Property.class))).thenAnswer(inv -> inv.getArgument(0));

            PropertyUpdateRequest request = new PropertyUpdateRequest();
            request.setName("Updated");
            // All extended fields left null

            PropertyResponse response = propertyService.updateProperty(PROPERTY_ID, request, USER_ID);

            verify(propertyRepository).save(propertyCaptor.capture());
            Property saved = propertyCaptor.getValue();

            // Extended fields remain unchanged
            assertEquals(ComplianceStatus.COMPLIANT, saved.getComplianceStatus());
            assertEquals("Existing notes", saved.getComplianceNotes());
            assertEquals(new BigDecimal("100.00"), saved.getRentalPricePerSlot());
        }
    }
}
