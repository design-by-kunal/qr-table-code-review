package com.gulfnet.shared_library.entity;

import com.gulfnet.shared_library.enums.ComboType;
import com.gulfnet.shared_library.enums.EntityStatus;
import com.gulfnet.shared_library.enums.DayOfWeek;
import com.gulfnet.shared_library.enums.ItemOrderType;
import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import com.gulfnet.shared_library.model.response.dto.ComboGroupTranslationDto;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.ArrayList;

@Entity
@Table(name = "combo")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Combo {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID comboId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "menu_id")
    private Menu menu;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ComboType type;

    @Column(name = "base_price", precision = 10, scale = 2)
    private BigDecimal basePrice;

    @Column(name = "combo_image_url")
    private String comboImageUrl;

    @Enumerated(EnumType.STRING)
    private EntityStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_order_type")
    private ItemOrderType itemOrderType;

    @Column(name = "valid_from", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime validFrom;

    @Column(name = "valid_to", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime validTo;

    @Column(name = "start_time", columnDefinition = "TIMETZ")
    private OffsetTime startTime;

    @Column(name = "end_time", columnDefinition = "TIMETZ")
    private OffsetTime endTime;

    @ElementCollection
    @Enumerated(EnumType.STRING)
    @CollectionTable(name = "combo_days_of_week", joinColumns = @JoinColumn(name = "combo_id"))
    @Column(name = "day_of_week")
    private List<DayOfWeek> daysOfWeek;

    @Builder.Default
    private Boolean isDeleted = false;

    @Column(name = "created_at", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", referencedColumnName = "id")
    private User createdBy;

    @Column(name = "updated_at", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by", referencedColumnName = "id")
    private User updatedBy;

    @OneToMany(mappedBy = "combo", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ComboGroup> comboGroups = new ArrayList<>();

    @OneToMany(mappedBy = "combo", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ComboTranslation> translations = new ArrayList<>();
}
