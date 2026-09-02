package com.craftbean.audit;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.craftbean.audit.dto.AuditLogResponse;
import com.craftbean.common.CurrentUser;
import com.craftbean.common.PageResult;
import com.craftbean.common.Result;
import com.craftbean.system.SysUser;
import com.craftbean.system.SysUserMapper;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {
    private final AuditLogMapper auditLogMapper;
    private final SysUserMapper userMapper;

    public AuditController(AuditLogMapper auditLogMapper, SysUserMapper userMapper) {
        this.auditLogMapper = auditLogMapper;
        this.userMapper = userMapper;
    }

    @GetMapping("/logs")
    public Result<PageResult<AuditLogResponse>> list(
            @RequestParam(required = false) Long operatorId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        SysUser current = CurrentUser.get();
        LambdaQueryWrapper<AuditLog> w = new LambdaQueryWrapper<>();
        if ("ADMIN".equals(current.getRole())) {
            if (operatorId != null) {
                w.eq(AuditLog::getOperatorId, operatorId);
            }
        } else {
            w.eq(AuditLog::getOperatorId, current.getId());
        }
        w.orderByDesc(AuditLog::getId);
        Page<AuditLog> p = auditLogMapper.selectPage(new Page<>(page, size), w);

        Map<Long, String> names = resolveNames(p.getRecords());
        List<AuditLogResponse> list = p.getRecords().stream()
                .map(l -> new AuditLogResponse(l.getId(), l.getOperatorId(),
                        l.getOperatorId() == null ? null : names.get(l.getOperatorId()),
                        l.getAction(), l.getTargetNo(), l.getDetail(), l.getCreatedAt()))
                .toList();
        return Result.ok(new PageResult<>(list, p.getTotal()));
    }

    private Map<Long, String> resolveNames(List<AuditLog> logs) {
        Set<Long> ids = logs.stream()
                .map(AuditLog::getOperatorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<SysUser> users = userMapper.selectBatchIds(ids);
        return users.stream().collect(Collectors.toMap(SysUser::getId, SysUser::getName, (a, b) -> a));
    }
}
