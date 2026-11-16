package com.craftbean.archive;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.craftbean.audit.AuditService;
import com.craftbean.archive.dto.GreenBeanRequest;
import com.craftbean.common.AppException;
import com.craftbean.common.PageResult;
import com.craftbean.greenbean.GreenLot;
import com.craftbean.greenbean.GreenLotMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class GreenBeanService {
    private static final String SINGLE = "SINGLE";
    private static final String BLEND = "BLEND";
    private static final List<String> ROAST_LEVELS = List.of("浅", "中浅", "中", "中深", "深");
    private final GreenBeanMapper mapper;
    private final SupplierMapper supplierMapper;
    private final BrewMethodMapper brewMethodMapper;
    private final GreenBeanBrewMethodMapper junctionMapper;
    private final GreenLotMapper lotMapper;
    private final AuditService auditService;

    public GreenBeanService(GreenBeanMapper mapper, SupplierMapper supplierMapper,
                            BrewMethodMapper brewMethodMapper, GreenBeanBrewMethodMapper junctionMapper,
                            GreenLotMapper lotMapper, AuditService auditService) {
        this.mapper = mapper;
        this.supplierMapper = supplierMapper;
        this.brewMethodMapper = brewMethodMapper;
        this.junctionMapper = junctionMapper;
        this.lotMapper = lotMapper;
        this.auditService = auditService;
    }

    public PageResult<GreenBean> list(String keyword, String status, long page, long size, boolean includeMethods) {
        LambdaQueryWrapper<GreenBean> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            w.and(x -> x.like(GreenBean::getName, keyword).or().like(GreenBean::getCode, keyword)
                    .or().like(GreenBean::getOrigin, keyword).or().like(GreenBean::getRegion, keyword)
                    .or().like(GreenBean::getProcess, keyword).or().like(GreenBean::getVariety, keyword)
                    .or().like(GreenBean::getFlavor, keyword).or().like(GreenBean::getRecipe, keyword));
        }
        if (StringUtils.hasText(status)) w.eq(GreenBean::getStatus, status);
        w.orderByDesc(GreenBean::getId);
        Page<GreenBean> result = mapper.selectPage(new Page<>(page, size), w);
        if (includeMethods) enrich(result.getRecords());
        return new PageResult<>(result.getRecords(), result.getTotal());
    }

    @Transactional
    public GreenBean create(GreenBeanRequest req, Long operatorId) {
        if (existsName(req.getName(), null)) throw new AppException(400, 400, "生豆名称已存在");
        GreenBean bean = new GreenBean();
        copy(bean, req);
        mapper.insert(bean);
        saveBrewMethods(bean.getId(), req.getBrewMethodIds());
        auditService.record(operatorId, "GREEN_BEAN_CREATE", bean.getName(), "创建生豆");
        return bean;
    }

    @Transactional
    public GreenBean update(Long id, GreenBeanRequest req, Long operatorId) {
        GreenBean bean = require(id);
        if (existsName(req.getName(), id)) throw new AppException(400, 400, "生豆名称已存在");
        String oldStatus = bean.getStatus();
        copy(bean, req);
        if (!StringUtils.hasText(req.getStatus())) bean.setStatus(oldStatus);
        mapper.updateById(bean);
        saveBrewMethods(bean.getId(), req.getBrewMethodIds());
        auditService.record(operatorId, "GREEN_BEAN_UPDATE", bean.getName(), "更新生豆");
        return bean;
    }

    @Transactional
    public void delete(Long id, Long operatorId) {
        GreenBean bean = require(id);
        // 只拦「还有可用批次」：批次作废（VOID）后生豆即可删除，历史流水不再阻挡
        Long activeLotCount = lotMapper.selectCount(new LambdaQueryWrapper<GreenLot>()
                .eq(GreenLot::getGreenBeanId, id)
                .ne(GreenLot::getStatus, "VOID"));
        if (activeLotCount > 0) throw new AppException(400, 400, "该生豆还有可用批次，不能删除，请先作废批次");
        // 清理自身「推荐方向」关联（明细表无逻辑删除列）
        junctionMapper.delete(new LambdaQueryWrapper<GreenBeanBrewMethod>()
                .eq(GreenBeanBrewMethod::getGreenBeanId, id));
        mapper.deleteById(id);
        auditService.record(operatorId, "GREEN_BEAN_DELETE", bean.getName(), "删除生豆 " + bean.getName());
    }

    public GreenBean require(Long id) {
        GreenBean bean = mapper.selectById(id);
        if (bean == null) throw new AppException(404, 404, "生豆不存在");
        return bean;
    }

    private boolean existsName(String name, Long exceptId) {
        LambdaQueryWrapper<GreenBean> w = new LambdaQueryWrapper<GreenBean>().eq(GreenBean::getName, name);
        if (exceptId != null) w.ne(GreenBean::getId, exceptId);
        return mapper.selectCount(w) > 0;
    }

    private void copy(GreenBean bean, GreenBeanRequest req) {
        bean.setName(req.getName());
        bean.setBeanType(StringUtils.hasText(req.getBeanType()) ? req.getBeanType() : SINGLE);
        if (!SINGLE.equals(bean.getBeanType()) && !BLEND.equals(bean.getBeanType())) {
            throw new AppException(400, 400, "类型不合法");
        }
        bean.setCode(req.getCode());
        bean.setOrigin(req.getOrigin());
        bean.setRegion(req.getRegion());
        bean.setProcess(req.getProcess());
        bean.setVariety(req.getVariety());
        bean.setSeason(req.getSeason());
        bean.setFlavor(req.getFlavor());
        bean.setRecipe(req.getRecipe());
        bean.setAgtronColor(req.getAgtronColor());
        if (req.getAgtronColor() != null && (req.getAgtronColor() < 0 || req.getAgtronColor() > 100)) {
            throw new AppException(400, 400, "推荐色值应在0-100之间");
        }
        bean.setMoisturePct(req.getMoisturePct());
        if (req.getMoisturePct() != null
                && (req.getMoisturePct().signum() < 0 || req.getMoisturePct().compareTo(new java.math.BigDecimal("100")) > 0)) {
            throw new AppException(400, 400, "含水率应在0-100之间");
        }
        bean.setAltitudeM(req.getAltitudeM());
        bean.setRecommendedRoast(req.getRecommendedRoast());
        if (StringUtils.hasText(req.getRecommendedRoast()) && !ROAST_LEVELS.contains(req.getRecommendedRoast())) {
            throw new AppException(400, 400, "推荐烘焙度取值不合法");
        }
        bean.setPackageSpec(req.getPackageSpec());
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

    /** 推荐方向整存整删：删旧插新；id 均须存在且未删 */
    private void saveBrewMethods(Long beanId, List<Long> methodIds) {
        junctionMapper.delete(new LambdaQueryWrapper<GreenBeanBrewMethod>()
                .eq(GreenBeanBrewMethod::getGreenBeanId, beanId));
        if (methodIds == null) return;
        for (Long methodId : methodIds.stream().filter(Objects::nonNull).distinct().toList()) {
            if (brewMethodMapper.selectById(methodId) == null) {
                throw new AppException(400, 400, "冲煮方式不存在");
            }
            GreenBeanBrewMethod row = new GreenBeanBrewMethod();
            row.setGreenBeanId(beanId);
            row.setBrewMethodId(methodId);
            junctionMapper.insert(row);
        }
    }

    /** 批量装配每页品种的推荐方向（ids + names，按关联顺序） */
    private void enrich(List<GreenBean> beans) {
        if (beans.isEmpty()) return;
        List<Long> ids = beans.stream().map(GreenBean::getId).toList();
        List<GreenBeanBrewMethod> rels = junctionMapper.selectList(new LambdaQueryWrapper<GreenBeanBrewMethod>()
                .in(GreenBeanBrewMethod::getGreenBeanId, ids)
                .orderByAsc(GreenBeanBrewMethod::getId));
        if (rels.isEmpty()) {
            beans.forEach(b -> { b.setBrewMethodIds(List.of()); b.setBrewMethodNames(List.of()); });
            return;
        }
        Map<Long, List<Long>> idsByBean = new LinkedHashMap<>();
        for (GreenBeanBrewMethod rel : rels) {
            idsByBean.computeIfAbsent(rel.getGreenBeanId(), k -> new ArrayList<>()).add(rel.getBrewMethodId());
        }
        Map<Long, String> nameById = brewMethodMapper.selectBatchIds(
                        rels.stream().map(GreenBeanBrewMethod::getBrewMethodId).distinct().toList())
                .stream().collect(Collectors.toMap(BrewMethod::getId, BrewMethod::getName, (a, b) -> a));
        for (GreenBean bean : beans) {
            List<Long> methodIds = idsByBean.getOrDefault(bean.getId(), List.of());
            bean.setBrewMethodIds(methodIds);
            bean.setBrewMethodNames(methodIds.stream()
                    .map(nameById::get).filter(Objects::nonNull).toList());
        }
    }
}
