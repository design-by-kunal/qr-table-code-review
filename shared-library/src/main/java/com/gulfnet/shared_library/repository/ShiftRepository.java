package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.Shift;
import com.gulfnet.shared_library.enums.EntityStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ShiftRepository extends JpaRepository<Shift, UUID>, JpaSpecificationExecutor<Shift> {
    
    // Find shifts by status
    List<Shift> findByStatus(EntityStatus status);
    
    // Find shifts by status with pagination
    Page<Shift> findByStatus(EntityStatus status, Pageable pageable);
    
    // Find shifts by name search (searches in translations)
    @Query("SELECT DISTINCT s FROM Shift s " +
           "JOIN s.translations t " +
           "WHERE LOWER(t.name) LIKE LOWER(CONCAT('%', :search, '%'))")
    List<Shift> findShiftsByNameContaining(@Param("search") String search);
    
    // Find shifts by name search and status
    @Query("SELECT DISTINCT s FROM Shift s " +
           "JOIN s.translations t " +
           "WHERE LOWER(t.name) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "AND (:status IS NULL OR s.status = :status)")
    List<Shift> findShiftsByNameContainingAndStatus(@Param("search") String search, @Param("status") EntityStatus status);
    
    // Find shifts by status (for filtering)
    @Query("SELECT s FROM Shift s WHERE (:status IS NULL OR s.status = :status)")
    Page<Shift> findByStatusWithPagination(@Param("status") EntityStatus status, Pageable pageable);
} 