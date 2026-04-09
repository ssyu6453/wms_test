package com.wsy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("t_purchase")
public class Purchase {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private Date purchaseDate;
    private String productName;
    private BigDecimal price;
    private String specification;
    private String counterpartyUnit;
    private String unit;
    private Integer purchaseQty;
    private BigDecimal purchaseAmount;
    private Integer isInvoiced;
    private String invoiceNo;
    private BigDecimal paidAmount;
    private BigDecimal unpaidAmount;
    private String purpose;
    private Date paymentDate;
    private Date inboundDate;
    private String registerRemark;
    private BigDecimal trafficFee;
    private BigDecimal totalAmount;
    private Integer inventoryId;
    private String status;
    private String operator;
    private String remark;
    private Date receiveTime;
    private Date createTime;
}
