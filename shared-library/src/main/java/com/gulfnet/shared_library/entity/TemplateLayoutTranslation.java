package com.gulfnet.shared_library.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(
  name = "template_layout_translation",
  uniqueConstraints = @UniqueConstraint(columnNames = {"template_layout_id", "language_code"})
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateLayoutTranslation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "language_code", length = 5, nullable = false)
    private String languageCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_layout_id", nullable = false)
    private TemplateLayout template;

    @Column(name = "name", nullable = false)
    private String name;
}
