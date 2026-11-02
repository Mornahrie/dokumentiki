package com.example.kursach.repository;

import com.example.kursach.entity.Nomenclature;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NomenclatureRepository extends JpaRepository<Nomenclature, Long> {
    List<Nomenclature> findByDepartmentDepartmentId(Long departmentId);
    List<Nomenclature> findByIsActiveTrue();

    Optional<Nomenclature> findByCaseCode(String caseCode);
    Optional<Nomenclature> findByDepartmentDepartmentIdAndCaseCode(Long departmentId, String caseCode);
    boolean existsByDepartmentDepartmentIdAndCaseCode(Long departmentId, String caseCode);

    List<Nomenclature> findByTitleContainingIgnoreCase(String titlePart);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT n FROM Nomenclature n WHERE n.nomenclatureId = :id")
    Optional<Nomenclature> findByIdForUpdate(@Param("id") Long id);
}