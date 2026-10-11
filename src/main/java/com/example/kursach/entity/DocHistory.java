package com.example.kursach.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;

/**
 * История документа — append-only (BR-09, SAF-01).
 * @Immutable запрещает Hibernate генерировать UPDATE.
 * DELETE на уровне приложения запрещён удалением соответствующих методов из репозитория.
 * DELETE на уровне БД запрещён триггером (см. db/migration/V__doc_history_immutable.sql).
 */
@Immutable
@Entity
@Table(name = "doc_histories")
public class DocHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "history_id")
    private Long historyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false, updatable = false)
    private Document document;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id", updatable = false)
    private DocRoute route;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "action_id", nullable = false, updatable = false)
    private DocAction action;

    @Column(name = "comment", columnDefinition = "TEXT", updatable = false)
    private String comment;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details", columnDefinition = "jsonb", updatable = false)
    private String details;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Long getHistoryId() { return historyId; }
    public void setHistoryId(Long historyId) { this.historyId = historyId; }

    public Document getDocument() { return document; }
    public void setDocument(Document document) { this.document = document; }

    public DocRoute getRoute() { return route; }
    public void setRoute(DocRoute route) { this.route = route; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public DocAction getAction() { return action; }
    public void setAction(DocAction action) { this.action = action; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}