package com.gulfnet.shared_library.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(
    name = "shift_translation",
    uniqueConstraints = @UniqueConstraint(columnNames = {"shift_id", "language_code"})
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShiftTranslation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shift_id", nullable = false)
    private Shift shift;

    @Column(name = "language_code", length = 5, nullable = false)
    private String languageCode;

    @Column(name = "name", nullable = false)
    private String name;
}
