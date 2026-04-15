package com.anju.appointment.file.dto;

import com.anju.appointment.file.entity.FileRecord;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class FileRecordResponse {

    private Long id;
    private String fileName;
    private String contentType;
    private Long fileSize;
    private String module;
    private Long referenceId;
    private String description;
    private int version;
    private Long parentFileId;
    private LocalDateTime uploadedAt;

    public static FileRecordResponse fromEntity(FileRecord record) {
        return FileRecordResponse.builder()
                .id(record.getId())
                .fileName(record.getFileName())
                .contentType(record.getContentType())
                .fileSize(record.getFileSize())
                .module(record.getModule())
                .referenceId(record.getReferenceId())
                .description(record.getDescription())
                .version(record.getVersion())
                .parentFileId(record.getParentFileId())
                .uploadedAt(record.getUploadedAt())
                .build();
    }
}
