package com.example.kursach.repository;

import com.example.kursach.entity.UserPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserPermissionRepository extends JpaRepository<UserPermission, Long> {
    List<UserPermission> findByUserUserId(Long userId);
    List<UserPermission> findByUserUserIdAndIsAllowedTrue(Long userId);
    List<UserPermission> findByPermissionPermissionId(Long permissionId);

    boolean existsByUserUserIdAndPermissionPermissionId(Long userId, Long permissionId);

    void deleteByUserUserId(Long userId);
}