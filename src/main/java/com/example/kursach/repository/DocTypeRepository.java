package com.example.kursach.repository;

import com.example.kursach.entity.DocType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocTypeRepository extends JpaRepository<DocType, Long> {
    Optional<DocType> findByCode(String code);
    Optional<DocType> findByName(String name);
    boolean existsByCode(String code);

    List<DocType> findByIsActiveTrue();
    List<DocType> findByCategoryCategoryId(Long categoryId);
    List<DocType> findByCategoryCategoryIdAndIsActiveTrue(Long categoryId);
}