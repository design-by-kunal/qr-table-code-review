package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.RestaurantLayout;
import com.gulfnet.shared_library.enums.EntityStatus;

import jakarta.transaction.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

public interface RestaurantLayoutRepository extends JpaRepository<RestaurantLayout, UUID> {

    @Query("SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END FROM RestaurantLayout r " +
           "JOIN r.translations tr " +
           "WHERE LOWER(tr.name) = LOWER(:name) AND tr.languageCode = :languageCode AND r.isDeleted = false")
    boolean existsByTranslations_NameAndTranslations_LanguageCodeAndIsDeletedFalse(
            @Param("name") String name,
            @Param("languageCode") String languageCode);

    Optional<RestaurantLayout> findByIdAndIsDeletedFalse(UUID id);

    /**
     * Returns a page of non-deleted {@link RestaurantLayout} entities for a restaurant, with optional
     * filters: {@code status} on the layout, {@code languageCode} requiring at least one matching layout
     * translation row, and {@code search} as a case-insensitive substring match on any translation
     * {@code name}. {@code NULL} parameters skip that filter.
     *
     * @param restaurantId layouts must belong to this restaurant
     * @param status         optional {@link EntityStatus}; {@code null} returns layouts in any status
     * @param languageCode   optional BCP-47 style code; {@code null} ignores language filter
     * @param search         optional fragment matched with {@code LIKE} against translation names; {@code null} skips search
     * @param pageable       paging and sorting
     * @return distinct layouts satisfying the predicates
     */
    @Query("SELECT DISTINCT rl FROM RestaurantLayout rl " +
            "WHERE rl.isDeleted = false " +
            "AND rl.restaurant.id = :restaurantId " +
            "AND (:status IS NULL OR rl.status = :status) " +
            "AND (:languageCode IS NULL OR EXISTS (SELECT tr FROM RestaurantLayoutTranslation tr WHERE tr.restaurantLayout = rl AND tr.languageCode = :languageCode)) " +
            "AND (:search IS NULL OR EXISTS (SELECT tr2 FROM RestaurantLayoutTranslation tr2 WHERE tr2.restaurantLayout = rl AND LOWER(tr2.name) LIKE LOWER(CONCAT('%', :search, '%'))))")
    Page<RestaurantLayout> findByRestaurantIdAndStatusAndLanguageCodeAndSearch(
            @Param("restaurantId") UUID restaurantId,
            @Param("status") EntityStatus status,
            @Param("languageCode") String languageCode,
            @Param("search") String search,
            Pageable pageable);

    @Query("SELECT rl FROM RestaurantLayout rl " +
            "WHERE rl.isDeleted = false " +
            "AND rl.restaurant.id = :restaurantId " +
            "AND (:status IS NULL OR rl.status = :status) " +
            "AND (:languageCode IS NULL OR EXISTS (" +
                "SELECT tr FROM RestaurantLayoutTranslation tr WHERE tr.restaurantLayout = rl AND tr.languageCode = :languageCode))")
    Page<RestaurantLayout> findByRestaurantIdAndStatus(
            @Param("restaurantId") UUID restaurantId,
            @Param("status") EntityStatus status,
            @Param("languageCode") String languageCode,
            Pageable pageable);


    @Query("SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END FROM RestaurantLayout r " +
           "JOIN r.translations tr " +
           "WHERE LOWER(tr.name) = LOWER(:name) AND tr.languageCode = :languageCode AND r.isDeleted = false AND r.id <> :excludeId")
    boolean existsByTranslations_NameAndTranslations_LanguageCodeAndIsDeletedFalseAndIdNot(
            @Param("name") String name,
            @Param("languageCode") String languageCode,
            @Param("excludeId") UUID excludeId);

    @Modifying
    @Transactional
    @Query("DELETE FROM RestaurantLayoutTranslation tr WHERE tr.restaurantLayout.id = :layoutId")
    void deleteTranslationsByRestaurantLayoutId(@Param("layoutId") UUID layoutId);

    Optional<RestaurantLayout> findByRestaurantIdAndIsDeletedFalse(UUID restaurantId);

    /**
     * Fetches RestaurantLayout with full structure (sections, rows, tables, templateLayout) in a single query
     * to avoid N+1 lazy loading. Section translations are loaded lazily (typically few sections).
     */
    @Query("SELECT DISTINCT rl FROM RestaurantLayout rl " +
           "LEFT JOIN FETCH rl.sections s " +
           "LEFT JOIN FETCH s.rows r " +
           "LEFT JOIN FETCH r.tables t " +
           "LEFT JOIN FETCH rl.templateLayout " +
           "WHERE rl.restaurant.id = :restaurantId AND rl.isDeleted = false")
    Optional<RestaurantLayout> findByRestaurantIdAndIsDeletedFalseWithStructure(@Param("restaurantId") UUID restaurantId);

}
