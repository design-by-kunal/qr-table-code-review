package com.gulfnet.shared_library.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "restaurant_item_availability")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RestaurantItemAvailability {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_item_mapping_id", nullable = false)
    private CategoryItemMapping categoryItemMapping;
    
    @Column(name = "is_available", nullable = false)
    private Boolean isAvailable;

    
    @Column(name = "created_at", updatable = false, columnDefinition = "timestamptz")
    private OffsetDateTime createdAt;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", updatable = false)
    private User createdBy;
    
    @Column(name = "updated_at", columnDefinition = "timestamptz")
    private OffsetDateTime updatedAt;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private User updatedBy;
    
    @PrePersist
    protected void onCreate() {
        // Store timestamps in UTC with timezone-aware type
        createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        
    }
    
    @PreUpdate
    protected void onUpdate() {
        // Store timestamps in UTC with timezone-aware type
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }
}
