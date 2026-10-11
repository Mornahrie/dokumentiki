package com.example.kursach.repository;

import com.example.kursach.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, Long> {
    Optional<Department> findByCode(String code);
    boolean existsByCode(String code);

    List<Department> findByNameContainingIgnoreCase(String namePart);
    List<Department> findByIsActiveTrue();
}