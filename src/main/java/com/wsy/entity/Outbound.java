package com.wsy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("t_outbound")
public class Outbound {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String productName;
    private BigDecimal price;
    private String specification;
    private String supplier;
    private String unit;
    private Integer inventoryId;
    private Date outboundDate;
    private Integer outboundQty;
    private BigDecimal outboundAmount;
    private String purpose;
    private String operator;
    private String remark;
    private Date createTime;
}
