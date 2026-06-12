package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.Kds;
import com.gulfnet.shared_library.enums.EntityStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface KdsRepository extends JpaRepository<Kds, UUID>, JpaSpecificationExecutor<Kds> {

    Page<Kds> findByStatusAndIsDeletedFalse(EntityStatus status, Pageable pageable);

    Page<Kds> findByIsDeletedFalse(Pageable pageable);

    List<Kds> findByRestaurantIdAndIsDefaultTrueAndIsDeletedFalse(UUID restaurantId);

    List<Kds> findByRestaurantIdAndIsDefaultFalseAndIsDeletedFalse(UUID restaurantId);

    boolean existsByDeviceCode(String deviceCode);

    java.util.Optional<Kds> findByDeviceCode(String deviceCode);
}

