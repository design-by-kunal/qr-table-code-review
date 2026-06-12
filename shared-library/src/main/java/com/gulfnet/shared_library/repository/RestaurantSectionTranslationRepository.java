package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.RestaurantSectionTranslation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RestaurantSectionTranslationRepository extends JpaRepository<RestaurantSectionTranslation, UUID> {

    /**
     * Batch fetch all name+language pairs for a layout. Used for in-memory validation during create.
     */
    @Query("SELECT LOWER(rst.name), rst.languageCode FROM RestaurantSectionTranslation rst " +
           "WHERE rst.restaurantSection.restaurantLayout.id = :restaurantLayoutId " +
           "AND rst.restaurantSection.isDeleted = false " +
           "AND rst.name IS NOT NULL AND rst.name <> '' AND LOWER(rst.name) <> 'na'")
    List<Object[]> findAllNameLanguagePairsByLayoutId(@Param("restaurantLayoutId") UUID restaurantLayoutId);

    @Query("SELECT CASE WHEN COUNT(rst) > 0 THEN true ELSE false END " +
           "FROM RestaurantSectionTranslation rst " +
           "WHERE LOWER(rst.name) = LOWER(:name) " +
           "AND rst.languageCode = :languageCode " +
           "AND rst.restaurantSection.restaurantLayout.id = :restaurantLayoutId " +
           "AND rst.restaurantSection.isDeleted = false")
    boolean existsByNameLanguageAndLayout(@Param("name") String name,
                                          @Param("languageCode") String languageCode,
                                          @Param("restaurantLayoutId") UUID restaurantLayoutId);

    @Query("SELECT CASE WHEN COUNT(rst) > 0 THEN true ELSE false END " +
           "FROM RestaurantSectionTranslation rst " +
           "WHERE LOWER(rst.name) = LOWER(:name) " +
           "AND rst.languageCode = :languageCode " +
           "AND rst.restaurantSection.restaurantLayout.id = :restaurantLayoutId " +
           "AND rst.restaurantSection.id <> :sectionId " +
           "AND rst.restaurantSection.isDeleted = false")
    boolean existsByNameLanguageAndLayoutAndSectionNot(@Param("name") String name,
                                                       @Param("languageCode") String languageCode,
                                                       @Param("restaurantLayoutId") UUID restaurantLayoutId,
                                                       @Param("sectionId") UUID sectionId);
}
