package com.gulfnet.shared_library.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "discount_translation")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiscountTranslation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "discount_id")
    private Discount discount;

    @Column(name = "language_code")
    private String languageCode;

    @Column(name = "name")
    private String name;

    @Column(name = "description")
    private String description;
} 