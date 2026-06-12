package com.gulfnet.shared_library.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(
    name = "combo_group_translation",
    uniqueConstraints = @UniqueConstraint(columnNames = {"combo_group_id", "language_code"})
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComboGroupTranslation {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "combo_group_id", nullable = false)
    private ComboGroup comboGroup;

    @Column(name = "language_code", length = 5, nullable = false)
    private String languageCode;

    @Column(name = "group_name", nullable = false)
    private String groupName;
}
