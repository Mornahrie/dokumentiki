package com.example.kursach.controller;

import com.example.kursach.dto.UserShortDto;
import com.example.kursach.repository.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    @GetMapping("/nomenclatures")  public ResponseEntity<?> nomenclatures()  { return ResponseEntity.ok(nomenclatureRepository.findByIsActiveTrue()); }
    @GetMapping("/doc-types")      public ResponseEntity<?> docTypes()       { return ResponseEntity.ok(docTypeRepository.findByIsActiveTrue()); }
    @GetMapping("/doc-categories") public ResponseEntity<?> docCategories()  { return ResponseEntity.ok(docCategoryRepository.findByIsActiveTrue()); }
    @GetMapping("/route-stages")   public ResponseEntity<?> routeStages()    { return ResponseEntity.ok(routeStageRepository.findAll()); }
    @GetMapping("/doc-actions")    public ResponseEntity<?> docActions()     { return ResponseEntity.ok(docActionRepository.findAll()); }
    @GetMapping("/positions")      public ResponseEntity<?> positions()      { return ResponseEntity.ok(positionRepository.findAll()); }
    @GetMapping("/base-roles")     public ResponseEntity<?> baseRoles()      { return ResponseEntity.ok(baseRoleRepository.findAll()); }

    /** Минимальные данные о пользователях — без паролей и lazy-связей. */
    @GetMapping("/users")
    public ResponseEntity<?> users() {
        return ResponseEntity.ok(
                userRepository.findByIsActiveTrue().stream().map(UserShortDto::from).toList()
        );
    }
}