package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.SystemPaymentSetting;
import com.gulfnet.shared_library.enums.PaymentGatewayCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SystemPaymentSettingRepository extends JpaRepository<SystemPaymentSetting, UUID> {
    
    Optional<SystemPaymentSetting> findByGatewayCode(PaymentGatewayCode gatewayCode);
    
    boolean existsByGatewayCode(PaymentGatewayCode gatewayCode);
}
