package com.mouse.mes.impl;

import com.mouse.mes.entity.Order;
import com.mouse.mes.entity.ProductionPlan;
import com.mouse.mes.entity.SalesOrder;
import com.mouse.mes.entity.SalesOrderItem;
import com.mouse.mes.mapper.OrderMapper;
import com.mouse.mes.mapper.ProductionPlanMapper;
import com.mouse.mes.mapper.SalesOrderItemMapper;
import com.mouse.mes.mapper.SalesOrderMapper;
import com.mouse.mes.service.ProductionPlanService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

@Service
public class ProductionPlanServiceImpl implements ProductionPlanService {

    private static final int ORDER_STATUS_CONFIRMED = 1;

    @Autowired
    private ProductionPlanMapper productionPlanMapper;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private SalesOrderMapper salesOrderMapper;

    @Autowired
    private SalesOrderItemMapper salesOrderItemMapper;

    @Override
    public List<ProductionPlan> findAll() {
        return productionPlanMapper.findAll();
    }

    @Override
    public ProductionPlan findById(Long id) {
        return productionPlanMapper.findById(id);
    }

    @Override
    public ProductionPlan findByPlanNo(String planNo) {
        return productionPlanMapper.findByPlanNo(planNo);
    }

    @Override
    public ProductionPlan create(ProductionPlan productionPlan) {
        if (productionPlan.getOrderId() != null) {
            List<ProductionPlan> existingPlans = productionPlanMapper.findByOrderId(productionPlan.getOrderId());
            if (existingPlans != null && !existingPlans.isEmpty()) {
                throw new RuntimeException("该订单已制定生产计划");
            }
        }
        productionPlan.setCreateTime(new Date());
        productionPlan.setUpdateTime(new Date());
        if (productionPlan.getPlanStatus() == null) {
            productionPlan.setPlanStatus(0);
        }
        if (productionPlan.getCompletedQuantity() == null) {
            productionPlan.setCompletedQuantity(0);
        }
        if (productionPlan.getPriority() == null) {
            productionPlan.setPriority(2);
        }
        productionPlanMapper.insert(productionPlan);
        markOrderConfirmed(productionPlan.getOrderId());
        return productionPlan;
    }

    @Override
    public ProductionPlan update(ProductionPlan productionPlan) {
        ProductionPlan existing = productionPlanMapper.findById(productionPlan.getId());
        if (existing == null) {
            throw new RuntimeException("生产计划不存在");
        }
        if (productionPlan.getPlanNo() != null) {
            existing.setPlanNo(productionPlan.getPlanNo());
        }
        if (productionPlan.getOrderId() != null) {
            existing.setOrderId(productionPlan.getOrderId());
        }
        if (productionPlan.getProductId() != null) {
            existing.setProductId(productionPlan.getProductId());
        }
        if (productionPlan.getProductName() != null) {
            existing.setProductName(productionPlan.getProductName());
        }
        if (productionPlan.getPlanQuantity() != null) {
            existing.setPlanQuantity(productionPlan.getPlanQuantity());
        }
        if (productionPlan.getCompletedQuantity() != null) {
            existing.setCompletedQuantity(productionPlan.getCompletedQuantity());
        }
        if (productionPlan.getStartDate() != null) {
            existing.setStartDate(productionPlan.getStartDate());
        }
        if (productionPlan.getEndDate() != null) {
            existing.setEndDate(productionPlan.getEndDate());
        }
        if (productionPlan.getPlanStatus() != null) {
            existing.setPlanStatus(productionPlan.getPlanStatus());
        }
        if (productionPlan.getPriority() != null) {
            existing.setPriority(productionPlan.getPriority());
        }
        existing.setUpdateTime(new Date());
        productionPlanMapper.update(existing);
        return productionPlanMapper.findById(productionPlan.getId());
    }

    @Override
    public void delete(Long id) {
        productionPlanMapper.delete(id);
    }

    @Override
    public List<ProductionPlan> findByStatus(Integer status) {
        return productionPlanMapper.findByStatus(status);
    }

