package com.example.kursach.entity;

import jakarta.persistence.*;

@Entity
@Table(
        name = "nomenclatures",
        uniqueConstraints = @UniqueConstraint(columnNames = {"department_id", "case_code"})
)
public class Nomenclature {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "nomenclature_id")
    private Long nomenclatureId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_type_id")
    private DocType documentType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private DocCategory category;

    @Column(name = "case_code", nullable = false, length = 20)
    private String caseCode;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "retention_period")
    private Integer retentionPeriod;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    public Long getNomenclatureId() { return nomenclatureId; }
    public void setNomenclatureId(Long nomenclatureId) { this.nomenclatureId = nomenclatureId; }

    public Department getDepartment() { return department; }
    public void setDepartment(Department department) { this.department = department; }

    public DocType getDocumentType() { return documentType; }
    public void setDocumentType(DocType documentType) { this.documentType = documentType; }

    public DocCategory getCategory() { return category; }
    public void setCategory(DocCategory category) { this.category = category; }

    public String getCaseCode() { return caseCode; }
    public void setCaseCode(String caseCode) { this.caseCode = caseCode; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public Integer getRetentionPeriod() { return retentionPeriod; }
    public void setRetentionPeriod(Integer retentionPeriod) { this.retentionPeriod = retentionPeriod; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
}