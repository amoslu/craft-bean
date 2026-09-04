package com.craftbean.greenbean;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.craftbean.archive.GreenBean;
import com.craftbean.archive.GreenBeanMapper;
import com.craftbean.archive.Supplier;
import com.craftbean.archive.SupplierMapper;
import com.craftbean.audit.AuditService;
import com.craftbean.common.AppException;
import com.craftbean.common.PageResult;
import com.craftbean.greenbean.dto.GreenLotResponse;
import com.craftbean.greenbean.dto.InventoryMovementResponse;
import com.craftbean.greenbean.dto.PurchaseRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class PurchaseService {
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private final GreenLotMapper lotMapper;
    private final InventoryMovementMapper movementMapper;
    private final SupplierMapper supplierMapper;
    private final GreenBeanMapper beanMapper;
    private final AuditService auditService;

    public PurchaseService(GreenLotMapper lotMapper, InventoryMovementMapper movementMapper,
                           SupplierMapper supplierMapper, GreenBeanMapper beanMapper,
                           AuditService auditService) {
        this.lotMapper = lotMapper;
        this.movementMapper = movementMapper;
        this.supplierMapper = supplierMapper;
        this.beanMapper = beanMapper;
        this.auditService = auditService;
    }

    @Transactional
    public GreenLotResponse purchase(PurchaseRequest req, Long operatorId) {
        if (req.getQuantityKg() == null || req.getQuantityKg().compareTo(ZERO) <= 0) {
            throw new AppException(400, 400, "数量必须为正数");
        }
        if (req.getUnitPrice() == null || req.getUnitPrice().compareTo(ZERO) <= 0) {
            throw new AppException(400, 400, "采购单价必须为正数");
        }
        Supplier supplier = supplierMapper.selectById(req.getSupplierId());
        if (supplier == null) throw new AppException(404, 404, "供货商不存在");
        if (!"ACTIVE".equals(supplier.getStatus())) throw new AppException(400, 400, "供货商已停用，不能采购");
        GreenBean bean = beanMapper.selectById(req.getGreenBeanId());
        if (bean == null) throw new AppException(404, 404, "生豆品种不存在");
        if (!"ACTIVE".equals(bean.getStatus())) throw new AppException(400, 400, "生豆品种已停用，不能采购");

        String lotNo = StringUtils.hasText(req.getLotNo()) ? req.getLotNo().trim() : generateLotNo();
        if (lotMapper.selectOne(new LambdaQueryWrapper<GreenLot>().eq(GreenLot::getLotNo, lotNo)) != null) {
            throw new AppException(400, 400, "批次号已存在");
        }
        BigDecimal quantity = req.getQuantityKg().setScale(3, RoundingMode.HALF_UP);
        BigDecimal unitPrice = req.getUnitPrice().setScale(2, RoundingMode.HALF_UP);
        BigDecimal expected = quantity.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
        BigDecimal amount = req.getAmount() == null ? expected : req.getAmount().setScale(2, RoundingMode.HALF_UP);
        if (amount.compareTo(ZERO) <= 0) throw new AppException(400, 400, "金额必须为正数");

        GreenLot lot = new GreenLot();
        lot.setLotNo(lotNo);
        lot.setGreenBeanId(bean.getId());
        lot.setSupplierId(supplier.getId());
        lot.setPurchaseDate(req.getPurchaseDate());
        lot.setTotalKg(quantity);
        lot.setRemainingKg(quantity);
        lot.setCostPrice(unitPrice);
        lot.setAmount(amount);
        lot.setStatus("AVAILABLE");
        lot.setRemark(req.getRemark());
        lotMapper.insert(lot);

        InventoryMovement movement = new InventoryMovement();
        movement.setBizType("PURCHASE_IN");
        movement.setBatchKind("GREEN");
        movement.setBatchId(lot.getId());
        movement.setGreenBeanId(bean.getId());
        movement.setSupplierId(supplier.getId());
        movement.setChangeKg(quantity);
        movement.setUnitPrice(unitPrice);
        movement.setAmount(amount);
        movement.setRefDocNo(lotNo);
        movement.setOperatorId(operatorId);
        movement.setMovementDate(req.getPurchaseDate());
        movement.setRemark(req.getRemark());
        movementMapper.insert(movement);
        auditService.record(operatorId, "PURCHASE_IN", lotNo,
                "采购入库 supplier=" + supplier.getName() + " bean=" + bean.getName() + " qty=" + quantity);
        return toLotResponse(lot, bean, supplier);
    }

    public PageResult<GreenLotResponse> listLots(String keyword, String status, Long supplierId,
                                                 Long greenBeanId, long page, long size) {
        LambdaQueryWrapper<GreenLot> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) w.like(GreenLot::getLotNo, keyword);
        if (StringUtils.hasText(status)) w.eq(GreenLot::getStatus, status);
        if (supplierId != null) w.eq(GreenLot::getSupplierId, supplierId);
        if (greenBeanId != null) w.eq(GreenLot::getGreenBeanId, greenBeanId);
        w.orderByDesc(GreenLot::getPurchaseDate).orderByDesc(GreenLot::getId);
        Page<GreenLot> p = lotMapper.selectPage(new Page<>(page, size), w);
        List<Long> beanIds = p.getRecords().stream().map(GreenLot::getGreenBeanId).distinct().toList();
        List<Long> supplierIds = p.getRecords().stream().map(GreenLot::getSupplierId).distinct().toList();
        Map<Long, GreenBean> beans = beanIds.isEmpty() ? Map.of() : beanMapper.selectBatchIds(beanIds).stream()
                .collect(Collectors.toMap(GreenBean::getId, Function.identity()));
        Map<Long, Supplier> suppliers = supplierIds.isEmpty() ? Map.of() : supplierMapper.selectBatchIds(supplierIds).stream()
                .collect(Collectors.toMap(Supplier::getId, Function.identity()));
        List<GreenLotResponse> result = p.getRecords().stream()
                .map(l -> toLotResponse(l, beans.get(l.getGreenBeanId()), suppliers.get(l.getSupplierId()))).toList();
        return new PageResult<>(result, p.getTotal());
    }

    public PageResult<InventoryMovementResponse> listMovements(Long greenBeanId, Long lotId,
                                                               String bizType, long page, long size) {
        LambdaQueryWrapper<InventoryMovement> w = new LambdaQueryWrapper<>();
        if (greenBeanId != null) w.eq(InventoryMovement::getGreenBeanId, greenBeanId);
        if (lotId != null) w.eq(InventoryMovement::getBatchId, lotId);
        if (StringUtils.hasText(bizType)) w.eq(InventoryMovement::getBizType, bizType);
        w.orderByDesc(InventoryMovement::getId);
        Page<InventoryMovement> p = movementMapper.selectPage(new Page<>(page, size), w);
        List<Long> lotIds = p.getRecords().stream().map(InventoryMovement::getBatchId).distinct().toList();
        List<Long> movementBeanIds = p.getRecords().stream().map(InventoryMovement::getGreenBeanId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, GreenLot> lots = lotIds.isEmpty() ? Map.of() : lotMapper.selectBatchIds(lotIds).stream()
                .collect(Collectors.toMap(GreenLot::getId, Function.identity()));
        Map<Long, GreenBean> beans = movementBeanIds.isEmpty() ? Map.of() : beanMapper.selectBatchIds(movementBeanIds).stream()
                .collect(Collectors.toMap(GreenBean::getId, Function.identity()));
        List<InventoryMovementResponse> result = p.getRecords().stream().map(m -> {
            GreenLot lot = lots.get(m.getBatchId());
            GreenBean bean = beans.get(m.getGreenBeanId());
            return new InventoryMovementResponse(m.getId(), m.getBizType(), m.getBatchKind(), m.getBatchId(),
                    lot == null ? null : lot.getLotNo(), m.getGreenBeanId(), bean == null ? null : bean.getName(),
                    m.getSupplierId(), m.getChangeKg(), m.getUnitPrice(), m.getAmount(), m.getRefDocNo(),
                    m.getOperatorId(), m.getMovementDate(), m.getRemark(), m.getCreatedAt());
        }).toList();
        return new PageResult<>(result, p.getTotal());
    }

    private String generateLotNo() {
        return "GB-" + Year.now().getValue() + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private GreenLotResponse toLotResponse(GreenLot lot, GreenBean bean, Supplier supplier) {
        return new GreenLotResponse(lot.getId(), lot.getLotNo(), lot.getGreenBeanId(),
                bean == null ? null : bean.getName(), lot.getSupplierId(),
                supplier == null ? null : supplier.getName(), lot.getPurchaseDate(), lot.getTotalKg(),
                lot.getRemainingKg(), lot.getCostPrice(), lot.getAmount(), lot.getStatus(), lot.getRemark(),
                lot.getCreatedAt());
    }
}
