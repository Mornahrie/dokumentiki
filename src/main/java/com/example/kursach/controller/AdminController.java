package com.example.kursach.controller;

import com.example.kursach.entity.*;
import com.example.kursach.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "*")
public class AdminController {

    private static final Logger logger = LoggerFactory.getLogger(AdminController.class);
    private static final String DEFAULT_PASSWORD = "12345678";

    private final UserRepository userRepository;
    private final BaseRoleRepository baseRoleRepository;
    private final DepartmentRepository departmentRepository;
    private final SectorRepository sectorRepository;
    private final NomenclatureRepository nomenclatureRepository;
    private final NomenclatureCaseRepository nomenclatureCaseRepository;
    private final DocTypeRepository docTypeRepository;
    private final DocCategoryRepository docCategoryRepository;
    private final PositionRepository positionRepository;
    private final PermissionRepository permissionRepository;
    private final UserPermissionRepository userPermissionRepository;
    private final PosPermissionRepository posPermissionRepository;
    private final DocActionRepository docActionRepository;
    private final RouteStageRepository routeStageRepository;
    private final DocumentRepository documentRepository;
    private final DocRouteRepository docRouteRepository;
    private final DocHistoryRepository docHistoryRepository;
    private final DocFileRepository docFileRepository;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AdminController(UserRepository userRepository, BaseRoleRepository baseRoleRepository,
                           DepartmentRepository departmentRepository, SectorRepository sectorRepository,
                           NomenclatureRepository nomenclatureRepository,
                           NomenclatureCaseRepository nomenclatureCaseRepository,
                           DocTypeRepository docTypeRepository, DocCategoryRepository docCategoryRepository,
                           PositionRepository positionRepository, PermissionRepository permissionRepository,
                           UserPermissionRepository userPermissionRepository,
                           PosPermissionRepository posPermissionRepository,
                           DocActionRepository docActionRepository,
                           RouteStageRepository routeStageRepository,
                           DocumentRepository documentRepository,
                           DocRouteRepository docRouteRepository,
                           DocHistoryRepository docHistoryRepository,
                           DocFileRepository docFileRepository) {
        this.userRepository = userRepository;
        this.baseRoleRepository = baseRoleRepository;
        this.departmentRepository = departmentRepository;
        this.sectorRepository = sectorRepository;
        this.nomenclatureRepository = nomenclatureRepository;
        this.nomenclatureCaseRepository = nomenclatureCaseRepository;
        this.docTypeRepository = docTypeRepository;
        this.docCategoryRepository = docCategoryRepository;
        this.positionRepository = positionRepository;
        this.permissionRepository = permissionRepository;
        this.userPermissionRepository = userPermissionRepository;
        this.posPermissionRepository = posPermissionRepository;
        this.docActionRepository = docActionRepository;
        this.routeStageRepository = routeStageRepository;
        this.documentRepository = documentRepository;
        this.docRouteRepository = docRouteRepository;
        this.docHistoryRepository = docHistoryRepository;
        this.docFileRepository = docFileRepository;
    }

    // ================= USERS =================
    @GetMapping("/users")
    public ResponseEntity<?> getAllUsers() {
        return ResponseEntity.ok(userRepository.findAllByOrderByLastNameAscFirstNameAsc());
    }

