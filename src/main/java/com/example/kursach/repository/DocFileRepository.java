package com.example.kursach.repository;

import com.example.kursach.entity.DocFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocFileRepository extends JpaRepository<DocFile, Long> {
    List<DocFile> findByDocumentDocumentId(Long documentId);
    List<DocFile> findByDocumentDocumentIdAndIsCurrentTrue(Long documentId);

    Optional<DocFile> findByDocumentDocumentIdAndVersionNumber(Long documentId, Integer versionNumber);

    // Тот самый метод, который требует IntelliJ IDEA (возвращает список версий по убыванию)
    List<DocFile> findByDocumentDocumentIdOrderByVersionNumberDesc(Long documentId);

    // Оставляем и этот метод для получения последней версии (возвращает Optional)
    Optional<DocFile> findTopByDocumentDocumentIdOrderByVersionNumberDesc(Long documentId);

    List<DocFile> findByUploadedByUserId(Long userId);

    void deleteByDocumentDocumentId(Long documentId);
}