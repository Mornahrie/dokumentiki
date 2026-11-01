package com.example.kursach.repository;

import com.example.kursach.entity.PosPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PosPermissionRepository extends JpaRepository<PosPermission, Long> {
    List<PosPermission> findByPositionPositionId(Long positionId);
    List<PosPermission> findByPositionPositionIdAndIsAllowedTrue(Long positionId);

    List<PosPermission> findByPermissionPermissionId(Long permissionId);

    List<PosPermission> findByDepartmentDepartmentId(Long departmentId);
    List<PosPermission> findBySectorSectorId(Long sectorId);
    List<PosPermission> findByDocumentTypeDocumentTypeId(Long documentTypeId);
    List<PosPermission> findByCategoryCategoryId(Long categoryId);

    boolean existsByPositionPositionIdAndPermissionPermissionId(Long positionId, Long permissionId);

    void deleteByPositionPositionId(Long positionId);
}