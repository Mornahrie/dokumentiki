package com.example.kursach.dto;

import com.example.kursach.entity.User;

public record UserShortDto(
        Long userId,
        String tabNumber,
        String firstName,
        String lastName,
        String middleName,
        Boolean isActive
) {
    public static UserShortDto from(User u) {
        return new UserShortDto(
                u.getUserId(),
                u.getTabNumber(),
                u.getFirstName(),
                u.getLastName(),
                u.getMiddleName(),
                u.getIsActive()
        );
    }
}