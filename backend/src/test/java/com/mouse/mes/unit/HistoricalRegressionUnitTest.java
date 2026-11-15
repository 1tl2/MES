package com.mouse.mes.unit;

import com.mouse.mes.controller.LoginLogController;
import com.mouse.mes.controller.OperationLogController;
import com.mouse.mes.entity.*;
import com.mouse.mes.impl.StockOutServiceImpl;
import com.mouse.mes.mapper.*;
import com.mouse.mes.service.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HistoricalRegressionUnitTest {
 StockOutServiceImpl stock; WorkOrderMapper work; BomMapper bom; StockOutMapper mapper;
 StockOutItemMapper items; InventoryService inventory; WorkOrder order;
 @BeforeEach void setup(){
  stock=new StockOutServiceImpl(); work=mock(WorkOrderMapper.class);bom=mock(BomMapper.class);mapper=mock(StockOutMapper.class);
  items=mock(StockOutItemMapper.class);inventory=mock(InventoryService.class);var warehouse=mock(WarehouseMapper.class);
  ReflectionTestUtils.setField(stock,"workOrderMapper",work);ReflectionTestUtils.setField(stock,"bomMapper",bom);
  ReflectionTestUtils.setField(stock,"stockOutMapper",mapper);ReflectionTestUtils.setField(stock,"stockOutItemMapper",items);
  ReflectionTestUtils.setField(stock,"inventoryService",inventory);ReflectionTestUtils.setField(stock,"warehouseMapper",warehouse);
  when(warehouse.findById(1L)).thenReturn(new Warehouse());
  order=new WorkOrder();order.setId(9L);order.setProductId(3L);order.setWoNo("WO-TEST");order.setPlannedQuantity(new BigDecimal("10"));order.setStatus("IN_PROGRESS");
  when(work.lockForIssue(9L)).thenReturn(order);var b=new Bom();b.setMaterialId(2L);b.setQuantity(new BigDecimal("2"));when(bom.findByProductId(3L)).thenReturn(List.of(b));
  when(mapper.issuedQuantity(9L,2L)).thenReturn(new BigDecimal("5"));when(inventory.getAvailableQuantity(eq(2L),eq(1L),any(),any())).thenReturn(new BigDecimal("100"));
 }
 StockOut request(String quantity){var o=new StockOut();o.setStockOutNo("REG");o.setStockOutType("PRODUCTION_ISSUE");o.setWarehouseId(1L);o.setSourceOrderId(9L);var i=new StockOutItem();i.setMaterialId(2L);i.setQuantity(new BigDecimal(quantity));o.setItems(List.of(i));return o;}
 @Test void issueNeedsWorkOrder(){var o=request("1");o.setSourceOrderId(null);assertThrows(IllegalArgumentException.class,()->stock.create(o));verify(mapper,never()).insert(any());}
 @Test void unknownWorkOrderRejected(){when(work.lockForIssue(9L)).thenReturn(null);assertThrows(IllegalArgumentException.class,()->stock.create(request("1")));}
 @Test void exceedsCumulativePlanRejectedWithoutWrites(){assertThrows(IllegalArgumentException.class,()->stock.create(request("16")));verify(mapper,never()).insert(any());verifyNoInteractions(items,inventory);}
 @Test void exactRemainingAllowanceAcceptedAndLinked(){var o=request("15");when(mapper.findById(any())).thenReturn(o);stock.create(o);assertEquals("WO-TEST",o.getSourceOrderNo());verify(mapper).insert(o);verify(inventory).decreaseStock(eq(2L),eq(1L),any(),any(),eq(new BigDecimal("15")),eq("PRODUCTION_ISSUE"),any(),eq("REG"),any(),any());}
 @Test void duplicateMaterialRowsAreSummed(){var o=request("10");o.setItems(List.of(o.getItems().get(0),o.getItems().get(0)));assertThrows(IllegalArgumentException.class,()->stock.create(o));verify(mapper,never()).insert(any());}
 @Test void nonBomMaterialRejected(){var o=request("1");o.getItems().get(0).setMaterialId(999L);assertThrows(IllegalArgumentException.class,()->stock.create(o));}
 @Test void closedOrderRejected(){order.setStatus("CANCELLED");assertThrows(IllegalArgumentException.class,()->stock.create(request("1")));}
 @Test void loginUserAndDateAreCombined(){var service=mock(LoginLogService.class);var c=new LoginLogController();ReflectionTestUtils.setField(c,"loginLogService",service);
  var a=new LoginLog();a.setUserId(1L);a.setUsername("甲");a.setLoginTime(com.mouse.mes.util.LogQuerySupport.bound("2026-09-21T23:59:59",false));
  var b=new LoginLog();b.setUserId(2L);b.setLoginTime(a.getLoginTime());var old=new LoginLog();old.setUserId(1L);old.setLoginTime(com.mouse.mes.util.LogQuerySupport.bound("2026-09-20",false));
  when(service.findAll()).thenReturn(List.of(a,b,old));assertEquals(List.of(a),c.getAllLoginLogs(1L,"甲",null,null,"2026-09-21","2026-09-21"));
 }
 @Test void operationFiltersCombineWithTime(){var service=mock(OperationLogService.class);var c=new OperationLogController();ReflectionTestUtils.setField(c,"operationLogService",service);
  var a=new OperationLog();a.setUserId(1L);a.setModule("库存");a.setOperation("新增");a.setOperateTime(com.mouse.mes.util.LogQuerySupport.bound("2026-09-21",false));
  var b=new OperationLog();b.setUserId(1L);b.setModule("库存");b.setOperation("删除");b.setOperateTime(a.getOperateTime());when(service.findAll()).thenReturn(List.of(a,b));
  assertEquals(List.of(a),c.getAllOperationLogs(1L,null,null,"库存","新增","2026-09-21","2026-09-21"));
 }
 @Test void invalidDateRejected(){assertThrows(org.springframework.web.server.ResponseStatusException.class,()->com.mouse.mes.util.LogQuerySupport.bound("not-a-date",false));}
}
