package com.example.kursach.controller;

import com.example.kursach.entity.*;
import com.example.kursach.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

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
    private final DocTypeRepository docTypeRepository;
    private final DocCategoryRepository docCategoryRepository;
    private final PositionRepository positionRepository;
    private final PermissionRepository permissionRepository;
    private final UserPermissionRepository userPermissionRepository;
    private final PosPermissionRepository posPermissionRepository;
    private final DocActionRepository docActionRepository;
    private final RouteStageRepository routeStageRepository;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AdminController(UserRepository userRepository, BaseRoleRepository baseRoleRepository,
                           DepartmentRepository departmentRepository, SectorRepository sectorRepository,
                           NomenclatureRepository nomenclatureRepository, DocTypeRepository docTypeRepository,
                           DocCategoryRepository docCategoryRepository, PositionRepository positionRepository,
                           PermissionRepository permissionRepository, UserPermissionRepository userPermissionRepository,
                           PosPermissionRepository posPermissionRepository, DocActionRepository docActionRepository,
                           RouteStageRepository routeStageRepository) {
        this.userRepository = userRepository;
        this.baseRoleRepository = baseRoleRepository;
        this.departmentRepository = departmentRepository;
        this.sectorRepository = sectorRepository;
        this.nomenclatureRepository = nomenclatureRepository;
        this.docTypeRepository = docTypeRepository;
        this.docCategoryRepository = docCategoryRepository;
        this.positionRepository = positionRepository;
        this.permissionRepository = permissionRepository;
        this.userPermissionRepository = userPermissionRepository;
        this.posPermissionRepository = posPermissionRepository;
        this.docActionRepository = docActionRepository;
        this.routeStageRepository = routeStageRepository;
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

    @DeleteMapping("/users/{id}")
    @Transactional
    public ResponseEntity<?> softDeleteUser(@PathVariable Long id) {
        User user = userRepository.findById(id).orElseThrow();
        user.setIsActive(false);
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("message", "Пользователь исключен"));
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

    // ================= PERMISSIONS / POSITIONS / ROLES =================
    @GetMapping("/permissions")    public ResponseEntity<?> getAllPermissions() { return ResponseEntity.ok(permissionRepository.findAll()); }
    @GetMapping("/positions")      public ResponseEntity<?> getPositions()      { return ResponseEntity.ok(positionRepository.findAll()); }
    @GetMapping("/base-roles")     public ResponseEntity<?> getBaseRoles()      { return ResponseEntity.ok(baseRoleRepository.findAll()); }

    // ================= СПРАВОЧНИКИ =================
    @GetMapping("/departments")    public ResponseEntity<?> getDepartments()   { return ResponseEntity.ok(departmentRepository.findAll()); }
    @GetMapping("/sectors")        public ResponseEntity<?> getSectors()       { return ResponseEntity.ok(sectorRepository.findAllByOrderByNameAsc()); }
    @GetMapping("/nomenclatures")  public ResponseEntity<?> getNomenclatures() { return ResponseEntity.ok(nomenclatureRepository.findAll()); }
    @GetMapping("/doc-types")      public ResponseEntity<?> getDocTypes()      { return ResponseEntity.ok(docTypeRepository.findAll()); }
    @GetMapping("/doc-categories") public ResponseEntity<?> getDocCategories() { return ResponseEntity.ok(docCategoryRepository.findAll()); }
    @GetMapping("/doc-actions")    public ResponseEntity<?> getDocActions()    { return ResponseEntity.ok(docActionRepository.findAll()); }
    @GetMapping("/route-stages")   public ResponseEntity<?> getRouteStages()   { return ResponseEntity.ok(routeStageRepository.findAll()); }

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
    public ResponseEntity<?> deleteDepartment(@PathVariable Long id) {
        Department d = departmentRepository.findById(id).orElseThrow();
        d.setIsActive(false);
        departmentRepository.save(d);
        return ResponseEntity.ok(Map.of("message", "Удалено"));
    }

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
    public ResponseEntity<?> deleteSector(@PathVariable Long id) {
        Sector s = sectorRepository.findById(id).orElseThrow();
        s.setIsActive(false);
        sectorRepository.save(s);
        return ResponseEntity.ok(Map.of("message", "Удалено"));
    }

    @PostMapping("/nomenclatures")
    public ResponseEntity<?> createNomenclature(@RequestBody Map<String, Object> b) {
        Nomenclature n = new Nomenclature();
        n.setCaseCode((String) b.get("caseCode"));
        n.setTitle((String) b.get("title"));
        n.setIsActive(true);
        n.setRetentionPeriod(Integer.valueOf(b.get("retentionPeriod").toString()));
        n.setDepartment(departmentRepository.findById(asLong(b.get("departmentId"))).orElseThrow());
        return ResponseEntity.ok(nomenclatureRepository.save(n));
    }
    @PutMapping("/nomenclatures/{id}")
    public ResponseEntity<?> updateNomenclature(@PathVariable Long id, @RequestBody Map<String, Object> b) {
        Nomenclature n = nomenclatureRepository.findById(id).orElseThrow();
        if (b.containsKey("caseCode")) n.setCaseCode((String) b.get("caseCode"));
        if (b.containsKey("title")) n.setTitle((String) b.get("title"));
        if (b.containsKey("retentionPeriod")) n.setRetentionPeriod(Integer.valueOf(b.get("retentionPeriod").toString()));
        if (b.containsKey("departmentId"))
            n.setDepartment(departmentRepository.findById(asLong(b.get("departmentId"))).orElseThrow());
        return ResponseEntity.ok(nomenclatureRepository.save(n));
    }
    @DeleteMapping("/nomenclatures/{id}")
    public ResponseEntity<?> deleteNomenclature(@PathVariable Long id) {
        Nomenclature n = nomenclatureRepository.findById(id).orElseThrow();
        n.setIsActive(false);
        nomenclatureRepository.save(n);
        return ResponseEntity.ok(Map.of("message", "Удалено"));
    }

    @PostMapping("/doc-types")
    public ResponseEntity<?> createDocType(@RequestBody Map<String, Object> b) {
        DocType t = new DocType();
        t.setCode((String) b.get("code"));
        t.setName((String) b.get("name"));
        t.setIsActive(true);
        return ResponseEntity.ok(docTypeRepository.save(t));
    }
    @PutMapping("/doc-types/{id}")
    public ResponseEntity<?> updateDocType(@PathVariable Long id, @RequestBody Map<String, Object> b) {
        DocType t = docTypeRepository.findById(id).orElseThrow();
        if (b.containsKey("code")) t.setCode((String) b.get("code"));
        if (b.containsKey("name")) t.setName((String) b.get("name"));
        return ResponseEntity.ok(docTypeRepository.save(t));
    }
    @DeleteMapping("/doc-types/{id}")
    public ResponseEntity<?> deleteDocType(@PathVariable Long id) {
        DocType t = docTypeRepository.findById(id).orElseThrow();
        t.setIsActive(false);
        docTypeRepository.save(t);
        return ResponseEntity.ok(Map.of("message", "Удалено"));
    }

    @PostMapping("/doc-categories")
    public ResponseEntity<?> createDocCategory(@RequestBody Map<String, Object> b) {
        DocCategory c = new DocCategory();
        c.setCode((String) b.get("code"));
        c.setName((String) b.get("name"));
        c.setIsActive(true);
        c.setDocumentType(docTypeRepository.findById(asLong(b.get("documentTypeId"))).orElseThrow());
        return ResponseEntity.ok(docCategoryRepository.save(c));
    }
    @PutMapping("/doc-categories/{id}")
    public ResponseEntity<?> updateDocCategory(@PathVariable Long id, @RequestBody Map<String, Object> b) {
        DocCategory c = docCategoryRepository.findById(id).orElseThrow();
        if (b.containsKey("code")) c.setCode((String) b.get("code"));
        if (b.containsKey("name")) c.setName((String) b.get("name"));
        if (b.containsKey("documentTypeId"))
            c.setDocumentType(docTypeRepository.findById(asLong(b.get("documentTypeId"))).orElseThrow());
        return ResponseEntity.ok(docCategoryRepository.save(c));
    }
    @DeleteMapping("/doc-categories/{id}")
    public ResponseEntity<?> deleteDocCategory(@PathVariable Long id) {
        DocCategory c = docCategoryRepository.findById(id).orElseThrow();
        c.setIsActive(false);
        docCategoryRepository.save(c);
        return ResponseEntity.ok(Map.of("message", "Удалено"));
    }

    // ================= DOC ACTIONS (hard delete с защитой) =================
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
    @DeleteMapping("/doc-actions/{id}")
    @Transactional
    public ResponseEntity<?> deleteDocAction(@PathVariable Long id) {
        try {
            docActionRepository.deleteById(id);
            return ResponseEntity.ok(Map.of("message", "Удалено"));
        } catch (DataIntegrityViolationException e) {
            logger.warn("Нельзя удалить действие ID={}: используется в маршрутах/истории", id);
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Действие используется в маршрутах или истории, удаление невозможно"));
        }
    }

    // ================= ROUTE STAGES (hard delete с защитой) =================
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
        try {
            routeStageRepository.deleteById(id);
            return ResponseEntity.ok(Map.of("message", "Удалено"));
        } catch (DataIntegrityViolationException e) {
            logger.warn("Нельзя удалить этап ID={}: используется в маршрутах", id);
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Этап используется в маршрутах документов, удаление невозможно"));
        }
    }

    // ================= BASE ROLES (hard delete с защитой) =================
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
        try {
            baseRoleRepository.deleteById(id);
            return ResponseEntity.ok(Map.of("message", "Удалено"));
        } catch (DataIntegrityViolationException e) {
            logger.warn("Нельзя удалить базовую роль ID={}: назначена пользователям", id);
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Роль назначена пользователям, удаление невозможно"));
        }
    }

    // ================= POSITIONS (CRUD + удаление с защитой) =================
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

    /**
     * Удаление должности. Нельзя удалить, если:
     *  - должность назначена хотя бы одному пользователю;
     *  - у должности есть связанные полномочия (PosPermission).
     */
    @DeleteMapping("/positions/{id}")
    @Transactional
    public ResponseEntity<?> deletePosition(@PathVariable Long id) {
        Position pos = positionRepository.findById(id).orElseThrow();

        if (!userRepository.findByPositionPositionId(id).isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Должность назначена пользователям. Сначала смените им должность."));
        }
        if (!posPermissionRepository.findByPositionPositionId(id).isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "У должности есть связанные полномочия. Сначала удалите их."));
        }

        positionRepository.deleteById(pos.getPositionId());
        return ResponseEntity.ok(Map.of("message", "Должность удалена"));
    }

    // ================= PERMISSIONS (CRUD + удаление с защитой) =================
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
        try {
            permissionRepository.deleteById(id);
            return ResponseEntity.ok(Map.of("message", "Удалено"));
        } catch (DataIntegrityViolationException e) {
            logger.warn("Нельзя удалить полномочие ID={}: выдано пользователям/должностям", id);
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Полномочие выдано пользователям или должностям, удаление невозможно"));
        }
    }

    // ================= helpers =================

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