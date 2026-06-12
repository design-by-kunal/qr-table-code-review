package com.gulfnet.shared_library.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "price_override_translation")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceOverrideTranslation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "price_override_id")
    private PriceOverride priceOverride;

    @Column(name = "language_code")
    private String languageCode;

    @Column(name = "name")
    private String name;

    @Column(name = "reason", length = 1000)
    private String reason;
}

