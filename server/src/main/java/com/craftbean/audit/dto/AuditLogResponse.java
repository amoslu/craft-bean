package com.craftbean.audit.dto;

import java.time.LocalDateTime;

public record AuditLogResponse(
        Long id,
        Long operatorId,
        String operatorName,
        String action,
        String targetNo,
        String detail,
        LocalDateTime createdAt) {
}
