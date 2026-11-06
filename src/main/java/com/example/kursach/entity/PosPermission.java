package com.example.kursach.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "pos_permissions")
public class PosPermission {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "position_permission_id")
    private Long positionPermissionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "position_id", nullable = false)
    private Position position;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "permission_id", nullable = false)
    private Permission permission;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "type_id")             private DocType documentType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private DocCategory category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sector_id")
    private Sector sector;

    @Column(name = "is_allowed", nullable = false)
    private Boolean isAllowed = true;

    public Long getPositionPermissionId() { return positionPermissionId; }
    public void setPositionPermissionId(Long positionPermissionId) { this.positionPermissionId = positionPermissionId; }

    public Position getPosition() { return position; }
    public void setPosition(Position position) { this.position = position; }

    public Permission getPermission() { return permission; }
    public void setPermission(Permission permission) { this.permission = permission; }

    public DocType getDocumentType() { return documentType; }
    public void setDocumentType(DocType documentType) { this.documentType = documentType; }

    public DocCategory getCategory() { return category; }
    public void setCategory(DocCategory category) { this.category = category; }

    public Department getDepartment() { return department; }
    public void setDepartment(Department department) { this.department = department; }

    public Sector getSector() { return sector; }
    public void setSector(Sector sector) { this.sector = sector; }

    public Boolean getIsAllowed() { return isAllowed; }
    public void setIsAllowed(Boolean isAllowed) { this.isAllowed = isAllowed; }
}