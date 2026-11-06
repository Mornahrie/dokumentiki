package com.example.kursach.controller;

import com.example.kursach.entity.DocRoute;
import com.example.kursach.entity.Document;
import com.example.kursach.entity.User;
import com.example.kursach.repository.DocRouteRepository;
import com.example.kursach.repository.DocumentRepository;
import com.example.kursach.repository.UserRepository;
import com.example.kursach.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/analytics")
@CrossOrigin(origins = "*")
public class AnalyticsController {

    private static final Logger logger = LoggerFactory.getLogger(AnalyticsController.class);

    private static final Set<String> ACTIVE_DOC_STATUSES = Set.of("IN_PROGRESS", "REVISION");
    private static final Set<String> EXCLUDED_DOC_STATUSES = Set.of("DRAFT", "REJECTED", "ARCHIVED");

    private final DocRouteRepository docRouteRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;

    public AnalyticsController(DocRouteRepository docRouteRepository,
                               DocumentRepository documentRepository,
                               UserRepository userRepository) {
        this.docRouteRepository = docRouteRepository;
        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
    }

    @GetMapping("/discipline")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getDisciplineStats(@RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {
        Long userId = JwtUtil.extractUserId(authHeader.substring(7));
        User currentUser = userRepository.findById(userId).orElseThrow();

        boolean isAdmin = "ADMIN".equals(currentUser.getBaseRole().getCode());
        boolean isManager = !isAdmin && isManagerPosition(currentUser);

        List<Document> scopedDocs;
        if (isAdmin) {
            scopedDocs = documentRepository.findAll();
        } else if (isManager) {
            scopedDocs = documentRepository.findByDepartmentDepartmentId(
                    currentUser.getDepartment().getDepartmentId());
        } else {
            scopedDocs = documentRepository.findMyDocs(userId);
        }

        List<Document> meaningful = scopedDocs.stream()
                .filter(d -> !EXCLUDED_DOC_STATUSES.contains(d.getStatus()))
                .collect(Collectors.toList());

        long totalActive = meaningful.stream()
                .filter(d -> ACTIVE_DOC_STATUSES.contains(d.getStatus()))
                .count();

        long completedOnTime = meaningful.stream().filter(this::isDocumentCompletedOnTime).count();
        long overdue = meaningful.stream().filter(this::isDocumentOverdue).count();

        long totalCompletedOrOverdue = completedOnTime + overdue;
        int onTimePercent = totalCompletedOrOverdue == 0
                ? 100
                : (int) Math.round((double) completedOnTime / totalCompletedOrOverdue * 100);
        int overduePercent = 100 - onTimePercent;

        List<Map<String, Object>> monthlyStats = new ArrayList<>();
        LocalDateTime sixMonthsAgo = LocalDateTime.now().minusMonths(5)
                .withDayOfMonth(1).withHour(0).withMinute(0);

        for (int i = 0; i < 6; i++) {
            LocalDateTime monthStart = sixMonthsAgo.plusMonths(i);
            LocalDateTime monthEnd = monthStart.plusMonths(1);

            long received = meaningful.stream()
                    .filter(d -> d.getCreatedAt() != null
                            && d.getCreatedAt().isAfter(monthStart)
                            && d.getCreatedAt().isBefore(monthEnd))
                    .count();
            long completed = meaningful.stream()
                    .filter(d -> d.getCompletedAt() != null
                            && d.getCompletedAt().isAfter(monthStart)
                            && d.getCompletedAt().isBefore(monthEnd))
                    .count();

            Map<String, Object> monthData = new HashMap<>();
            monthData.put("month", monthStart.getMonth().getDisplayName(TextStyle.SHORT, new Locale("ru")));
            monthData.put("received", received);
            monthData.put("completed", completed);
            monthlyStats.add(monthData);
        }

        List<Map<String, Object>> employeeStats = new ArrayList<>();
        if (isAdmin || isManager) {
            List<User> targetUsers = isAdmin
                    ? userRepository.findByIsActiveTrue()
                    : userRepository.findByDepartmentDepartmentId(currentUser.getDepartment().getDepartmentId());

            Set<Long> docIds = meaningful.stream()
                    .map(Document::getDocumentId)
                    .collect(Collectors.toSet());

            List<DocRoute> allRoutes = docIds.isEmpty()
                    ? List.of()
                    : docRouteRepository.findByDocumentDocumentIdIn(docIds);

            Map<Long, Document> docById = meaningful.stream()
                    .collect(Collectors.toMap(Document::getDocumentId, d -> d));

            Map<Long, Set<Long>> docIdsByUser = allRoutes.stream()
                    .collect(Collectors.groupingBy(
                            r -> r.getAssignedUser().getUserId(),
                            Collectors.mapping(r -> r.getDocument().getDocumentId(), Collectors.toSet())
                    ));

            for (User u : targetUsers) {
                if ("ADMIN".equals(u.getBaseRole().getCode())) continue;

                Set<Long> userDocIds = docIdsByUser.getOrDefault(u.getUserId(), Set.of());
                if (userDocIds.isEmpty()) continue;

                List<Document> userDocs = userDocIds.stream()
                        .map(docById::get)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());

                long uOnTime = userDocs.stream().filter(this::isDocumentCompletedOnTime).count();
                long uOverdue = userDocs.stream().filter(this::isDocumentOverdue).count();
                long uTotal = uOnTime + uOverdue;
                if (uTotal == 0) continue;

                int uEfficiency = (int) Math.round((double) uOnTime / uTotal * 100);

                Map<String, Object> stat = new HashMap<>();
                stat.put("name", shortName(u));
                stat.put("total", uTotal);
                stat.put("onTime", uOnTime);
                stat.put("overdue", uOverdue);
                stat.put("efficiency", uEfficiency);
                employeeStats.add(stat);
            }
            employeeStats.sort((a, b) -> Integer.compare((int) b.get("efficiency"), (int) a.get("efficiency")));
        }

        Map<String, Object> response = new HashMap<>();
        response.put("isAdmin", isAdmin);
        response.put("isManager", isManager);
        response.put("totalActive", totalActive);
        response.put("completedOnTime", completedOnTime);
        response.put("overdue", overdue);
        response.put("onTimePercent", onTimePercent);
        response.put("overduePercent", overduePercent);
        response.put("monthlyStats", monthlyStats);
        response.put("employeeStats", employeeStats);

        return ResponseEntity.ok(response);
    }

