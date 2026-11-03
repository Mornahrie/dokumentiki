package com.example.kursach.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "nomenclatures_cases")
public class NomenclatureCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "case_id")
    private Long caseId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nomenclature_id", nullable = false)
    private Nomenclature nomenclature;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private DocCategory category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "type_id")
    private DocType type;

    @Column(name = "case_code", nullable = false, length = 20)
    private String caseCode;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "retention_period")
    private Integer retentionPeriod;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    public Long getCaseId() { return caseId; }
    public void setCaseId(Long caseId) { this.caseId = caseId; }

    public Nomenclature getNomenclature() { return nomenclature; }
    public void setNomenclature(Nomenclature nomenclature) { this.nomenclature = nomenclature; }

    public DocCategory getCategory() { return category; }
    public void setCategory(DocCategory category) { this.category = category; }

    public DocType getType() { return type; }
    public void setType(DocType type) { this.type = type; }

    public String getCaseCode() { return caseCode; }
    public void setCaseCode(String caseCode) { this.caseCode = caseCode; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public Integer getRetentionPeriod() { return retentionPeriod; }
    public void setRetentionPeriod(Integer retentionPeriod) { this.retentionPeriod = retentionPeriod; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
}