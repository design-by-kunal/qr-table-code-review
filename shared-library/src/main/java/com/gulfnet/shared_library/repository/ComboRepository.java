package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.Combo;
import com.gulfnet.shared_library.entity.ComboGroup;
import com.gulfnet.shared_library.entity.ComboItemMapping;
import com.gulfnet.shared_library.entity.ComboGroupTranslation;
import com.gulfnet.shared_library.entity.ComboTranslation;
import com.gulfnet.shared_library.enums.ComboType;
import com.gulfnet.shared_library.enums.EntityStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ComboRepository extends JpaRepository<Combo, UUID> {
    
    Page<Combo> findByMenuIdAndIsDeletedFalse(UUID menuId, Pageable pageable);
    
    List<Combo> findByMenuIdAndStatusAndIsDeletedFalse(UUID menuId, EntityStatus status);
    
    List<Combo> findByTypeAndStatusAndIsDeletedFalse(ComboType type, EntityStatus status);
    
    @Query("SELECT c FROM Combo c WHERE c.menu.id = :menuId AND c.status = :status " +
           "AND c.isDeleted = false AND " +
           "(:currentTime BETWEEN c.validFrom AND c.validTo OR c.validFrom IS NULL OR c.validTo IS NULL)")
    List<Combo> findActiveCombosByMenuAndTime(@Param("menuId") UUID menuId, 
                                            @Param("status") EntityStatus status,
                                            @Param("currentTime") OffsetDateTime currentTime);
    
    @Query("SELECT c FROM Combo c WHERE c.menu.id = :menuId AND c.status = :status " +
           "AND c.isDeleted = false AND " +
           "(:dayOfWeek MEMBER OF c.daysOfWeek OR 'ALL_DAYS' MEMBER OF c.daysOfWeek)")
    List<Combo> findActiveCombosByMenuAndDay(@Param("menuId") UUID menuId, 
                                           @Param("status") EntityStatus status,
                                           @Param("dayOfWeek") String dayOfWeek);
    
    // Separate queries to avoid multiple bag fetch
    @Query("SELECT DISTINCT c FROM Combo c " +
           "LEFT JOIN FETCH c.translations ct " +
           "WHERE c.comboId = :comboId")
    Optional<Combo> findByIdWithTranslations(@Param("comboId") UUID comboId);

    // Fetch combo with daysOfWeek for validation
    @Query("SELECT DISTINCT c FROM Combo c " +
           "LEFT JOIN FETCH c.daysOfWeek " +
           "WHERE c.comboId = :comboId")
    Optional<Combo> findByIdWithDaysOfWeek(@Param("comboId") UUID comboId);

    @Query("SELECT cg FROM ComboGroup cg WHERE cg.combo.comboId = :comboId")
    List<ComboGroup> findComboGroupsByComboId(@Param("comboId") UUID comboId);
    @Query("SELECT cim FROM ComboItemMapping cim " +
           "JOIN FETCH cim.categoryItemMapping catItemMapping " +
           "JOIN FETCH catItemMapping.item item " +
           "WHERE cim.comboGroup.combo.comboId = :comboId")
    List<ComboItemMapping> findComboItemMappingsWithItems(@Param("comboId") UUID comboId);

    @Query("SELECT cgt FROM ComboGroupTranslation cgt " +
           "WHERE cgt.comboGroup.combo.comboId = :comboId")
    List<ComboGroupTranslation> findComboGroupTranslationsByComboId(@Param("comboId") UUID comboId);

    @Query("SELECT ct FROM ComboTranslation ct WHERE ct.combo.comboId = :comboId")
    List<ComboTranslation> findComboTranslationsByComboId(@Param("comboId") UUID comboId);

    /**
     * Finds all non-deleted combos with optional filtering by status, type, and search term.
     * The search term matches against combo name or description in translations.
     *
     * @param status optional status filter (ACTIVE, INACTIVE, etc.)
     * @param type   optional combo type filter
     * @param search optional search term to match against combo name or description
     * @param locale optional locale parameter (currently not used in query but kept for API consistency)
     * @return list of combos matching the filters
     */
    @Query(value = "SELECT DISTINCT c.* FROM combo c " +
        "LEFT JOIN combo_translation ct ON c.combo_id = ct.combo_id " +
        "WHERE c.is_deleted = false " +
        "AND (:status IS NULL OR c.status = :status) " +
        "AND (:type IS NULL OR c.type = :type) " +
        "AND (:search IS NULL OR " +
        "     ct.name ILIKE '%' || CAST(:search AS text) || '%' OR " +
        "     ct.description ILIKE '%' || CAST(:search AS text) || '%') " +
        "AND ct.language_code = :locale",
        nativeQuery = true)
    List<Combo> findAllCombosWithFilters(
        @Param("status") String status,
        @Param("type") String type,
        @Param("search") String search,
        @Param("locale") String locale);

    /**
     * Finds all non-deleted combos for a specific menu with optional filtering by status,
     * type, and search term. The search term matches against combo name or description
     * in translations.
     *
     * @param menuId  the UUID of the menu to filter combos by
     * @param status  optional status filter (ACTIVE, INACTIVE, etc.)
     * @param type    optional combo type filter
     * @param search  optional search term to match against combo name or description
     * @param locale  optional locale parameter (currently not used in query but kept for API consistency)
     * @return list of combos for the specified menu matching the filters
     */
    @Query(value = "SELECT DISTINCT c.* FROM combo c " +
        "LEFT JOIN combo_translation ct ON c.combo_id = ct.combo_id " +
        "WHERE c.is_deleted = false " +
        "AND c.menu_id = :menuId " +
        "AND (:status IS NULL OR c.status = :status) " +
        "AND (:type IS NULL OR c.type = :type) " +
        "AND (:search IS NULL OR " +
        "     ct.name ILIKE '%' || CAST(:search AS text) || '%' OR " +
        "     ct.description ILIKE '%' || CAST(:search AS text) || '%') " +
        "AND ct.language_code = :locale",
        nativeQuery = true)
    List<Combo> findCombosByMenuWithFilters(
        @Param("menuId") UUID menuId,
        @Param("status") String status,
        @Param("type") String type,
        @Param("search") String search,
        @Param("locale") String locale);

    // Fetch combos by menu with daysOfWeek loaded for time-based filtering
    @Query("SELECT DISTINCT c FROM Combo c " +
           "LEFT JOIN FETCH c.daysOfWeek " +
           "WHERE c.menu.id = :menuId " +
           "AND c.isDeleted = false " +
           "AND c.status = :status " +
           "AND (:type IS NULL OR c.type = :type)")
    List<Combo> findCombosByMenuWithDaysOfWeek(
        @Param("menuId") UUID menuId,
        @Param("status") EntityStatus status,
        @Param("type") ComboType type);
}
