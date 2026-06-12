package com.gulfnet.shared_library.entity;

import com.gulfnet.shared_library.enums.EntityStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;

@Entity
@Table(name = "restaurant_chain")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RestaurantChain {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String countryCode;
    private String countryName;
    private String paymentMethodType;
    private String logoUrl;

    @Column(name = "created_at", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @OneToMany(mappedBy = "restaurantChain", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @JsonIgnore
    private List<RestaurantChainTranslation> translations = new ArrayList<>();

    @Column(name = "updated_at", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private User updatedBy;

    @Enumerated(EnumType.STRING)
    private EntityStatus status;
}

