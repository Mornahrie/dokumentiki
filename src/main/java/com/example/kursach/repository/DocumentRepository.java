package com.example.kursach.repository;

import com.example.kursach.entity.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {

    List<Document> findByAuthorUserId(Long authorId);
    List<Document> findByCategoryCategoryId(Long categoryId);
    List<Document> findByNomenclatureNomenclatureId(Long nomenclatureId);
    List<Document> findByDocumentTypeDocumentTypeId(Long documentTypeId);
    List<Document> findByDepartmentDepartmentId(Long departmentId);
    List<Document> findBySectorSectorId(Long sectorId);

    Optional<Document> findByRegistrationNumber(String registrationNumber);

    List<Document> findByTitleContainingIgnoreCase(String titlePart);
    List<Document> findByStatus(String status);

    List<Document> findAllByOrderByCreatedAtDesc();
    List<Document> findByCreatedAtBetween(LocalDateTime from, LocalDateTime to);

    List<Document> findByNomenclatureDepartmentDepartmentId(Long departmentId);

    Page<Document> findByAuthorUserId(Long authorId, Pageable pageable);
    Page<Document> findByStatus(String status, Pageable pageable);

    long countByCategoryCategoryId(Long categoryId);
    long countByStatus(String status);

    @Query("SELECT DISTINCT d FROM Document d " +
            "WHERE d.documentType.code = :typeCode " +
            "AND (d.author.userId = :userId OR d.department.departmentId = :deptId) " +
            "ORDER BY d.createdAt DESC")
    List<Document> findMyDocsByTypeCode(@Param("userId") Long userId,
                                        @Param("deptId") Long deptId,
                                        @Param("typeCode") String typeCode);

    @Query("SELECT d FROM Document d " +
            "WHERE (LOWER(d.title) LIKE LOWER(CONCAT('%', :q, '%')) " +
            "   OR LOWER(d.content) LIKE LOWER(CONCAT('%', :q, '%'))) " +
            "AND (d.author.userId = :userId " +
            "  OR d.department.departmentId = :deptId " +
            "  OR EXISTS (SELECT r FROM DocRoute r WHERE r.document = d AND r.assignedUser.userId = :userId)) " +
            "ORDER BY d.createdAt DESC")
    List<Document> searchAccessible(@Param("q") String q,
                                    @Param("userId") Long userId,
                                    @Param("deptId") Long deptId,
                                    Pageable pageable);

    @Query("SELECT COUNT(d) FROM Document d " +
            "WHERE d.department.departmentId = :deptId " +
            "AND d.nomenclature.nomenclatureId = :nomId " +
            "AND d.registrationNumber IS NOT NULL " +
            "AND YEAR(d.registrationDate) = :year")
    long countRegistrationsInYear(@Param("deptId") Long deptId,
                                  @Param("nomId") Long nomId,
                                  @Param("year") int year);

    @Query("SELECT DISTINCT d FROM Document d " +
            "JOIN DocRoute r ON r.document = d " +
            "WHERE r.assignedUser.userId = :userId " +
            "ORDER BY d.createdAt DESC")
    List<Document> findMyDocs(@Param("userId") Long userId);

    @Query("SELECT COUNT(d) FROM Document d " +
            "WHERE d.department.departmentId = :deptId " +
            "AND d.nomenclature.nomenclatureId = :nomId " +
            "AND d.registrationNumber IS NOT NULL")
    long countRegistrationsByDeptAndCase(@Param("deptId") Long deptId,
                                         @Param("nomId") Long nomId);

    @Query("SELECT MAX(d.registrationDate) FROM Document d " +
            "WHERE d.department.departmentId = :deptId " +
            "AND d.nomenclature.nomenclatureId = :nomId " +
            "AND d.registrationNumber IS NOT NULL")
    Optional<LocalDate> findLastRegistrationDate(@Param("deptId") Long deptId,
                                                 @Param("nomId") Long nomId);
}