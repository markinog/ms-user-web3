package com.exemplo.secrest.dto;

import java.util.List;

public record UserProfileDto(
    Long id,
    String name,
    String email,
    List<String> roles
) {}
