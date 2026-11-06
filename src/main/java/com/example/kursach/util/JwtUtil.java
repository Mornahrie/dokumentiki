package com.example.kursach.util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.example.kursach.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

public class JwtUtil {
    private static final Logger logger = LoggerFactory.getLogger(JwtUtil.class);

    private static final String SECRET = "kursach_secret_key_2024_super_long";
    private static final long EXPIRATION_MS = 24 * 60 * 60 * 1000; // 24 часа

    public static String generateToken(User user) {
        logger.info("Генерация JWT-токена для пользователя ID: {}, Табельный номер: {}",
                user.getUserId(), user.getTabNumber());

        Algorithm algorithm = Algorithm.HMAC256(SECRET);
        Date now = new Date();
        Date expiresAt = new Date(now.getTime() + EXPIRATION_MS);

        String token = JWT.create()
                .withSubject(String.valueOf(user.getUserId()))
                .withClaim("tabNumber", user.getTabNumber())
                .withClaim("roleCode", user.getBaseRole().getCode())
                .withClaim("lastName", user.getLastName())
                .withClaim("firstName", user.getFirstName())
                .withIssuedAt(now)
                .withExpiresAt(expiresAt)
                .sign(algorithm);

        logger.debug("Токен успешно сгенерирован. Действителен до: {}", expiresAt);
        return token;
    }

    public static DecodedJWT verifyToken(String token) throws JWTVerificationException {
        logger.debug("Проверка JWT-токена...");
        Algorithm algorithm = Algorithm.HMAC256(SECRET);

        try {
            DecodedJWT jwt = JWT.require(algorithm).build().verify(token);
            logger.debug("Токен успешно проверен. Владелец (Subject/ID): {}", jwt.getSubject());
            return jwt;
        } catch (JWTVerificationException e) {
            logger.warn("Ошибка верификации JWT токена: {}", e.getMessage());
            throw e;
        }
    }

    public static Long extractUserId(String token) {
        DecodedJWT jwt = verifyToken(token);
        return Long.parseLong(jwt.getSubject());
    }
}