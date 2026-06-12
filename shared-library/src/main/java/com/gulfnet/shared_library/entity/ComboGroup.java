package com.gulfnet.shared_library.entity;

import com.gulfnet.shared_library.enums.ComboGroupType;
import jakarta.persistence.*;
import lombok.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "combo_group")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComboGroup {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID comboGroupId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "combo_id", nullable = false)
    private Combo combo;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "group_type", nullable = false)
    private ComboGroupType groupType;

    @Builder.Default
    private Integer minSelect = 1;

    @Builder.Default
    private Integer maxSelect = 1;

    @OneToMany(mappedBy = "comboGroup", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ComboItemMapping> comboItemMappings = new ArrayList<>();
    
    @OneToMany(mappedBy = "comboGroup", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ComboGroupTranslation> translations = new ArrayList<>();
}
