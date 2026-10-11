package com.example.kursach.filter;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.example.kursach.entity.User;
import com.example.kursach.repository.UserRepository;
import com.example.kursach.util.JwtUtil;
import com.example.kursach.util.PasswordUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT-фильтр для /api/**.
 *
 * Публичные эндпоинты: /api/auth/login, /api/auth/change-password.
 * Для /api/admin/** требуется роль ADMIN (проверяется по БД, а не по токену).
 *
 * F025: пока пользователю требуется смена пароля (temporary / expired),
 * все эндпоинты, кроме /api/auth/change-password, возвращают 403.
 *
 * SEC-03: пользователь и его роль загружаются из БД на каждом запросе,
 * чтобы деактивация / смена роли вступали в силу немедленно.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthFilter.class);

    private final UserRepository userRepository;

    public JwtAuthFilter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
            throws ServletException, IOException {

        String path = req.getRequestURI();

        if (!path.startsWith("/api/")) {
            chain.doFilter(req, resp);
            return;
        }

        // Публичные эндпоинты
        if (path.equals("/api/auth/login") || path.equals("/api/auth/change-password")) {
            chain.doFilter(req, resp);
            return;
        }

        String auth = req.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            sendJsonError(resp, 401, "Требуется авторизация");
            return;
        }

        try {
            DecodedJWT jwt = JwtUtil.verifyToken(auth.substring(7));
            Long userId = Long.parseLong(jwt.getSubject());

            // Загружаем пользователя из БД — источник истины для активности и роли
            User user = userRepository.findById(userId).orElse(null);
            if (user == null) {
                sendJsonError(resp, 401, "Пользователь не найден");
                return;
            }
            if (!Boolean.TRUE.equals(user.getIsActive())) {
                sendJsonError(resp, 401, "Учётная запись деактивирована");
                return;
            }

            // F025: принудительная смена пароля
            boolean needsPasswordChange =
                    Boolean.TRUE.equals(user.getIsTemporaryPassword())
                            || PasswordUtils.isPasswordExpired(user.getPasswordChangedAt());
            if (needsPasswordChange) {
                logger.warn("Блокировка {} для ID={}: требуется смена пароля", path, userId);
                sendJsonError(resp, 403, "Требуется смена пароля");
                return;
            }

            // Роль берём из БД, а не из токена
            String actualRole = user.getBaseRole() != null ? user.getBaseRole().getCode() : null;
            if (path.startsWith("/api/admin/") && !"ADMIN".equals(actualRole)) {
                sendJsonError(resp, 403, "Требуется роль администратора");
                return;
            }

            req.setAttribute("jwtUserId", userId);
            req.setAttribute("jwtRole", actualRole);
            chain.doFilter(req, resp);

        } catch (JWTVerificationException e) {
            logger.warn("Невалидный токен на {}: {}", path, e.getMessage());
            sendJsonError(resp, 401, "Невалидный или истёкший токен");
        }
    }

    private void sendJsonError(HttpServletResponse resp, int status, String message) throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json;charset=UTF-8");
        resp.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}