package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.CategoryKds;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
public interface CategoryKdsRepository extends JpaRepository<CategoryKds, UUID> {
    
    @Query("SELECT ck FROM CategoryKds ck WHERE ck.kds.id = :kdsId")
    List<CategoryKds> findByKdsId(@Param("kdsId") UUID kdsId);
    
    /**
     * Batch fetch CategoryKds rows for a list of KDS ids with all relations needed by callers eagerly loaded.
     * <p>
     * Callers (KDS dashboard listing + KDS notification routing) read {@code ck.kds.id},
     * {@code ck.menuCategoryMapping}, {@code ck.menuCategoryMapping.category}, and
     * {@code ck.menuCategoryMapping.category.parentCategory}. When the result is used outside the original
     * transaction (e.g. KDS WebSocket dispatch runs async after the HTTP transaction ends), proxy-only
     * relations cause {@code LazyInitializationException}s that are caught and silently drop rows,
     * which previously caused non-default KDS stations to be excluded from item-pushed notifications.
     */
    @Query("SELECT DISTINCT ck FROM CategoryKds ck " +
           "LEFT JOIN FETCH ck.kds k " +
           "LEFT JOIN FETCH ck.menuCategoryMapping mcm " +
           "LEFT JOIN FETCH mcm.category c " +
           "LEFT JOIN FETCH c.parentCategory pc " +
           "WHERE k.id IN :kdsIds")
    List<CategoryKds> findAllByKdsIdIn(@Param("kdsIds") List<UUID> kdsIds);
    
    @Query("SELECT COUNT(ck) > 0 FROM CategoryKds ck WHERE ck.menuCategoryMapping.id = :menuCategoryMappingId AND ck.kds.id = :kdsId")
    boolean existsByMenuCategoryMappingIdAndKdsId(@Param("menuCategoryMappingId") UUID menuCategoryMappingId, @Param("kdsId") UUID kdsId);
    
    @Query("SELECT COUNT(ck) > 0 FROM CategoryKds ck WHERE ck.menuCategoryMapping.id = :menuCategoryMappingId")
    boolean existsByMenuCategoryMappingId(@Param("menuCategoryMappingId") UUID menuCategoryMappingId);

    @Modifying
    @Transactional
    @Query("DELETE FROM CategoryKds ck WHERE ck.kds.id = :kdsId")
    void deleteByKdsId(@Param("kdsId") UUID kdsId);
    
    /**
     * Get menu_category_mapping_id directly from category_kds table to avoid lazy loading issues
     * @param kdsId The KDS ID
     * @return List of menu_category_mapping_id UUIDs
     */
    @Query(value = "SELECT menu_category_mapping_id FROM category_kds WHERE kds_id = :kdsId", nativeQuery = true)
    List<UUID> findMenuCategoryMappingIdsByKdsId(@Param("kdsId") UUID kdsId);
}

