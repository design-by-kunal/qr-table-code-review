package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.RestaurantTranslation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface RestaurantTranslationRepository extends JpaRepository<RestaurantTranslation, UUID> {

    @Query("SELECT rt FROM RestaurantTranslation rt WHERE rt.restaurant.id = :restaurantId")
    List<RestaurantTranslation> findAllByRestaurantIdWithLanguage(@Param("restaurantId") UUID restaurantId);
    
    @Query("SELECT COUNT(rt) > 0 FROM RestaurantTranslation rt WHERE rt.restaurant.id = :restaurantId AND LOWER(rt.name) = LOWER(:name)")
    boolean existsByNameAndRestaurantId(@Param("name") String name, @Param("restaurantId") UUID restaurantId);
    
    @Query("SELECT COUNT(rt) > 0 FROM RestaurantTranslation rt " +
       "WHERE rt.restaurant.id = :restaurantId AND LOWER(rt.name) = LOWER(:name) AND rt.id != :excludeId")
boolean existsByNameAndRestaurantIdAndIdNotIgnoreCase(@Param("name") String name,
                                                      @Param("restaurantId") UUID restaurantId,
                                                      @Param("excludeId") UUID excludeId);

    @Query("SELECT COUNT(rt) > 0 FROM RestaurantTranslation rt " +
           "WHERE rt.name = :name AND rt.restaurant.isDeleted = false")
    boolean existsByNameIgnoreCase(@Param("name") String name);

    @Query("SELECT COUNT(rt) > 0 FROM RestaurantTranslation rt WHERE rt.name = :name AND rt.restaurant.isDeleted = false")
    boolean existsByNameAnywhere(@Param("name") String name);


    // Batch loading methods for N+1 query fixes
    @Query("SELECT rt FROM RestaurantTranslation rt WHERE rt.restaurant.id IN :restaurantIds")
    List<RestaurantTranslation> findAllByRestaurantIdIn(@Param("restaurantIds") List<UUID> restaurantIds);

    // Check name exists outside the same restaurant group
@Query("SELECT CASE WHEN COUNT(rt) > 0 THEN true ELSE false END " +
"FROM RestaurantTranslation rt " +
"JOIN rt.restaurant r " +
"WHERE rt.name = :name " +
"AND (r.restaurantGroup.id <> :groupId OR r.restaurantGroup IS NULL) " +
"AND (:excludeId IS NULL OR rt.id <> :excludeId) " +
"AND r.isDeleted = false")
boolean existsByNameOutsideGroup(@Param("name") String name,
                          @Param("groupId") UUID groupId,
                          @Param("excludeId") UUID excludeId);

// Check name exists in other restaurants if no group
@Query("SELECT CASE WHEN COUNT(rt) > 0 THEN true ELSE false END " +
"FROM RestaurantTranslation rt " +
"JOIN rt.restaurant r " +
"WHERE LOWER(rt.name) = LOWER(:name) " +
"AND rt.languageCode = :languageCode " +
"AND r.id <> :restaurantId " +
"AND r.isDeleted = false")
boolean existsByNameInOtherRestaurants(@Param("name") String name,
                                @Param("languageCode") String languageCode,
                                @Param("restaurantId") UUID restaurantId);

    @Query("SELECT CASE WHEN COUNT(rt) > 0 THEN true ELSE false END " +
           "FROM RestaurantTranslation rt " +
           "JOIN rt.restaurant r " +
           "WHERE rt.name = :name " +
           "AND r.restaurantGroup.id = :groupId " +
           "AND r.isDeleted = false")
    boolean existsByNameInSameGroup(@Param("name") String name,
                                  @Param("groupId") UUID groupId);

    /**
     * Checks if a restaurant translation name exists for other restaurants in the same group,
     * excluding the specified restaurant. Used for validation to ensure name uniqueness
     * within a restaurant group while allowing updates to the same restaurant.
     *
     * @param name         the restaurant translation name to check (case-insensitive)
     * @param languageCode the language code of the translation
     * @param groupId      the restaurant group ID to check within
     * @param restaurantId the restaurant ID to exclude from the check
     * @return true if the name exists for another restaurant in the same group, false otherwise
     */
    @Query("SELECT CASE WHEN COUNT(rt) > 0 THEN true ELSE false END " +
           "FROM RestaurantTranslation rt " +
           "JOIN rt.restaurant r " +
           "WHERE LOWER(rt.name) = LOWER(:name) " +
           "AND rt.languageCode = :languageCode " +
           "AND r.restaurantGroup.id = :groupId " +
           "AND r.id <> :restaurantId " +
           "AND r.isDeleted = false")
    boolean existsByNameInSameGroupForOtherRestaurants(@Param("name") String name,
                                                      @Param("languageCode") String languageCode,
                                                      @Param("groupId") UUID groupId,
                                                      @Param("restaurantId") UUID restaurantId);

}
    
