package com.gulfnet.shared_library.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    
  name = "restaurant_layout_translation",
  uniqueConstraints = @UniqueConstraint(columnNames = {"restaurant_layout_id", "language_code"})
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RestaurantLayoutTranslation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "language_code", length = 5, nullable = false)
    private String languageCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_layout_id", nullable = false)
    private RestaurantLayout restaurantLayout;

    @Column(name = "name", nullable = false)
    private String name;

}
