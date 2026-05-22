package com.exemplo.secrest.dto;

import com.exemplo.secrest.enums.RoleName;

public record CreateUserDto(
    String email,
    String password,
    RoleName role
) {}
