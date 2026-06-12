package com.gulfnet.shared_library.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(
    name = "combo_translation",
    uniqueConstraints = @UniqueConstraint(columnNames = {"combo_id", "language_code"})
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComboTranslation {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "combo_id", nullable = false)
    private Combo combo;

    @Column(name = "language_code", length = 5, nullable = false)
    private String languageCode;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;
}
