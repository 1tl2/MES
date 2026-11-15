package com.mouse.mes.controller;

import com.mouse.mes.entity.*;
import com.mouse.mes.service.*;
import com.mouse.mes.util.JsonResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.math.BigDecimal;

@RestController
@RequestMapping("/api/dashboard")
public class ProductionDashboardController {

    @Autowired
    private OrderService orderService;

    @Autowired
    private ProductionPlanService productionPlanService;

    @Autowired
    private ProductionTaskService productionTaskService;

    @Autowired
    private WorkOrderService workOrderService;

    @Autowired
    private ProductionReportService productionReportService;

    @Autowired
    private MaterialService materialService;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private QualityCheckService qualityCheckService;

    @Autowired
    private BomService bomService;

    @Autowired
    private StockInService stockInService;

    @Autowired
    private ProductService productService;

    /**
     * 全景看板数据接口
     * 返回前端 DashboardData 结构所需的所有数据
     */
    @GetMapping("/production")
    public JsonResult<Map<String, Object>> getProductionDashboard(
            @RequestParam(defaultValue = "") String date,
            @RequestParam(defaultValue = "day") String shift,
            @RequestParam(defaultValue = "all") String line) {

        Map<String, Object> result = new HashMap<>();

        // 1. filters
        Map<String, String> filters = new HashMap<>();
        filters.put("date", date);
        filters.put("shift", shift);
        filters.put("line", line);
        result.put("filters", filters);

        // 2. updatedAt
        result.put("updatedAt", new SimpleDateFormat("HH:mm:ss").format(new Date()));

        // 3. kpis
        List<WorkOrder> allWorkOrders = workOrderService.findAll();
        List<ProductionReport> allReports = productionReportService.findAll();
        List<ProductionPlan> allPlans = productionPlanService.findAll();
        List<Order> allOrders = orderService.findAll();
        List<Material> allMaterials = materialService.findAll();

        // Plan total / completed from plans (matching summary logic)
        int totalPlanQty = 0, totalCompletedQty = 0;
        for (ProductionPlan plan : allPlans) {
            totalPlanQty += plan.getPlanQuantity() != null ? plan.getPlanQuantity() : 0;
            totalCompletedQty += plan.getCompletedQuantity() != null ? plan.getCompletedQuantity() : 0;
        }

        // Production report totals
        int totalReportQty = 0, totalQualifiedQty = 0, totalDefectQty = 0;
        for (ProductionReport r : allReports) {
            totalReportQty += r.getReportQuantity() != null ? r.getReportQuantity() : 0;
            totalQualifiedQty += r.getQualifiedQuantity() != null ? r.getQualifiedQuantity() : 0;
            totalDefectQty += r.getDefectQuantity() != null ? r.getDefectQuantity() : 0;
        }

        // Active work orders (not completed)
        long activeWoCount = allWorkOrders.stream()
                .filter(wo -> !"COMPLETED".equals(wo.getStatus()))
                .count();

        // Alerts count (inventory low stock + material below safety)
        long lowStockCount = allMaterials.stream()
                .filter(m -> m.getCurrentStock() != null && m.getSafetyStock() != null
                        && m.getCurrentStock() < m.getSafetyStock())
                .count();

        double completionRate = totalPlanQty > 0 ? (double) totalCompletedQty / totalPlanQty * 100 : 0;
        double passRate = totalReportQty > 0 ? (double) totalQualifiedQty / totalReportQty * 100 : 100;

        List<Map<String, Object>> kpis = new ArrayList<>();
        kpis.add(kpiMap("plan", "今日计划产量", (double) totalPlanQty, "只", "blue"));
        kpis.add(kpiMap("done", "今日完成产量", (double) totalCompletedQty, "只", "green"));
        kpis.add(kpiMap("rate", "计划达成率", completionRate, "%", "yellow", 1));
        kpis.add(kpiMap("pass", "一次合格率", passRate, "%", "green", 1));
        kpis.add(kpiMap("active", "在制工单数", (double) activeWoCount, "单", "blue"));
        kpis.add(kpiMap("alerts", "缺料与异常数", (double) lowStockCount, "条", "red"));
        result.put("kpis", kpis);

        // 4. hourlyOutput — from ProductionReport grouped by hour
        Map<String, Map<String, Integer>> hourlyMap = new LinkedHashMap<>();
        SimpleDateFormat hourFmt = new SimpleDateFormat("HH");
        for (ProductionReport r : allReports) {
            if (r.getReportTime() == null) continue;
            String h = hourFmt.format(r.getReportTime());
            hourlyMap.putIfAbsent(h, new HashMap<>());
            Map<String, Integer> hm = hourlyMap.get(h);
            int actual = hm.getOrDefault("actual", 0) + (r.getReportQuantity() != null ? r.getReportQuantity() : 0);
            hm.put("actual", actual);
            // plan from tasks — approximate by even distribution, using total plan / 10 hours
        }
        // Spread plan across hours 08-17 (typical work hours)
        int planPerHour = totalPlanQty > 0 ? (int) Math.ceil((double) totalPlanQty / 10) : 0;
        List<Map<String, Object>> hourlyOutput = new ArrayList<>();
        for (int h = 8; h <= 17; h++) {
            String hh = String.format("%02d", h);
            int actual = 0;
            if (hourlyMap.containsKey(hh)) {
                actual = hourlyMap.get(hh).getOrDefault("actual", 0);
            }
            Map<String, Object> ho = new HashMap<>();
            ho.put("hour", hh);
            ho.put("plan", planPerHour);
            ho.put("actual", actual);
            hourlyOutput.add(ho);
        }
        result.put("hourlyOutput", hourlyOutput);

        // 5. workOrderStatus
        Map<String, Integer> statusCount = new LinkedHashMap<>();
        for (WorkOrder wo : allWorkOrders) {
            String st = wo.getStatus() != null ? wo.getStatus() : "UNKNOWN";
            statusCount.merge(st, 1, Integer::sum);
        }
        List<Map<String, Object>> workOrderStatusList = new ArrayList<>();
        for (Map.Entry<String, Integer> e : statusCount.entrySet()) {
            Map<String, Object> s = new HashMap<>();
            s.put("label", statusLabel(e.getKey()));
            s.put("value", e.getValue());
            s.put("tone", statusTone(e.getKey()));
            workOrderStatusList.add(s);
        }
        result.put("workOrderStatus", workOrderStatusList);

        // 6. materialReadinessRate + materialShortages
        int totalMaterials = allMaterials.size();
        long inStockCount = allMaterials.stream()
                .filter(m -> m.getCurrentStock() != null && m.getSafetyStock() != null
                        && m.getCurrentStock() >= m.getSafetyStock())
                .count();
        double readinessRate = totalMaterials > 0 ? (double) inStockCount / totalMaterials * 100 : 100;
        result.put("materialReadinessRate", readinessRate);

        List<Map<String, Object>> materialShortages = new ArrayList<>();
        for (Material m : allMaterials) {
            if (m.getCurrentStock() != null && m.getSafetyStock() != null
                    && m.getCurrentStock() < m.getSafetyStock()) {
                Map<String, Object> ms = new HashMap<>();
                ms.put("materialName", m.getMaterialName());
                int shortage = m.getSafetyStock() - m.getCurrentStock();
                ms.put("shortageQuantity", shortage);
                ms.put("incomingQuantity", 0); // incoming not tracked in material table
                ms.put("unit", m.getUnit() != null ? m.getUnit() : "个");
                materialShortages.add(ms);
            }
        }
        result.put("materialShortages", materialShortages);

        // 7. flowNodes — production flow nodes with counts from each module
        List<QualityCheck> allQc = qualityCheckService.findAll();
        List<StockIn> allStockIn = stockInService.findAll();
        List<Bom> allBoms = bomService.findAll();

        List<Map<String, Object>> flowNodes = new ArrayList<>();
        flowNodes.add(flowNodeMap("order", "客户订单", allOrders.size(), calcCompletionRate(allOrders, o -> {
            Order ord = (Order) o;
            return ord.getOrderStatus() != null && ord.getOrderStatus() >= 3;
        }), statusToneFromRate(calcCompletionRate(allOrders, o -> {
            Order ord = (Order) o;
            return ord.getOrderStatus() != null && ord.getOrderStatus() >= 3;
        }))));
        flowNodes.add(flowNodeMap("plan", "生产计划", allPlans.size(), calcCompletionRate(allPlans, p -> {
            ProductionPlan pp = (ProductionPlan) p;
            return pp.getPlanStatus() != null && pp.getPlanStatus() >= 2;
        }), statusToneFromRate(calcCompletionRate(allPlans, p -> {
            ProductionPlan pp = (ProductionPlan) p;
            return pp.getPlanStatus() != null && pp.getPlanStatus() >= 2;
        }))));
        flowNodes.add(flowNodeMap("bom", "BOM齐套", allBoms.size(), calcCompletionRate(allBoms, b -> true),
                countShortageMaterials(allMaterials) > 0 ? "warning" : "normal"));
        flowNodes.add(flowNodeMap("work", "生产工单", allWorkOrders.size(), calcCompletionRate(allWorkOrders, wo -> {
            WorkOrder w = (WorkOrder) wo;
            return "COMPLETED".equals(w.getStatus());
        }), statusToneFromRate(calcCompletionRate(allWorkOrders, wo -> {
            WorkOrder w = (WorkOrder) wo;
            return "COMPLETED".equals(w.getStatus());
        }))));
        flowNodes.add(flowNodeMap("report", "过程报工", allReports.size(), calcCompletionRate(allReports, r -> true), "normal"));
        flowNodes.add(flowNodeMap("qc", "质量检验", allQc.size(), calcCompletionRate(allQc, qc -> {
            QualityCheck q = (QualityCheck) qc;
            return q.getCheckResult() != null && q.getCheckResult() == 1;
        }), statusToneFromRate(calcCompletionRate(allQc, qc -> {
            QualityCheck q = (QualityCheck) qc;
            return q.getCheckResult() != null && q.getCheckResult() == 1;
        }))));
        flowNodes.add(flowNodeMap("stock", "成品入库", allStockIn.size(), calcCompletionRate(allStockIn, si -> {
            StockIn s = (StockIn) si;
            return "COMPLETED".equals(s.getStatus()) || "DONE".equals(s.getStatus());
        }), statusToneFromRate(calcCompletionRate(allStockIn, si -> {
            StockIn s = (StockIn) si;
            return "COMPLETED".equals(s.getStatus()) || "DONE".equals(s.getStatus());
        }))));
        result.put("flowNodes", flowNodes);

        // 8. workOrders — convert WorkOrder entities and Order entities to frontend WorkOrderRow format
        List<Product> allProducts = getAllProducts();
        List<Map<String, Object>> workOrderRows = new ArrayList<>();
        
        // Add customer orders first
        for (Order order : allOrders) {
            Map<String, Object> row = new HashMap<>();
            row.put("id", order.getId());
            row.put("orderNo", order.getOrderNo() != null ? order.getOrderNo() : String.valueOf(order.getId()));
            row.put("productName", order.getProductName() != null ? order.getProductName() : "未知产品");
            row.put("quantity", order.getQuantity() != null ? order.getQuantity().doubleValue() : 0);
            row.put("status", order.getOrderStatus() != null ? order.getOrderStatus() : 0);
            row.put("statusLabel", orderStatusLabel(order.getOrderStatus()));
            row.put("statusTone", orderStatusTone(order.getOrderStatus()));
            double progress = 0;
            if (order.getQuantity() != null && order.getQuantity() > 0) {
                progress = order.getOrderStatus() != null && order.getOrderStatus() >= 3 ? 100 : 
                           order.getOrderStatus() != null && order.getOrderStatus() >= 2 ? 50 : 0;
            }
            row.put("progress", progress);
            row.put("priority", order.getPriority() != null ? order.getPriority() : 2);
            row.put("deliveryDate", order.getDeliveryDate() != null
                    ? new SimpleDateFormat("yyyy/M/dd HH:mm").format(order.getDeliveryDate()) : "");
            row.put("nodeKey", "order");
            workOrderRows.add(row);
        }
        
        // Add work orders
        for (WorkOrder wo : allWorkOrders) {
            Map<String, Object> row = new HashMap<>();
            row.put("id", wo.getId());
            row.put("orderNo", wo.getWoNo() != null ? wo.getWoNo() : String.valueOf(wo.getId()));
            String productModel = findProductModel(wo.getProductId(), allProducts);
            row.put("productName", productModel);
            row.put("quantity", wo.getPlannedQuantity() != null ? wo.getPlannedQuantity().doubleValue() : 0);
            row.put("status", wo.getStatus());
            row.put("statusLabel", statusLabel(wo.getStatus()));
            row.put("statusTone", statusTone(wo.getStatus()));
            double progress = 0;
            if (wo.getPlannedQuantity() != null && wo.getPlannedQuantity().compareTo(BigDecimal.ZERO) > 0 && wo.getActualQuantity() != null) {
                progress = wo.getActualQuantity().doubleValue() / wo.getPlannedQuantity().doubleValue() * 100;
            }
            row.put("progress", progress);
            row.put("priority", 2);
            row.put("deliveryDate", wo.getPlannedEndDate() != null
                    ? new SimpleDateFormat("yyyy/M/dd HH:mm").format(wo.getPlannedEndDate()) : "");
            row.put("nodeKey", nodeKeyFromStatus(wo.getStatus()));
            workOrderRows.add(row);
        }
        result.put("workOrders", workOrderRows);

        // 9. qualityTrend — from ProductionReport grouped by hour with qualified/defect
        Map<String, Map<String, Integer>> qualityHourlyMap = new LinkedHashMap<>();
        for (ProductionReport r : allReports) {
            if (r.getReportTime() == null) continue;
            String h = hourFmt.format(r.getReportTime());
            qualityHourlyMap.putIfAbsent(h, new HashMap<>());
            Map<String, Integer> hm = qualityHourlyMap.get(h);
            hm.put("passed", hm.getOrDefault("passed", 0) + (r.getQualifiedQuantity() != null ? r.getQualifiedQuantity() : 0));
            hm.put("failed", hm.getOrDefault("failed", 0) + (r.getDefectQuantity() != null ? r.getDefectQuantity() : 0));
        }
        List<Map<String, Object>> qualityTrend = new ArrayList<>();
        for (int h = 8; h <= 17; h++) {
            String hh = String.format("%02d", h);
            if (qualityHourlyMap.containsKey(hh)) {
                Map<String, Object> qt = new HashMap<>();
                qt.put("label", hh);
                qt.put("passed", qualityHourlyMap.get(hh).getOrDefault("passed", 0));
                qt.put("failed", qualityHourlyMap.get(hh).getOrDefault("failed", 0));
                qualityTrend.add(qt);
            }
        }
        result.put("qualityTrend", qualityTrend);

        // 10. defectRanks — from QualityCheck and ProductionReport defect data
        List<Map<String, Object>> defectRanks = new ArrayList<>();
        // Aggregate defect descriptions from QualityCheck
        Map<String, Integer> defectCountMap = new LinkedHashMap<>();
        for (QualityCheck qc : allQc) {
            if (qc.getDefectDesc() != null && !qc.getDefectDesc().isEmpty()) {
                defectCountMap.merge(qc.getDefectDesc(), qc.getDefectQuantity() != null ? qc.getDefectQuantity() : 1, Integer::sum);
            }
            // Also count from defectQuantity field even if no description
            if (qc.getDefectQuantity() != null && qc.getDefectQuantity() > 0) {
                String desc = qc.getDefectDesc() != null && !qc.getDefectDesc().isEmpty()
                        ? qc.getDefectDesc() : "其他缺陷";
                defectCountMap.merge(desc, qc.getDefectQuantity(), Integer::sum);
            }
        }
        // Sort by quantity descending
        defectCountMap.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(10)
                .forEach(e -> {
                    Map<String, Object> dr = new HashMap<>();
                    dr.put("defectType", e.getKey());
                    dr.put("quantity", e.getValue());
                    defectRanks.add(dr);
                });
        if (defectRanks.isEmpty() && totalDefectQty > 0) {
            Map<String, Object> dr = new HashMap<>();
            dr.put("defectType", "生产缺陷");
            dr.put("quantity", totalDefectQty);
            defectRanks.add(dr);
        }
        result.put("defectRanks", defectRanks);

        // 11. inventory — from material/inventory data
        List<Inventory> allInventory = inventoryService.findAll();
        double rawMatQty = 0, wipQty = 0, finishedQty = 0;
        for (Inventory inv : allInventory) {
            if (inv.getQuantity() != null) {
                // Approximate classification by material type
                wipQty += inv.getQuantity().doubleValue();
            }
        }
        for (Material mat : allMaterials) {
            if (mat.getCurrentStock() != null) {
                if (mat.getMaterialType() != null && mat.getMaterialType() == 0) {
                    rawMatQty += mat.getCurrentStock();
                } else {
                    finishedQty += mat.getCurrentStock();
                }
            }
        }
        Map<String, Object> inventory = new HashMap<>();
        inventory.put("rawMaterial", rawMatQty);
        inventory.put("wip", wipQty);
        inventory.put("finishedGoods", finishedQty);
        inventory.put("lowStockCount", lowStockCount);
        result.put("inventory", inventory);

        // 12. alerts — from low stock materials
        List<Map<String, Object>> alerts = new ArrayList<>();
        int alertId = 0;
        for (Material m : allMaterials) {
            if (m.getCurrentStock() != null && m.getSafetyStock() != null
                    && m.getCurrentStock() < m.getSafetyStock()) {
                alertId++;
                Map<String, Object> alert = new HashMap<>();
                alert.put("id", "ALERT-" + String.format("%03d", alertId));
                alert.put("time", new SimpleDateFormat("HH:mm").format(new Date()));
                alert.put("type", "error");
                alert.put("title", m.getMaterialName() + "库存不足");
                int shortage = m.getSafetyStock() - m.getCurrentStock();
                alert.put("content", m.getMaterialName() + " 库存 " + m.getCurrentStock()
                        + "，低于安全库存 " + m.getSafetyStock() + "，缺少 " + shortage + " " + (m.getUnit() != null ? m.getUnit() : "个"));
                alerts.add(alert);
            }
        }
        // Also add warning for active overdue work orders
        for (WorkOrder wo : allWorkOrders) {
            if ("IN_PROGRESS".equals(wo.getStatus()) && wo.getPlannedEndDate() != null
                    && wo.getPlannedEndDate().before(new Date())) {
                alertId++;
                Map<String, Object> alert = new HashMap<>();
                alert.put("id", "ALERT-" + String.format("%03d", alertId));
                alert.put("time", new SimpleDateFormat("HH:mm").format(new Date()));
                alert.put("type", "warning");
                alert.put("title", "工单存在延期风险");
                alert.put("content", "工单 " + wo.getWoNo() + " 计划完成时间 "
                        + new SimpleDateFormat("yyyy/M/dd HH:mm").format(wo.getPlannedEndDate()) + "，已超期");
                alerts.add(alert);
            }
        }
        result.put("alerts", alerts);

        return JsonResult.success(result);
    }

