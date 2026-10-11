package com.example.kursach.repository;

import com.example.kursach.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, Long> {
    Optional<Permission> findByCode(String code);
    boolean existsByCode(String code);

    List<Permission> findByCodeIn(List<String> codes);
    List<Permission> findByNameContainingIgnoreCase(String namePart);
}