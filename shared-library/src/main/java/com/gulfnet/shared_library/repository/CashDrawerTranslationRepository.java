package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.CashDrawerTranslation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CashDrawerTranslationRepository extends JpaRepository<CashDrawerTranslation, UUID> {

    List<CashDrawerTranslation> findAllByCashDrawer_IdOrderByLanguageCodeAsc(UUID cashDrawerId);

    Optional<CashDrawerTranslation> findByCashDrawer_IdAndLanguageCode(UUID cashDrawerId, String languageCode);

    @Query("SELECT t FROM CashDrawerTranslation t WHERE t.cashDrawer.id IN :drawerIds")
    List<CashDrawerTranslation> findAllByCashDrawerIdIn(@Param("drawerIds") Collection<UUID> drawerIds);

    void deleteAllByCashDrawer_Id(UUID cashDrawerId);

    @Query("SELECT COUNT(t) FROM CashDrawerTranslation t JOIN t.cashDrawer cd WHERE cd.restaurant.id = :restaurantId "
            + "AND LOWER(t.name) = LOWER(:name) AND LOWER(t.languageCode) = LOWER(:languageCode) "
            + "AND (:excludeDrawerId IS NULL OR t.cashDrawer.id <> :excludeDrawerId)")
    long countDuplicateNameInRestaurantForLanguage(
            @Param("restaurantId") UUID restaurantId,
            @Param("name") String name,
            @Param("languageCode") String languageCode,
            @Param("excludeDrawerId") UUID excludeDrawerId);
}
