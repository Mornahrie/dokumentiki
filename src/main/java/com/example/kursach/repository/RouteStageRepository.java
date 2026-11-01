package com.example.kursach.repository;

import com.example.kursach.entity.RouteStage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RouteStageRepository extends JpaRepository<RouteStage, Long> {
    Optional<RouteStage> findByCode(String code);
    boolean existsByCode(String code);
}