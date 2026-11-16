package com.craftbean.archive;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.craftbean.audit.AuditService;
import com.craftbean.archive.dto.SupplierRequest;
import com.craftbean.common.AppException;
import com.craftbean.common.PageResult;
import com.craftbean.greenbean.GreenLot;
import com.craftbean.greenbean.GreenLotMapper;
import com.craftbean.greenbean.InventoryMovement;
import com.craftbean.greenbean.InventoryMovementMapper;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class SupplierService {
    private static final String ACTIVE = "ACTIVE";
    private static final String DISABLED = "DISABLED";
    private final SupplierMapper mapper;
    private final GreenLotMapper lotMapper;
    private final InventoryMovementMapper movementMapper;
    private final GreenBeanMapper beanMapper;
    private final AuditService auditService;

    public SupplierService(SupplierMapper mapper, GreenLotMapper lotMapper,
                           InventoryMovementMapper movementMapper, GreenBeanMapper beanMapper,
                           AuditService auditService) {
        this.mapper = mapper;
        this.lotMapper = lotMapper;
        this.movementMapper = movementMapper;
        this.beanMapper = beanMapper;
        this.auditService = auditService;
    }

    public PageResult<Supplier> list(String keyword, String status, long page, long size) {
        LambdaQueryWrapper<Supplier> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            w.and(x -> x.like(Supplier::getName, keyword).or().like(Supplier::getContactPerson, keyword)
                    .or().like(Supplier::getPhone, keyword));
        }
        if (StringUtils.hasText(status)) w.eq(Supplier::getStatus, status);
        w.orderByDesc(Supplier::getId);
        Page<Supplier> result = mapper.selectPage(new Page<>(page, size), w);
        return new PageResult<>(result.getRecords(), result.getTotal());
    }

    @Transactional
    public Supplier create(SupplierRequest req, Long operatorId) {
        if (existsName(req.getName(), null)) throw new AppException(400, 400, "供货商名称已存在");
        Supplier s = new Supplier();
        copy(s, req);
        mapper.insert(s);
        auditService.record(operatorId, "SUPPLIER_CREATE", s.getName(), "创建供货商");
        return s;
    }

    @Transactional
    public Supplier update(Long id, SupplierRequest req, Long operatorId) {
        Supplier s = require(id);
        if (existsName(req.getName(), id)) throw new AppException(400, 400, "供货商名称已存在");
        String oldStatus = s.getStatus();
        copy(s, req);
        if (!StringUtils.hasText(req.getStatus())) s.setStatus(oldStatus);
        mapper.updateById(s);
        auditService.record(operatorId, "SUPPLIER_UPDATE", s.getName(), "更新供货商");
        return s;
    }

    @Transactional
    public Supplier changeStatus(Long id, String status, Long operatorId) {
        if (!ACTIVE.equals(status) && !DISABLED.equals(status)) {
            throw new AppException(400, 400, "状态不合法");
        }
        Supplier s = require(id);
        s.setStatus(status);
        mapper.updateById(s);
        auditService.record(operatorId, "SUPPLIER_STATUS", s.getName(), "供货商状态=" + status);
        return s;
    }

    @Transactional
    public void delete(Long id, Long operatorId) {
        Supplier s = require(id);
        Long lotCount = lotMapper.selectCount(
                new LambdaQueryWrapper<GreenLot>().eq(GreenLot::getSupplierId, id));
        if (lotCount > 0) throw new AppException(400, 400, "该供货商已有生豆采购入库，不能删除");
        Long movementCount = movementMapper.selectCount(
                new LambdaQueryWrapper<InventoryMovement>().eq(InventoryMovement::getSupplierId, id));
        if (movementCount > 0) throw new AppException(400, 400, "该供货商已有库存流水，不能删除");
        Long beanCount = beanMapper.selectCount(
                new LambdaQueryWrapper<GreenBean>().eq(GreenBean::getDefaultSupplierId, id));
        if (beanCount > 0) throw new AppException(400, 400, "该供货商被生豆设为默认供货商，不能删除");
        mapper.deleteById(id);
        auditService.record(operatorId, "SUPPLIER_DELETE", s.getName(), "删除供货商 " + s.getName());
    }

    public Supplier require(Long id) {
        Supplier s = mapper.selectById(id);
        if (s == null) throw new AppException(404, 404, "供货商不存在");
        return s;
    }

    private boolean existsName(String name, Long exceptId) {
        LambdaQueryWrapper<Supplier> w = new LambdaQueryWrapper<Supplier>().eq(Supplier::getName, name);
        if (exceptId != null) w.ne(Supplier::getId, exceptId);
        return mapper.selectCount(w) > 0;
    }

    private void copy(Supplier s, SupplierRequest req) {
        s.setName(req.getName());
        s.setContactPerson(req.getContactPerson());
        s.setPhone(req.getPhone());
        s.setWechat(req.getWechat());
        s.setAddress(req.getAddress());
        s.setMainCategory(req.getMainCategory());
        s.setStatus(StringUtils.hasText(req.getStatus()) ? req.getStatus() : ACTIVE);
        if (!ACTIVE.equals(s.getStatus()) && !DISABLED.equals(s.getStatus())) {
            throw new AppException(400, 400, "状态不合法");
        }
    }
}