    @Override
    public List<ProductionPlan> findByOrderId(Long orderId) {
        return productionPlanMapper.findByOrderId(orderId);
    }

    @Override
    @Transactional
    public ProductionPlan createFromOrder(Long orderId) {
        SalesOrder salesOrder = salesOrderMapper.findById(orderId);
        Order order = orderMapper.findById(orderId);

        if (salesOrder != null) {
            String status = salesOrder.getStatus();
            if (status == null || (!"CONFIRMED".equals(status) && !"1".equals(status))) {
                throw new RuntimeException("订单未确认，无法生成生产计划");
            }

            List<SalesOrderItem> items = salesOrderItemMapper.findBySoId(orderId);
            int totalQuantity = 0;
            String productName = salesOrder.getProductName() != null ? salesOrder.getProductName() : salesOrder.getSoNo();
            Long productId = salesOrder.getProductId() != null ? salesOrder.getProductId() : 1L;
            
            if (items != null && !items.isEmpty()) {
                SalesOrderItem firstItem = items.get(0);
                if (firstItem.getProductId() != null) {
                    productId = firstItem.getProductId();
                }
                for (SalesOrderItem item : items) {
                    if (item.getQuantity() != null) {
                        totalQuantity += item.getQuantity().intValue();
                    }
                }
            }

            if (totalQuantity == 0 && salesOrder.getQuantity() != null) {
                totalQuantity = salesOrder.getQuantity();
            }

            ProductionPlan plan = new ProductionPlan();
            plan.setPlanNo("PLAN" + System.currentTimeMillis());
            plan.setOrderId(orderId);
            plan.setProductId(productId);
            plan.setProductName(productName);
            plan.setPlanQuantity(totalQuantity > 0 ? totalQuantity : 1);
            plan.setCompletedQuantity(0);
            plan.setStartDate(new Date());
            plan.setEndDate(salesOrder.getDeliveryDate());
            plan.setPlanStatus(0);
            plan.setPriority(salesOrder.getPriority() != null ? salesOrder.getPriority() : 2);
            plan.setCreateBy(salesOrder.getCreatedBy() != null ? salesOrder.getCreatedBy() : 1L);
            plan.setCreateTime(new Date());
            plan.setUpdateTime(new Date());

            productionPlanMapper.insert(plan);
            return plan;
        }

        if (order != null) {
            if (order.getOrderStatus() != null && (order.getOrderStatus() == 3 || order.getOrderStatus() == 4)) {
                throw new RuntimeException("已完成或已取消订单不能生成生产计划");
            }

            ProductionPlan plan = new ProductionPlan();
            plan.setPlanNo("PP" + System.currentTimeMillis());
            plan.setOrderId(orderId);
            plan.setProductId(1L);
            plan.setProductName(order.getProductName());
            plan.setPlanQuantity(order.getQuantity());
            plan.setCompletedQuantity(0);
            plan.setStartDate(new Date());
            plan.setEndDate(order.getDeliveryDate());
            plan.setPlanStatus(0);
            plan.setPriority(order.getPriority());
            plan.setCreateBy(order.getCreateBy() != null ? order.getCreateBy() : 1L);
            plan.setCreateTime(new Date());
            plan.setUpdateTime(new Date());

            productionPlanMapper.insert(plan);
            markOrderConfirmed(orderId);
            return plan;
        }

        throw new RuntimeException("订单不存在");
    }

    /**
     * 生产计划创建成功后，来源销售订单进入“已确认”状态。
     */
    private void markOrderConfirmed(Long orderId) {
        if (orderId != null && orderMapper.findById(orderId) != null) {
            orderMapper.updateStatus(orderId, ORDER_STATUS_CONFIRMED, new Date());
        }
    }
    @Override
    public ProductionPlan getProgress(Long id) {
        ProductionPlan plan = productionPlanMapper.findById(id);
        if (plan == null) {
            throw new RuntimeException("生产计划不存在");
        }
        return plan;
    }
}