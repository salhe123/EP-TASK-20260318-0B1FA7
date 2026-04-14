package com.anju.appointment.file.controller;

import com.anju.appointment.auth.security.AuthenticatedUser;
import com.anju.appointment.file.dto.FileRecordResponse;
import com.anju.appointment.file.entity.FileRecord;
import com.anju.appointment.file.service.FileService;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping("/upload")
    public ResponseEntity<FileRecordResponse> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("module") String module,
            @RequestParam(value = "referenceId", required = false) Long referenceId,
            @RequestParam(value = "description", required = false) String description,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(fileService.uploadFile(file, module, referenceId, description, principal.getUserId()));
    }

    @GetMapping
    public ResponseEntity<Page<FileRecordResponse>> listFiles(
            @RequestParam(required = false) String module,
            @RequestParam(required = false) Long referenceId,
            @RequestParam(required = false) String fileName,
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(fileService.listFiles(module, referenceId, fileName, pageable,
                principal.getUserId(), principal.getRole()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<FileRecordResponse> getFileRecord(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(fileService.getFileRecord(id, principal.getUserId(), principal.getRole()));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> downloadFile(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        FileRecord record = fileService.findFileOrThrow(id);
        fileService.enforceFileAccess(record, principal.getUserId(), principal.getRole());
        Resource resource = fileService.downloadFile(id);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(record.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + record.getFileName() + "\"")
                .body(resource);
    }

    @GetMapping("/{id}/versions")
    public ResponseEntity<List<FileRecordResponse>> getVersionHistory(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(fileService.getVersionHistory(id, principal.getUserId(), principal.getRole()));
    }
}
