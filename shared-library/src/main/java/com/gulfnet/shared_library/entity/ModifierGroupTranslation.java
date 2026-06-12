package com.gulfnet.shared_library.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(
        name = "modifier_group_translation",
        uniqueConstraints = @UniqueConstraint(columnNames = {"modifier_group_id", "language_code"})
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModifierGroupTranslation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "modifier_group_id", nullable = false)
    private ModifierGroup modifierGroup;

    @Column(name = "language_code", length = 5, nullable = false)
    private String languageCode;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", nullable = false)
    private String description;
}
