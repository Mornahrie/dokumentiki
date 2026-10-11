package com.example.kursach.repository;

import com.example.kursach.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByTabNumber(String tabNumber);
    boolean existsByTabNumber(String tabNumber);

    List<User> findByDepartmentDepartmentId(Long departmentId);
    List<User> findBySectorSectorId(Long sectorId);
    List<User> findByBaseRoleBaseRoleId(Long baseRoleId);
    List<User> findByPositionPositionId(Long positionId);

    List<User> findByLastNameContainingIgnoreCase(String lastNamePart);
    Optional<User> findByLastNameAndFirstName(String lastName, String firstName);

    List<User> findByIsTemporaryPasswordTrue();
    List<User> findByIsActiveTrue();

    List<User> findAllByOrderByLastNameAscFirstNameAsc();

    long countByDepartmentDepartmentId(Long departmentId);
    long countBySectorSectorId(Long sectorId);
}