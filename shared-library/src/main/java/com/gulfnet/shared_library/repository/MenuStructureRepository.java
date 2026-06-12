package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.MenuStructure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.Optional;
import java.util.UUID;

public interface MenuStructureRepository extends JpaRepository<MenuStructure, UUID> , JpaSpecificationExecutor<MenuStructure>{
    @Query("SELECT m FROM MenuStructure m WHERE m.id = :id AND m.isDeleted = false")
    Optional<MenuStructure> findByIdAndIsDeletedFalse(@Param("id") UUID id);
}