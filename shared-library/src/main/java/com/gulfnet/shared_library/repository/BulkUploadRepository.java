package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.BulkUpload;
import org.springframework.data.domain.Page;
import com.gulfnet.shared_library.enums.BulkUploadStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import com.gulfnet.shared_library.enums.UploadType;

import java.util.List;
import java.util.UUID;

@Repository
public interface BulkUploadRepository extends JpaRepository<BulkUpload, UUID> {
    /**
     * Finds all bulk uploads with optional search term matching against multiple fields including
     * ID, status, reason, record counts, and file paths (with URL decoding for encoded paths).
     * Supports pagination and sorting via Pageable.
     *
     * @param search   optional search term to match against multiple fields, null returns all uploads
     * @param pageable pagination and sorting parameters
     * @return paginated list of bulk uploads matching the search criteria
     */
    @Query("SELECT b FROM BulkUpload b WHERE " +
            "CAST(b.id AS string) LIKE CONCAT('%', :search, '%') OR " +
            "b.status LIKE CONCAT('%', :search, '%') OR " +
            "b.reason LIKE CONCAT('%', :search, '%') OR " +
            "CAST(b.totalRecordCount AS string) LIKE CONCAT('%', :search, '%') OR " +
            "CAST(b.successRecordCount AS string) LIKE CONCAT('%', :search, '%') OR " +
            "CAST(b.failureRecordCount AS string) LIKE CONCAT('%', :search, '%') OR " +
            "b.filePath LIKE CONCAT('%', :search, '%') OR " +
            "b.errorFilePath LIKE CONCAT('%', :search, '%') OR " +
            "REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(b.filePath, '%20', ' '), '%2B', '+'), '%2F', '/'), '%3A', ':'), '%3D', '=') LIKE CONCAT('%', :search, '%') OR " +
            "REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(b.errorFilePath, '%20', ' '), '%2B', '+'), '%2F', '/'), '%3A', ':'), '%3D', '=') LIKE CONCAT('%', :search, '%')")
    Page<BulkUpload> findAll(String search, Pageable pageable);
    
    /**
     * Counts bulk uploads with a specific status and optional search term matching against
     * multiple fields including ID, status, reason, record counts, and file paths (with URL decoding).
     *
     * @param search the search term to match against multiple fields
     * @param status the bulk upload status to filter by
     * @return the count of bulk uploads matching the search and status criteria
     */
    @Query("SELECT COUNT(b) FROM BulkUpload b WHERE " +
            "b.status = :status AND (" +
            "CAST(b.id AS string) LIKE CONCAT('%', :search, '%') OR " +
            "b.status LIKE CONCAT('%', :search, '%') OR " +
            "b.reason LIKE CONCAT('%', :search, '%') OR " +
            "CAST(b.totalRecordCount AS string) LIKE CONCAT('%', :search, '%') OR " +
            "CAST(b.successRecordCount AS string) LIKE CONCAT('%', :search, '%') OR " +
            "CAST(b.failureRecordCount AS string) LIKE CONCAT('%', :search, '%') OR " +
            "b.filePath LIKE CONCAT('%', :search, '%') OR " +
            "b.errorFilePath LIKE CONCAT('%', :search, '%') OR " +
            "REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(b.filePath, '%20', ' '), '%2B', '+'), '%2F', '/'), '%3A', ':'), '%3D', '=') LIKE CONCAT('%', :search, '%') OR " +
            "REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(b.errorFilePath, '%20', ' '), '%2B', '+'), '%2F', '/'), '%3A', ':'), '%3D', '=') LIKE CONCAT('%', :search, '%'))")
    long countBySearchAndStatus(String search, com.gulfnet.shared_library.enums.BulkUploadStatus status);
    
    
    List<BulkUpload> findByCreatedByOrderByCreatedAtDesc(UUID createdBy);

    Page<BulkUpload> findByStatus(BulkUploadStatus status, Pageable pageable);
    
