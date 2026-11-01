package com.example.kursach.repository;

import com.example.kursach.entity.BaseRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BaseRoleRepository extends JpaRepository<BaseRole, Long> {
    Optional<BaseRole> findByCode(String code);
    boolean existsByCode(String code);
}