    // ============== Helper methods ==============

    private Map<String, Object> kpiMap(String key, String label, double value, String unit, String tone) {
        Map<String, Object> m = new HashMap<>();
        m.put("key", key);
        m.put("label", label);
        m.put("value", value);
        m.put("unit", unit);
        m.put("tone", tone);
        return m;
    }

    private Map<String, Object> kpiMap(String key, String label, double value, String unit, String tone, int precision) {
        Map<String, Object> m = kpiMap(key, label, value, unit, tone);
        m.put("precision", precision);
        return m;
    }

    private Map<String, Object> flowNodeMap(String key, String name, int count, double rate, String status) {
        Map<String, Object> m = new HashMap<>();
        m.put("key", key);
        m.put("name", name);
        m.put("count", count);
        m.put("completionRate", rate);
        m.put("status", status);
        return m;
    }

    @SuppressWarnings("unchecked")
    private <T> double calcCompletionRate(List<T> items, java.util.function.Predicate<T> completedPred) {
        if (items == null || items.isEmpty()) return 100;
        long completed = items.stream().filter(completedPred).count();
        return (double) completed / items.size() * 100;
    }

    private String statusToneFromRate(double rate) {
        if (rate >= 80) return "normal";
        if (rate >= 50) return "warning";
        return "error";
    }