    Page<BulkUpload> findByStatusAndUploadType(BulkUploadStatus status, UploadType uploadType, Pageable pageable);
    
    Page<BulkUpload> findByUploadType(UploadType uploadType, Pageable pageable);
    
    /**
     * Finds bulk uploads by upload type with optional search term matching against multiple fields
     * including ID, status, reason, record counts, and file paths (with URL decoding).
     * Supports pagination and sorting via Pageable.
     *
     * @param uploadType the upload type to filter by (EMPLOYEE, ITEMS, RESTAURANTS, etc.)
     * @param search     optional search term to match against multiple fields, null returns all uploads of the type
     * @param pageable   pagination and sorting parameters
     * @return paginated list of bulk uploads matching the upload type and search criteria
     */
    @Query("SELECT b FROM BulkUpload b WHERE " +
            "b.uploadType = :uploadType AND (" +
            "CAST(b.id AS string) LIKE CONCAT('%', :search, '%') OR " +
            "b.status LIKE CONCAT('%', :search, '%') OR " +
            "b.reason LIKE CONCAT('%', :search, '%') OR " +
            "CAST(b.totalRecordCount AS string) LIKE CONCAT('%', :search, '%') OR " +
            "CAST(b.successRecordCount AS string) LIKE CONCAT('%', :search, '%') OR " +
            "CAST(b.failureRecordCount AS string) LIKE CONCAT('%', :search, '%') OR " +
            "b.filePath LIKE CONCAT('%', :search, '%') OR " +
            "b.errorFilePath LIKE CONCAT('%', :search, '%') OR " +
            "REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(b.filePath, '%20', ' '), '%2B', '+'), '%2F', '/'), '%3A', ':'), '%3D', '=') LIKE CONCAT('%', :search, '%') OR " +
            "REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(b.errorFilePath, '%20', ' '), '%2B', '+'), '%2F', '/'), '%3A', ':'), '%3D', '=') LIKE CONCAT('%', :search, '%'))")
    Page<BulkUpload> findByUploadTypeAndSearch(UploadType uploadType, String search, Pageable pageable);
    
    /**
     * Counts bulk uploads by upload type with optional search term matching against multiple fields
     * including ID, status, reason, record counts, and file paths (with URL decoding).
     *
     * @param uploadType the upload type to filter by (EMPLOYEE, ITEMS, RESTAURANTS, etc.)
     * @param search     the search term to match against multiple fields
     * @return the count of bulk uploads matching the upload type and search criteria
     */
    @Query("SELECT COUNT(b) FROM BulkUpload b WHERE " +
            "b.uploadType = :uploadType AND (" +
            "CAST(b.id AS string) LIKE CONCAT('%', :search, '%') OR " +
            "b.status LIKE CONCAT('%', :search, '%') OR " +
            "b.reason LIKE CONCAT('%', :search, '%') OR " +
            "CAST(b.totalRecordCount AS string) LIKE CONCAT('%', :search, '%') OR " +
            "CAST(b.successRecordCount AS string) LIKE CONCAT('%', :search, '%') OR " +
            "CAST(b.failureRecordCount AS string) LIKE CONCAT('%', :search, '%') OR " +
            "b.filePath LIKE CONCAT('%', :search, '%') OR " +
            "b.errorFilePath LIKE CONCAT('%', :search, '%') OR " +
            "REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(b.filePath, '%20', ' '), '%2B', '+'), '%2F', '/'), '%3A', ':'), '%3D', '=') LIKE CONCAT('%', :search, '%') OR " +
            "REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(b.errorFilePath, '%20', ' '), '%2B', '+'), '%2F', '/'), '%3A', ':'), '%3D', '=') LIKE CONCAT('%', :search, '%'))")
    long countByUploadTypeAndSearch(UploadType uploadType, String search);
    
