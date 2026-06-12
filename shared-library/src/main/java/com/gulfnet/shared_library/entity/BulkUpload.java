package com.gulfnet.shared_library.entity;

import com.gulfnet.shared_library.enums.BulkUploadStatus;
import com.gulfnet.shared_library.enums.UploadType;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "bulk_uploads")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BulkUpload {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "file_path", columnDefinition = "VARCHAR(1000)")
    private String filePath;

    @Column(name = "error_file_path", columnDefinition = "VARCHAR(1000)")
    private String errorFilePath;

    @Column(name = "total_record_count")
    private Integer totalRecordCount;

    @Column(name = "success_record_count")
    private Integer successRecordCount;

    @Column(name = "failure_record_count")
    private Integer failureRecordCount;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private BulkUploadStatus status;

    @Column(name = "reason")
    private String reason;

    @Column(name = "created_at", columnDefinition = "TIMESTAMPTZ")
    @CreationTimestamp
    private OffsetDateTime createdAt;

    @Column(name = "upload_type")
    @Enumerated(EnumType.STRING)
    private UploadType uploadType;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_at", columnDefinition = "TIMESTAMPTZ")
    @UpdateTimestamp
    private OffsetDateTime updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;
}