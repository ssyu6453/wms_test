package com.wsy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("t_inventory")
public class Inventory {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String productName;
    private BigDecimal price;
    private String specification;
    private String supplier;
    private String unit;
    private Integer initStockQty;
    private BigDecimal initStockAmount;
    private Integer currentStockQty;
    private Integer safeStockQty;
    private String remark;
    private Date createTime;
    private Date updateTime;
}
