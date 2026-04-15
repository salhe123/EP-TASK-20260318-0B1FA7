package com.anju.appointment.file.service;

import com.anju.appointment.audit.service.AuditService;
import com.anju.appointment.common.AuthorizationException;
import com.anju.appointment.common.BusinessRuleException;
import com.anju.appointment.common.ResourceNotFoundException;
import com.anju.appointment.file.dto.FileRecordResponse;
import com.anju.appointment.file.entity.FileRecord;
import com.anju.appointment.file.repository.FileRecordRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

@Service
public class FileService {

    private static final Set<String> ALLOWED_MODULES = Set.of(
            "PROPERTY", "APPOINTMENT", "FINANCIAL", "AUDIT", "USER");

    private final FileRecordRepository fileRecordRepository;
    private final AuditService auditService;
    private final Path storagePath;

    public FileService(FileRecordRepository fileRecordRepository,
                       AuditService auditService,
                       @Value("${app.storage.path}") String storagePath) {
        this.fileRecordRepository = fileRecordRepository;
        this.auditService = auditService;
        this.storagePath = Paths.get(storagePath).toAbsolutePath().normalize();
    }

    @Transactional
    public FileRecordResponse uploadFile(MultipartFile file, String module, Long referenceId,
                                          String description, Long uploadedBy) {
        if (file.isEmpty()) {
            throw new BusinessRuleException("File is empty");
        }

        String upperModule = module.toUpperCase();
        if (!ALLOWED_MODULES.contains(upperModule)) {
            throw new BusinessRuleException("Invalid file module: " + module
                    + ". Allowed: " + String.join(", ", ALLOWED_MODULES));
        }

        String originalFilename = sanitizeFilename(file.getOriginalFilename());

        // Generate a unique storage name to avoid collisions and traversal
        String storageName = UUID.randomUUID() + "_" + originalFilename;
        String relativePath = "files/" + module.toLowerCase()
                + (referenceId != null ? "/" + referenceId : "")
                + "/" + storageName;

        Path targetPath = storagePath.resolve(relativePath).normalize();

        // Verify the resolved path stays within the storage root
        if (!targetPath.startsWith(storagePath)) {
            throw new BusinessRuleException("Invalid file path");
        }

        try {
            Files.createDirectories(targetPath.getParent());
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BusinessRuleException("Failed to store file: " + e.getMessage());
        }

        FileRecord record = new FileRecord();
        record.setFileName(originalFilename);
        record.setFilePath(relativePath);
        record.setContentType(file.getContentType());
        record.setFileSize(file.getSize());
        record.setModule(upperModule);
        record.setReferenceId(referenceId);
        record.setDescription(description);
        record.setUploadedBy(uploadedBy);

        // Handle versioning: find existing files with same name/module/reference
        Integer maxVersion = fileRecordRepository.findMaxVersion(upperModule, referenceId, originalFilename);
        if (maxVersion != null) {
            record.setVersion(maxVersion + 1);
            // Set parentFileId to the first version's ID
            Page<FileRecord> firstPage = fileRecordRepository.findByFilters(
                    upperModule, referenceId, originalFilename, Pageable.ofSize(1));
            if (!firstPage.isEmpty()) {
                FileRecord earliest = firstPage.getContent().get(0);
                record.setParentFileId(earliest.getParentFileId() != null
                        ? earliest.getParentFileId() : earliest.getId());
            }
        }

        record = fileRecordRepository.save(record);

        // First version: set parentFileId to self for future version chain
        if (record.getParentFileId() == null) {
            record.setParentFileId(record.getId());
            record = fileRecordRepository.save(record);
        }
        auditService.log(uploadedBy, null, "FILE", "CREATE",
                "FileRecord", record.getId(),
                "Uploaded file: " + record.getFileName() + " (" + upperModule + ")", null);
        return FileRecordResponse.fromEntity(record);
    }

    public Page<FileRecordResponse> listFiles(String module, Long referenceId, String fileName,
                                               Pageable pageable, Long userId, String role) {
        String normalizedModule = module != null ? module.toUpperCase() : null;
        boolean canSeeAll = "ADMIN".equals(role) || "DISPATCHER".equals(role);
        Page<FileRecordResponse> result;
        if (canSeeAll) {
            result = fileRecordRepository.findByFilters(normalizedModule, referenceId, fileName, pageable)
                    .map(FileRecordResponse::fromEntity);
        } else {
            result = fileRecordRepository.findByFiltersAndUploadedBy(normalizedModule, referenceId, fileName, userId, pageable)
                    .map(FileRecordResponse::fromEntity);
        }
        auditService.log(userId, null, "FILE", "LIST",
                "FileRecord", null, "Listed files" + (normalizedModule != null ? " module=" + normalizedModule : ""), null);
        return result;
    }

    public FileRecordResponse getFileRecord(Long id, Long userId, String role) {
        FileRecord record = findFileOrThrow(id);
        enforceFileAccess(record, userId, role);
        auditService.log(userId, null, "FILE", "READ",
                "FileRecord", id, "Read file record: " + record.getFileName(), null);
        return FileRecordResponse.fromEntity(record);
    }

    public Resource downloadFile(Long id) {
        FileRecord record = findFileOrThrow(id);

        Path filePath = storagePath.resolve(record.getFilePath()).normalize();

        // Verify the resolved path stays within the storage root
        if (!filePath.startsWith(storagePath)) {
            throw new BusinessRuleException("Invalid file path");
        }

        try {
            Resource resource = new UrlResource(filePath.toUri());
            if (!resource.exists()) {
                throw new ResourceNotFoundException("File not found on disk");
            }
            auditService.log(null, null, "FILE", "DOWNLOAD",
                    "FileRecord", id,
                    "Downloaded file: " + record.getFileName(), null);
            return resource;
        } catch (MalformedURLException e) {
            throw new ResourceNotFoundException("File not found");
        }
    }

    public java.util.List<FileRecordResponse> getVersionHistory(Long fileId, Long userId, String role) {
        FileRecord record = findFileOrThrow(fileId);
        enforceFileAccess(record, userId, role);
        // Resolve to root of the version chain
        Long chainId = record.getParentFileId() != null ? record.getParentFileId() : record.getId();
        return fileRecordRepository.findVersionHistory(chainId).stream()
                .map(FileRecordResponse::fromEntity)
                .toList();
    }

    public void enforceFileAccess(FileRecord record, Long userId, String role) {
        boolean canSeeAll = "ADMIN".equals(role) || "DISPATCHER".equals(role);
        if (!canSeeAll && !record.getUploadedBy().equals(userId)) {
            throw new AuthorizationException("Not authorized to access this file");
        }
    }

    public FileRecord findFileOrThrow(Long id) {
        return fileRecordRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("File record not found with id: " + id));
    }

    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "unnamed";
        }
        // Strip path separators and traversal segments
        String sanitized = filename.replace("\\", "/");
        // Take only the final path component
        int lastSlash = sanitized.lastIndexOf('/');
        if (lastSlash >= 0) {
            sanitized = sanitized.substring(lastSlash + 1);
        }
        // Remove leading dots to prevent hidden files / traversal
        sanitized = sanitized.replaceAll("^\\.+", "");
        if (sanitized.isBlank()) {
            return "unnamed";
        }
        return sanitized;
    }
}
