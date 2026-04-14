package com.anju.appointment.file.repository;

import com.anju.appointment.file.entity.FileRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FileRecordRepository extends JpaRepository<FileRecord, Long> {

    @Query("SELECT f FROM FileRecord f WHERE " +
           "(:module IS NULL OR f.module = :module) AND " +
           "(:referenceId IS NULL OR f.referenceId = :referenceId) AND " +
           "(:fileName IS NULL OR LOWER(f.fileName) LIKE LOWER(CONCAT('%', :fileName, '%')))")
    Page<FileRecord> findByFilters(@Param("module") String module,
                                    @Param("referenceId") Long referenceId,
                                    @Param("fileName") String fileName,
                                    Pageable pageable);

    @Query("SELECT f FROM FileRecord f WHERE f.uploadedBy = :uploadedBy AND " +
           "(:module IS NULL OR f.module = :module) AND " +
           "(:referenceId IS NULL OR f.referenceId = :referenceId) AND " +
           "(:fileName IS NULL OR LOWER(f.fileName) LIKE LOWER(CONCAT('%', :fileName, '%')))")
    Page<FileRecord> findByFiltersAndUploadedBy(@Param("module") String module,
                                                 @Param("referenceId") Long referenceId,
                                                 @Param("fileName") String fileName,
                                                 @Param("uploadedBy") Long uploadedBy,
                                                 Pageable pageable);

    @Query("SELECT MAX(f.version) FROM FileRecord f WHERE f.module = :module " +
           "AND f.referenceId = :referenceId AND f.fileName = :fileName")
    Integer findMaxVersion(@Param("module") String module,
                           @Param("referenceId") Long referenceId,
                           @Param("fileName") String fileName);

    @Query("SELECT f FROM FileRecord f WHERE f.parentFileId = :parentFileId ORDER BY f.version DESC")
    java.util.List<FileRecord> findVersionHistory(@Param("parentFileId") Long parentFileId);
}
