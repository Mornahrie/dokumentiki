package com.example.kursach.util;

import java.time.LocalDateTime;

public class PasswordUtils {
    public static final long PASSWORD_TTL_DAYS = 30;

    public static boolean isPasswordExpired(LocalDateTime passwordChangedAt) {
        return passwordChangedAt == null
                || passwordChangedAt.isBefore(LocalDateTime.now().minusDays(PASSWORD_TTL_DAYS));
    }
}