package com.mouse.mes.impl;

import com.mouse.mes.entity.StockOut;
import com.mouse.mes.entity.StockOutItem;
import com.mouse.mes.mapper.StockOutItemMapper;
import com.mouse.mes.mapper.StockOutMapper;
import com.mouse.mes.mapper.WarehouseMapper;
import com.mouse.mes.service.InventoryService;
import com.mouse.mes.service.StockOutService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Service
public class StockOutServiceImpl implements StockOutService {
    @Autowired private com.mouse.mes.mapper.WorkOrderMapper workOrderMapper;
    @Autowired private com.mouse.mes.mapper.BomMapper bomMapper;

    private void validateProductionIssue(StockOut out) {
        if(!"PRODUCTION_ISSUE".equals(out.getStockOutType()))return;
        if(out.getSourceOrderId()==null)throw new IllegalArgumentException("生产领料必须关联工单");
        var work=workOrderMapper.lockForIssue(out.getSourceOrderId());
        if(work==null || work.getPlannedQuantity()==null || work.getPlannedQuantity().signum()<=0)throw new IllegalArgumentException("工单不存在或计划数量无效");
        if(java.util.Set.of("CANCELLED","COMPLETED","FINISHED").contains(String.valueOf(work.getStatus())))throw new IllegalArgumentException("工单已关闭，不能领料");
        java.util.Map<Long,BigDecimal> requested=new java.util.HashMap<>(), limits=new java.util.HashMap<>();
        for(var item:out.getItems()){validateStockOutItem(item);requested.merge(item.getMaterialId(),item.getQuantity(),BigDecimal::add);}
        for(var bom:bomMapper.findByProductId(work.getProductId()))if(bom.getQuantity()!=null && bom.getQuantity().signum()>0)limits.merge(bom.getMaterialId(),bom.getQuantity().multiply(work.getPlannedQuantity()),BigDecimal::add);
        for(var item:requested.entrySet()){
            BigDecimal limit=limits.get(item.getKey());
            if(limit==null)throw new IllegalArgumentException("领料物料不在工单产品BOM中");
            BigDecimal issued=stockOutMapper.issuedQuantity(work.getId(),item.getKey());
            if((issued==null?BigDecimal.ZERO:issued).add(item.getValue()).compareTo(limit)>0)throw new IllegalArgumentException("累计领料超过工单计划用量");
        }
        out.setSourceOrderNo(work.getWoNo());out.setStatus("COMPLETED");
    }

    /**
     * 出库单数据库访问对象。
     */
    @Autowired
    private StockOutMapper stockOutMapper;

    /**
     * 出库单明细数据库访问对象。
     */
    @Autowired
    private StockOutItemMapper stockOutItemMapper;

    /**
     * 仓库数据库访问对象，用于校验仓库是否存在。
     */
    @Autowired
    private WarehouseMapper warehouseMapper;

    /**
     * 库存业务服务，用于出库前校验库存、扣减库存和写流水。
     */
    @Autowired
    private InventoryService inventoryService;

    /**
     * 查询全部未删除出库单，并带出明细。
     */
    @Override
    public List<StockOut> findAll() {
        List<StockOut> stockOuts = stockOutMapper.findAll();
        for (StockOut stockOut : stockOuts) {
            fillItems(stockOut);
        }
        return stockOuts;
    }

    /**
     * 按出库单ID查询出库单，并带出明细。
     */
    @Override
    public StockOut findById(Long id) {
        StockOut stockOut = stockOutMapper.findById(id);
        fillItems(stockOut);
        return stockOut;
    }

    /**
     * 按出库单号查询出库单，并带出明细。
     */
    @Override
    public StockOut findByStockOutNo(String stockOutNo) {
        StockOut stockOut = stockOutMapper.findByStockOutNo(stockOutNo);
        fillItems(stockOut);
        return stockOut;
    }

    /**
     * 创建出库单，先校验库存，库存充足时保存明细、扣减库存并写入库存流水。
     */
    @Override
    @Transactional
    public StockOut create(StockOut stockOut) {
        validateStockOut(stockOut);
        validateProductionIssue(stockOut);
        for (StockOutItem item : stockOut.getItems()) {
            validateStockOutItem(item);
            BigDecimal availableQuantity = inventoryService.getAvailableQuantity(
                    item.getMaterialId(), stockOut.getWarehouseId(), item.getLocationId(), item.getLotId());
            if (availableQuantity.compareTo(item.getQuantity()) < 0) {
                throw new IllegalArgumentException("库存不足，不能出库");
            }
        }
        stockOut.setCreatedAt(new Date());
        stockOut.setUpdatedAt(new Date());
        stockOut.setIsDeleted(0);
        if (stockOut.getStatus() == null || stockOut.getStatus().isBlank()) {
            stockOut.setStatus("COMPLETED");
        }
        stockOut.setTotalQuantity(calculateTotalQuantity(stockOut.getItems()));
        stockOutMapper.insert(stockOut);
        for (StockOutItem item : stockOut.getItems()) {
            item.setStockOutId(stockOut.getId());
            stockOutItemMapper.insert(item);
            inventoryService.decreaseStock(item.getMaterialId(), stockOut.getWarehouseId(), item.getLocationId(),
                    item.getLotId(), item.getQuantity(), stockOut.getStockOutType(), stockOut.getId(),
                    stockOut.getStockOutNo(), stockOut.getCreatedBy(), item.getRemark());
        }
        return findById(stockOut.getId());
    }

