package com.example.kursach.config;

import com.example.kursach.entity.*;
import com.example.kursach.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final BaseRoleRepository baseRoleRepository;
    private final DepartmentRepository departmentRepository;
    private final PositionRepository positionRepository;

    public DataInitializer(UserRepository userRepository, BaseRoleRepository baseRoleRepository,
                           DepartmentRepository departmentRepository, PositionRepository positionRepository) {
        this.userRepository = userRepository;
        this.baseRoleRepository = baseRoleRepository;
        this.departmentRepository = departmentRepository;
        this.positionRepository = positionRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        if (!userRepository.existsByTabNumber("admin")) {
            User admin = new User();
            admin.setTabNumber("admin");
            admin.setPasswordHash(new BCryptPasswordEncoder().encode("admin"));
            admin.setFirstName("Администратор");
            admin.setLastName("Системный");
            admin.setMiddleName("");

            Department dept = departmentRepository.findByCode("ADMIN_DEPT")
                    .orElseGet(() -> {
                        Department d = new Department();
                        d.setCode("ADMIN_DEPT");
                        d.setName("Администрация");
                        d.setIsActive(true);
                        return departmentRepository.save(d);
                    });

            Position pos = positionRepository.findByCode("ADMIN_POS")
                    .orElseGet(() -> {
                        Position p = new Position();
                        p.setCode("ADMIN_POS");
                        p.setName("Администратор");
                        return positionRepository.save(p);
                    });

            BaseRole role = baseRoleRepository.findByCode("ADMIN")
                    .orElseGet(() -> {
                        BaseRole r = new BaseRole();
                        r.setCode("ADMIN");
                        r.setName("Администратор");
                        return baseRoleRepository.save(r);
                    });

            admin.setDepartment(dept);
            admin.setPosition(pos);
            admin.setBaseRole(role);
            admin.setIsTemporaryPassword(false);
            admin.setPasswordChangedAt(LocalDateTime.now());
            admin.setIsActive(true);
            admin.setCreatedAt(LocalDateTime.now());

            userRepository.save(admin);
            System.out.println("=================================================");
            System.out.println("Создан администратор по умолчанию!");
            System.out.println("Логин: admin");
            System.out.println("Пароль: admin");
            System.out.println("=================================================");
        }
    }
}