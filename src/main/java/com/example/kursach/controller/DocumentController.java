package com.example.kursach.controller;

import com.example.kursach.entity.*;
import com.example.kursach.repository.*;
import com.example.kursach.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/documents")
@CrossOrigin(origins = "*")
public class DocumentController {

    private static final Logger logger = LoggerFactory.getLogger(DocumentController.class);

    private static final List<String> STAGE_ORDER =
            List.of("APPROVE", "SIGN", "REGISTER", "REVIEW", "EXECUTE", "READ");

    private static final Set<String> OVERRIDE_ACTIONS = Set.of("REJECT", "REDIRECT");

    private static final Set<String> NON_TERMINAL_ROUTE_STATUSES =
            Set.of("PENDING", "IN_PROGRESS", "WAITING");

    private static final Set<String> EDITABLE_DOC_STATUSES = Set.of("DRAFT", "REVISION");

    private static final Set<String> LOCKED_FILE_STATUSES = Set.of("COMPLETED", "ARCHIVED");

    private final DocumentRepository documentRepository;
    private final DocFileRepository docFileRepository;
    private final DocHistoryRepository docHistoryRepository;
    private final DocRouteRepository docRouteRepository;
    private final DocActionRepository docActionRepository;
    private final DocTypeRepository docTypeRepository;
    private final DocCategoryRepository docCategoryRepository;
    private final NomenclatureRepository nomenclatureRepository;
    private final RouteStageRepository routeStageRepository;
    private final UserRepository userRepository;
    private final PermissionRepository permissionRepository;
    private final UserPermissionRepository userPermissionRepository;
    private final PosPermissionRepository posPermissionRepository;

    @Value("${app.upload.dir:uploads}")
    private String uploadBaseDir;

    public DocumentController(DocumentRepository documentRepository,
                              DocFileRepository docFileRepository,
                              DocHistoryRepository docHistoryRepository,
                              DocRouteRepository docRouteRepository,
                              DocActionRepository docActionRepository,
                              DocTypeRepository docTypeRepository,
                              DocCategoryRepository docCategoryRepository,
                              NomenclatureRepository nomenclatureRepository,
                              RouteStageRepository routeStageRepository,
                              UserRepository userRepository,
                              PermissionRepository permissionRepository,
                              UserPermissionRepository userPermissionRepository,
                              PosPermissionRepository posPermissionRepository) {
        this.documentRepository = documentRepository;
        this.docFileRepository = docFileRepository;
        this.docHistoryRepository = docHistoryRepository;
        this.docRouteRepository = docRouteRepository;
        this.docActionRepository = docActionRepository;
        this.docTypeRepository = docTypeRepository;
        this.docCategoryRepository = docCategoryRepository;
        this.nomenclatureRepository = nomenclatureRepository;
        this.routeStageRepository = routeStageRepository;
        this.userRepository = userRepository;
        this.permissionRepository = permissionRepository;
        this.userPermissionRepository = userPermissionRepository;
        this.posPermissionRepository = posPermissionRepository;
    }

    // =====================================================================
    // СПИСКИ И КАРТОЧКА
    // =====================================================================

