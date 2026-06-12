package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.PriceOverride;
import com.gulfnet.shared_library.enums.OverrideLevel;
import com.gulfnet.shared_library.enums.PriceOverrideStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PriceOverrideRepository extends JpaRepository<PriceOverride, UUID>, JpaSpecificationExecutor<PriceOverride> {
    
    Page<PriceOverride> findByStatusAndIsDeletedFalse(PriceOverrideStatus status, Pageable pageable);
    
    List<PriceOverride> findByOverrideLevelAndStatusAndIsDeletedFalse(OverrideLevel overrideLevel, PriceOverrideStatus status);
    
    Optional<PriceOverride> findByIdAndIsDeletedFalse(UUID id);
    
    List<PriceOverride> findAllByIsDeletedFalse();
}

