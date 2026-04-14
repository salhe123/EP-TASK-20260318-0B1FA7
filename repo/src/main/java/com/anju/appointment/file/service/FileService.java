package com.anju.appointment.file.service;

import com.anju.appointment.audit.service.AuditService;
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

@Service
public class FileService {

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

        String relativePath = "files/" + module.toLowerCase()
                + (referenceId != null ? "/" + referenceId : "")
                + "/" + file.getOriginalFilename();

        Path targetPath = storagePath.resolve(relativePath).normalize();

        try {
            Files.createDirectories(targetPath.getParent());
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BusinessRuleException("Failed to store file: " + e.getMessage());
        }

        FileRecord record = new FileRecord();
        record.setFileName(file.getOriginalFilename());
        record.setFilePath(relativePath);
        record.setContentType(file.getContentType());
        record.setFileSize(file.getSize());
        record.setModule(upperModule);
        record.setReferenceId(referenceId);
        record.setDescription(description);
        record.setUploadedBy(uploadedBy);

        // Handle versioning: find existing files with same name/module/reference
        Integer maxVersion = fileRecordRepository.findMaxVersion(upperModule, referenceId, file.getOriginalFilename());
        if (maxVersion != null) {
            record.setVersion(maxVersion + 1);
            // Set parentFileId to the first version's ID
            Page<FileRecord> firstPage = fileRecordRepository.findByFilters(
                    upperModule, referenceId, file.getOriginalFilename(), Pageable.ofSize(1));
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
        boolean canSeeAll = "ADMIN".equals(role) || "DISPATCHER".equals(role);
        if (canSeeAll) {
            return fileRecordRepository.findByFilters(module, referenceId, fileName, pageable)
                    .map(FileRecordResponse::fromEntity);
        }
        return fileRecordRepository.findByFiltersAndUploadedBy(module, referenceId, fileName, userId, pageable)
                .map(FileRecordResponse::fromEntity);
    }

    public FileRecordResponse getFileRecord(Long id, Long userId, String role) {
        FileRecord record = findFileOrThrow(id);
        enforceFileAccess(record, userId, role);
        return FileRecordResponse.fromEntity(record);
    }

    public Resource downloadFile(Long id) {
        FileRecord record = findFileOrThrow(id);

        Path filePath = storagePath.resolve(record.getFilePath()).normalize();
        try {
            Resource resource = new UrlResource(filePath.toUri());
            if (!resource.exists()) {
                throw new ResourceNotFoundException("File not found on disk: " + record.getFilePath());
            }
            return resource;
        } catch (MalformedURLException e) {
            throw new ResourceNotFoundException("File not found: " + record.getFilePath());
        }
    }

    public java.util.List<FileRecordResponse> getVersionHistory(Long fileId, Long userId, String role) {
        FileRecord record = findFileOrThrow(fileId);
        enforceFileAccess(record, userId, role);
        return fileRecordRepository.findVersionHistory(fileId).stream()
                .map(FileRecordResponse::fromEntity)
                .toList();
    }

    public void enforceFileAccess(FileRecord record, Long userId, String role) {
        boolean canSeeAll = "ADMIN".equals(role) || "DISPATCHER".equals(role);
        if (!canSeeAll && !record.getUploadedBy().equals(userId)) {
            throw new BusinessRuleException("Not authorized to access this file");
        }
    }

    public FileRecord findFileOrThrow(Long id) {
        return fileRecordRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("File record not found with id: " + id));
    }
}