    /**
     * Finds bulk uploads by status and upload type with optional search term matching against
     * multiple fields including ID, status, reason, record counts, and file paths (with URL decoding).
     * Supports pagination and sorting via Pageable.
     *
     * @param status     the bulk upload status to filter by (PENDING, IN_PROGRESS, SUCCESS, FAILURE, etc.)
     * @param uploadType the upload type to filter by (EMPLOYEE, ITEMS, RESTAURANTS, etc.)
     * @param search     optional search term to match against multiple fields, null returns all uploads matching status and type
     * @param pageable   pagination and sorting parameters
     * @return paginated list of bulk uploads matching the status, upload type, and search criteria
     */
    @Query("SELECT b FROM BulkUpload b WHERE " +
            "b.status = :status AND b.uploadType = :uploadType AND (" +
            "CAST(b.id AS string) LIKE CONCAT('%', :search, '%') OR " +
            "b.status LIKE CONCAT('%', :search, '%') OR " +
            "b.reason LIKE CONCAT('%', :search, '%') OR " +
            "CAST(b.totalRecordCount AS string) LIKE CONCAT('%', :search, '%') OR " +
            "CAST(b.successRecordCount AS string) LIKE CONCAT('%', :search, '%') OR " +
            "CAST(b.failureRecordCount AS string) LIKE CONCAT('%', :search, '%') OR " +
            "b.filePath LIKE CONCAT('%', :search, '%') OR " +
            "b.errorFilePath LIKE CONCAT('%', :search, '%') OR " +
            "REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(b.filePath, '%20', ' '), '%2B', '+'), '%2F', '/'), '%3A', ':'), '%3D', '=') LIKE CONCAT('%', :search, '%') OR " +
            "REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(b.errorFilePath, '%20', ' '), '%2B', '+'), '%2F', '/'), '%3A', ':'), '%3D', '=') LIKE CONCAT('%', :search, '%'))")
    Page<BulkUpload> findByStatusAndUploadTypeAndSearch(BulkUploadStatus status, UploadType uploadType, String search, Pageable pageable);
    
    /**
     * Counts bulk uploads by status and upload type with optional search term matching against
     * multiple fields including ID, status, reason, record counts, and file paths (with URL decoding).
     *
     * @param status     the bulk upload status to filter by (PENDING, IN_PROGRESS, SUCCESS, FAILURE, etc.)
     * @param uploadType the upload type to filter by (EMPLOYEE, ITEMS, RESTAURANTS, etc.)
     * @param search     the search term to match against multiple fields
     * @return the count of bulk uploads matching the status, upload type, and search criteria
     */
    @Query("SELECT COUNT(b) FROM BulkUpload b WHERE " +
            "b.status = :status AND b.uploadType = :uploadType AND (" +
            "CAST(b.id AS string) LIKE CONCAT('%', :search, '%') OR " +
            "b.status LIKE CONCAT('%', :search, '%') OR " +
            "b.reason LIKE CONCAT('%', :search, '%') OR " +
            "CAST(b.totalRecordCount AS string) LIKE CONCAT('%', :search, '%') OR " +
            "CAST(b.successRecordCount AS string) LIKE CONCAT('%', :search, '%') OR " +
            "CAST(b.failureRecordCount AS string) LIKE CONCAT('%', :search, '%') OR " +
            "b.filePath LIKE CONCAT('%', :search, '%') OR " +
            "b.errorFilePath LIKE CONCAT('%', :search, '%') OR " +
            "REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(b.filePath, '%20', ' '), '%2B', '+'), '%2F', '/'), '%3A', ':'), '%3D', '=') LIKE CONCAT('%', :search, '%') OR " +
            "REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(b.errorFilePath, '%20', ' '), '%2B', '+'), '%2F', '/'), '%3A', ':'), '%3D', '=') LIKE CONCAT('%', :search, '%'))")
    long countByStatusAndUploadTypeAndSearch(BulkUploadStatus status, UploadType uploadType, String search);

} 