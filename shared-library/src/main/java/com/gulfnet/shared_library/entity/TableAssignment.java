package com.gulfnet.shared_library.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "table_assignment")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TableAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_table_id")
    private RestaurantTable restaurantTable;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "waiter_id")
    private User waiter;

    @Column(name = "assigned_at", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime assignedAt;

    @Column(name = "unassigned_at", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime unassignedAt;

    @Column(name = "created_at", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "updated_at", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private User updatedBy;
}
