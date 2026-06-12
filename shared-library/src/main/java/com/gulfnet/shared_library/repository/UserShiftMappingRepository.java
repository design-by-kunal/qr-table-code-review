package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.UserShiftMapping;
import com.gulfnet.shared_library.entity.UserShiftId;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserShiftMappingRepository extends JpaRepository<UserShiftMapping, UserShiftId> {
    Optional<UserShiftMapping> findFirstByUser_Id(UUID userId);

    @Query("SELECT usm FROM UserShiftMapping usm JOIN FETCH usm.shift JOIN FETCH usm.user WHERE usm.user.id = :userId")
    Optional<UserShiftMapping> findFirstByUser_IdWithShift(@Param("userId") UUID userId);

    @Transactional
    void deleteByUserId(UUID id);

    @Query("SELECT DISTINCT usm FROM UserShiftMapping usm JOIN FETCH usm.shift JOIN FETCH usm.user WHERE usm.user.id IN :userIds")
    List<UserShiftMapping> findAllByUser_IdIn(@Param("userIds") List<UUID> userIds);

    @Query("SELECT COUNT(usm) > 0 FROM UserShiftMapping usm WHERE usm.id.shiftId = :shiftId")
    boolean existsByShiftId(@Param("shiftId") UUID shiftId);
} 