    private long countShortageMaterials(List<Material> materials) {
        return materials.stream()
                .filter(m -> m.getCurrentStock() != null && m.getSafetyStock() != null
                        && m.getCurrentStock() < m.getSafetyStock())
                .count();
    }

    private String statusLabel(String status) {
        if (status == null) return "未知";
        switch (status) {
            case "PLANNED": return "待下发";
            case "ASSIGNED": return "已分配";
            case "IN_PROGRESS": return "生产中";
            case "COMPLETED": return "已完成";
            case "PENDING_QC": return "待质检";
            case "SUSPENDED": return "暂停";
            default: return status;
        }
    }

    private String orderStatusLabel(Integer status) {
        if (status == null) return "未知";
        switch (status) {
            case 0: return "待确认";
            case 1: return "已确认";
            case 2: return "已排产";
            case 3: return "已完成";
            default: return "未知";
        }
    }

    private String statusTone(String status) {
        if (status == null) return "gray";
        switch (status) {
            case "PLANNED":
            case "ASSIGNED": return "yellow";
            case "IN_PROGRESS": return "blue";
            case "PENDING_QC": return "yellow";
            case "COMPLETED": return "green";
            case "SUSPENDED": return "red";
            default: return "gray";
        }
    }

    private String orderStatusTone(Integer status) {
        if (status == null) return "gray";
        switch (status) {
            case 0: return "yellow";
            case 1: return "green";
            case 2: return "blue";
            case 3: return "green";
            default: return "gray";
        }
    }

