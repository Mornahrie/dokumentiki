package com.example.kursach.repository;

import com.example.kursach.entity.DocRoute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface DocRouteRepository extends JpaRepository<DocRoute, Long> {
    List<DocRoute> findByDocumentDocumentIdOrderByStepNumberAsc(Long documentId);
    List<DocRoute> findByDocumentDocumentIdOrderByCreatedAtAsc(Long documentId);

    /** Для аналитики: маршруты сразу по набору документов — один запрос, без N+1. */
    List<DocRoute> findByDocumentDocumentIdIn(Collection<Long> documentIds);

    List<DocRoute> findByAssignedUserUserIdAndStatus(Long userId, String status);
    List<DocRoute> findByAssignedUserUserId(Long userId);
    List<DocRoute> findByStatus(String status);

    @Query("SELECT r FROM DocRoute r WHERE r.assignedUser.userId = :userId AND r.action.code = :actionCode ORDER BY r.createdAt DESC")
    List<DocRoute> findTasksByUserAndAction(@Param("userId") Long userId, @Param("actionCode") String actionCode);

    List<DocRoute> findByStageStageId(Long stageId);
    List<DocRoute> findByActionActionId(Long actionId);

    @Query("SELECT r FROM DocRoute r WHERE r.deadline < :now AND r.completedAt IS NULL " +
            "AND r.status IN ('PENDING', 'IN_PROGRESS')")
    List<DocRoute> findOverdue(@Param("now") LocalDateTime now);

    @Query("SELECT r FROM DocRoute r WHERE r.deadline BETWEEN :now AND :soon " +
            "AND r.completedAt IS NULL AND r.status IN ('PENDING', 'IN_PROGRESS')")
    List<DocRoute> findExpiringSoon(@Param("now") LocalDateTime now, @Param("soon") LocalDateTime soon);

    List<DocRoute> findByParentRouteRouteId(Long parentRouteId);

    List<DocRoute> findByAssignedUserDepartmentDepartmentId(Long departmentId);

    List<DocRoute> findByAssignedUserUserIdIn(Collection<Long> userIds);

    @Query("SELECT r FROM DocRoute r WHERE r.document.documentId = :docId " +
            "AND r.status IN ('PENDING','IN_PROGRESS') " +
            "ORDER BY r.stepNumber ASC")
    List<DocRoute> findActiveRoutesByDocument(@Param("docId") Long docId);

    long countByAssignedUserUserIdAndStatus(Long userId, String status);

    @Query("SELECT r FROM DocRoute r WHERE r.document.documentId = :docId " +
            "AND r.assignedUser.userId = :userId AND r.action.code = :actionCode " +
            "AND r.status IN ('PENDING','IN_PROGRESS') ORDER BY r.stepNumber ASC")
    Optional<DocRoute> findActiveRouteByUserAndAction(
            @Param("docId") Long docId,
            @Param("userId") Long userId,
            @Param("actionCode") String actionCode);
}