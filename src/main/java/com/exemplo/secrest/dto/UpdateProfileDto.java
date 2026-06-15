package com.exemplo.secrest.dto;

import com.exemplo.secrest.enums.RoleName;

public record UpdateProfileDto(
    String name,
    RoleName role
) {}
