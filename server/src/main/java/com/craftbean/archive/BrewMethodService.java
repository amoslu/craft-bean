package com.craftbean.archive;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.craftbean.audit.AuditService;
import com.craftbean.archive.dto.BrewMethodRequest;
import com.craftbean.common.AppException;
import com.craftbean.common.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class BrewMethodService {
    private static final String ACTIVE = "ACTIVE";
    private static final String DISABLED = "DISABLED";
    private final BrewMethodMapper mapper;
    private final GreenBeanBrewMethodMapper junctionMapper;
    private final AuditService auditService;

    public BrewMethodService(BrewMethodMapper mapper, GreenBeanBrewMethodMapper junctionMapper,
                             AuditService auditService) {
        this.mapper = mapper;
        this.junctionMapper = junctionMapper;
        this.auditService = auditService;
    }

    public PageResult<BrewMethod> list(String keyword, String status, long page, long size) {
        LambdaQueryWrapper<BrewMethod> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            w.like(BrewMethod::getName, keyword);
        }
        if (StringUtils.hasText(status)) w.eq(BrewMethod::getStatus, status);
        w.orderByAsc(BrewMethod::getId);
        Page<BrewMethod> result = mapper.selectPage(new Page<>(page, size), w);
        return new PageResult<>(result.getRecords(), result.getTotal());
    }

    @Transactional
    public BrewMethod create(BrewMethodRequest req, Long operatorId) {
        if (existsName(req.getName(), null)) throw new AppException(400, 400, "冲煮方式名称已存在");
        BrewMethod m = new BrewMethod();
        copy(m, req);
        mapper.insert(m);
        auditService.record(operatorId, "BREW_METHOD_CREATE", m.getName(), "创建冲煮方式");
        return m;
    }

    @Transactional
    public BrewMethod update(Long id, BrewMethodRequest req, Long operatorId) {
        BrewMethod m = require(id);
        if (existsName(req.getName(), id)) throw new AppException(400, 400, "冲煮方式名称已存在");
        String oldStatus = m.getStatus();
        copy(m, req);
        if (!StringUtils.hasText(req.getStatus())) m.setStatus(oldStatus);
        mapper.updateById(m);
        auditService.record(operatorId, "BREW_METHOD_UPDATE", m.getName(), "更新冲煮方式");
        return m;
    }

    @Transactional
    public BrewMethod changeStatus(Long id, String status, Long operatorId) {
        if (!ACTIVE.equals(status) && !DISABLED.equals(status)) {
            throw new AppException(400, 400, "状态不合法");
        }
        BrewMethod m = require(id);
        m.setStatus(status);
        mapper.updateById(m);
        auditService.record(operatorId, "BREW_METHOD_STATUS", m.getName(), "冲煮方式状态=" + status);
        return m;
    }

    @Transactional
    public void delete(Long id, Long operatorId) {
        BrewMethod m = require(id);
        Long refCount = junctionMapper.selectCount(
                new LambdaQueryWrapper<GreenBeanBrewMethod>().eq(GreenBeanBrewMethod::getBrewMethodId, id));
        if (refCount > 0) throw new AppException(400, 400, "该冲煮方式已被生豆引用为推荐方向，不能删除");
        mapper.deleteById(id);
        auditService.record(operatorId, "BREW_METHOD_DELETE", m.getName(), "删除冲煮方式 " + m.getName());
    }

    public BrewMethod require(Long id) {
        BrewMethod m = mapper.selectById(id);
        if (m == null) throw new AppException(404, 404, "冲煮方式不存在");
        return m;
    }

    private boolean existsName(String name, Long exceptId) {
        LambdaQueryWrapper<BrewMethod> w = new LambdaQueryWrapper<BrewMethod>().eq(BrewMethod::getName, name);
        if (exceptId != null) w.ne(BrewMethod::getId, exceptId);
        return mapper.selectCount(w) > 0;
    }

    private void copy(BrewMethod m, BrewMethodRequest req) {
        m.setName(req.getName());
        m.setStatus(StringUtils.hasText(req.getStatus()) ? req.getStatus() : ACTIVE);
        if (!ACTIVE.equals(m.getStatus()) && !DISABLED.equals(m.getStatus())) {
            throw new AppException(400, 400, "状态不合法");
        }
    }
}
