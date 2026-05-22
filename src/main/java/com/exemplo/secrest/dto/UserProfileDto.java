package com.exemplo.secrest.dto;

import java.util.List;

public record UserProfileDto(
    Long id,
    String email,
    List<String> roles
) {}
