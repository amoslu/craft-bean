package com.craftbean.audit;

import org.springframework.stereotype.Service;

@Service
public class AuditService {
    private final AuditLogMapper auditLogMapper;

    public AuditService(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    public void record(Long operatorId, String action, String targetNo, String detail) {
        AuditLog log = new AuditLog();
        log.setOperatorId(operatorId);
        log.setAction(action);
        log.setTargetNo(targetNo);
        log.setDetail(detail);
        auditLogMapper.insert(log);
    }
}