    /**
     * 修改出库单基础信息，不重新处理库存。
     */
    @Override
    public StockOut update(StockOut stockOut) {
        StockOut existing = stockOutMapper.findById(stockOut.getId());
        if (existing == null) {
            throw new IllegalArgumentException("出库单不存在");
        }
        if("PRODUCTION_ISSUE".equals(existing.getStockOutType())) {
            if(stockOut.getSourceOrderId()!=null && !stockOut.getSourceOrderId().equals(existing.getSourceOrderId()) || stockOut.getStockOutType()!=null && !stockOut.getStockOutType().equals(existing.getStockOutType()))throw new IllegalArgumentException("已领料出库单不能变更工单或类型");
        } else if("PRODUCTION_ISSUE".equals(stockOut.getStockOutType()))throw new IllegalArgumentException("普通出库不能改为生产领料");
        mergeMissingFields(stockOut, existing);
        stockOut.setUpdatedAt(new Date());
        stockOutMapper.update(stockOut);
        return findById(stockOut.getId());
    }

    /**
     * 逻辑删除出库单。
     */
    @Override
    public void delete(Long id) {
        StockOut stockOut = stockOutMapper.findById(id);
        if (stockOut == null) {
            throw new IllegalArgumentException("出库单不存在");
        }
        stockOut.setIsDeleted(1);
        stockOut.setUpdatedAt(new Date());
        stockOutMapper.update(stockOut);
    }

    /**
     * 给出库单填充出库明细。
     */
    private void fillItems(StockOut stockOut) {
        if (stockOut != null) {
            stockOut.setItems(stockOutItemMapper.findByStockOutId(stockOut.getId()));
        }
    }

    /**
     * 校验出库单主表字段。
     */
    private void validateStockOut(StockOut stockOut) {
        if (stockOut == null) {
            throw new IllegalArgumentException("出库单不能为空");
        }
        if (stockOut.getStockOutNo() == null || stockOut.getStockOutNo().isBlank()) {
            throw new IllegalArgumentException("出库单号不能为空");
        }
        if (stockOut.getStockOutType() == null || stockOut.getStockOutType().isBlank()) {
            throw new IllegalArgumentException("出库类型不能为空");
        }
        if (stockOut.getWarehouseId() == null) {
            throw new IllegalArgumentException("仓库ID不能为空");
        }
        if (warehouseMapper.findById(stockOut.getWarehouseId()) == null) {
            throw new IllegalArgumentException("仓库不存在");
        }
        if (stockOutMapper.findByStockOutNo(stockOut.getStockOutNo()) != null) {
            throw new IllegalArgumentException("出库单号已存在");
        }
        if (stockOut.getItems() == null || stockOut.getItems().isEmpty()) {
            throw new IllegalArgumentException("出库明细不能为空");
        }
    }

    /**
     * 校验出库明细字段。
     */
    private void validateStockOutItem(StockOutItem item) {
        if (item == null) {
            throw new IllegalArgumentException("出库明细不能为空");
        }
        if (item.getMaterialId() == null) {
            throw new IllegalArgumentException("物料ID不能为空");
        }
        if (item.getQuantity() == null || item.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("出库数量必须大于0");
        }
    }

    /**
     * 计算出库总数量。
     */
    private BigDecimal calculateTotalQuantity(List<StockOutItem> items) {
        BigDecimal total = BigDecimal.ZERO;
        for (StockOutItem item : items) {
            validateStockOutItem(item);
            total = total.add(item.getQuantity());
        }
        return total;
    }

    /**
     * 修改出库单时补齐未传字段，避免被空值覆盖。
     */
    private void mergeMissingFields(StockOut target, StockOut existing) {
        if (target.getStockOutNo() == null || target.getStockOutNo().isBlank()) {
            target.setStockOutNo(existing.getStockOutNo());
        }
        if (target.getStockOutType() == null || target.getStockOutType().isBlank()) {
            target.setStockOutType(existing.getStockOutType());
        }
        if (target.getWarehouseId() == null) {
            target.setWarehouseId(existing.getWarehouseId());
        }
        if (target.getSourceOrderId() == null) {
            target.setSourceOrderId(existing.getSourceOrderId());
        }
        if (target.getSourceOrderNo() == null) {
            target.setSourceOrderNo(existing.getSourceOrderNo());
        }
        if (target.getTotalQuantity() == null) {
            target.setTotalQuantity(existing.getTotalQuantity());
        }
        if (target.getStatus() == null || target.getStatus().isBlank()) {
            target.setStatus(existing.getStatus());
        }
        if (target.getRemark() == null) {
            target.setRemark(existing.getRemark());
        }
        if (target.getCreatedBy() == null) {
            target.setCreatedBy(existing.getCreatedBy());
        }
        target.setCreatedAt(existing.getCreatedAt());
        target.setIsDeleted(existing.getIsDeleted());
    }
}
