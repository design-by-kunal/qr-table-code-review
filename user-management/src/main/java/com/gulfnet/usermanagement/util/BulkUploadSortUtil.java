package com.gulfnet.usermanagement.util;

import com.gulfnet.shared_library.entity.BulkUpload;
import com.gulfnet.shared_library.enums.BulkUploadStatus;
import com.gulfnet.shared_library.enums.UploadType;
import com.gulfnet.shared_library.exception.BadRequestException;
import com.gulfnet.shared_library.model.response.dto.BulkUploadListWithPresignedUrlsResponse;
import com.gulfnet.shared_library.model.response.dto.BulkUploadWithPresignedUrls;
import com.gulfnet.shared_library.model.response.dto.PaginationMetaData;
import com.gulfnet.shared_library.model.response.dto.ResponseDto;
import com.gulfnet.shared_library.repository.BulkUploadRepository;
import com.gulfnet.shared_library.config.AWSService;
import com.gulfnet.usermanagement.util.MessageUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class BulkUploadSortUtil {

    private BulkUploadSortUtil() {
        // Utility class - prevent instantiation
    }

    /**
     * Sort field name constants for bulk upload sorting.
     * Following industry standard practice of using constants for field names.
     */
    private static class SortFields {
        // Lowercase sort field names (for input comparison)
        static final String CREATED_AT = "createdat";
        static final String UPDATED_AT = "updatedat";
        static final String STATUS = "status";
        static final String TOTAL_RECORD_COUNT = "totalrecordcount";
        static final String SUCCESS_RECORD_COUNT = "successrecordcount";
        static final String FAILURE_RECORD_COUNT = "failurerecordcount";
        static final String FILENAME = "filename";
        static final String ORIGINAL_FILENAME = "originalfilename";
        static final String ERROR_FILENAME = "errorfilename";
        static final String NAME = "name";
        
        // Entity field names (camelCase for database queries)
        static final String ENTITY_CREATED_AT = "createdAt";
        static final String ENTITY_UPDATED_AT = "updatedAt";
        static final String ENTITY_STATUS = "status";
        static final String ENTITY_TOTAL_RECORD_COUNT = "totalRecordCount";
        static final String ENTITY_SUCCESS_RECORD_COUNT = "successRecordCount";
        static final String ENTITY_FAILURE_RECORD_COUNT = "failureRecordCount";
        
        private SortFields() {
            // Utility class - prevent instantiation
        }
    }

    /**
     * Get all bulk uploads with sorting, filtering, and pagination
     * @param bulkUploadRepository Repository for bulk upload operations
     * @param awsService AWS service for presigned URLs
     * @param messageUtil Message utility for localization
     * @param page Page number (1-based)
     * @param size Page size
     * @param status Filter by status
     * @param search Search term
     * @param sortBy Sort field(s)
     * @param direction Sort direction
     * @return Response with bulk upload list
     */
    public static ResponseEntity<ResponseDto<BulkUploadListWithPresignedUrlsResponse>> getAllBulkUploads(
            BulkUploadRepository bulkUploadRepository,
            AWSService awsService,
            MessageUtil messageUtil,
            int page, int size, String status, String search, String sortBy, Sort.Direction direction) {
        
        log.info("Getting bulk uploads - page: {}, size: {}, status: {}, search: {}", page, size, status, search);

        Page<BulkUpload> bulkUploadList = fetchBulkUploadsWithSorting(
                bulkUploadRepository, status, search, sortBy, direction, page, size);
        
        List<BulkUpload> bulkUploads = addPresignedUrlsToBulkUploads(bulkUploadList.getContent(), awsService);
        List<BulkUploadWithPresignedUrls> bulkUploadWithPresignedUrlsList = buildBulkUploadDtoList(bulkUploads);
        
        return buildBulkUploadResponse(bulkUploadWithPresignedUrlsList, bulkUploadList, 
                page, size, messageUtil);
    }

    /**
     * Fetches bulk uploads with appropriate sorting strategy
     * @param bulkUploadRepository Repository for bulk upload operations
     * @param status Filter by status
     * @param search Search term
     * @param sortBy Sort field(s)
     * @param direction Sort direction
     * @param page Page number (1-based)
     * @param size Page size
     * @return Page of bulk uploads
     */
    private static Page<BulkUpload> fetchBulkUploadsWithSorting(
            BulkUploadRepository bulkUploadRepository,
            String status,
            String search,
            String sortBy,
            Sort.Direction direction,
            int page,
            int size) {
        
        int zeroBasedPage = page > 0 ? page - 1 : 0;
        
        if (requiresCustomSorting(sortBy)) {
            Pageable allPageRequest = PageRequest.of(0, Integer.MAX_VALUE);
            Page<BulkUpload> allResults = getFilteredBulkUploads(bulkUploadRepository, status, search, null, allPageRequest);
            return applyCustomSortingWithPagination(allResults, sortBy, direction, zeroBasedPage, size);
        }
        
        Pageable pageRequest = createPageable(zeroBasedPage, size, sortBy, direction);
        return getFilteredBulkUploads(bulkUploadRepository, status, search, null, pageRequest);
    }

    /**
     * Adds presigned URLs to bulk upload file paths
     * @param bulkUploads List of bulk uploads
     * @param awsService AWS service for presigned URLs
     * @return List of bulk uploads with presigned URLs
     */
    private static List<BulkUpload> addPresignedUrlsToBulkUploads(List<BulkUpload> bulkUploads, AWSService awsService) {
        return bulkUploads.stream()
                .map(b -> {
                    b.setFilePath(b.getFilePath() != null ? awsService.getPreSignedUrl(b.getFilePath()) : null);
                    b.setErrorFilePath(b.getErrorFilePath() != null ? awsService.getPreSignedUrl(b.getErrorFilePath()) : null);
                    return b;
                })
                .toList();
    }

    /**
     * Fills count, status, reason, and audit timestamp fields on a {@link BulkUploadWithPresignedUrls} builder.
     */
    public static BulkUploadWithPresignedUrls.BulkUploadWithPresignedUrlsBuilder populateBulkUploadWithPresignedUrlsMetadata(
            BulkUploadWithPresignedUrls.BulkUploadWithPresignedUrlsBuilder builder,
            BulkUpload bulkUpload) {
        return builder
                .totalRecordCount(bulkUpload.getTotalRecordCount())
                .successRecordCount(bulkUpload.getSuccessRecordCount())
                .failureRecordCount(bulkUpload.getFailureRecordCount())
                .status(bulkUpload.getStatus().name())
                .reason(bulkUpload.getReason())
                .createdAt(bulkUpload.getCreatedAt() != null ? bulkUpload.getCreatedAt().toLocalDateTime() : null)
                .createdBy(bulkUpload.getCreatedBy())
                .updatedAt(bulkUpload.getUpdatedAt() != null ? bulkUpload.getUpdatedAt().toLocalDateTime() : null)
                .updatedBy(bulkUpload.getUpdatedBy());
    }

    /**
     * Builds DTO list from bulk upload entities
     * @param bulkUploads List of bulk uploads with presigned URLs
     * @return List of bulk upload DTOs
     */
    private static List<BulkUploadWithPresignedUrls> buildBulkUploadDtoList(List<BulkUpload> bulkUploads) {
        return bulkUploads.stream()
                .map(bulkUpload -> populateBulkUploadWithPresignedUrlsMetadata(
                        BulkUploadWithPresignedUrls.builder()
                                .id(bulkUpload.getId())
                                .originalFilePresignedUrl(bulkUpload.getFilePath())
                                .originalFileName(extractFileNameFromPath(bulkUpload.getFilePath()))
                                .errorFilePresignedUrl(bulkUpload.getErrorFilePath())
                                .errorFileName(extractFileNameFromPath(bulkUpload.getErrorFilePath())),
                        bulkUpload)
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Builds the final response with pagination metadata
     * @param bulkUploadWithPresignedUrlsList List of bulk upload DTOs
     * @param bulkUploadList Page of bulk uploads for metadata
     * @param page Page number (1-based)
     * @param size Page size
     * @param messageUtil Message utility for localization
     * @return ResponseEntity with response DTO
     */
    private static ResponseEntity<ResponseDto<BulkUploadListWithPresignedUrlsResponse>> buildBulkUploadResponse(
            List<BulkUploadWithPresignedUrls> bulkUploadWithPresignedUrlsList,
            Page<BulkUpload> bulkUploadList,
            int page,
            int size,
            MessageUtil messageUtil) {
        
        PaginationMetaData metaData = PaginationMetaData.builder()
                .page(page)
                .size(size)
                .totalPages(bulkUploadList.getTotalPages())
                .totalRecords(bulkUploadList.getTotalElements())
                .build();

        BulkUploadListWithPresignedUrlsResponse response = BulkUploadListWithPresignedUrlsResponse.builder()
                .bulkUploads(bulkUploadWithPresignedUrlsList)
                .build();

        return ResponseEntity.ok(ResponseDto.<BulkUploadListWithPresignedUrlsResponse>builder()
                .data(response)
                .metaData(metaData)
                .message(messageUtil.getMessage("bulk.upload.list.success", null, LocaleContextHolder.getLocale()))
                .build());
    }

    /**
     * Creates a Sort.Order for a given field
     * @param field The sort field name
     * @param direction Sort direction
     * @return Sort.Order for the field
     */
    private static Sort.Order createSortOrder(String field, Sort.Direction direction) {
        String trimmedField = field.trim().toLowerCase();
        String entityField;
        
        switch (trimmedField) {
            case SortFields.CREATED_AT:
                entityField = SortFields.ENTITY_CREATED_AT;
                break;
            case SortFields.UPDATED_AT:
                entityField = SortFields.ENTITY_UPDATED_AT;
                break;
            case SortFields.STATUS:
                entityField = SortFields.ENTITY_STATUS;
                break;
            case SortFields.TOTAL_RECORD_COUNT:
                entityField = SortFields.ENTITY_TOTAL_RECORD_COUNT;
                break;
            case SortFields.SUCCESS_RECORD_COUNT:
                entityField = SortFields.ENTITY_SUCCESS_RECORD_COUNT;
                break;
            case SortFields.FAILURE_RECORD_COUNT:
                entityField = SortFields.ENTITY_FAILURE_RECORD_COUNT;
                break;
            case SortFields.FILENAME:
            case SortFields.ORIGINAL_FILENAME:
            case SortFields.NAME:
                // For fileName/name sorting, we'll handle it in the service layer
                entityField = SortFields.ENTITY_UPDATED_AT; // Fallback to updatedAt
                break;
            default:
                // Default to createdAt if field is not recognized
                entityField = SortFields.ENTITY_CREATED_AT;
                break;
        }
        
        return new Sort.Order(direction, entityField);
    }

    /**
     * Creates a Pageable object with sorting support for bulk uploads
     * @param page Zero-based page number
     * @param size Page size
     * @param sortBy Sort field(s) - can be comma-separated for multiple fields
     * @param direction Sort direction
     * @return Pageable object with sorting configuration
     */
    public static Pageable createPageable(int page, int size, String sortBy, Sort.Direction direction) {
        Sort sort = buildSort(sortBy, direction);
        return PageRequest.of(page, size, sort);
    }
    
    /**
     * Builds a Sort object from sortBy string and direction
     * @param sortBy Sort field(s) - can be comma-separated for multiple fields
     * @param direction Sort direction
     * @return Sort object
     */
    private static Sort buildSort(String sortBy, Sort.Direction direction) {
        if (sortBy != null && sortBy.contains(",")) {
            return buildMultiFieldSort(sortBy, direction);
        } else {
            return buildSingleFieldSort(sortBy, direction);
        }
    }
    
    /**
     * Builds Sort for multiple comma-separated fields
     */
    private static Sort buildMultiFieldSort(String sortBy, Sort.Direction direction) {
            String[] sortFields = sortBy.split(",");
            List<Sort.Order> orders = new ArrayList<>();
            
            for (String field : sortFields) {
            orders.add(createSortOrder(field, direction));
        }
        
        return Sort.by(orders);
    }
    
    /**
     * Builds Sort for a single field
     */
    private static Sort buildSingleFieldSort(String sortBy, Sort.Direction direction) {
            String field = sortBy != null ? sortBy.toLowerCase() : SortFields.CREATED_AT;
        Sort.Order order = createSortOrder(field, direction);
        return Sort.by(order);
    }

    /**
     * Get filtered bulk uploads based on status, search criteria, and upload type
     * @param bulkUploadRepository Repository for bulk upload operations
     * @param status Filter by status
     * @param search Search term
     * @param uploadType Filter by upload type
     * @param pageRequest Pageable request
     * @return Filtered page of bulk uploads
     */
    public static Page<BulkUpload> getFilteredBulkUploads(
            BulkUploadRepository bulkUploadRepository, 
            String status, 
            String search, 
            UploadType uploadType,
            Pageable pageRequest) {
        
        // Preprocess search term to handle filename searches
        String processedSearch = preprocessSearchTerm(search);
        
        if (status != null && !status.trim().isEmpty()) {
            try {
                BulkUploadStatus statusEnum = BulkUploadStatus.valueOf(status.toUpperCase());
                if (uploadType != null) {
                    // Filter by both status and upload type
                    if (search != null && !search.trim().isEmpty()) {
                        return bulkUploadRepository.findByStatusAndUploadTypeAndSearch(statusEnum, uploadType, processedSearch, pageRequest);
                    } else {
                        return bulkUploadRepository.findByStatusAndUploadType(statusEnum, uploadType, pageRequest);
                    }
                } else {
                    // Filter by status only
                    if (search != null && !search.trim().isEmpty()) {
                        // Get all results for search to apply status filter
                        Page<BulkUpload> allResults = bulkUploadRepository.findAll(processedSearch, pageRequest);
                        List<BulkUpload> filteredContent = allResults.getContent().stream()
                                .filter(bulkUpload -> bulkUpload.getStatus() == statusEnum)
                                .toList();
                        
                        // Calculate total count of filtered results across all pages
                        long totalFilteredCount = allResults.getTotalElements() > 0 ? 
                            bulkUploadRepository.countBySearchAndStatus(processedSearch, statusEnum) : 0;
                        
                        return new PageImpl<>(filteredContent, pageRequest, totalFilteredCount);
                    } else {
                        return bulkUploadRepository.findByStatus(statusEnum, pageRequest);
                    }
                }
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Invalid status: " + status);
            }
        } else if (uploadType != null) {
            // Filter by upload type only
            if (search != null && !search.trim().isEmpty()) {
                return bulkUploadRepository.findByUploadTypeAndSearch(uploadType, processedSearch, pageRequest);
            } else {
                return bulkUploadRepository.findByUploadType(uploadType, pageRequest);
            }
        } else if (search != null && !search.trim().isEmpty()) {
            return bulkUploadRepository.findAll(processedSearch, pageRequest);
        } else {
            return bulkUploadRepository.findAll(pageRequest);
        }
    }

    /**
     * Checks if the sortBy parameter requires custom sorting (filename-based)
     * @param sortBy Sort field parameter
     * @return true if custom sorting is required
     */
    public static boolean requiresCustomSorting(String sortBy) {
        if (sortBy == null) return false;
        
        String lowerSortBy = sortBy.toLowerCase();
        return SortFields.ORIGINAL_FILENAME.equals(lowerSortBy) || 
               SortFields.ERROR_FILENAME.equals(lowerSortBy) ||
               SortFields.NAME.equals(lowerSortBy) ||
               lowerSortBy.contains(SortFields.FILENAME) || 
               lowerSortBy.contains(SortFields.ORIGINAL_FILENAME);
    }

    /**
     * Applies custom sorting for filename-based sorting with pagination
     * @param allResults All bulk upload results
     * @param sortBy Sort field
     * @param direction Sort direction
     * @param zeroBasedPage Zero-based page number
     * @param size Page size
     * @return Paginated and sorted results
     */
    public static Page<BulkUpload> applyCustomSortingWithPagination(
            Page<BulkUpload> allResults, 
            String sortBy, 
            Sort.Direction direction, 
            int zeroBasedPage, 
            int size) {
        
        // Sort by filename and updatedAt
        List<BulkUpload> sortedList = allResults.getContent().stream()
                .sorted(createCustomComparator(sortBy, direction))
                .collect(Collectors.toList());
        
        // Apply pagination manually
        int start = zeroBasedPage * size;
        int end = Math.min(start + size, sortedList.size());
        List<BulkUpload> paginatedList = start < sortedList.size() ? 
                sortedList.subList(start, end) : new ArrayList<>();
        
        return new PageImpl<>(paginatedList, PageRequest.of(zeroBasedPage, size), allResults.getTotalElements());
    }

    /**
     * Creates a custom comparator for bulk upload sorting
     * @param sortBy Sort field
     * @param direction Sort direction
     * @return Comparator for bulk upload sorting
     */
    public static Comparator<BulkUpload> createCustomComparator(String sortBy, Sort.Direction direction) {
        return (a, b) -> {
            // Sort by filename as primary criteria
            String fileNameA = extractFileNameFromPath(a.getFilePath());
            String fileNameB = extractFileNameFromPath(b.getFilePath());
            
            String lowerSortBy = sortBy != null ? sortBy.toLowerCase() : "";
            if (SortFields.ERROR_FILENAME.equals(lowerSortBy)) {
                fileNameA = extractFileNameFromPath(a.getErrorFilePath());
                fileNameB = extractFileNameFromPath(b.getErrorFilePath());
            } else if (SortFields.NAME.equals(lowerSortBy) || SortFields.ORIGINAL_FILENAME.equals(lowerSortBy) || SortFields.FILENAME.equals(lowerSortBy)) {
                // For name/originalFileName sorting, use the original file path
                fileNameA = extractFileNameFromPath(a.getFilePath());
                fileNameB = extractFileNameFromPath(b.getFilePath());
            }
            
            // Handle null filenames
            if (fileNameA == null) fileNameA = "";
            if (fileNameB == null) fileNameB = "";
            
            // Case-insensitive filename comparison
            int fileNameComparison = fileNameA.toLowerCase().compareTo(fileNameB.toLowerCase());
            if (fileNameComparison != 0) {
                return direction == Sort.Direction.DESC ? -fileNameComparison : fileNameComparison;
            }
            
            // If filenames are the same, sort by updatedAt as secondary criteria
            int updatedAtComparison = b.getUpdatedAt().compareTo(a.getUpdatedAt());
            return direction == Sort.Direction.DESC ? updatedAtComparison : -updatedAtComparison;
        };
    }

    /**
     * Extracts filename from a file path
     * @param filePath The file path
     * @return The filename or empty string if path is null
     */
    public static String extractFileNameFromPath(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            return "";
        }
        
        // Handle S3 URLs and local paths
        String fileName = filePath;
        
        // Remove query parameters if present (for S3 URLs)
        if (fileName.contains("?")) {
            fileName = fileName.substring(0, fileName.indexOf("?"));
        }
        
        // Extract filename from path
        int lastSlashIndex = fileName.lastIndexOf('/');
        if (lastSlashIndex != -1 && lastSlashIndex < fileName.length() - 1) {
            fileName = fileName.substring(lastSlashIndex + 1);
        }
        
        // Remove any remaining path separators
        fileName = fileName.replace("\\", "/");
        lastSlashIndex = fileName.lastIndexOf('/');
        if (lastSlashIndex != -1 && lastSlashIndex < fileName.length() - 1) {
            fileName = fileName.substring(lastSlashIndex + 1);
        }
        
        return fileName;
    }

    /**
     * Preprocesses search term to handle filename searches with timestamps
     * @param search The original search term
     * @return Processed search term that can match filenames with timestamps
     */
    private static String preprocessSearchTerm(String search) {
        if (search == null || search.trim().isEmpty()) {
            return search;
        }
        
        // If the search term looks like a filename (contains .csv, .xlsx, etc.)
        // create additional search patterns to match filenames with timestamps
        if (search.matches(".*\\.(csv|xlsx|xls|txt)$")) {
            // Extract the base name and extension
            String baseName = search.substring(0, search.lastIndexOf('.'));
            String extension = search.substring(search.lastIndexOf('.'));
            
            // Create a search pattern that matches the base name with any timestamp
            // This will match patterns like "restaurant bulkupload_20250902_130401.csv"
            return baseName + "%" + extension;
        }
        
        return search;
    }

    /**
     * Validates if a sort field is supported for custom sorting
     * @param sortBy Sort field to validate
     * @return true if the field is supported for custom sorting
     */
    public static boolean isSupportedCustomSortField(String sortBy) {
        if (sortBy == null) return false;
        
        String field = sortBy.toLowerCase();
        return field.equals(SortFields.FILENAME) ||
               field.equals(SortFields.ORIGINAL_FILENAME) ||
               field.equals(SortFields.ERROR_FILENAME) ||
               field.equals(SortFields.NAME);
    }
} 