package com.gulfnet.shared_library.repository;

import com.gulfnet.shared_library.entity.RestaurantPaymentAccount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RestaurantPaymentAccountRepository extends JpaRepository<RestaurantPaymentAccount, UUID> {

    Optional<RestaurantPaymentAccount> findByRestaurant_IdAndPaymentTypeIgnoreCase(UUID restaurantId, String paymentType);

    Optional<RestaurantPaymentAccount> findByRestaurant_IdAndPaymentTypeIgnoreCaseAndIsDeletedFalse(UUID restaurantId, String paymentType);

    List<RestaurantPaymentAccount> findByRestaurant_IdAndIsDeletedFalse(UUID restaurantId);

    Page<RestaurantPaymentAccount> findByRestaurant_IdAndIsDeletedFalse(UUID restaurantId, Pageable pageable);
}

