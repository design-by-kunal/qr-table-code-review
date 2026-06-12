package com.gulfnet.shared_library.repository;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.repository.Modifying;
import com.gulfnet.shared_library.entity.RestaurantGroupTranslation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable; 
import java.util.List;
import java.util.UUID;
import java.util.Optional; 



public interface RestaurantGroupTranslationRepository extends JpaRepository<RestaurantGroupTranslation, UUID> {

    @Query("SELECT t FROM RestaurantGroupTranslation t WHERE t.restaurantGroup.id = :groupId AND t.languageCode = :languageCode")
    Optional<RestaurantGroupTranslation> findByRestaurantGroupIdAndLanguageCode(@Param("groupId") UUID groupId, @Param("languageCode") String languageCode);

    @Query("SELECT t FROM RestaurantGroupTranslation t WHERE t.restaurantGroup.id = :groupId ORDER BY t.languageCode")
    List<RestaurantGroupTranslation> findTopByRestaurantGroupId(@Param("groupId") UUID groupId, Pageable pageable);

    // Re-adding this method to ensure other services compile without changes.
    @Query("SELECT t FROM RestaurantGroupTranslation t WHERE t.restaurantGroup.id = :groupId")
    List<RestaurantGroupTranslation> findAllByRestaurantGroupIdWithLanguage(@Param("groupId") UUID groupId);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM restaurant_group_translation WHERE restaurant_group_id = :groupId", nativeQuery = true)
    void deleteAllByRestaurantGroupId(@Param("groupId") UUID groupId);

    // Batch loading methods for N+1 query fixes
    @Query("SELECT rgt FROM RestaurantGroupTranslation rgt WHERE rgt.restaurantGroup.id IN :groupIds")
    List<RestaurantGroupTranslation> findAllByRestaurantGroupIdIn(@Param("groupIds") List<UUID> groupIds);

    @Query("SELECT CASE WHEN COUNT(rgt) > 0 THEN true ELSE false END " +
    "FROM RestaurantGroupTranslation rgt " +
    "WHERE LOWER(rgt.name) = LOWER(:name) AND rgt.languageCode = :languageCode")
boolean existsByNameAndLanguageCode(@Param("name") String name, @Param("languageCode") String languageCode);

@Query("SELECT COUNT(t) > 0 FROM RestaurantGroupTranslation t WHERE LOWER(t.name) = LOWER(:name) AND t.languageCode = :languageCode AND t.restaurantGroup.id != :groupId")
boolean existsByNameAndLanguageCodeAndRestaurantGroupIdNot(@Param("name") String name, @Param("languageCode") String languageCode, @Param("groupId") UUID restaurantGroupId);


} 