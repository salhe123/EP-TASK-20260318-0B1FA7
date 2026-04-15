package com.anju.appointment.file;

import com.anju.appointment.audit.service.AuditService;
import com.anju.appointment.common.AuthorizationException;
import com.anju.appointment.common.BusinessRuleException;
import com.anju.appointment.common.ResourceNotFoundException;
import com.anju.appointment.file.dto.FileRecordResponse;
import com.anju.appointment.file.entity.FileRecord;
import com.anju.appointment.file.repository.FileRecordRepository;
import com.anju.appointment.file.service.FileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileServiceTest {

    @Mock
    private FileRecordRepository fileRecordRepository;

    @Mock
    private AuditService auditService;

    @Mock
    private MultipartFile multipartFile;

    @TempDir
    Path tempDir;

    private FileService fileService;

    @Captor
    private ArgumentCaptor<FileRecord> fileRecordCaptor;

    private static final Long USER_ID = 1L;
    private static final Long ADMIN_USER_ID = 2L;
    private static final Long OTHER_USER_ID = 3L;

    @BeforeEach
    void setUp() {
        fileService = new FileService(fileRecordRepository, auditService, tempDir.toString());
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private FileRecord buildFileRecord(Long id, String fileName, Long uploadedBy, int version, Long parentFileId) {
        FileRecord r = new FileRecord();
        r.setId(id);
        r.setFileName(fileName);
        r.setFilePath("files/property/" + id + "/" + fileName);
        r.setContentType("application/pdf");
        r.setFileSize(1024L);
        r.setModule("PROPERTY");
        r.setReferenceId(100L);
        r.setDescription("Test file");
        r.setUploadedBy(uploadedBy);
        r.setVersion(version);
        r.setParentFileId(parentFileId);
        r.setUploadedAt(java.time.LocalDateTime.now());
        return r;
    }

    private void stubMultipartFile(String fileName) throws IOException {
        byte[] content = "file content".getBytes();
        when(multipartFile.isEmpty()).thenReturn(false);
        when(multipartFile.getOriginalFilename()).thenReturn(fileName);
        when(multipartFile.getContentType()).thenReturn("application/pdf");
        when(multipartFile.getSize()).thenReturn((long) content.length);
        when(multipartFile.getInputStream()).thenReturn(new ByteArrayInputStream(content));
    }

    // ---------------------------------------------------------------------------
    // uploadFile
    // ---------------------------------------------------------------------------

    @Nested
    class UploadFile {

        @Test
        void success() throws IOException {
            stubMultipartFile("report.pdf");

            // No existing version
            when(fileRecordRepository.findMaxVersion("PROPERTY", 100L, "report.pdf")).thenReturn(null);

            // First save: record gets ID assigned
            FileRecord firstSave = buildFileRecord(10L, "report.pdf", USER_ID, 1, null);
            // Second save: parentFileId set to self
            FileRecord secondSave = buildFileRecord(10L, "report.pdf", USER_ID, 1, 10L);

            when(fileRecordRepository.save(any(FileRecord.class)))
                    .thenReturn(firstSave)
                    .thenReturn(secondSave);

            FileRecordResponse response = fileService.uploadFile(
                    multipartFile, "property", 100L, "Test upload", USER_ID);

            assertThat(response.getId()).isEqualTo(10L);
            assertThat(response.getFileName()).isEqualTo("report.pdf");
            assertThat(response.getModule()).isEqualTo("PROPERTY");
            assertThat(response.getVersion()).isEqualTo(1);
            assertThat(response.getParentFileId()).isEqualTo(10L);

            // Saved twice: once initial, once to set parentFileId
            verify(fileRecordRepository, times(2)).save(any(FileRecord.class));
        }

        @Test
        void emptyFileThrows() {
            when(multipartFile.isEmpty()).thenReturn(true);

            assertThatThrownBy(() ->
                    fileService.uploadFile(multipartFile, "property", 100L, "desc", USER_ID))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("File is empty");

            verify(fileRecordRepository, never()).save(any());
        }

        @Test
        void versioningIncrementsVersion() throws IOException {
            stubMultipartFile("report.pdf");

            // Existing version 2
            when(fileRecordRepository.findMaxVersion("PROPERTY", 100L, "report.pdf")).thenReturn(2);

            // Return first version record when looking up version chain
            FileRecord firstVersion = buildFileRecord(5L, "report.pdf", USER_ID, 1, 5L);
            Page<FileRecord> firstPage = new PageImpl<>(List.of(firstVersion));
            when(fileRecordRepository.findByFilters("PROPERTY", 100L, "report.pdf", Pageable.ofSize(1)))
                    .thenReturn(firstPage);

            FileRecord saved = buildFileRecord(15L, "report.pdf", USER_ID, 3, 5L);
            when(fileRecordRepository.save(any(FileRecord.class))).thenReturn(saved);

            FileRecordResponse response = fileService.uploadFile(
                    multipartFile, "property", 100L, "Updated version", USER_ID);

            assertThat(response.getVersion()).isEqualTo(3);
            assertThat(response.getParentFileId()).isEqualTo(5L);

            verify(fileRecordRepository).save(fileRecordCaptor.capture());
            FileRecord captured = fileRecordCaptor.getValue();
            assertThat(captured.getVersion()).isEqualTo(3);
            assertThat(captured.getParentFileId()).isEqualTo(5L);
        }

        @Test
        void firstUploadSetsParentFileIdToSelf() throws IOException {
            stubMultipartFile("contract.pdf");

            when(fileRecordRepository.findMaxVersion("PROPERTY", 100L, "contract.pdf")).thenReturn(null);

            // First save returns record with null parentFileId and an assigned ID
            FileRecord afterFirstSave = buildFileRecord(20L, "contract.pdf", USER_ID, 1, null);
            // Second save returns record with parentFileId = self
            FileRecord afterSecondSave = buildFileRecord(20L, "contract.pdf", USER_ID, 1, 20L);

            when(fileRecordRepository.save(any(FileRecord.class)))
                    .thenReturn(afterFirstSave)
                    .thenReturn(afterSecondSave);

            FileRecordResponse response = fileService.uploadFile(
                    multipartFile, "property", 100L, "First upload", USER_ID);

            assertThat(response.getParentFileId()).isEqualTo(20L);

            // Should save twice: first to create, then to set parentFileId to self
            verify(fileRecordRepository, times(2)).save(fileRecordCaptor.capture());
            List<FileRecord> allSaves = fileRecordCaptor.getAllValues();
            // Second save should have parentFileId set to the record's own ID
            assertThat(allSaves.get(1).getParentFileId()).isEqualTo(20L);
        }

        @Test
        void auditLogged() throws IOException {
            stubMultipartFile("doc.pdf");

            when(fileRecordRepository.findMaxVersion("PROPERTY", 100L, "doc.pdf")).thenReturn(null);

            FileRecord saved = buildFileRecord(30L, "doc.pdf", USER_ID, 1, null);
            FileRecord savedWithParent = buildFileRecord(30L, "doc.pdf", USER_ID, 1, 30L);
            when(fileRecordRepository.save(any(FileRecord.class)))
                    .thenReturn(saved)
                    .thenReturn(savedWithParent);

            fileService.uploadFile(multipartFile, "property", 100L, "Audit test", USER_ID);

            verify(auditService).log(
                    eq(USER_ID), isNull(), eq("FILE"), eq("CREATE"),
                    eq("FileRecord"), eq(30L),
                    anyString(), isNull());
        }
    }

    // ---------------------------------------------------------------------------
    // listFiles
    // ---------------------------------------------------------------------------

    @Nested
    class ListFiles {

        @Test
        void adminSeesAll() {
            Pageable pageable = PageRequest.of(0, 10);
            FileRecord r1 = buildFileRecord(1L, "a.pdf", USER_ID, 1, 1L);
            FileRecord r2 = buildFileRecord(2L, "b.pdf", OTHER_USER_ID, 1, 2L);
            Page<FileRecord> page = new PageImpl<>(List.of(r1, r2));

            when(fileRecordRepository.findByFilters("PROPERTY", null, null, pageable))
                    .thenReturn(page);

            Page<FileRecordResponse> result = fileService.listFiles("PROPERTY", null, null, pageable, ADMIN_USER_ID, "ADMIN");

            assertThat(result.getTotalElements()).isEqualTo(2);
            verify(fileRecordRepository).findByFilters("PROPERTY", null, null, pageable);
            verify(fileRecordRepository, never()).findByFiltersAndUploadedBy(any(), any(), any(), any(), any());
        }

        @Test
        void nonPrivilegedSeesOnlyOwn() {
            Pageable pageable = PageRequest.of(0, 10);
            FileRecord r1 = buildFileRecord(1L, "a.pdf", USER_ID, 1, 1L);
            Page<FileRecord> page = new PageImpl<>(List.of(r1));

            when(fileRecordRepository.findByFiltersAndUploadedBy("PROPERTY", null, null, USER_ID, pageable))
                    .thenReturn(page);

            Page<FileRecordResponse> result = fileService.listFiles("PROPERTY", null, null, pageable, USER_ID, "SERVICE_STAFF");

            assertThat(result.getTotalElements()).isEqualTo(1);
            verify(fileRecordRepository, never()).findByFilters(any(), any(), any(), any());
            verify(fileRecordRepository).findByFiltersAndUploadedBy("PROPERTY", null, null, USER_ID, pageable);
        }
    }

    // ---------------------------------------------------------------------------
    // getFileRecord
    // ---------------------------------------------------------------------------

    @Nested
    class GetFileRecord {

        @Test
        void withAccess() {
            FileRecord record = buildFileRecord(10L, "doc.pdf", USER_ID, 1, 10L);
            when(fileRecordRepository.findById(10L)).thenReturn(Optional.of(record));

            FileRecordResponse response = fileService.getFileRecord(10L, USER_ID, "SERVICE_STAFF");

            assertThat(response.getId()).isEqualTo(10L);
            assertThat(response.getFileName()).isEqualTo("doc.pdf");
        }

        @Test
        void withoutAccessThrows() {
            FileRecord record = buildFileRecord(10L, "doc.pdf", USER_ID, 1, 10L);
            when(fileRecordRepository.findById(10L)).thenReturn(Optional.of(record));

            assertThatThrownBy(() -> fileService.getFileRecord(10L, OTHER_USER_ID, "SERVICE_STAFF"))
                    .isInstanceOf(AuthorizationException.class)
                    .hasMessageContaining("Not authorized to access this file");
        }
    }

    // ---------------------------------------------------------------------------
    // enforceFileAccess
    // ---------------------------------------------------------------------------

    @Nested
    class EnforceFileAccess {

        @Test
        void adminPasses() {
            FileRecord record = buildFileRecord(10L, "doc.pdf", OTHER_USER_ID, 1, 10L);
            // Should not throw
            fileService.enforceFileAccess(record, ADMIN_USER_ID, "ADMIN");
        }

        @Test
        void dispatcherPasses() {
            FileRecord record = buildFileRecord(10L, "doc.pdf", OTHER_USER_ID, 1, 10L);
            // Should not throw
            fileService.enforceFileAccess(record, ADMIN_USER_ID, "DISPATCHER");
        }

        @Test
        void ownerPasses() {
            FileRecord record = buildFileRecord(10L, "doc.pdf", USER_ID, 1, 10L);
            // Should not throw
            fileService.enforceFileAccess(record, USER_ID, "SERVICE_STAFF");
        }

        @Test
        void nonOwnerNonPrivilegedThrows() {
            FileRecord record = buildFileRecord(10L, "doc.pdf", USER_ID, 1, 10L);

            assertThatThrownBy(() -> fileService.enforceFileAccess(record, OTHER_USER_ID, "SERVICE_STAFF"))
                    .isInstanceOf(AuthorizationException.class)
                    .hasMessageContaining("Not authorized to access this file");
        }
    }

    // ---------------------------------------------------------------------------
    // downloadFile
    // ---------------------------------------------------------------------------

    @Nested
    class DownloadFile {

        @Test
        void success() throws IOException {
            // Create the file on disk inside the temp directory
            String relativePath = "files/property/10/report.pdf";
            Path filePath = tempDir.resolve(relativePath);
            Files.createDirectories(filePath.getParent());
            Files.write(filePath, "PDF content".getBytes());

            FileRecord record = buildFileRecord(10L, "report.pdf", USER_ID, 1, 10L);
            record.setFilePath(relativePath);
            when(fileRecordRepository.findById(10L)).thenReturn(Optional.of(record));

            Resource resource = fileService.downloadFile(10L);

            assertThat(resource).isNotNull();
            assertThat(resource.exists()).isTrue();
        }

        @Test
        void notOnDiskThrows() {
            FileRecord record = buildFileRecord(10L, "missing.pdf", USER_ID, 1, 10L);
            record.setFilePath("files/property/10/missing.pdf");
            when(fileRecordRepository.findById(10L)).thenReturn(Optional.of(record));

            assertThatThrownBy(() -> fileService.downloadFile(10L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("File not found on disk");
        }
    }

    // ---------------------------------------------------------------------------
    // getVersionHistory
    // ---------------------------------------------------------------------------

    @Nested
    class GetVersionHistory {

        @Test
        void withAccess() {
            FileRecord record = buildFileRecord(10L, "doc.pdf", USER_ID, 1, 10L);
            FileRecord v2 = buildFileRecord(15L, "doc.pdf", USER_ID, 2, 10L);
            when(fileRecordRepository.findById(10L)).thenReturn(Optional.of(record));
            when(fileRecordRepository.findVersionHistory(10L)).thenReturn(List.of(v2, record));

            List<FileRecordResponse> history = fileService.getVersionHistory(10L, USER_ID, "SERVICE_STAFF");

            assertThat(history).hasSize(2);
            assertThat(history.get(0).getVersion()).isEqualTo(2);
            assertThat(history.get(1).getVersion()).isEqualTo(1);
        }

        @Test
        void withoutAccessThrows() {
            FileRecord record = buildFileRecord(10L, "doc.pdf", USER_ID, 1, 10L);
            when(fileRecordRepository.findById(10L)).thenReturn(Optional.of(record));

            assertThatThrownBy(() -> fileService.getVersionHistory(10L, OTHER_USER_ID, "FINANCE"))
                    .isInstanceOf(AuthorizationException.class)
                    .hasMessageContaining("Not authorized to access this file");
        }
    }

    // ---------------------------------------------------------------------------
    // Path traversal defense
    // ---------------------------------------------------------------------------

    @Nested
    class PathTraversalDefense {

        @Test
        void traversalFilename_isStripped() throws IOException {
            stubMultipartFile("../../etc/passwd");

            when(fileRecordRepository.findMaxVersion("PROPERTY", 100L, "passwd")).thenReturn(null);

            FileRecord saved = buildFileRecord(50L, "passwd", USER_ID, 1, null);
            FileRecord savedWithParent = buildFileRecord(50L, "passwd", USER_ID, 1, 50L);
            when(fileRecordRepository.save(any(FileRecord.class)))
                    .thenReturn(saved)
                    .thenReturn(savedWithParent);

            FileRecordResponse response = fileService.uploadFile(
                    multipartFile, "property", 100L, "Traversal test", USER_ID);

            assertThat(response).isNotNull();
            verify(fileRecordRepository, times(2)).save(fileRecordCaptor.capture());
            FileRecord captured = fileRecordCaptor.getAllValues().get(0);
            assertThat(captured.getFileName()).isEqualTo("passwd");
        }

        @Test
        void backslashTraversal_isStripped() throws IOException {
            stubMultipartFile("..\\..\\windows\\system32\\config");

            when(fileRecordRepository.findMaxVersion("PROPERTY", 100L, "config")).thenReturn(null);

            FileRecord saved = buildFileRecord(51L, "config", USER_ID, 1, null);
            FileRecord savedWithParent = buildFileRecord(51L, "config", USER_ID, 1, 51L);
            when(fileRecordRepository.save(any(FileRecord.class)))
                    .thenReturn(saved)
                    .thenReturn(savedWithParent);

            FileRecordResponse response = fileService.uploadFile(
                    multipartFile, "property", 100L, "Backslash test", USER_ID);

            assertThat(response).isNotNull();
        }
    }

    // ---------------------------------------------------------------------------
    // Module allowlist validation
    // ---------------------------------------------------------------------------

    @Nested
    class ModuleAllowlist {

        @Test
        void validModule_accepted() throws IOException {
            stubMultipartFile("doc.pdf");

            when(fileRecordRepository.findMaxVersion("PROPERTY", 100L, "doc.pdf")).thenReturn(null);
            FileRecord saved = buildFileRecord(60L, "doc.pdf", USER_ID, 1, null);
            FileRecord savedWithParent = buildFileRecord(60L, "doc.pdf", USER_ID, 1, 60L);
            when(fileRecordRepository.save(any(FileRecord.class)))
                    .thenReturn(saved)
                    .thenReturn(savedWithParent);

            FileRecordResponse response = fileService.uploadFile(
                    multipartFile, "property", 100L, "Valid module", USER_ID);
            assertThat(response).isNotNull();
        }

        @Test
        void invalidModule_rejected() {
            when(multipartFile.isEmpty()).thenReturn(false);

            assertThatThrownBy(() ->
                    fileService.uploadFile(multipartFile, "HACKING", 100L, "Invalid module", USER_ID))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Invalid file module");
        }

        @Test
        void caseInsensitiveModule_accepted() throws IOException {
            stubMultipartFile("doc.pdf");

            when(fileRecordRepository.findMaxVersion("APPOINTMENT", 100L, "doc.pdf")).thenReturn(null);
            FileRecord saved = buildFileRecord(61L, "doc.pdf", USER_ID, 1, null);
            FileRecord savedWithParent = buildFileRecord(61L, "doc.pdf", USER_ID, 1, 61L);
            when(fileRecordRepository.save(any(FileRecord.class)))
                    .thenReturn(saved)
                    .thenReturn(savedWithParent);

            FileRecordResponse response = fileService.uploadFile(
                    multipartFile, "appointment", 100L, "Case test", USER_ID);
            assertThat(response).isNotNull();
        }
    }
}
