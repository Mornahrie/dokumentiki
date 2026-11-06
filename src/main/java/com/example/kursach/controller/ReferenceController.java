package com.example.kursach.controller;

import com.example.kursach.dto.UserShortDto;
import com.example.kursach.repository.*;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ref")
@CrossOrigin(origins = "*")
public class ReferenceController {

    private final DepartmentRepository departmentRepository;
    private final SectorRepository sectorRepository;
    private final NomenclatureCaseRepository nomenclatureCaseRepository;
    private final DocTypeRepository docTypeRepository;
    private final DocCategoryRepository docCategoryRepository;
    private final RouteStageRepository routeStageRepository;
    private final DocActionRepository docActionRepository;
    private final PositionRepository positionRepository;
    private final BaseRoleRepository baseRoleRepository;
    private final UserRepository userRepository;

    public ReferenceController(DepartmentRepository departmentRepository,
                               SectorRepository sectorRepository,
                               NomenclatureCaseRepository nomenclatureCaseRepository,
                               DocTypeRepository docTypeRepository,
                               DocCategoryRepository docCategoryRepository,
                               RouteStageRepository routeStageRepository,
                               DocActionRepository docActionRepository,
                               PositionRepository positionRepository,
                               BaseRoleRepository baseRoleRepository,
                               UserRepository userRepository) {
        this.departmentRepository = departmentRepository;
        this.sectorRepository = sectorRepository;
        this.nomenclatureCaseRepository = nomenclatureCaseRepository;
        this.docTypeRepository = docTypeRepository;
        this.docCategoryRepository = docCategoryRepository;
        this.routeStageRepository = routeStageRepository;
        this.docActionRepository = docActionRepository;
        this.positionRepository = positionRepository;
        this.baseRoleRepository = baseRoleRepository;
        this.userRepository = userRepository;
    }

    @GetMapping("/departments")
    public ResponseEntity<?> departments()   { return ResponseEntity.ok(departmentRepository.findByIsActiveTrue()); }

    @GetMapping("/sectors")
    public ResponseEntity<?> sectors()       { return ResponseEntity.ok(sectorRepository.findByIsActiveTrue()); }

    @GetMapping("/route-stages")
    public ResponseEntity<?> routeStages()   { return ResponseEntity.ok(routeStageRepository.findAll()); }

    @GetMapping("/doc-actions")
    public ResponseEntity<?> docActions()    { return ResponseEntity.ok(docActionRepository.findAll()); }

    @GetMapping("/positions")
    public ResponseEntity<?> positions()     { return ResponseEntity.ok(positionRepository.findAll()); }

    @GetMapping("/base-roles")
    public ResponseEntity<?> baseRoles()     { return ResponseEntity.ok(baseRoleRepository.findAll()); }

    // КАТЕГОРИИ
    @GetMapping("/doc-categories")
    @Transactional(readOnly = true)
    public ResponseEntity<?> docCategories() {
        return ResponseEntity.ok(docCategoryRepository.findByIsActiveTrue());
    }

    // ТИПЫ
    @GetMapping("/doc-types")
    @Transactional(readOnly = true)
    public ResponseEntity<?> docTypes() {
        List<Map<String, Object>> result = docTypeRepository.findByIsActiveTrue().stream().map(t -> {
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

    // НОМЕНКЛАТУРЫ
    @GetMapping("/nomenclatures")
    @Transactional(readOnly = true)
    public ResponseEntity<?> nomenclatures() {
        List<Map<String, Object>> result = nomenclatureCaseRepository.findByIsActiveTrue().stream().map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("nomenclatureId", c.getCaseId());
            m.put("caseId", c.getCaseId());
            m.put("caseCode", c.getCaseCode());
            m.put("title", c.getTitle());
            m.put("retentionPeriod", c.getRetentionPeriod());
            m.put("isActive", c.getIsActive());

            if (c.getNomenclature() != null && c.getNomenclature().getDepartment() != null) {
                m.put("departmentId", c.getNomenclature().getDepartment().getDepartmentId());
                m.put("departmentName", c.getNomenclature().getDepartment().getName());
                m.put("nomenclatureYear", c.getNomenclature().getYear());
            }

            if (c.getCategory() != null) {
                Map<String, Object> cat = new LinkedHashMap<>();
                cat.put("categoryId", c.getCategory().getCategoryId());
                cat.put("code", c.getCategory().getCode());
                cat.put("name", c.getCategory().getName());
                m.put("category", cat);
            } else m.put("category", null);

            if (c.getType() != null) {
                Map<String, Object> t = new LinkedHashMap<>();
                t.put("documentTypeId", c.getType().getDocumentTypeId());
                t.put("code", c.getType().getCode());
                t.put("name", c.getType().getName());
                m.put("type", t);
            } else m.put("type", null);

            return m;
        }).toList();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/users")
    public ResponseEntity<?> users() {
        return ResponseEntity.ok(
                userRepository.findByIsActiveTrue().stream().map(UserShortDto::from).toList()
        );
    }
}