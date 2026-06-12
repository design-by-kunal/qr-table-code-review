package com.gulfnet.shared_library.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(
        name = "cash_drawer_translation",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_cash_drawer_translation_drawer_lang",
                columnNames = {"cash_drawer_id", "language_code"}
        )
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashDrawerTranslation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cash_drawer_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private CashDrawer cashDrawer;

    @Column(name = "language_code", nullable = false, length = 10)
    private String languageCode;

    @Column(name = "name", nullable = false, length = 100)
    private String name;
}
