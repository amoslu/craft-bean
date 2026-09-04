package com.craftbean.archive;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.craftbean.audit.AuditService;
import com.craftbean.archive.dto.GreenBeanRequest;
import com.craftbean.common.AppException;
import com.craftbean.common.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class GreenBeanService {
    private final GreenBeanMapper mapper;
    private final SupplierMapper supplierMapper;
    private final AuditService auditService;

    public GreenBeanService(GreenBeanMapper mapper, SupplierMapper supplierMapper, AuditService auditService) {
        this.mapper = mapper;
        this.supplierMapper = supplierMapper;
        this.auditService = auditService;
    }

    public PageResult<GreenBean> list(String keyword, String status, long page, long size) {
        LambdaQueryWrapper<GreenBean> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            w.and(x -> x.like(GreenBean::getName, keyword).or().like(GreenBean::getOrigin, keyword)
                    .or().like(GreenBean::getVariety, keyword));
        }
        if (StringUtils.hasText(status)) w.eq(GreenBean::getStatus, status);
        w.orderByDesc(GreenBean::getId);
        Page<GreenBean> result = mapper.selectPage(new Page<>(page, size), w);
        return new PageResult<>(result.getRecords(), result.getTotal());
    }

    @Transactional
    public GreenBean create(GreenBeanRequest req, Long operatorId) {
        if (existsName(req.getName(), null)) throw new AppException(400, 400, "生豆品种名称已存在");
        GreenBean bean = new GreenBean();
        copy(bean, req);
        mapper.insert(bean);
        auditService.record(operatorId, "GREEN_BEAN_CREATE", bean.getName(), "创建生豆品种");
        return bean;
    }

    @Transactional
    public GreenBean update(Long id, GreenBeanRequest req, Long operatorId) {
        GreenBean bean = require(id);
        if (existsName(req.getName(), id)) throw new AppException(400, 400, "生豆品种名称已存在");
        String oldStatus = bean.getStatus();
        copy(bean, req);
        if (!StringUtils.hasText(req.getStatus())) bean.setStatus(oldStatus);
        mapper.updateById(bean);
        auditService.record(operatorId, "GREEN_BEAN_UPDATE", bean.getName(), "更新生豆品种");
        return bean;
    }

    public GreenBean require(Long id) {
        GreenBean bean = mapper.selectById(id);
        if (bean == null) throw new AppException(404, 404, "生豆品种不存在");
        return bean;
    }

    private boolean existsName(String name, Long exceptId) {
        LambdaQueryWrapper<GreenBean> w = new LambdaQueryWrapper<GreenBean>().eq(GreenBean::getName, name);
        if (exceptId != null) w.ne(GreenBean::getId, exceptId);
        return mapper.selectCount(w) > 0;
    }

    private void copy(GreenBean bean, GreenBeanRequest req) {
        bean.setName(req.getName());
        bean.setOrigin(req.getOrigin());
        bean.setProcess(req.getProcess());
        bean.setVariety(req.getVariety());
        bean.setDefaultSupplierId(req.getDefaultSupplierId());
        bean.setStatus(StringUtils.hasText(req.getStatus()) ? req.getStatus() : "ACTIVE");
        bean.setRemark(req.getRemark());
        if (!"ACTIVE".equals(bean.getStatus()) && !"DISABLED".equals(bean.getStatus())) {
            throw new AppException(400, 400, "状态不合法");
        }
        if (bean.getDefaultSupplierId() != null && supplierMapper.selectById(bean.getDefaultSupplierId()) == null) {
            throw new AppException(400, 400, "默认供货商不存在");
        }
    }
}
