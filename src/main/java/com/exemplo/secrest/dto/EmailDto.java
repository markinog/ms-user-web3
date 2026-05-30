package com.exemplo.secrest.dto;

import java.util.UUID;

public record EmailDto(
        String emailTo,
        String subject,
        String text,
        UUID userId
) {}
