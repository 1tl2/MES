package com.mouse.mes.mapper;

import com.mouse.mes.entity.StockOut;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface StockOutMapper {
    @org.apache.ibatis.annotations.Select("SELECT COALESCE(SUM(i.quantity),0) FROM t_stock_out o JOIN t_stock_out_item i ON i.stock_out_id=o.id WHERE o.stock_out_type='PRODUCTION_ISSUE' AND o.source_order_id=#{orderId} AND i.material_id=#{materialId}")
    java.math.BigDecimal issuedQuantity(@Param("orderId") Long orderId,@Param("materialId") Long materialId);
    List<StockOut> findAll();
    StockOut findById(@Param("id") Long id);
    StockOut findByStockOutNo(@Param("stockOutNo") String stockOutNo);
    int insert(StockOut stockOut);
    int update(StockOut stockOut);
    int delete(@Param("id") Long id);
}
