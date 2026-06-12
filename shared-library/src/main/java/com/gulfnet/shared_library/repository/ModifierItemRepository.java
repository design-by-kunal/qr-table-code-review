package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.ModifierItem;
import com.gulfnet.shared_library.enums.EntityStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ModifierItemRepository extends JpaRepository<ModifierItem, UUID> {

    Optional<ModifierItem> findByIdAndIsDeletedFalse(UUID id);
    List<ModifierItem> findByModifierGroup_IdAndIsDeletedFalse(UUID modifierGroupId);
    List<ModifierItem> findByModifierGroup_IdInAndIsDeletedFalse(List<UUID> modifierGroupIds);
    boolean existsByIdAndIsDeletedFalse(UUID id);

    @Query("SELECT CASE WHEN COUNT(mi) > 0 THEN true ELSE false END FROM ModifierItem mi " +
           "WHERE mi.modifierCode IS NOT NULL AND LOWER(TRIM(mi.modifierCode)) = LOWER(TRIM(:code)) " +
           "AND (mi.isDeleted IS NULL OR mi.isDeleted = false)")
    boolean existsActiveModifierItemByModifierCode(@Param("code") String code);

    @Query("SELECT CASE WHEN COUNT(mi) > 0 THEN true ELSE false END FROM ModifierItem mi " +
           "WHERE mi.modifierCode IS NOT NULL AND LOWER(TRIM(mi.modifierCode)) = LOWER(TRIM(:code)) " +
           "AND (mi.isDeleted IS NULL OR mi.isDeleted = false) AND mi.id <> :modifierItemId")
    boolean existsActiveModifierItemByModifierCodeExcludingId(@Param("code") String code, @Param("modifierItemId") UUID modifierItemId);

    // Check if sort order already exists for a modifier group
    @Query("SELECT COUNT(mi) > 0 FROM ModifierItem mi WHERE mi.sortOrder = :sortOrder AND mi.modifierGroup.id = :modifierGroupId AND mi.isDeleted = false")
    boolean existsBySortOrderAndModifierGroupIdAndIsDeletedFalse(@Param("sortOrder") Integer sortOrder, @Param("modifierGroupId") UUID modifierGroupId);
    
    // Check if sort order already exists for a modifier group (excluding current item for updates)
    @Query("SELECT COUNT(mi) > 0 FROM ModifierItem mi WHERE mi.sortOrder = :sortOrder AND mi.modifierGroup.id = :modifierGroupId AND mi.isDeleted = false AND mi.id != :modifierItemId")
    boolean existsBySortOrderAndModifierGroupIdAndIsDeletedFalseAndIdNot(@Param("sortOrder") Integer sortOrder, @Param("modifierGroupId") UUID modifierGroupId, @Param("modifierItemId") UUID modifierItemId);
    
    @Query("SELECT mi FROM ModifierItem mi WHERE mi.isDeleted = false AND " +
           "mi.modifierGroup.id = :modifierGroupId AND " +
           "(:status IS NULL OR mi.status = :status) AND " +
           "(:search IS NULL OR EXISTS (SELECT mit FROM ModifierItemTranslation mit WHERE mit.modifierItem = mi AND LOWER(mit.name) LIKE LOWER(CONCAT('%', :search, '%'))))")
    List<ModifierItem> findByModifierGroupIdAndStatusAndSearch(
        @Param("modifierGroupId") UUID modifierGroupId,
        @Param("status") EntityStatus status,
        @Param("search") String search
    );
}