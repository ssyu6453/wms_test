package com.wsy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("t_inbound")
public class Inbound {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String productName;
    private BigDecimal price;
    private String specification;
    private String supplier;
    private String unit;
    private Integer initStockQty;
    private Integer inventoryId;
    private Date inboundDate;
    private String certNo;
    private Integer inboundQty;
    private BigDecimal inboundAmount;
    private String productionDate;  // 改为 String，支持各种格式如 "2024.03"
    private String validDate;       // 改为 String，支持各种格式
    private String operator;
    private String remark;
    private Date createTime;
}