    private String nodeKeyFromStatus(String status) {
        if (status == null) return "work";
        switch (status) {
            case "PLANNED":
            case "ASSIGNED": return "plan";
            case "IN_PROGRESS": return "work";
            case "PENDING_QC": return "qc";
            case "COMPLETED": return "stock";
            case "SUSPENDED": return "bom";
            default: return "work";
        }
    }

    private String findProductModel(Long productId, List<Product> products) {
        if (productId == null || products == null) return "未知产品";
        return products.stream()
                .filter(p -> p.getId().equals(productId))
                .findFirst()
                .map(Product::getProductName)
                .orElse("未知产品");
    }

    private List<Product> getAllProducts() {
        try {
            return productService.findAll();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    @GetMapping("/summary")
    public JsonResult<Map<String, Object>> getSummary() {
        Map<String, Object> summary = new HashMap<>();

        List<Order> orders = orderService.findAll();
        int pendingOrders = 0, confirmedOrders = 0, producingOrders = 0, completedOrders = 0;
        for (Order order : orders) {
            if (order.getOrderStatus() == 0) pendingOrders++;
            else if (order.getOrderStatus() == 1) confirmedOrders++;
            else if (order.getOrderStatus() == 2) producingOrders++;
            else if (order.getOrderStatus() == 3) completedOrders++;
        }
        Map<String, Integer> orderStats = new HashMap<>();
        orderStats.put("pending", pendingOrders);
        orderStats.put("confirmed", confirmedOrders);
        orderStats.put("producing", producingOrders);
        orderStats.put("completed", completedOrders);
        summary.put("orders", orderStats);

        List<ProductionPlan> plans = productionPlanService.findAll();
        int pendingPlans = 0, inProgressPlans = 0, completedPlans = 0;
        int totalPlanQuantity = 0, totalCompletedQuantity = 0;
        for (ProductionPlan plan : plans) {
            if (plan.getPlanStatus() == 0) pendingPlans++;
            else if (plan.getPlanStatus() == 1) inProgressPlans++;
            else if (plan.getPlanStatus() == 2) completedPlans++;
            totalPlanQuantity += plan.getPlanQuantity() != null ? plan.getPlanQuantity() : 0;
            totalCompletedQuantity += plan.getCompletedQuantity() != null ? plan.getCompletedQuantity() : 0;
        }
        Map<String, Object> planStats = new HashMap<>();
        planStats.put("pending", pendingPlans);
        planStats.put("inProgress", inProgressPlans);
        planStats.put("completed", completedPlans);
        planStats.put("totalQuantity", totalPlanQuantity);
        planStats.put("completedQuantity", totalCompletedQuantity);
        planStats.put("completionRate", totalPlanQuantity > 0 ? (double) totalCompletedQuantity / totalPlanQuantity * 100 : 0);
        summary.put("plans", planStats);

        List<WorkOrder> workOrders = workOrderService.findAll();
        int plannedWorkOrders = 0, assignedWorkOrders = 0, inProgressWorkOrders = 0, completedWorkOrders = 0;
        for (WorkOrder wo : workOrders) {
            if ("PLANNED".equals(wo.getStatus())) plannedWorkOrders++;
            else if ("ASSIGNED".equals(wo.getStatus())) assignedWorkOrders++;
            else if ("IN_PROGRESS".equals(wo.getStatus())) inProgressWorkOrders++;
            else if ("COMPLETED".equals(wo.getStatus())) completedWorkOrders++;
        }
        Map<String, Integer> workOrderStats = new HashMap<>();
        workOrderStats.put("planned", plannedWorkOrders);
        workOrderStats.put("assigned", assignedWorkOrders);
        workOrderStats.put("inProgress", inProgressWorkOrders);
        workOrderStats.put("completed", completedWorkOrders);
        summary.put("workOrders", workOrderStats);

        List<ProductionTask> tasks = productionTaskService.findAll();
        summary.put("totalTasks", tasks.size());

        return JsonResult.success(summary);
    }

    @GetMapping("/recent-orders")
    public JsonResult<List<Order>> getRecentOrders() {
        List<Order> orders = orderService.findAll();
        return JsonResult.success(orders.size() > 10 ? orders.subList(0, 10) : orders);
    }

    @GetMapping("/recent-plans")
    public JsonResult<List<ProductionPlan>> getRecentPlans() {
        List<ProductionPlan> plans = productionPlanService.findAll();
        return JsonResult.success(plans.size() > 10 ? plans.subList(0, 10) : plans);
    }

    @GetMapping("/recent-work-orders")
    public JsonResult<List<WorkOrder>> getRecentWorkOrders() {
        List<WorkOrder> workOrders = workOrderService.findAll();
        return JsonResult.success(workOrders.size() > 10 ? workOrders.subList(0, 10) : workOrders);
    }
}