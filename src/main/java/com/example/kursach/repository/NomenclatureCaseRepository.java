package com.example.kursach.repository;

import com.example.kursach.entity.NomenclatureCase;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NomenclatureCaseRepository extends JpaRepository<NomenclatureCase, Long> {

    List<NomenclatureCase> findByIsActiveTrue();
    List<NomenclatureCase> findByNomenclatureNomenclatureId(Long nomenclatureId);

    List<NomenclatureCase> findByCategoryCategoryId(Long categoryId);
    List<NomenclatureCase> findByTypeDocumentTypeId(Long typeId);

    Optional<NomenclatureCase> findByNomenclatureNomenclatureIdAndCaseCode(Long nomenclatureId, String caseCode);
    boolean existsByNomenclatureNomenclatureIdAndCaseCode(Long nomenclatureId, String caseCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM NomenclatureCase c WHERE c.caseId = :id")
    Optional<NomenclatureCase> findByIdForUpdate(@Param("id") Long id);
}