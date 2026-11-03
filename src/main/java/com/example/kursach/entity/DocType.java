package com.example.kursach.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "doc_types",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"category_id", "code"}),
                @UniqueConstraint(columnNames = {"type_id", "category_id"})
        })
public class DocType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "type_id")
    private Long documentTypeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private DocCategory category;

    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    public Long getDocumentTypeId() { return documentTypeId; }
    public void setDocumentTypeId(Long documentTypeId) { this.documentTypeId = documentTypeId; }

    public DocCategory getCategory() { return category; }
    public void setCategory(DocCategory category) { this.category = category; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
}