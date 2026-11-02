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
    private final NomenclatureRepository nomenclatureRepository;
    private final DocTypeRepository docTypeRepository;
    private final DocCategoryRepository docCategoryRepository;
    private final RouteStageRepository routeStageRepository;
    private final DocActionRepository docActionRepository;
    private final PositionRepository positionRepository;
    private final BaseRoleRepository baseRoleRepository;
    private final UserRepository userRepository;

    public ReferenceController(DepartmentRepository departmentRepository,
                               SectorRepository sectorRepository,
                               NomenclatureRepository nomenclatureRepository,
                               DocTypeRepository docTypeRepository,
                               DocCategoryRepository docCategoryRepository,
                               RouteStageRepository routeStageRepository,
                               DocActionRepository docActionRepository,
                               PositionRepository positionRepository,
                               BaseRoleRepository baseRoleRepository,
                               UserRepository userRepository) {
        this.departmentRepository = departmentRepository;
        this.sectorRepository = sectorRepository;
        this.nomenclatureRepository = nomenclatureRepository;
        this.docTypeRepository = docTypeRepository;
        this.docCategoryRepository = docCategoryRepository;
        this.routeStageRepository = routeStageRepository;
        this.docActionRepository = docActionRepository;
        this.positionRepository = positionRepository;
        this.baseRoleRepository = baseRoleRepository;
        this.userRepository = userRepository;
    }

    @GetMapping("/departments")    public ResponseEntity<?> departments()    { return ResponseEntity.ok(departmentRepository.findByIsActiveTrue()); }
    @GetMapping("/sectors")        public ResponseEntity<?> sectors()        { return ResponseEntity.ok(sectorRepository.findByIsActiveTrue()); }
    @GetMapping("/doc-types")      public ResponseEntity<?> docTypes()       { return ResponseEntity.ok(docTypeRepository.findByIsActiveTrue()); }
    @GetMapping("/doc-categories") public ResponseEntity<?> docCategories()  { return ResponseEntity.ok(docCategoryRepository.findByIsActiveTrue()); }
    @GetMapping("/route-stages")   public ResponseEntity<?> routeStages()    { return ResponseEntity.ok(routeStageRepository.findAll()); }
    @GetMapping("/doc-actions")    public ResponseEntity<?> docActions()     { return ResponseEntity.ok(docActionRepository.findAll()); }
    @GetMapping("/positions")      public ResponseEntity<?> positions()      { return ResponseEntity.ok(positionRepository.findAll()); }
    @GetMapping("/base-roles")     public ResponseEntity<?> baseRoles()      { return ResponseEntity.ok(baseRoleRepository.findAll()); }

    @GetMapping("/nomenclatures")
    @Transactional(readOnly = true)
    public ResponseEntity<?> nomenclatures() {
        List<Map<String, Object>> result = nomenclatureRepository.findByIsActiveTrue().stream().map(n -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("nomenclatureId", n.getNomenclatureId());
            m.put("caseCode", n.getCaseCode());
            m.put("title", n.getTitle());
            m.put("retentionPeriod", n.getRetentionPeriod());
            m.put("isActive", n.getIsActive());

            m.put("departmentId", n.getDepartment() != null ? n.getDepartment().getDepartmentId() : null);
            m.put("departmentName", n.getDepartment() != null ? n.getDepartment().getName() : null);

            if (n.getDocumentType() != null) {
                Map<String, Object> t = new LinkedHashMap<>();
                t.put("documentTypeId", n.getDocumentType().getDocumentTypeId());
                t.put("code", n.getDocumentType().getCode());
                t.put("name", n.getDocumentType().getName());
                m.put("documentType", t);
            } else {
                m.put("documentType", null);
            }

            if (n.getCategory() != null) {
                Map<String, Object> c = new LinkedHashMap<>();
                c.put("categoryId", n.getCategory().getCategoryId());
                c.put("code", n.getCategory().getCode());
                c.put("name", n.getCategory().getName());
                m.put("category", c);
            } else {
                m.put("category", null);
            }

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