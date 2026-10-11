package com.example.kursach.controller;

import com.example.kursach.dto.ChangePasswordRequest;
import com.example.kursach.dto.LoginRequest;
import com.example.kursach.dto.LoginResponse;
import com.example.kursach.entity.User;
import com.example.kursach.repository.UserRepository;
import com.example.kursach.util.JwtUtil;
import com.example.kursach.util.PasswordUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);
    private static final int MIN_PASSWORD_LENGTH = 8;

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Autowired
    public AuthController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        logger.info("Запрос на вход. Табельный номер: '{}'", request.getTabNumber());

        if (isBlank(request.getTabNumber()) || isBlank(request.getPassword())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Табельный номер и пароль обязательны"));
        }

        Optional<User> userOpt = userRepository.findByTabNumber(request.getTabNumber().trim());
        if (userOpt.isEmpty()) {
            logger.warn("Отказ: пользователь '{}' не найден", request.getTabNumber());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Пользователь не найден"));
        }
        User user = userOpt.get();

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Учётная запись деактивирована"));
        }

        if (!encoder.matches(request.getPassword(), user.getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Неверный пароль"));
        }

        boolean requireChange = false;
        String reason = null;

        if (Boolean.TRUE.equals(user.getIsTemporaryPassword())) {
            requireChange = true;
            reason = "TEMPORARY_PASSWORD";
        } else if (PasswordUtils.isPasswordExpired(user.getPasswordChangedAt())) {
            requireChange = true;
            reason = "PASSWORD_EXPIRED";
        }

        String token = JwtUtil.generateToken(user);
        String fullName = buildFullName(user);

        LoginResponse response = new LoginResponse(
                token, user.getUserId(), user.getTabNumber(), fullName,
                user.getBaseRole().getCode(), user.getBaseRole().getName(),
                requireChange, reason
        );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
            @RequestBody ChangePasswordRequest request) {

        Long userId;
        try {
            userId = JwtUtil.extractUserId(extractToken(authHeader));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Невалидный или отсутствующий токен"));
        }

        if (isBlank(request.getOldPassword()) || isBlank(request.getNewPassword())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Старый и новый пароль обязательны"));
        }
        if (request.getNewPassword().length() < MIN_PASSWORD_LENGTH) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Новый пароль должен содержать не менее " + MIN_PASSWORD_LENGTH + " символов"));
        }
        if (request.getOldPassword().equals(request.getNewPassword())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Новый пароль должен отличаться от старого"));
        }

        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Пользователь не найден"));
        }
        User user = userOpt.get();

        if (!encoder.matches(request.getOldPassword(), user.getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Старый пароль указан неверно"));
        }

        user.setPasswordHash(encoder.encode(request.getNewPassword()));
        user.setIsTemporaryPassword(false);
        user.setPasswordChangedAt(LocalDateTime.now());
        userRepository.save(user);

        String newToken = JwtUtil.generateToken(user);

        return ResponseEntity.ok(Map.of(
                "message", "Пароль успешно изменён",
                "passwordChangedAt", user.getPasswordChangedAt().toString(),
                "token", newToken
        ));
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String extractToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Нет Bearer-токена");
        }
        return authHeader.substring(7);
    }

    private static String buildFullName(User user) {
        StringBuilder sb = new StringBuilder(user.getLastName()).append(' ').append(user.getFirstName());
        if (user.getMiddleName() != null && !user.getMiddleName().isBlank()) {
            sb.append(' ').append(user.getMiddleName());
        }
        return sb.toString();
    }
}