    @GetMapping("/my")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getMyDocuments(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
            @RequestParam("type") String typeCode) {
        Long userId = extractUserId(authHeader);
        User user = userRepository.findById(userId).orElseThrow();
        return ResponseEntity.ok(documentRepository.findMyDocsByTypeCode(
                userId, user.getDepartment().getDepartmentId(), typeCode));
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getDocumentCard(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
            @PathVariable Long id) {
        Long userId = extractUserId(authHeader);
        User user = userRepository.findById(userId).orElseThrow();
        Document doc = documentRepository.findById(id).orElseThrow();

        if (!hasAccessToDocument(user, doc)) {
            logger.warn("Отказ доступа к документу ID={} пользователю ID={}", id, userId);
            return ResponseEntity.status(403).body(Map.of("error", "Нет доступа к документу"));
        }

        List<DocFile> files = docFileRepository.findByDocumentDocumentIdOrderByVersionNumberDesc(id);
        List<DocRoute> routes = docRouteRepository.findByDocumentDocumentIdOrderByStepNumberAsc(id);

        boolean canRegister = computeCanRegister(user, doc, routes);
        boolean canEdit = computeCanEdit(user, doc);

        Map<String, Object> response = new HashMap<>();
        response.put("document", doc);
        response.put("files", files);
        response.put("canRegister", canRegister);
        response.put("canEdit", canEdit);
        response.put("isInRoute", isUserInRoute(userId, id));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/search")
    @Transactional(readOnly = true)
    public ResponseEntity<?> globalSearch(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
            @RequestParam("q") String query) {
        Long userId = extractUserId(authHeader);
        User user = userRepository.findById(userId).orElseThrow();
        List<Document> found = documentRepository.searchAccessible(
                query, userId, user.getDepartment().getDepartmentId(),
                PageRequest.of(0, 50));
        return ResponseEntity.ok(found);
    }

    @GetMapping("/tasks")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getMyTasks(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
            @RequestParam("action") String actionCode,
            @RequestParam(value = "status", required = false) String statusFilter) {
        Long userId = extractUserId(authHeader);
        List<DocRoute> routes = docRouteRepository.findTasksByUserAndAction(userId, actionCode);

        if (statusFilter != null && !statusFilter.isEmpty() && !"ALL".equalsIgnoreCase(statusFilter)) {
            if ("ACTIVE".equalsIgnoreCase(statusFilter)) {
                routes = routes.stream()
                        .filter(r -> NON_TERMINAL_ROUTE_STATUSES.contains(r.getStatus()))
                        .collect(Collectors.toList());
            } else {
                routes = routes.stream()
                        .filter(r -> r.getStatus().equalsIgnoreCase(statusFilter))
                        .collect(Collectors.toList());
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (DocRoute r : routes) {
            User author = r.getDocument().getAuthor();
            String authorName = buildShortName(author);

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("routeId", r.getRouteId());
            m.put("documentId", r.getDocument().getDocumentId());
            m.put("title", r.getDocument().getTitle());
            m.put("status", r.getDocument().getStatus());
            m.put("routeStatus", r.getStatus());
            m.put("registrationNumber", r.getDocument().getRegistrationNumber());
            m.put("createdAt", r.getDocument().getCreatedAt());
            m.put("completedAt", r.getCompletedAt());
            m.put("deadline", r.getDeadline());
            m.put("stepNumber", r.getStepNumber());
            m.put("authorName", authorName);
            result.add(m);
        }
        return ResponseEntity.ok(result);
    }

    // =====================================================================
    // СОЗДАНИЕ / РЕДАКТИРОВАНИЕ
    // =====================================================================

    @PostMapping
    @Transactional
    public ResponseEntity<?> createDocument(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
            @RequestBody Map<String, Object> body) {

        Long userId = extractUserId(authHeader);
        User user = userRepository.findById(userId).orElseThrow();

        String title = (String) body.get("title");
        if (title == null || title.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Название документа обязательно"));
        }
        if (body.get("documentTypeId") == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Тип документа обязателен"));
        }
        if (body.get("categoryId") == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Категория обязательна"));
        }
        if (body.get("nomenclatureId") == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Номенклатура дел обязательна"));
        }

        DocType type = docTypeRepository.findById(asLong(body.get("documentTypeId"))).orElseThrow();
        DocCategory category = docCategoryRepository.findById(asLong(body.get("categoryId"))).orElseThrow();
        Nomenclature nom = nomenclatureRepository.findById(asLong(body.get("nomenclatureId"))).orElseThrow();

        // 1. Категория должна принадлежать выбранному типу
        if (category.getDocumentType() == null
                || !category.getDocumentType().getDocumentTypeId().equals(type.getDocumentTypeId())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Категория не относится к выбранному типу документа"));
        }

        // 2. Номенклатура должна принадлежать подразделению автора (BR-15)
        if (nom.getDepartment() == null
                || !nom.getDepartment().getDepartmentId().equals(user.getDepartment().getDepartmentId())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Номенклатура принадлежит другому подразделению"));
        }

        // 3. Номенклатура должна соответствовать выбранному типу и категории
        if (!nom.getDocumentType().getDocumentTypeId().equals(type.getDocumentTypeId())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Номенклатура не подходит для выбранного типа документа"));
        }
        if (!nom.getCategory().getCategoryId().equals(category.getCategoryId())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Номенклатура не подходит для выбранной категории"));
        }

        Document doc = new Document();
        doc.setTitle(title);
        doc.setContent((String) body.getOrDefault("content", ""));
        doc.setDocumentType(type);
        doc.setCategory(category);
        doc.setNomenclature(nom);
        doc.setAuthor(user);
        doc.setDepartment(user.getDepartment());
        doc.setSector(user.getSector());
        doc.setCreatedAt(LocalDateTime.now());
        doc.setUpdatedAt(LocalDateTime.now());
        doc.setStatus("DRAFT");

        documentRepository.save(doc);
        logHistory(doc, user, "CREATE", null);
        return ResponseEntity.ok(doc);
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<?> editDocument(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {

        Long userId = extractUserId(authHeader);
        User user = userRepository.findById(userId).orElseThrow();
        Document doc = documentRepository.findById(id).orElseThrow();

        boolean isAuthor = doc.getAuthor().getUserId().equals(userId);
        boolean isAdmin = "ADMIN".equals(user.getBaseRole().getCode());
        if (!isAuthor && !isAdmin) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "Нет прав на редактирование. Вы не автор и не администратор."));
        }
        if (!EDITABLE_DOC_STATUSES.contains(doc.getStatus())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Редактирование возможно только в черновике или на доработке"));
        }

        if (body.containsKey("title")) {
            String t = (String) body.get("title");
            if (t == null || t.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Название не может быть пустым"));
            }
            doc.setTitle(t);
        }
        if (body.containsKey("content")) doc.setContent((String) body.get("content"));

        DocType newType = doc.getDocumentType();
        if (body.containsKey("documentTypeId") && body.get("documentTypeId") != null) {
            newType = docTypeRepository.findById(asLong(body.get("documentTypeId"))).orElse(null);
            if (newType == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Тип документа не найден"));
            }
        }
        if (body.containsKey("categoryId") && body.get("categoryId") != null) {
            DocCategory cat = docCategoryRepository.findById(asLong(body.get("categoryId"))).orElse(null);
            if (cat == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Категория не найдена"));
            }
            if (cat.getDocumentType() == null
                    || newType == null
                    || !cat.getDocumentType().getDocumentTypeId().equals(newType.getDocumentTypeId())) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Категория не относится к выбранному типу документа"));
            }
            doc.setCategory(cat);
        }
        if (newType != null && body.containsKey("documentTypeId")) {
            doc.setDocumentType(newType);
        }

        if (body.containsKey("nomenclatureId") && body.get("nomenclatureId") != null) {
            Nomenclature nom = nomenclatureRepository.findById(asLong(body.get("nomenclatureId"))).orElse(null);
            if (nom == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Номенклатура не найдена"));
            }
            if (nom.getDepartment() == null
                    || !nom.getDepartment().getDepartmentId().equals(user.getDepartment().getDepartmentId())) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Номенклатура принадлежит другому подразделению"));
            }
            doc.setNomenclature(nom);
        }

        // Финальная проверка соответствия номенклатуры типу/категории
        Nomenclature finalNom = doc.getNomenclature();
        DocType finalType = doc.getDocumentType();
        DocCategory finalCat = doc.getCategory();
        if (finalNom != null && finalType != null && finalNom.getDocumentType() != null
                && !finalNom.getDocumentType().getDocumentTypeId().equals(finalType.getDocumentTypeId())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Номенклатура не подходит для выбранного типа документа"));
        }
        if (finalNom != null && finalCat != null && finalNom.getCategory() != null
                && !finalNom.getCategory().getCategoryId().equals(finalCat.getCategoryId())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Номенклатура не подходит для выбранной категории"));
        }

        doc.setUpdatedAt(LocalDateTime.now());
        documentRepository.save(doc);
        logHistory(doc, user, "EDIT", null);
        return ResponseEntity.ok(doc);
    }

    // =====================================================================
    // ФАЙЛЫ
    // =====================================================================

    @PostMapping(value = "/{id}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public ResponseEntity<?> uploadFile(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) throws IOException {

        Long userId = extractUserId(authHeader);
        User user = userRepository.findById(userId).orElseThrow();
        Document doc = documentRepository.findById(id).orElseThrow();

        boolean isAuthor = doc.getAuthor().getUserId().equals(userId);
        boolean isAdmin = "ADMIN".equals(user.getBaseRole().getCode());
        if (!isAuthor && !isAdmin) {
            return ResponseEntity.status(403).body(Map.of("error", "Нет прав на добавление файла"));
        }
        if (LOCKED_FILE_STATUSES.contains(doc.getStatus())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Нельзя изменять файлы завершённого документа"));
        }
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Файл пустой"));
        }

        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) originalName = "file";
        String ext = originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf('.')) : "";
        String storedName = UUID.randomUUID() + ext;

        Path uploadDir = Paths.get(uploadBaseDir).toAbsolutePath().normalize();
        Files.createDirectories(uploadDir);
        Path target = uploadDir.resolve(storedName).normalize();
        if (!target.startsWith(uploadDir)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Небезопасное имя файла"));
        }
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

        docFileRepository.findByDocumentDocumentIdAndIsCurrentTrue(id).forEach(f -> {
            f.setIsCurrent(false);
            docFileRepository.save(f);
        });

        DocFile docFile = new DocFile();
        docFile.setDocument(doc);
        docFile.setFileName(originalName);
        docFile.setFilePath(storedName);
        docFile.setFileSize(file.getSize());
        docFile.setMimeType(file.getContentType());
        docFile.setUploadedBy(user);
        docFile.setUploadedAt(LocalDateTime.now());

        Integer maxVersion = docFileRepository.findTopByDocumentDocumentIdOrderByVersionNumberDesc(id)
                .map(DocFile::getVersionNumber).orElse(0);
        docFile.setVersionNumber(maxVersion + 1);
        docFile.setIsCurrent(true);

        docFileRepository.save(docFile);
        logHistory(doc, user, "ADD_FILE", originalName);
        return ResponseEntity.ok(docFile);
    }

    @GetMapping("/{id}/files/{fileId}/download")
    public ResponseEntity<?> downloadFile(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
            @PathVariable Long id,
            @PathVariable Long fileId) throws IOException {

        Long userId = extractUserId(authHeader);
        User user = userRepository.findById(userId).orElseThrow();
        Document doc = documentRepository.findById(id).orElseThrow();
        if (!hasAccessToDocument(user, doc)) {
            return ResponseEntity.status(403).body(Map.of("error", "Нет доступа"));
        }

        DocFile docFile = docFileRepository.findById(fileId).orElseThrow();
        if (!docFile.getDocument().getDocumentId().equals(id)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Файл не относится к документу"));
        }

        Path path = Paths.get(uploadBaseDir).toAbsolutePath().normalize()
                .resolve(docFile.getFilePath()).normalize();
        Resource resource = new FileSystemResource(path);
        if (!resource.exists()) {
            return ResponseEntity.status(404).body(Map.of("error", "Файл не найден на диске"));
        }

        String mime = docFile.getMimeType() != null ? docFile.getMimeType() : "application/octet-stream";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + docFile.getFileName() + "\"")
                .contentType(MediaType.parseMediaType(mime))
                .body(resource);
    }

    // =====================================================================
    // МАРШРУТ
    // =====================================================================

    @PostMapping("/{id}/route")
    @Transactional
    public ResponseEntity<?> createRoute(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {

        Long userId = extractUserId(authHeader);
        User author = userRepository.findById(userId).orElseThrow();
        Document doc = documentRepository.findById(id).orElseThrow();

        boolean isAuthor = doc.getAuthor().getUserId().equals(userId);
        boolean isAdmin = "ADMIN".equals(author.getBaseRole().getCode());
        if (!isAuthor && !isAdmin) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "Только автор или администратор может создать маршрут"));
        }

        if (!"DRAFT".equals(doc.getStatus()) && !"REVISION".equals(doc.getStatus())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Маршрут можно создать только для черновика или документа на доработке"));
        }

        List<DocRoute> previousRoutes = docRouteRepository.findByDocumentDocumentIdOrderByStepNumberAsc(id);
        LocalDateTime now = LocalDateTime.now();
        for (DocRoute r : previousRoutes) {
            if (NON_TERMINAL_ROUTE_STATUSES.contains(r.getStatus())) {
                r.setStatus("CANCELLED");
                r.setCompletedAt(now);
                docRouteRepository.save(r);
            }
        }

        Object stagesObj = body.get("stages");
        if (!(stagesObj instanceof List)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Поле stages должно быть массивом"));
        }
        @SuppressWarnings("unchecked")
        List<Object> rawStages = (List<Object>) stagesObj;
        if (rawStages.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Маршрут не может быть пустым"));
        }

        List<Map<String, Object>> stages = new ArrayList<>();
        for (Object o : rawStages) {
            if (!(o instanceof Map)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Некорректный формат этапа маршрута"));
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) o;
            stages.add(m);
        }

        int prevIdx = -1;
        Set<String> providedActions = new LinkedHashSet<>();
        for (Map<String, Object> s : stages) {
            String action = (String) s.get("actionCode");
            int idx = STAGE_ORDER.indexOf(action);
            if (idx < 0) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Недопустимое действие в маршруте: " + action));
            }
            if (idx <= prevIdx) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Нарушена последовательность этапов: " + action));
            }
            prevIdx = idx;
            providedActions.add(action);
        }

        Set<String> required = requiredActionsFor(doc);
        Set<String> missing = new LinkedHashSet<>(required);
        missing.removeAll(providedActions);
        if (!missing.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "В маршруте отсутствуют обязательные этапы: " + missing));
        }

        int step = 1;
        LocalDateTime previousDeadline = null;

        for (Map<String, Object> stageMap : stages) {
            Long assigneeId = asLong(stageMap.get("assigneeId"));
            String stageCode = (String) stageMap.get("stageCode");
            String actionCode = (String) stageMap.get("actionCode");

            if (assigneeId == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Не указан исполнитель для этапа " + actionCode));
            }
            User assignee = userRepository.findById(assigneeId).orElse(null);
            if (assignee == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Пользователь не найден"));
            }
            if (!Boolean.TRUE.equals(assignee.getIsActive())) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Пользователь " + assignee.getTabNumber() + " деактивирован"));
            }
            if (!hasPermission(assignee, actionCode, doc)) {
                return ResponseEntity.status(403)
                        .body(Map.of("error", "У пользователя " + assignee.getTabNumber()
                                + " нет полномочий на действие " + actionCode));
            }

            if (stageMap.get("deadline") == null || stageMap.get("deadline").toString().isBlank()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Не указан срок для этапа " + actionCode));
            }
            LocalDateTime deadline;
            try {
                deadline = LocalDateTime.parse(stageMap.get("deadline").toString());
            } catch (DateTimeParseException e) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Неверный формат срока для этапа " + actionCode));
            }
            if (deadline.isBefore(LocalDateTime.now())) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Срок не может быть в прошлом (" + actionCode + ")"));
            }

            if (previousDeadline != null && deadline.isBefore(previousDeadline)) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Срок этапа " + actionCode + " не может быть раньше срока предыдущего этапа"));
            }
            previousDeadline = deadline;

            DocRoute route = new DocRoute();
            route.setDocument(doc);
            route.setStage(routeStageRepository.findByCode(stageCode).orElseThrow(
                    () -> new IllegalArgumentException("Неизвестный этап: " + stageCode)));
            route.setAction(docActionRepository.findByCode(actionCode).orElseThrow(
                    () -> new IllegalArgumentException("Неизвестное действие: " + actionCode)));
            route.setAssignedUser(assignee);
            route.setStepNumber(step);
            route.setCreatedAt(now);
            route.setStatus(step == 1 ? "PENDING" : "WAITING");
            route.setDeadline(deadline);
            if (step == 1) route.setStartedAt(now);

            docRouteRepository.save(route);
            step++;
        }

        if (previousDeadline != null) {
            doc.setDeadline(previousDeadline);
        }

        doc.setStatus("IN_PROGRESS");
        doc.setUpdatedAt(now);
        documentRepository.save(doc);
        logHistory(doc, author, "ROUTE", null);

        return ResponseEntity.ok(Map.of("message", "Маршрут успешно создан"));
    }

    // =====================================================================
    // РЕГИСТРАЦИЯ
    // =====================================================================

    @PostMapping("/{id}/register")
    @Transactional
    public ResponseEntity<?> registerDocument(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
            @PathVariable Long id) {

        Long userId = extractUserId(authHeader);
        User user = userRepository.findById(userId).orElseThrow();
        Document doc = documentRepository.findById(id).orElseThrow();

        if (doc.getRegistrationNumber() != null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Документ уже зарегистрирован"));
        }
        if (doc.getNomenclature() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "У документа не задана номенклатура дел"));
        }
        if (!"IN_PROGRESS".equals(doc.getStatus())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Регистрация возможна только для документа в работе"));
        }
        if (!hasPermission(user, "REGISTER", doc)) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "Недостаточно полномочий для регистрации"));
        }

        List<DocRoute> routes = docRouteRepository.findByDocumentDocumentIdOrderByStepNumberAsc(id);

        boolean approveDone = routes.stream()
                .anyMatch(r -> "APPROVE".equals(r.getAction().getCode())
                        && "COMPLETED".equals(r.getStatus()));
        boolean signDone = routes.stream()
                .anyMatch(r -> "SIGN".equals(r.getAction().getCode())
                        && "COMPLETED".equals(r.getStatus()));
        boolean reviewExists = routes.stream()
                .anyMatch(r -> "REVIEW".equals(r.getAction().getCode()));

        if (!approveDone || !signDone) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Сначала должны быть завершены согласование и подписание"));
        }
        if (!reviewExists) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "В маршруте отсутствует этап рассмотрения"));
        }

        DocRoute registerRoute = routes.stream()
                .filter(r -> "REGISTER".equals(r.getAction().getCode()))
                .filter(r -> NON_TERMINAL_ROUTE_STATUSES.contains(r.getStatus()))
                .findFirst().orElse(null);

        boolean isAdmin = "ADMIN".equals(user.getBaseRole().getCode());
        if (registerRoute != null && !isAdmin
                && !registerRoute.getAssignedUser().getUserId().equals(userId)) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "Вы не назначены регистратором по этому документу"));
        }

        Nomenclature lockedNom = nomenclatureRepository.findByIdForUpdate(doc.getNomenclature().getNomenclatureId())
                .orElseThrow(() -> new IllegalArgumentException("Номенклатура не найдена"));

        String deptCode = doc.getDepartment().getCode();
        String caseCode = lockedNom.getCaseCode();
        long seq = documentRepository.countRegistrationsByDeptAndCase(
                doc.getDepartment().getDepartmentId(),
                lockedNom.getNomenclatureId()) + 1;
        String regNumber = String.format("%s-%s-%03d", deptCode, caseCode, seq);

        if (documentRepository.findByRegistrationNumber(regNumber).isPresent()) {
            return ResponseEntity.status(409)
                    .body(Map.of("error", "Регистрационный номер уже существует, повторите"));
        }

        LocalDateTime now = LocalDateTime.now();
        doc.setRegistrationNumber(regNumber);
        doc.setRegistrationDate(LocalDate.now());
        doc.setUpdatedAt(now);
        documentRepository.save(doc);

        if (registerRoute != null) {
            registerRoute.setStatus("COMPLETED");
            registerRoute.setCompletedAt(now);
            docRouteRepository.save(registerRoute);
        }

        boolean reviewActivated = false;
        for (DocRoute r : routes) {
            if ("REVIEW".equals(r.getAction().getCode()) && "WAITING".equals(r.getStatus())) {
                r.setStatus("PENDING");
                r.setStartedAt(now);
                docRouteRepository.save(r);
                reviewActivated = true;
                break;
            }
        }

        logHistory(doc, user, "REGISTER", null);
        return ResponseEntity.ok(Map.of(
                "message", "Документ зарегистрирован",
                "registrationNumber", regNumber,
                "reviewActivated", reviewActivated
        ));
    }

    // =====================================================================
    // ДЕЙСТВИЯ
    // =====================================================================

    @PostMapping("/{id}/action")
    @Transactional
    public ResponseEntity<?> performAction(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {

        Long userId = extractUserId(authHeader);
        String actionCode = (String) body.get("actionCode");
        String comment = (String) body.get("comment");

        if (actionCode == null || actionCode.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Не указан actionCode"));
        }

        if ("REGISTER".equals(actionCode)) {
            return registerDocument(authHeader, id);
        }

        User user = userRepository.findById(userId).orElseThrow();
        Document doc = documentRepository.findById(id).orElseThrow();

        DocRoute activeRoute;
        Object routeIdObj = body.get("routeId");
        if (routeIdObj != null && !routeIdObj.toString().isBlank()) {
            Long routeId = asLong(routeIdObj);
            activeRoute = docRouteRepository.findById(routeId).orElse(null);
            if (activeRoute == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Маршрут не найден"));
            }
            if (!activeRoute.getDocument().getDocumentId().equals(id)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Маршрут не относится к документу"));
            }
            if (!activeRoute.getAssignedUser().getUserId().equals(userId)) {
                return ResponseEntity.status(403).body(Map.of("error", "Маршрут назначен другому пользователю"));
            }
            if (!Set.of("PENDING", "IN_PROGRESS").contains(activeRoute.getStatus())) {
                return ResponseEntity.badRequest().body(Map.of("error", "Маршрут уже завершён"));
            }
        } else {
            if (OVERRIDE_ACTIONS.contains(actionCode)) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Для действия " + actionCode + " обязателен routeId"));
            }
            activeRoute = docRouteRepository
                    .findActiveRouteByUserAndAction(id, userId, actionCode)
                    .orElse(null);
            if (activeRoute == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Нет активной задачи по этому документу для вас"));
            }
        }

        String originalAction = activeRoute.getAction().getCode();

        if (!OVERRIDE_ACTIONS.contains(actionCode) && !originalAction.equals(actionCode)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Действие " + actionCode + " не соответствует этапу " + originalAction));
        }

        if ("REJECT".equals(actionCode) && (comment == null || comment.isBlank())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Комментарий обязателен при отклонении"));
        }

        if (!OVERRIDE_ACTIONS.contains(actionCode) && !hasPermission(user, actionCode, doc)) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "Недостаточно полномочий для действия " + actionCode));
        }

        List<DocRoute> allRoutes = docRouteRepository.findByDocumentDocumentIdOrderByStepNumberAsc(id);

        boolean hasEarlierActive = allRoutes.stream()
                .filter(r -> r.getStepNumber() < activeRoute.getStepNumber())
                .anyMatch(r -> NON_TERMINAL_ROUTE_STATUSES.contains(r.getStatus()));
        if (hasEarlierActive) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Предыдущие этапы маршрута не завершены"));
        }

        if ("REVIEW".equals(actionCode) && doc.getRegistrationNumber() == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Документ ещё не зарегистрирован"));
        }

        if ("EXECUTE".equals(actionCode)) {
            if (doc.getRegistrationNumber() == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Документ ещё не зарегистрирован"));
            }
            boolean reviewDone = allRoutes.stream()
                    .anyMatch(r -> "REVIEW".equals(r.getAction().getCode())
                            && "COMPLETED".equals(r.getStatus()));
            if (!reviewDone) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Документ не прошёл рассмотрение"));
            }
        }

        LocalDateTime now = LocalDateTime.now();

        if ("REJECT".equals(actionCode)) {
            activeRoute.setStatus("REJECTED");
            activeRoute.setComment(comment);
            activeRoute.setCompletedAt(now);
            docRouteRepository.save(activeRoute);

            for (DocRoute r : allRoutes) {
                if (r.getStepNumber() > activeRoute.getStepNumber()
                        && NON_TERMINAL_ROUTE_STATUSES.contains(r.getStatus())) {
                    r.setStatus("CANCELLED");
                    r.setCompletedAt(now);
                    docRouteRepository.save(r);
                }
            }

            doc.setStatus("REVISION");
            doc.setUpdatedAt(now);
            documentRepository.save(doc);
            logHistory(doc, user, actionCode, comment);
            return ResponseEntity.ok(Map.of("message", "Документ отклонён и возвращён автору"));
        }

        if ("REDIRECT".equals(actionCode)) {
            if (body.get("newAssigneeId") == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Не указан новый исполнитель"));
            }
            Long newAssigneeId = asLong(body.get("newAssigneeId"));
            User newAssignee = userRepository.findById(newAssigneeId).orElse(null);
            if (newAssignee == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Пользователь не найден"));
            }
            if (!Boolean.TRUE.equals(newAssignee.getIsActive())) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Новый исполнитель деактивирован"));
            }
            if (!hasPermission(newAssignee, originalAction, doc)) {
                return ResponseEntity.status(403)
                        .body(Map.of("error", "У нового исполнителя нет полномочий на " + originalAction));
            }
            if (newAssignee.getUserId().equals(userId)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Нельзя перенаправить на себя"));
            }

            activeRoute.setStatus("COMPLETED");
            activeRoute.setComment(comment);
            activeRoute.setCompletedAt(now);
            docRouteRepository.save(activeRoute);

            DocRoute newRoute = new DocRoute();
            newRoute.setDocument(doc);
            newRoute.setParentRoute(activeRoute);
            newRoute.setStage(activeRoute.getStage());
            newRoute.setAction(activeRoute.getAction());
            newRoute.setAssignedUser(newAssignee);
            newRoute.setStepNumber(activeRoute.getStepNumber());
            newRoute.setStatus("PENDING");
            newRoute.setCreatedAt(now);
            newRoute.setStartedAt(now);
            newRoute.setDeadline(activeRoute.getDeadline());
            docRouteRepository.save(newRoute);

            doc.setUpdatedAt(now);
            documentRepository.save(doc);
            logHistory(doc, user, actionCode, comment);
            return ResponseEntity.ok(Map.of("message", "Задача перенаправлена"));
        }

        activeRoute.setStatus("COMPLETED");
        activeRoute.setComment(comment);
        activeRoute.setCompletedAt(now);
        docRouteRepository.save(activeRoute);

        List<DocRoute> fresh = docRouteRepository.findByDocumentDocumentIdOrderByStepNumberAsc(id);
        Optional<DocRoute> nextWaiting = fresh.stream()
                .filter(r -> r.getStepNumber() > activeRoute.getStepNumber())
                .filter(r -> "WAITING".equals(r.getStatus()))
                .filter(r -> !"REVIEW".equals(r.getAction().getCode()))
                .min(Comparator.comparingInt(DocRoute::getStepNumber));
        nextWaiting.ifPresent(r -> {
            r.setStatus("PENDING");
            r.setStartedAt(now);
            docRouteRepository.save(r);
        });

        boolean anyActive = docRouteRepository.findByDocumentDocumentIdOrderByStepNumberAsc(id)
                .stream().anyMatch(r -> NON_TERMINAL_ROUTE_STATUSES.contains(r.getStatus()));
        if (!anyActive) {
            doc.setStatus("COMPLETED");
            doc.setCompletedAt(now);
        }
        doc.setUpdatedAt(now);
        documentRepository.save(doc);
        logHistory(doc, user, actionCode, comment);

        return ResponseEntity.ok(Map.of("message", "Действие выполнено успешно"));
    }

    // =====================================================================
    // ИСТОРИЯ / МАРШРУТ
    // =====================================================================

    @GetMapping("/{id}/history")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getHistory(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
            @PathVariable Long id) {
        Long userId = extractUserId(authHeader);
        User user = userRepository.findById(userId).orElseThrow();
        Document doc = documentRepository.findById(id).orElseThrow();
        if (!hasAccessToDocument(user, doc)) {
            return ResponseEntity.status(403).body(Map.of("error", "Нет доступа"));
        }
        return ResponseEntity.ok(docHistoryRepository.findByDocumentDocumentIdOrderByCreatedAtDesc(id));
    }

    @GetMapping("/{id}/routes")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getDocumentRoutes(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
            @PathVariable Long id) {
        Long userId = extractUserId(authHeader);
        User user = userRepository.findById(userId).orElseThrow();
        Document doc = documentRepository.findById(id).orElseThrow();
        if (!hasAccessToDocument(user, doc)) {
            return ResponseEntity.status(403).body(Map.of("error", "Нет доступа"));
        }
        return ResponseEntity.ok(docRouteRepository.findByDocumentDocumentIdOrderByStepNumberAsc(id));
    }

    // =====================================================================
    // ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ
    // =====================================================================

    private Long extractUserId(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Нет Bearer-токена");
        }
        return JwtUtil.extractUserId(authHeader.substring(7));
    }

    private static Long asLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        String s = o.toString().trim();
        if (s.isEmpty()) return null;
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Некорректный числовой идентификатор: " + s);
        }
    }

    private static String buildShortName(User u) {
        if (u == null) return "—";
        String first = u.getFirstName() != null ? u.getFirstName() : "";
        String middle = u.getMiddleName() != null && !u.getMiddleName().isBlank()
                ? " " + u.getMiddleName().charAt(0) + "." : "";
        String firstInitial = first.isEmpty() ? "" : " " + first.charAt(0) + ".";
        return (u.getLastName() == null ? "" : u.getLastName()) + firstInitial + middle;
    }

    private boolean isUserInRoute(Long userId, Long docId) {
        return docRouteRepository.findByDocumentDocumentIdOrderByStepNumberAsc(docId)
                .stream().anyMatch(r -> r.getAssignedUser().getUserId().equals(userId));
    }

    private boolean hasAccessToDocument(User user, Document doc) {
        boolean isAdmin = "ADMIN".equals(user.getBaseRole().getCode());
        boolean isAuthor = doc.getAuthor().getUserId().equals(user.getUserId());
        boolean isInRoute = isUserInRoute(user.getUserId(), doc.getDocumentId());
        boolean sameDept = doc.getDepartment() != null
                && user.getDepartment() != null
                && doc.getDepartment().getDepartmentId()
                .equals(user.getDepartment().getDepartmentId());
        return isAdmin || isAuthor || isInRoute || sameDept;
    }

    private Set<String> requiredActionsFor(Document doc) {
        Set<String> required = new LinkedHashSet<>(
                List.of("APPROVE", "SIGN", "REGISTER", "REVIEW"));
        if (doc.getDocumentType() != null) {
            String code = doc.getDocumentType().getCode();
            if ("ORGANIZATIONAL".equals(code)) required.add("READ");
            else if ("ADMINISTRATIVE".equals(code)) required.add("EXECUTE");
        }
        return required;
    }

    private boolean computeCanEdit(User user, Document doc) {
        if (!EDITABLE_DOC_STATUSES.contains(doc.getStatus())) return false;
        boolean isAuthor = doc.getAuthor().getUserId().equals(user.getUserId());
        boolean isAdmin = "ADMIN".equals(user.getBaseRole().getCode());
        return isAuthor || isAdmin;
    }

    private boolean computeCanRegister(User user, Document doc, List<DocRoute> routes) {
        if (doc.getRegistrationNumber() != null) return false;
        if (!"IN_PROGRESS".equals(doc.getStatus())) return false;
        if (!hasPermission(user, "REGISTER", doc)) return false;

        boolean approveDone = routes.stream()
                .anyMatch(r -> "APPROVE".equals(r.getAction().getCode())
                        && "COMPLETED".equals(r.getStatus()));
        boolean signDone = routes.stream()
                .anyMatch(r -> "SIGN".equals(r.getAction().getCode())
                        && "COMPLETED".equals(r.getStatus()));
        if (!approveDone || !signDone) return false;

        DocRoute registerRoute = routes.stream()
                .filter(r -> "REGISTER".equals(r.getAction().getCode()))
                .filter(r -> NON_TERMINAL_ROUTE_STATUSES.contains(r.getStatus()))
                .findFirst().orElse(null);

        if (registerRoute == null) return true;
        boolean isAdmin = "ADMIN".equals(user.getBaseRole().getCode());
        return isAdmin || registerRoute.getAssignedUser().getUserId().equals(user.getUserId());
    }

    private boolean hasPermission(User user, String actionCode, Document doc) {
        String permCode = switch (actionCode) {
            case "APPROVE"  -> "APPROVE_DOCUMENT";
            case "SIGN"     -> "SIGN_DOCUMENT";
            case "REGISTER" -> "REGISTER_DOCUMENT";
            case "REVIEW"   -> "REVIEW_DOCUMENT";
            case "EXECUTE"  -> "EXECUTE_DOCUMENT";
            case "REDIRECT" -> "REDIRECT_DOCUMENT";
            case "READ"     -> "VIEW_DOCUMENT";
            case "REJECT"   -> "VIEW_DOCUMENT";
            default         -> "VIEW_DOCUMENT";
        };

        Permission permission = permissionRepository.findByCode(permCode).orElse(null);
        if (permission == null) {
            return "READ".equals(actionCode) || "REJECT".equals(actionCode);
        }

        Long permId = permission.getPermissionId();
        Long docTypeId = doc.getDocumentType() != null ? doc.getDocumentType().getDocumentTypeId() : null;
        Long catId = doc.getCategory() != null ? doc.getCategory().getCategoryId() : null;
        Long deptId = doc.getDepartment() != null ? doc.getDepartment().getDepartmentId() : null;
        Long sectorId = doc.getSector() != null ? doc.getSector().getSectorId() : null;

        boolean hasUserPerm = userPermissionRepository.findByUserUserId(user.getUserId()).stream().anyMatch(p ->
                p.getPermission().getPermissionId().equals(permId)
                        && Boolean.TRUE.equals(p.getIsAllowed())
                        && (p.getDocumentType() == null || p.getDocumentType().getDocumentTypeId().equals(docTypeId))
                        && (p.getCategory() == null || (catId != null && p.getCategory().getCategoryId().equals(catId)))
                        && (p.getDepartment() == null || (deptId != null && p.getDepartment().getDepartmentId().equals(deptId)))
                        && (p.getSector() == null || (sectorId != null && p.getSector().getSectorId().equals(sectorId)))
                        && (p.getExpiresAt() == null || p.getExpiresAt().isAfter(LocalDateTime.now()))
        );
        if (hasUserPerm) return true;

        if (user.getPosition() == null) return false;
        return posPermissionRepository.findByPositionPositionIdAndIsAllowedTrue(
                user.getPosition().getPositionId()).stream().anyMatch(p ->
                p.getPermission().getPermissionId().equals(permId)
                        && (p.getDocumentType() == null || p.getDocumentType().getDocumentTypeId().equals(docTypeId))
                        && (p.getCategory() == null || (catId != null && p.getCategory().getCategoryId().equals(catId)))
                        && (p.getDepartment() == null || (deptId != null && p.getDepartment().getDepartmentId().equals(deptId)))
                        && (p.getSector() == null || (sectorId != null && p.getSector().getSectorId().equals(sectorId)))
        );
    }

    private void logHistory(Document doc, User user, String actionCode, String comment) {
        DocAction action = docActionRepository.findByCode(actionCode).orElseGet(() -> {
            DocAction a = new DocAction();
            a.setCode(actionCode);
            a.setName(actionCode);
            return docActionRepository.save(a);
        });
        DocHistory history = new DocHistory();
        history.setDocument(doc);
        history.setUser(user);
        history.setAction(action);
        history.setComment(comment);
        history.setCreatedAt(LocalDateTime.now());
        docHistoryRepository.save(history);
    }
}