    private boolean isDocumentCompletedOnTime(Document d) {
        if (d.getCompletedAt() == null) return false;
        return d.getDeadline() == null || !d.getCompletedAt().isAfter(d.getDeadline());
    }

    private boolean isDocumentOverdue(Document d) {
        if (d.getDeadline() == null) return false;

        if (d.getCompletedAt() != null) {
            return d.getCompletedAt().isAfter(d.getDeadline());
        }

        return ACTIVE_DOC_STATUSES.contains(d.getStatus())
                && d.getDeadline().isBefore(LocalDateTime.now());
    }

    /**
     * Умное определение руководителя по названию и коду должности (без поля в БД).
     */
    private boolean isManagerPosition(User user) {
        if (user.getPosition() == null) return false;

        String name = user.getPosition().getName() == null ? "" : user.getPosition().getName().toLowerCase();
        String code = user.getPosition().getCode() == null ? "" : user.getPosition().getCode().toLowerCase();

        if (name.contains("заместитель") || name.contains("зам.")) {
            return false;
        }

        boolean codeMatch = code.contains("head")
                || code.contains("chief")
                || code.contains("director")
                || code.contains("manager")
                || code.contains("lead");

        boolean nameMatch = name.contains("начальник")
                || name.contains("директор")
                || name.contains("руководитель")
                || name.contains("заведующий");

        return codeMatch || nameMatch;
    }

    private static String shortName(User u) {
        String firstName = u.getFirstName() == null ? "" : u.getFirstName();
        String middleName = u.getMiddleName() == null ? "" : u.getMiddleName();
        String firstInitial = firstName.isEmpty() ? "" : " " + firstName.charAt(0) + ".";
        String middleInitial = middleName.isBlank() ? "" : " " + middleName.charAt(0) + ".";
        return (u.getLastName() == null ? "" : u.getLastName()) + firstInitial + middleInitial;
    }
}