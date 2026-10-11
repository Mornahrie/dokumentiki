package com.example.kursach.repository;

import com.example.kursach.entity.Sector;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SectorRepository extends JpaRepository<Sector, Long> {
    List<Sector> findByDepartmentDepartmentId(Long departmentId);
    List<Sector> findAllByOrderByNameAsc();
    List<Sector> findByIsActiveTrue();

    Optional<Sector> findByDepartmentDepartmentIdAndCode(Long departmentId, String code);
    boolean existsByDepartmentDepartmentIdAndCode(Long departmentId, String code);
}