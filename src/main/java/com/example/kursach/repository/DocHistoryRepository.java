package com.example.kursach.repository;

import com.example.kursach.entity.DocHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий истории документа.
 * Методы удаления/изменения намеренно отсутствуют — BR-09, SAF-01.
 */
@Repository
public interface DocHistoryRepository extends JpaRepository<DocHistory, Long> {
    List<DocHistory> findByDocumentDocumentIdOrderByHistoryIdAsc(Long documentId);
    List<DocHistory> findByDocumentDocumentIdOrderByCreatedAtDesc(Long documentId);

    List<DocHistory> findByUserUserId(Long userId);
    List<DocHistory> findByRouteRouteId(Long routeId);

    List<DocHistory> findByDocumentDocumentIdAndActionActionId(Long documentId, Long actionId);

    Optional<DocHistory> findTopByDocumentDocumentIdOrderByHistoryIdDesc(Long documentId);

    long countByUserUserId(Long userId);

    // deleteByDocumentDocumentId() — удалён специально (BR-09 / SAF-01)
}