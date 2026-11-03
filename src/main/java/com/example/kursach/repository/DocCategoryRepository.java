package com.example.kursach.repository;

import com.example.kursach.entity.DocCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocCategoryRepository extends JpaRepository<DocCategory, Long> {
    List<DocCategory> findByIsActiveTrue();
    Optional<DocCategory> findByCode(String code);
}