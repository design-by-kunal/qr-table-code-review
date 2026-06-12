package com.gulfnet.shared_library.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "promotion_translation")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromotionTranslation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String name;

    @Column(name = "heading", columnDefinition = "TEXT")
    private String heading;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "language_code", length = 5)
    private String languageCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promotion_id", columnDefinition = "UUID")
    private Promotion promotion;
} 