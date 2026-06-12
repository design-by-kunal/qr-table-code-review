package com.gulfnet.shared_library.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(
  name = "template_section_translation",
  uniqueConstraints = @UniqueConstraint(columnNames = {"template_section_id", "language_code"})
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateSectionTranslation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_section_id", nullable = false)
    private TemplateSection templateSection;

    @Column(name = "language_code", length = 5, nullable = false)
    private String languageCode;

    @Column(name = "name", nullable = false)
    private String name;
}
