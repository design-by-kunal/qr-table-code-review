package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.ModifierGroup;
import com.gulfnet.shared_library.enums.EntityStatus;
import com.gulfnet.shared_library.enums.ModifierType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Optional;
import java.util.UUID;
import java.util.List;

public interface ModifierGroupRepository extends JpaRepository<ModifierGroup, UUID>, JpaSpecificationExecutor<ModifierGroup> {

    Optional<ModifierGroup> findByIdAndIsDeletedFalse(UUID id);

    Page<ModifierGroup> findByStatusAndModifierTypeAndAllowMultiSelectAndIsDeletedFalse(EntityStatus status, ModifierType modifierType, Boolean allowMultiSelect, Pageable pageable);

    Page<ModifierGroup> findByStatusAndModifierTypeAndIsDeletedFalse(EntityStatus status, ModifierType modifierType, Pageable pageable);

    Page<ModifierGroup> findByStatusAndAllowMultiSelectAndIsDeletedFalse(EntityStatus status, Boolean allowMultiSelect, Pageable pageable);

    Page<ModifierGroup> findByModifierTypeAndAllowMultiSelectAndIsDeletedFalse(ModifierType modifierType, Boolean allowMultiSelect, Pageable pageable);

    Page<ModifierGroup> findByStatusAndIsDeletedFalse(EntityStatus status, Pageable pageable);

    Page<ModifierGroup> findByModifierTypeAndIsDeletedFalse(ModifierType modifierType, Pageable pageable);

    Page<ModifierGroup> findByAllowMultiSelectAndIsDeletedFalse(Boolean allowMultiSelect, Pageable pageable);

    Page<ModifierGroup> findByIsDeletedFalse(Pageable pageable);

    List<ModifierGroup> findByIsDeletedFalse();

    // Non-paginated methods for optional pagination
    List<ModifierGroup> findByStatusAndModifierTypeAndAllowMultiSelectAndIsDeletedFalse(EntityStatus status, ModifierType modifierType, Boolean allowMultiSelect);
    
    List<ModifierGroup> findByStatusAndModifierTypeAndIsDeletedFalse(EntityStatus status, ModifierType modifierType);
    
    List<ModifierGroup> findByStatusAndAllowMultiSelectAndIsDeletedFalse(EntityStatus status, Boolean allowMultiSelect);
    
    List<ModifierGroup> findByModifierTypeAndAllowMultiSelectAndIsDeletedFalse(ModifierType modifierType, Boolean allowMultiSelect);
    
    List<ModifierGroup> findByStatusAndIsDeletedFalse(EntityStatus status);
    
    List<ModifierGroup> findByModifierTypeAndIsDeletedFalse(ModifierType modifierType);
    
    List<ModifierGroup> findByAllowMultiSelectAndIsDeletedFalse(Boolean allowMultiSelect);

    @Query("SELECT DISTINCT mg FROM ModifierGroup mg " +
           "JOIN mg.translations t " +
           "WHERE mg.isDeleted = false " +
           "AND LOWER(t.name) LIKE LOWER(CONCAT('%', :search, '%'))")
    Page<ModifierGroup> searchByNameAndIsDeletedFalse(@Param("search") String search, Pageable pageable);

}