    @PostMapping("/users")
    @Transactional
    public ResponseEntity<?> createUser(@RequestBody Map<String, Object> body) {
        if (userRepository.existsByTabNumber((String) body.get("tabNumber"))) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Пользователь с таким табельным номером уже существует"));
        }
        User user = new User();
        user.setTabNumber((String) body.get("tabNumber"));
        user.setPasswordHash(encoder.encode((String) body.getOrDefault("password", DEFAULT_PASSWORD)));
        user.setIsTemporaryPassword(true);
        user.setPasswordChangedAt(LocalDateTime.now());
        user.setFirstName((String) body.get("firstName"));
        user.setLastName((String) body.get("lastName"));
        user.setMiddleName((String) body.get("middleName"));
        user.setDepartment(departmentRepository.findById(asLong(body.get("departmentId"))).orElseThrow());
        if (body.get("sectorId") != null && !body.get("sectorId").toString().isEmpty()) {
            user.setSector(sectorRepository.findById(asLong(body.get("sectorId"))).orElse(null));
        }
        user.setPosition(positionRepository.findById(asLong(body.get("positionId"))).orElseThrow());
        user.setBaseRole(baseRoleRepository.findByCode(body.getOrDefault("roleCode", "EMPLOYEE").toString()).orElseThrow());
        user.setIsActive(true);
        user.setCreatedAt(LocalDateTime.now());
        userRepository.save(user);
        return ResponseEntity.ok(user);
    }

    @PutMapping("/users/{id}")
    @Transactional
    public ResponseEntity<?> updateUser(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        User user = userRepository.findById(id).orElseThrow();
        if (body.containsKey("firstName")) user.setFirstName((String) body.get("firstName"));
        if (body.containsKey("lastName")) user.setLastName((String) body.get("lastName"));
        if (body.containsKey("middleName")) user.setMiddleName((String) body.get("middleName"));
        if (body.containsKey("departmentId"))
            user.setDepartment(departmentRepository.findById(asLong(body.get("departmentId"))).orElseThrow());
        if (body.containsKey("sectorId")) {
            Long sid = asLong(body.get("sectorId"));
            user.setSector(sid == null ? null : sectorRepository.findById(sid).orElse(null));
        }
        if (body.containsKey("positionId"))
            user.setPosition(positionRepository.findById(asLong(body.get("positionId"))).orElseThrow());
        if (body.containsKey("roleCode"))
            user.setBaseRole(baseRoleRepository.findByCode(body.get("roleCode").toString()).orElseThrow());
        userRepository.save(user);
        return ResponseEntity.ok(user);
    }

    /**
     * Удаление пользователя: если есть связанные данные — деактивируем, иначе удаляем полностью.
     */
    @DeleteMapping("/users/{id}")
    @Transactional
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        User user = userRepository.findById(id).orElseThrow();
        boolean hasRelated =
                !documentRepository.findByAuthorUserId(id).isEmpty()
                        || !docRouteRepository.findByAssignedUserUserId(id).isEmpty()
                        || !docHistoryRepository.findByUserUserId(id).isEmpty()
                        || !userPermissionRepository.findByUserUserId(id).isEmpty()
                        || !docFileRepository.findByUploadedByUserId(id).isEmpty();
        if (hasRelated) {
            user.setIsActive(false);
            userRepository.save(user);
            return ResponseEntity.ok(Map.of("message", "Пользователь деактивирован: есть связанные данные"));
        }
        userRepository.delete(user);
        return ResponseEntity.ok(Map.of("message", "Пользователь удалён полностью"));
    }

    // ================= USER PERMISSIONS =================
    @GetMapping("/users/{id}/permissions")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getUserPermissions(@PathVariable Long id) {
        List<Map<String, Object>> result = userPermissionRepository.findByUserUserId(id).stream().map(p -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("userPermissionId", p.getUserPermissionId());
            m.put("permissionCode", p.getPermission().getCode());
            m.put("permissionName", p.getPermission().getName());
            m.put("documentTypeId", extractId(p.getDocumentType()));
            m.put("documentTypeName", p.getDocumentType() != null ? p.getDocumentType().getName() : "Все");
            m.put("categoryId", extractId(p.getCategory()));
            m.put("categoryName", p.getCategory() != null ? p.getCategory().getName() : "Все");
            m.put("departmentId", extractId(p.getDepartment()));
            m.put("departmentName", p.getDepartment() != null ? p.getDepartment().getName() : "Все");
            m.put("sectorId", extractId(p.getSector()));
            m.put("sectorName", p.getSector() != null ? p.getSector().getName() : "Все");
            m.put("isAllowed", p.getIsAllowed());
            m.put("expiresAt", p.getExpiresAt());
            return m;
        }).toList();
        return ResponseEntity.ok(result);
    }

    @PostMapping("/users/{id}/permissions")
    @Transactional
    public ResponseEntity<?> grantPermission(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        User user = userRepository.findById(id).orElseThrow();

        String permCode = (String) body.get("permissionCode");
        if (permCode == null || permCode.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Не указан permissionCode"));
        }
        Permission perm = permissionRepository.findByCode(permCode)
                .orElseThrow(() -> new IllegalArgumentException("Полномочие не найдено: " + permCode));

        DocType docType = resolveDocType(body.get("documentTypeId"));
        DocCategory category = resolveCategory(body.get("categoryId"));
        Department department = resolveDepartment(body.get("departmentId"));
        Sector sector = resolveSector(body.get("sectorId"));

        if (docType != null && category != null
                && (docType.getCategory() == null
                || !docType.getCategory().getCategoryId().equals(category.getCategoryId()))) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Тип не относится к выбранной категории"));
        }

        boolean duplicate = userPermissionRepository.findByUserUserId(id).stream().anyMatch(p ->
                p.getPermission().getPermissionId().equals(perm.getPermissionId())
                        && Objects.equals(extractId(p.getDocumentType()), extractId(docType))
                        && Objects.equals(extractId(p.getCategory()), extractId(category))
                        && Objects.equals(extractId(p.getDepartment()), extractId(department))
                        && Objects.equals(extractId(p.getSector()), extractId(sector))
        );
        if (duplicate) {
            return ResponseEntity.badRequest().body(Map.of("error", "Такое полномочие уже выдано"));
        }

        UserPermission up = new UserPermission();
        up.setUser(user);
        up.setPermission(perm);
        up.setDocumentType(docType);
        up.setCategory(category);
        up.setDepartment(department);
        up.setSector(sector);
        up.setIsAllowed(true);

        if (body.get("expiresAt") != null && !body.get("expiresAt").toString().isBlank()) {
            try {
                up.setExpiresAt(LocalDateTime.parse(body.get("expiresAt").toString()));
            } catch (DateTimeParseException e) {
                return ResponseEntity.badRequest().body(Map.of("error", "Неверный формат expiresAt"));
            }
        }
        userPermissionRepository.save(up);
        return ResponseEntity.ok(up);
    }

    @DeleteMapping("/users/{id}/permissions/{permId}")
    @Transactional
    public ResponseEntity<?> revokeUserPermission(@PathVariable Long id, @PathVariable Long permId) {
        userPermissionRepository.deleteById(permId);
        return ResponseEntity.ok(Map.of("message", "Полномочие отозвано"));
    }

    // ================= POSITION PERMISSIONS =================
    @GetMapping("/positions/{id}/permissions")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getPositionPermissions(@PathVariable Long id) {
        List<Map<String, Object>> result = posPermissionRepository.findByPositionPositionId(id).stream().map(p -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("positionPermissionId", p.getPositionPermissionId());
            m.put("permissionCode", p.getPermission().getCode());
            m.put("permissionName", p.getPermission().getName());
            m.put("documentTypeId", extractId(p.getDocumentType()));
            m.put("documentTypeName", p.getDocumentType() != null ? p.getDocumentType().getName() : "Все");
            m.put("categoryId", extractId(p.getCategory()));
            m.put("categoryName", p.getCategory() != null ? p.getCategory().getName() : "Все");
            m.put("departmentId", extractId(p.getDepartment()));
            m.put("departmentName", p.getDepartment() != null ? p.getDepartment().getName() : "Все");
            m.put("sectorId", extractId(p.getSector()));
            m.put("sectorName", p.getSector() != null ? p.getSector().getName() : "Все");
            m.put("isAllowed", p.getIsAllowed());
            return m;
        }).toList();
        return ResponseEntity.ok(result);
    }

    @PostMapping("/positions/{id}/permissions")
    @Transactional
    public ResponseEntity<?> grantPositionPermission(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Position pos = positionRepository.findById(id).orElseThrow();

        String permCode = (String) body.get("permissionCode");
        if (permCode == null || permCode.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Не указан permissionCode"));
        }
        Permission perm = permissionRepository.findByCode(permCode)
                .orElseThrow(() -> new IllegalArgumentException("Полномочие не найдено: " + permCode));

        DocType docType = resolveDocType(body.get("documentTypeId"));
        DocCategory category = resolveCategory(body.get("categoryId"));
        Department department = resolveDepartment(body.get("departmentId"));
        Sector sector = resolveSector(body.get("sectorId"));

        if (docType != null && category != null
                && (docType.getCategory() == null
                || !docType.getCategory().getCategoryId().equals(category.getCategoryId()))) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Тип не относится к выбранной категории"));
        }

        boolean duplicate = posPermissionRepository.findByPositionPositionId(id).stream().anyMatch(p ->
                p.getPermission().getPermissionId().equals(perm.getPermissionId())
                        && Objects.equals(extractId(p.getDocumentType()), extractId(docType))
                        && Objects.equals(extractId(p.getCategory()), extractId(category))
                        && Objects.equals(extractId(p.getDepartment()), extractId(department))
                        && Objects.equals(extractId(p.getSector()), extractId(sector))
        );
        if (duplicate) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Такое полномочие уже назначено должности"));
        }

        PosPermission pp = new PosPermission();
        pp.setPosition(pos);
        pp.setPermission(perm);
        pp.setDocumentType(docType);
        pp.setCategory(category);
        pp.setDepartment(department);
        pp.setSector(sector);
        pp.setIsAllowed(true);
        posPermissionRepository.save(pp);
        return ResponseEntity.ok(pp);
    }

    @DeleteMapping("/positions/{id}/permissions/{permId}")
    @Transactional
    public ResponseEntity<?> revokePositionPermission(@PathVariable Long id, @PathVariable Long permId) {
        posPermissionRepository.deleteById(permId);
        return ResponseEntity.ok(Map.of("message", "Полномочие отозвано"));
    }

    // ================= SIMPLE LISTS =================
    @GetMapping("/permissions")    public ResponseEntity<?> getAllPermissions() { return ResponseEntity.ok(permissionRepository.findAll()); }
    @GetMapping("/positions")      public ResponseEntity<?> getPositions()      { return ResponseEntity.ok(positionRepository.findAll()); }
    @GetMapping("/base-roles")     public ResponseEntity<?> getBaseRoles()      { return ResponseEntity.ok(baseRoleRepository.findAll()); }
    @GetMapping("/departments")    public ResponseEntity<?> getDepartments()    { return ResponseEntity.ok(departmentRepository.findAll()); }
    @GetMapping("/sectors")        public ResponseEntity<?> getSectors()        { return ResponseEntity.ok(sectorRepository.findAllByOrderByNameAsc()); }
    @GetMapping("/doc-actions")    public ResponseEntity<?> getDocActions()     { return ResponseEntity.ok(docActionRepository.findAll()); }
    @GetMapping("/route-stages")   public ResponseEntity<?> getRouteStages()    { return ResponseEntity.ok(routeStageRepository.findAll()); }

    @GetMapping("/doc-categories")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getDocCategories() {
        return ResponseEntity.ok(docCategoryRepository.findAll());
    }

    @GetMapping("/doc-types")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getDocTypes() {
        List<Map<String, Object>> result = docTypeRepository.findAll().stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("documentTypeId", t.getDocumentTypeId());
            m.put("code", t.getCode());
            m.put("name", t.getName());
            m.put("isActive", t.getIsActive());
            if (t.getCategory() != null) {
                Map<String, Object> cat = new LinkedHashMap<>();
                cat.put("categoryId", t.getCategory().getCategoryId());
                cat.put("code", t.getCategory().getCode());
                cat.put("name", t.getCategory().getName());
                m.put("category", cat);
            }
            return m;
        }).toList();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/nomenclatures")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getNomenclatures() {
        List<Map<String, Object>> result = nomenclatureCaseRepository.findAll().stream().map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("nomenclatureId", c.getCaseId());
            m.put("caseId", c.getCaseId());
            m.put("caseCode", c.getCaseCode());
            m.put("title", c.getTitle());
            m.put("retentionPeriod", c.getRetentionPeriod());
            m.put("isActive", c.getIsActive());

            if (c.getNomenclature() != null) {
                Map<String, Object> nom = new LinkedHashMap<>();
                nom.put("nomenclatureId", c.getNomenclature().getNomenclatureId());
                nom.put("name", c.getNomenclature().getName());
                nom.put("year", c.getNomenclature().getYear());
                m.put("nomenclature", nom);

                if (c.getNomenclature().getDepartment() != null) {
                    Map<String, Object> dept = new LinkedHashMap<>();
                    dept.put("departmentId", c.getNomenclature().getDepartment().getDepartmentId());
                    dept.put("code", c.getNomenclature().getDepartment().getCode());
                    dept.put("name", c.getNomenclature().getDepartment().getName());
                    m.put("department", dept);
                }
            }
            if (c.getCategory() != null) {
                Map<String, Object> cat = new LinkedHashMap<>();
                cat.put("categoryId", c.getCategory().getCategoryId());
                cat.put("code", c.getCategory().getCode());
                cat.put("name", c.getCategory().getName());
                m.put("category", cat);
            }
            if (c.getType() != null) {
                Map<String, Object> t = new LinkedHashMap<>();
                t.put("documentTypeId", c.getType().getDocumentTypeId());
                t.put("code", c.getType().getCode());
                t.put("name", c.getType().getName());
                m.put("type", t);
            }
            return m;
        }).toList();
        return ResponseEntity.ok(result);
    }

    // ================= DEPARTMENTS CRUD =================
    @PostMapping("/departments")
    public ResponseEntity<?> createDepartment(@RequestBody Map<String, Object> b) {
        Department d = new Department();
        d.setCode((String) b.get("code"));
        d.setName((String) b.get("name"));
        d.setIsActive(true);
        return ResponseEntity.ok(departmentRepository.save(d));
    }
    @PutMapping("/departments/{id}")
    public ResponseEntity<?> updateDepartment(@PathVariable Long id, @RequestBody Map<String, Object> b) {
        Department d = departmentRepository.findById(id).orElseThrow();
        if (b.containsKey("code")) d.setCode((String) b.get("code"));
        if (b.containsKey("name")) d.setName((String) b.get("name"));
        return ResponseEntity.ok(departmentRepository.save(d));
    }
    @DeleteMapping("/departments/{id}")
    @Transactional
    public ResponseEntity<?> deleteDepartment(@PathVariable Long id) {
        Department d = departmentRepository.findById(id).orElseThrow();
        boolean hasRelated =
                !userRepository.findByDepartmentDepartmentId(id).isEmpty()
                        || !sectorRepository.findByDepartmentDepartmentId(id).isEmpty()
                        || !nomenclatureRepository.findByDepartmentDepartmentId(id).isEmpty()
                        || !documentRepository.findByDepartmentDepartmentId(id).isEmpty()
                        || !posPermissionRepository.findByDepartmentDepartmentId(id).isEmpty()
                        || !userPermissionRepository.findByDepartmentDepartmentId(id).isEmpty();
        if (hasRelated) {
            d.setIsActive(false);
            departmentRepository.save(d);
            return ResponseEntity.ok(Map.of("message", "Подразделение деактивировано: есть связанные данные"));
        }
        departmentRepository.delete(d);
        return ResponseEntity.ok(Map.of("message", "Подразделение удалено полностью"));
    }

    // ================= SECTORS CRUD =================
    @PostMapping("/sectors")
    public ResponseEntity<?> createSector(@RequestBody Map<String, Object> b) {
        Sector s = new Sector();
        s.setCode((String) b.get("code"));
        s.setName((String) b.get("name"));
        s.setIsActive(true);
        s.setDepartment(departmentRepository.findById(asLong(b.get("departmentId"))).orElseThrow());
        return ResponseEntity.ok(sectorRepository.save(s));
    }
    @PutMapping("/sectors/{id}")
    public ResponseEntity<?> updateSector(@PathVariable Long id, @RequestBody Map<String, Object> b) {
        Sector s = sectorRepository.findById(id).orElseThrow();
        if (b.containsKey("code")) s.setCode((String) b.get("code"));
        if (b.containsKey("name")) s.setName((String) b.get("name"));
        if (b.containsKey("departmentId"))
            s.setDepartment(departmentRepository.findById(asLong(b.get("departmentId"))).orElseThrow());
        return ResponseEntity.ok(sectorRepository.save(s));
    }
    @DeleteMapping("/sectors/{id}")
    @Transactional
    public ResponseEntity<?> deleteSector(@PathVariable Long id) {
        Sector s = sectorRepository.findById(id).orElseThrow();
        boolean hasRelated =
                !userRepository.findBySectorSectorId(id).isEmpty()
                        || !documentRepository.findBySectorSectorId(id).isEmpty()
                        || !posPermissionRepository.findBySectorSectorId(id).isEmpty()
                        || !userPermissionRepository.findBySectorSectorId(id).isEmpty();
        if (hasRelated) {
            s.setIsActive(false);
            sectorRepository.save(s);
            return ResponseEntity.ok(Map.of("message", "Сектор деактивирован: есть связанные данные"));
        }
        sectorRepository.delete(s);
        return ResponseEntity.ok(Map.of("message", "Сектор удалён полностью"));
    }

    // ================= NOMENCLATURES CRUD =================
    @PostMapping("/nomenclatures")
    @Transactional
    public ResponseEntity<?> createNomenclature(@RequestBody Map<String, Object> b) {
        Long departmentId = asLong(b.get("departmentId"));
        Long categoryId   = asLong(b.get("categoryId"));
        Long typeId       = asLong(b.get("typeId"));

        if (departmentId == null) return ResponseEntity.badRequest()
                .body(Map.of("error", "Подразделение обязательно"));
        if (categoryId == null) return ResponseEntity.badRequest()
                .body(Map.of("error", "Категория обязательна"));
        if (typeId == null) return ResponseEntity.badRequest()
                .body(Map.of("error", "Тип документа обязателен"));

        Department dept = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new IllegalArgumentException("Подразделение не найдено"));
        DocCategory cat = docCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("Категория не найдена"));
        DocType type = docTypeRepository.findById(typeId)
                .orElseThrow(() -> new IllegalArgumentException("Тип не найден"));

        if (type.getCategory() == null
                || !type.getCategory().getCategoryId().equals(cat.getCategoryId())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Тип не относится к выбранной категории"));
        }

        int year = LocalDate.now().getYear();
        Nomenclature header = nomenclatureRepository
                .findFirstByDepartmentDepartmentIdAndYear(departmentId, year)
                .orElseGet(() -> {
                    Nomenclature n = new Nomenclature();
                    n.setDepartment(dept);
                    n.setName("Номенклатура дел на " + year + " год");
                    n.setYear(year);
                    n.setIsActive(true);
                    return nomenclatureRepository.save(n);
                });

        NomenclatureCase c = new NomenclatureCase();
        c.setNomenclature(header);
        c.setCategory(cat);
        c.setType(type);
        c.setCaseCode((String) b.get("caseCode"));
        c.setTitle((String) b.get("title"));
        if (b.get("retentionPeriod") != null && !b.get("retentionPeriod").toString().isBlank()) {
            c.setRetentionPeriod(Integer.valueOf(b.get("retentionPeriod").toString()));
        }
        c.setIsActive(true);

        return ResponseEntity.ok(nomenclatureCaseRepository.save(c));
    }

    @PutMapping("/nomenclatures/{id}")
    @Transactional
    public ResponseEntity<?> updateNomenclature(@PathVariable Long id, @RequestBody Map<String, Object> b) {
        NomenclatureCase c = nomenclatureCaseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Номенклатура не найдена"));

        if (b.containsKey("caseCode"))        c.setCaseCode((String) b.get("caseCode"));
        if (b.containsKey("title"))           c.setTitle((String) b.get("title"));
        if (b.containsKey("retentionPeriod") && !b.get("retentionPeriod").toString().isBlank())
            c.setRetentionPeriod(Integer.valueOf(b.get("retentionPeriod").toString()));

        if (b.containsKey("categoryId") && b.get("categoryId") != null) {
            c.setCategory(docCategoryRepository.findById(asLong(b.get("categoryId")))
                    .orElseThrow(() -> new IllegalArgumentException("Категория не найдена")));
        }
        if (b.containsKey("typeId") && b.get("typeId") != null) {
            c.setType(docTypeRepository.findById(asLong(b.get("typeId")))
                    .orElseThrow(() -> new IllegalArgumentException("Тип не найден")));
        }

        if (b.containsKey("departmentId") && b.get("departmentId") != null) {
            Department dept = departmentRepository.findById(asLong(b.get("departmentId")))
                    .orElseThrow(() -> new IllegalArgumentException("Подразделение не найдено"));
            int year = LocalDate.now().getYear();
            Nomenclature header = nomenclatureRepository
                    .findFirstByDepartmentDepartmentIdAndYear(dept.getDepartmentId(), year)
                    .orElseGet(() -> {
                        Nomenclature n = new Nomenclature();
                        n.setDepartment(dept);
                        n.setName("Номенклатура дел на " + year + " год");
                        n.setYear(year);
                        n.setIsActive(true);
                        return nomenclatureRepository.save(n);
                    });
            c.setNomenclature(header);
        }

        if (c.getType() != null && c.getCategory() != null
                && (c.getType().getCategory() == null
                || !c.getType().getCategory().getCategoryId().equals(c.getCategory().getCategoryId()))) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Тип не относится к выбранной категории"));
        }

        return ResponseEntity.ok(nomenclatureCaseRepository.save(c));
    }

    @DeleteMapping("/nomenclatures/{id}")
    @Transactional
    public ResponseEntity<?> deleteNomenclature(@PathVariable Long id) {
        NomenclatureCase c = nomenclatureCaseRepository.findById(id).orElseThrow();
        boolean hasRelated = !documentRepository.findByNomenclatureCaseId(id).isEmpty();
        if (hasRelated) {
            c.setIsActive(false);
            nomenclatureCaseRepository.save(c);
            return ResponseEntity.ok(Map.of("message", "Номенклатура деактивирована: есть связанные документы"));
        }
        nomenclatureCaseRepository.delete(c);
        return ResponseEntity.ok(Map.of("message", "Номенклатура удалена полностью"));
    }

    // ================= DOC CATEGORIES CRUD (родитель) =================
    @PostMapping("/doc-categories")
    public ResponseEntity<?> createDocCategory(@RequestBody Map<String, Object> b) {
        DocCategory c = new DocCategory();
        c.setCode((String) b.get("code"));
        c.setName((String) b.get("name"));
        c.setIsActive(true);
        return ResponseEntity.ok(docCategoryRepository.save(c));
    }
    @PutMapping("/doc-categories/{id}")
    public ResponseEntity<?> updateDocCategory(@PathVariable Long id, @RequestBody Map<String, Object> b) {
        DocCategory c = docCategoryRepository.findById(id).orElseThrow();
        if (b.containsKey("code")) c.setCode((String) b.get("code"));
        if (b.containsKey("name")) c.setName((String) b.get("name"));
        return ResponseEntity.ok(docCategoryRepository.save(c));
    }
    @DeleteMapping("/doc-categories/{id}")
    @Transactional
    public ResponseEntity<?> deleteDocCategory(@PathVariable Long id) {
        DocCategory c = docCategoryRepository.findById(id).orElseThrow();
        boolean hasRelated =
                !docTypeRepository.findByCategoryCategoryId(id).isEmpty()
                        || !documentRepository.findByCategoryCategoryId(id).isEmpty()
                        || !posPermissionRepository.findByCategoryCategoryId(id).isEmpty()
                        || !userPermissionRepository.findByCategoryCategoryId(id).isEmpty()
                        || !nomenclatureCaseRepository.findByCategoryCategoryId(id).isEmpty();
        if (hasRelated) {
            c.setIsActive(false);
            docCategoryRepository.save(c);
            return ResponseEntity.ok(Map.of("message", "Категория деактивирована: есть связанные данные"));
        }
        docCategoryRepository.delete(c);
        return ResponseEntity.ok(Map.of("message", "Категория удалена полностью"));
    }

    // ================= DOC TYPES CRUD (дочерний) =================
    @PostMapping("/doc-types")
    public ResponseEntity<?> createDocType(@RequestBody Map<String, Object> b) {
        if (b.get("categoryId") == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Категория обязательна"));
        }
        DocType t = new DocType();
        t.setCode((String) b.get("code"));
        t.setName((String) b.get("name"));
        t.setIsActive(true);
        t.setCategory(docCategoryRepository.findById(asLong(b.get("categoryId"))).orElseThrow());
        return ResponseEntity.ok(docTypeRepository.save(t));
    }
    @PutMapping("/doc-types/{id}")
    public ResponseEntity<?> updateDocType(@PathVariable Long id, @RequestBody Map<String, Object> b) {
        DocType t = docTypeRepository.findById(id).orElseThrow();
        if (b.containsKey("code")) t.setCode((String) b.get("code"));
        if (b.containsKey("name")) t.setName((String) b.get("name"));
        if (b.containsKey("categoryId") && b.get("categoryId") != null)
            t.setCategory(docCategoryRepository.findById(asLong(b.get("categoryId"))).orElseThrow());
        return ResponseEntity.ok(docTypeRepository.save(t));
    }
    @DeleteMapping("/doc-types/{id}")
    @Transactional
    public ResponseEntity<?> deleteDocType(@PathVariable Long id) {
        DocType t = docTypeRepository.findById(id).orElseThrow();
        boolean hasRelated =
                !documentRepository.findByDocumentTypeDocumentTypeId(id).isEmpty()
                        || !posPermissionRepository.findByDocumentTypeDocumentTypeId(id).isEmpty()
                        || !userPermissionRepository.findByDocumentTypeDocumentTypeId(id).isEmpty()
                        || !nomenclatureCaseRepository.findByTypeDocumentTypeId(id).isEmpty();
        if (hasRelated) {
            t.setIsActive(false);
            docTypeRepository.save(t);
            return ResponseEntity.ok(Map.of("message", "Тип документа деактивирован: есть связанные данные"));
        }
        docTypeRepository.delete(t);
        return ResponseEntity.ok(Map.of("message", "Тип документа удалён полностью"));
    }

    // ================= DOC ACTIONS =================
    @PostMapping("/doc-actions")
    public ResponseEntity<?> createDocAction(@RequestBody Map<String, Object> b) {
        DocAction a = new DocAction();
        a.setCode((String) b.get("code"));
        a.setName((String) b.get("name"));
        a.setDescription((String) b.get("description"));
        return ResponseEntity.ok(docActionRepository.save(a));
    }
    @PutMapping("/doc-actions/{id}")
    public ResponseEntity<?> updateDocAction(@PathVariable Long id, @RequestBody Map<String, Object> b) {
        DocAction a = docActionRepository.findById(id).orElseThrow();
        if (b.containsKey("code")) a.setCode((String) b.get("code"));
        if (b.containsKey("name")) a.setName((String) b.get("name"));
        if (b.containsKey("description")) a.setDescription((String) b.get("description"));
        return ResponseEntity.ok(docActionRepository.save(a));
    }
    /** У действия нет isActive — либо полное удаление, либо отказ. */
    @DeleteMapping("/doc-actions/{id}")
    @Transactional
    public ResponseEntity<?> deleteDocAction(@PathVariable Long id) {
        DocAction a = docActionRepository.findById(id).orElseThrow();
        boolean hasRelated =
                !docRouteRepository.findByActionActionId(id).isEmpty()
                        || !docHistoryRepository.findByActionActionId(id).isEmpty();
        if (hasRelated) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Действие используется в маршрутах или истории. Удаление невозможно."));
        }
        docActionRepository.delete(a);
        return ResponseEntity.ok(Map.of("message", "Действие удалено полностью"));
    }

    // ================= ROUTE STAGES =================
    @PostMapping("/route-stages")
    public ResponseEntity<?> createRouteStage(@RequestBody Map<String, Object> b) {
        RouteStage s = new RouteStage();
        s.setCode((String) b.get("code"));
        s.setName((String) b.get("name"));
        s.setDescription((String) b.get("description"));
        return ResponseEntity.ok(routeStageRepository.save(s));
    }
    @PutMapping("/route-stages/{id}")
    public ResponseEntity<?> updateRouteStage(@PathVariable Long id, @RequestBody Map<String, Object> b) {
        RouteStage s = routeStageRepository.findById(id).orElseThrow();
        if (b.containsKey("code")) s.setCode((String) b.get("code"));
        if (b.containsKey("name")) s.setName((String) b.get("name"));
        if (b.containsKey("description")) s.setDescription((String) b.get("description"));
        return ResponseEntity.ok(routeStageRepository.save(s));
    }
    @DeleteMapping("/route-stages/{id}")
    @Transactional
    public ResponseEntity<?> deleteRouteStage(@PathVariable Long id) {
        RouteStage s = routeStageRepository.findById(id).orElseThrow();
        boolean hasRelated = !docRouteRepository.findByStageStageId(id).isEmpty();
        if (hasRelated) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Этап используется в маршрутах документов. Удаление невозможно."));
        }
        routeStageRepository.delete(s);
        return ResponseEntity.ok(Map.of("message", "Этап удалён полностью"));
    }

    // ================= BASE ROLES =================
    @PostMapping("/base-roles")
    public ResponseEntity<?> createBaseRole(@RequestBody Map<String, Object> b) {
        BaseRole r = new BaseRole();
        r.setCode((String) b.get("code"));
        r.setName((String) b.get("name"));
        return ResponseEntity.ok(baseRoleRepository.save(r));
    }
    @PutMapping("/base-roles/{id}")
    public ResponseEntity<?> updateBaseRole(@PathVariable Long id, @RequestBody Map<String, Object> b) {
        BaseRole r = baseRoleRepository.findById(id).orElseThrow();
        if (b.containsKey("code")) r.setCode((String) b.get("code"));
        if (b.containsKey("name")) r.setName((String) b.get("name"));
        return ResponseEntity.ok(baseRoleRepository.save(r));
    }
    @DeleteMapping("/base-roles/{id}")
    @Transactional
    public ResponseEntity<?> deleteBaseRole(@PathVariable Long id) {
        BaseRole r = baseRoleRepository.findById(id).orElseThrow();
        boolean hasRelated = !userRepository.findByBaseRoleBaseRoleId(id).isEmpty();
        if (hasRelated) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Роль назначена пользователям. Удаление невозможно."));
        }
        baseRoleRepository.delete(r);
        return ResponseEntity.ok(Map.of("message", "Роль удалена полностью"));
    }

    // ================= POSITIONS =================
    @PostMapping("/positions")
    public ResponseEntity<?> createPosition(@RequestBody Map<String, Object> b) {
        Position p = new Position();
        p.setCode((String) b.get("code"));
        p.setName((String) b.get("name"));
        return ResponseEntity.ok(positionRepository.save(p));
    }
    @PutMapping("/positions/{id}")
    public ResponseEntity<?> updatePosition(@PathVariable Long id, @RequestBody Map<String, Object> b) {
        Position p = positionRepository.findById(id).orElseThrow();
        if (b.containsKey("code")) p.setCode((String) b.get("code"));
        if (b.containsKey("name")) p.setName((String) b.get("name"));
        return ResponseEntity.ok(positionRepository.save(p));
    }
    @DeleteMapping("/positions/{id}")
    @Transactional
    public ResponseEntity<?> deletePosition(@PathVariable Long id) {
        Position p = positionRepository.findById(id).orElseThrow();
        boolean hasRelated =
                !userRepository.findByPositionPositionId(id).isEmpty()
                        || !posPermissionRepository.findByPositionPositionId(id).isEmpty();
        if (hasRelated) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Должность назначена пользователям или имеет полномочия. Удаление невозможно."));
        }
        positionRepository.delete(p);
        return ResponseEntity.ok(Map.of("message", "Должность удалена полностью"));
    }

    // ================= PERMISSIONS =================
    @PostMapping("/permissions/create")
    public ResponseEntity<?> createPermission(@RequestBody Map<String, Object> b) {
        Permission p = new Permission();
        p.setCode((String) b.get("code"));
        p.setName((String) b.get("name"));
        p.setDescription((String) b.get("description"));
        return ResponseEntity.ok(permissionRepository.save(p));
    }
    @PutMapping("/permissions/{id}")
    public ResponseEntity<?> updatePermission(@PathVariable Long id, @RequestBody Map<String, Object> b) {
        Permission p = permissionRepository.findById(id).orElseThrow();
        if (b.containsKey("code")) p.setCode((String) b.get("code"));
        if (b.containsKey("name")) p.setName((String) b.get("name"));
        if (b.containsKey("description")) p.setDescription((String) b.get("description"));
        return ResponseEntity.ok(permissionRepository.save(p));
    }
    @DeleteMapping("/permissions/{id}")
    @Transactional
    public ResponseEntity<?> deletePermission(@PathVariable Long id) {
        Permission p = permissionRepository.findById(id).orElseThrow();
        boolean hasRelated =
                !posPermissionRepository.findByPermissionPermissionId(id).isEmpty()
                        || !userPermissionRepository.findByPermissionPermissionId(id).isEmpty();
        if (hasRelated) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Полномочие выдано пользователям или должностям. Удаление невозможно."));
        }
        permissionRepository.delete(p);
        return ResponseEntity.ok(Map.of("message", "Полномочие удалено полностью"));
    }

    // ================= HELPERS =================
    private static Long asLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        String s = o.toString().trim();
        if (s.isEmpty() || "null".equals(s)) return null;
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Некорректный идентификатор: " + s);
        }
    }

    private DocType resolveDocType(Object o) {
        Long id = asLong(o);
        return id == null ? null : docTypeRepository.findById(id).orElse(null);
    }

    private DocCategory resolveCategory(Object o) {
        Long id = asLong(o);
        return id == null ? null : docCategoryRepository.findById(id).orElse(null);
    }

    private Department resolveDepartment(Object o) {
        Long id = asLong(o);
        return id == null ? null : departmentRepository.findById(id).orElse(null);
    }

    private Sector resolveSector(Object o) {
        Long id = asLong(o);
        return id == null ? null : sectorRepository.findById(id).orElse(null);
    }

    private static Long extractId(Object entity) {
        if (entity == null) return null;
        if (entity instanceof DocType t) return t.getDocumentTypeId();
        if (entity instanceof DocCategory c) return c.getCategoryId();
        if (entity instanceof Department d) return d.getDepartmentId();
        if (entity instanceof Sector s) return s.getSectorId();
        return null;
    }
}