package com.example.kursach.repository;

import com.example.kursach.entity.DocAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocActionRepository extends JpaRepository<DocAction, Long> {
    Optional<DocAction> findByCode(String code);
    Optional<DocAction> findByName(String name);

    List<DocAction> findByCodeIn(List<String> codes);
    boolean existsByCode(String code);
}