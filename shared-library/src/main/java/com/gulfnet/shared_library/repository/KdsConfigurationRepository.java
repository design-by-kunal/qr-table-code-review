package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.KdsConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface KdsConfigurationRepository extends JpaRepository<KdsConfiguration, UUID> {
    
    @Query("SELECT kc FROM KdsConfiguration kc WHERE kc.user.id = :userId AND kc.kds.id = :kdsId")
    Optional<KdsConfiguration> findByUserIdAndKdsId(@Param("userId") UUID userId, @Param("kdsId") UUID kdsId);
    
    @Query("SELECT kc FROM KdsConfiguration kc WHERE kc.user.id = :userId")
    List<KdsConfiguration> findByUserId(@Param("userId") UUID userId);
    
    @Query("SELECT kc FROM KdsConfiguration kc WHERE kc.kds.id = :kdsId")
    List<KdsConfiguration> findByKdsId(@Param("kdsId") UUID kdsId);
    
    @Query("SELECT CASE WHEN COUNT(kc) > 0 THEN true ELSE false END FROM KdsConfiguration kc WHERE kc.user.id = :userId AND kc.kds.id = :kdsId")
    boolean existsByUserIdAndKdsId(@Param("userId") UUID userId, @Param("kdsId") UUID kdsId);
    
    @Query("SELECT CASE WHEN COUNT(kc) > 0 THEN true ELSE false END FROM KdsConfiguration kc WHERE kc.deviceCode = :deviceCode")
    boolean existsByDeviceCode(@Param("deviceCode") String deviceCode);
    
    @Query("SELECT kc FROM KdsConfiguration kc WHERE kc.deviceCode = :deviceCode")
    List<KdsConfiguration> findByDeviceCode(@Param("deviceCode") String deviceCode);
    
    @Query("SELECT kc FROM KdsConfiguration kc WHERE kc.kds.id = :kdsId")
    Optional<KdsConfiguration> findFirstByKdsId(@Param("kdsId") UUID kdsId);
    
    @Query("SELECT kc FROM KdsConfiguration kc WHERE kc.kds.id IN :kdsIds")
    List<KdsConfiguration> findAllByKdsIdIn(@Param("kdsIds") List<UUID> kdsIds);
    
    /**
     * Get user IDs directly from kds_configuration table to avoid lazy loading issues
     * @param kdsIds List of KDS IDs
     * @return List of user IDs (UUIDs)
     */
    @Query(value = "SELECT DISTINCT user_id FROM kds_configuration WHERE kds_id IN :kdsIds", nativeQuery = true)
    List<UUID> findUserIdsByKdsIdIn(@Param("kdsIds") List<UUID> kdsIds);
}

