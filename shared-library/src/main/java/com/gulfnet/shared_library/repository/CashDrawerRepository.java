package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.CashDrawer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CashDrawerRepository extends JpaRepository<CashDrawer, UUID>, CashDrawerRepositoryCustom {

    boolean existsBySerialNumber(String serialNumber);

    boolean existsBySerialNumberAndIdNot(String serialNumber, UUID id);

    Optional<CashDrawer> findBySerialNumber(String serialNumber);
}
